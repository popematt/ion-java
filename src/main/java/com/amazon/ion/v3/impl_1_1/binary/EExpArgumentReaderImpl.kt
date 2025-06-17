package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.IonException
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*
import java.nio.ByteBuffer
import java.util.*

/**
 * Reads the raw E-expression arguments
 */
class EExpArgumentReaderImpl(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
    macroTable: Array<MacroV2>,
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
//        set(value) {
//            // val e = Exception()
//            //println("[$id] setting nextParameterIndex $field --> $value\n    " + e.stackTrace.take(5).joinToString("\n    "))
//            field = value
//        }

    override fun close() {
//        if (this in pool.eexpArgumentReaders) throw IllegalStateException("Already closed: $this")
        pool.eexpArgumentReaders.add(this)
//        println("Closing EExpArgumentReaderImpl...")
//        while (nextToken() != TokenTypeConst.END) { skip() }
//        println("Ended: $source")
    }

    fun initArgs(signature: Array<Macro.Parameter>) {
        this.signature = signature
        this.nextParameterIndex = 0
        // Make sure we have enough space in our arrays
        if (signature.size > argumentIndices.size) {
            argumentIndices = IntArray(signature.size)
            presence = IntArray(signature.size)
        }
        var presenceByteOffset = 8
        var presenceByte = 0

        Arrays.fill(argumentIndices, -1)

        for (i in signature.indices) {
            if (signature[i].cardinality != Macro.ParameterCardinality.ExactlyOne) {
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
        }
//        println("Position after presence bits: ${source.position()}")

        // Set the first argument to the current position. That's where it will be, if it is present.
        argumentIndices[0] = source.position()
    }

    // FIXME: The skipping in this method is ~17% of ALL
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
            seekToArgument(signatureIndex - 1)
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
            pool.getList(position, length, symbolTable, macroTable)
        }
    }

    // reuse regular sequence reader for tagged groups
    // returns tagless expression group reader for other groups

    override fun skip() {
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
    }
}
