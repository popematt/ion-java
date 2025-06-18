package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*
import java.nio.ByteBuffer
import java.util.*

/**
 * Reads the raw E-expression arguments
 *
 * TODO: Make this update the parent reader's position on close, just like the delimited seq reader
 */
class EExpArgumentReaderImpl(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
    macroTable: Array<MacroV2>,
    @JvmField
    var parent: ValueReaderBase,
): ValueReaderBase(source, pool, symbolTable, macroTable), ArgumentReader {

    override var signature: Array<Macro.Parameter> = emptyArray()
        private set

    // 0, 1, or 2
    @JvmField
    var presence = IntArray(8)
    // The position of a "not present" argument is always the first byte after the last byte of the previous argument
    @JvmField
    var argumentIndices = IntArray(8)
    // This is specifically, the index of the parameter within the macro signature, not within the ByteBuffer.
    private var nextParameterIndex = 0

    override fun close() {
        seekToBeforeArgument(signature.size)
        parent.seekTo(source.position())
        pool.eexpArgumentReaders.add(this)
    }

    fun initArgs(signature: Array<Macro.Parameter>) {
        this.signature = signature
        this.nextParameterIndex = 0
        // Make sure we have enough space in our arrays
        if (signature.size > argumentIndices.size) {
            // The last entry is the first position after the arguments.
            argumentIndices = IntArray(signature.size + 1)
            presence = IntArray(signature.size)
        }
        var presenceByteOffset = 8
        var presenceByte = 0

        Arrays.fill(argumentIndices, -1)

        var i = 0
        while (i < signature.size) {
            val parameter = signature[i]
            if (parameter.iCardinality != 1) {
                // Read a value from the presence bitmap
                // But we might need to "refill" our presence byte first
                if (presenceByteOffset > 7) {
                    presenceByte = source.get().toInt() and 0xFF
                    presenceByteOffset = 0
                }
                presence[i] = (presenceByte ushr presenceByteOffset) and 0b11
                presenceByteOffset += 2
            } else {
                // Or set it to the implied "1" value
                presence[i] = 1
            }
            i++
        }

//        for (i in signature.indices) {
//            if (signature[i].iCardinality != 1) {
//                // Read a value from the presence bitmap
//                // But we might need to "refill" our presence byte first
//                if (presenceByteOffset > 7) {
//                    presenceByte = source.get().toInt() and 0xFF
//                    presenceByteOffset = 0
//                }
//                presence[i] = (presenceByte ushr presenceByteOffset) and 0b11
//                presenceByteOffset += 2
//            } else {
//                // Or set it to the implied "1" value
//                presence[i] = 1
//            }
//        }

        // Set the first argument to the current position. That's where it will be, if it is present.
        argumentIndices[0] = source.position()
    }

    // FIXME: The skipping in this method is ~14% of ALL
    fun calculateEndPosition(): Int {
        source.mark()
        for (i in signature.indices) {
            nextParameterIndex = i
            val argPosition = source.position()
            if (presence[i] > 0) {
                if (nextToken() != TokenTypeConst.END) {
                    skip()
                }
            }
            argumentIndices[i] = argPosition
        }
        val endPosition = source.position()
        source.reset()
        source.limit(endPosition)
        return endPosition
    }

    override fun seekToBeforeArgument(signatureIndex: Int) {
        if (signatureIndex > signature.size) throw IllegalArgumentException("signatureIndex is greater than signature size")
        nextParameterIndex = signatureIndex

        var argumentPosition = argumentIndices[signatureIndex]
        if (argumentPosition < 0) {
            // TODO: See if this is faster if it uses a loop instead of recursion.
            // TODO: If it's faster to calculate all of the argument positions up front, then consider adding a reader
            //       option that allows users to decide between eager and lazy argument calculations.
            seekToBeforeArgument(signatureIndex - 1)
            nextToken()
            skip()
            argumentPosition = source.position()
            argumentIndices[signatureIndex] = argumentPosition
        } else {
            source.position(argumentPosition)
        }
        opcode = TID_UNSET
    }

    override fun seekToArgument(signatureIndex: Int): Int {
        seekToBeforeArgument(signatureIndex)
        return nextToken()
    }

    override fun nextToken(): Int {
        val cpIndex = nextParameterIndex
        if (cpIndex >= signature.size) {
            opcode = TID_END
            return TokenTypeConst.END
        }
        if (argumentIndices[cpIndex] < 0) {
            argumentIndices[cpIndex] = source.position()
        }

        nextParameterIndex++

        val currentParameter = signature[cpIndex]
        val presence = presence[cpIndex]

        return when (presence) {
            0 -> {
                opcode = TID_EMPTY_ARGUMENT
                TokenTypeConst.ABSENT_ARGUMENT
            }
            1 -> {
                // Assuming tagged
                super.nextToken()
            }
            2 -> {
                opcode = TID_EXPRESSION_GROUP
                TokenTypeConst.EXPRESSION_GROUP
            }
            else -> throw IonException("Invalid presence bits for parameter: $currentParameter")
        }
    }

    override fun nullValue() = withArgIndexUpdates { super.nullValue() }
    override fun booleanValue() = withArgIndexUpdates { super.booleanValue() }
    override fun longValue() = withArgIndexUpdates { super.longValue() }
    override fun doubleValue() = withArgIndexUpdates { super.doubleValue() }
    override fun decimalValue() = withArgIndexUpdates { super.decimalValue() }
    override fun timestampValue() = withArgIndexUpdates { super.timestampValue() }
    override fun stringValue() = withArgIndexUpdates { super.stringValue() }
    override fun symbolValue() = withArgIndexUpdates { super.symbolValue() }
    override fun symbolValueSid() = withArgIndexUpdates { super.symbolValueSid() }
    override fun clobValue() = withArgIndexUpdates { super.clobValue() }
    override fun blobValue() = withArgIndexUpdates { super.blobValue() }

    override fun expressionGroup(): SequenceReader {
        val opcode = this.opcode
        if (opcode == TID_EMPTY_ARGUMENT) {
            this.opcode = TID_UNSET
            return NoneReader
        }
        if (opcode != TID_EXPRESSION_GROUP) throw IonException("Not positioned on an expression group")
        this.opcode = TID_UNSET

        val length = IntHelper.readFlexUInt(source)
        val position = source.position()
        // TODO: Check if we're on a tagless parameter.
        //       For now, we'll assume that they are all tagged.
        return if (length == 0) {
            // TODO: We might be able to get the length based on the argument indices.
            pool.getDelimitedSequence(position, this, symbolTable, macroTable)
        } else {
            source.position(position + length)
            updateNextArgIndex()
            pool.getList(position, length, symbolTable, macroTable)
        }
    }

    // reuse regular sequence reader for tagged groups
    // returns tagless expression group reader for other groups

    override fun skip() {
        val nextArgPosition = argumentIndices[nextParameterIndex]
        if (nextArgPosition >= 0) {
            source.position(nextArgPosition)
            opcode = TID_UNSET
            return
        }

        // TODO: If we know which parameter we're on, we can check to see if the start of the next parameter is already known.
        when (opcode) {
            TID_EMPTY_ARGUMENT -> {
                // Nothing to skip.
            }
            TID_EXPRESSION_GROUP -> {
                // Assuming tagged.
                val length = IdMappings.length(TID_EXPRESSION_GROUP.toInt(), source)
                if (length > 0) {
                    source.position(source.position() + length)
                } else {
                    val position = source.position()
                    val sacrificialReader = pool.getDelimitedSequence(position, this, symbolTable, macroTable)
                    while (sacrificialReader.nextToken() != TokenTypeConst.END) { sacrificialReader.skip() };
                    sacrificialReader.close()
                }
            }
            else -> super.skip()
        }
        updateNextArgIndex()
    }

    private inline fun <T> withArgIndexUpdates(action: () -> T): T {
        val value = action()
        updateNextArgIndex()
        return value
    }

    private fun updateNextArgIndex() {
        val argumentIndices = argumentIndices
        if (argumentIndices[nextParameterIndex] < 0) {
            // TODO: Can we automatically handle args that are not present?
            argumentIndices[nextParameterIndex] = source.position()
        }
    }
}
