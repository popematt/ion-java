package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.IonException
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

/**
 * Reads the raw E-expression arguments
 */
class EExpArgumentReaderImpl(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
    macroTable: Array<Macro>,
): ValueReaderBase(source, pool, symbolTable, macroTable), ArgumentReader {

    private var signature: List<Macro.Parameter> = emptyList()

    // 0, 1, or 2
    private var presence = IntArray(8)
    // The position of a "not present" argument is always the first byte after the last byte of the previous argument
    private var argumentIndices = IntArray(8)
    private var currentParameterIndex = 0

    override fun close() {
//        println("Closing EExpArgumentReaderImpl...")
        while (nextToken() != TokenTypeConst.END) { skip() }
//        println("Ended: $source")
    }

    fun initArgs(signature: List<Macro.Parameter>) {
        this.signature = signature
        this.currentParameterIndex = 0
        // Make sure we have enough space in our arrays
        if (signature.size > argumentIndices.size) {
            argumentIndices = IntArray(signature.size)
            presence = IntArray(signature.size)
        }
        var presenceByteOffset = 8
        var presenceByte = 0
        for (i in signature.indices) {
            argumentIndices[i] = -1
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


    fun calculateEndPosition(): Int {
        source.mark()
        for (i in 1 until argumentIndices.size) {
            val previous = signature[i - 1]
            val previousPosition = argumentIndices[i - 1]
            var sizeOfPrevious = 0
            nextToken()


            argumentIndices[i] = previousPosition + sizeOfPrevious
        }
        return argumentIndices[argumentIndices.size - 1] // + size of last argument
    }

    override fun seekToArgument(signatureIndex: Int): Int {
        var argumentPosition = argumentIndices[signatureIndex]
        if (argumentPosition < 0) {
            // TODO: See if this is faster if it uses a loop instead of recursion.
            // TODO: If it's faster to calculate all of the argument positions up front, then consider adding a reader
            //       option that allows users to decide between eager and lazy argument calculations.
            seekToArgument(signatureIndex - 1)
            skip()
            argumentPosition = source.position()
            argumentIndices[signatureIndex] = argumentPosition
        }
        val tokenType = when (presence[signatureIndex]) {
            2 -> {
                source.position(argumentIndices[signatureIndex])
                opcode = TID_EXPRESSION_GROUP
                TokenTypeConst.EXPRESSION_GROUP
            }
            1 -> {
                source.position(argumentIndices[signatureIndex])
                super.nextToken()
            }
            0 -> {
                opcode = TID_EMPTY_ARGUMENT
                TokenTypeConst.EMPTY_ARGUMENT
            }
            else -> throw IonException("Invalid presence bits for parameter $signatureIndex; was ${presence[signatureIndex]}")
        }
        return tokenType
    }

    override fun nextToken(): Int {
        val cpIndex = currentParameterIndex
        currentParameterIndex++
        if (cpIndex >= signature.size) {
            opcode = TID_END
//            println("Position: ${source.position()}, token: END")
            return TokenTypeConst.END
        }
        val currentParameter = signature[cpIndex]
        val presence = presence[cpIndex]
//        println("Checking next argument presence. Positioned at ${source.position()}, presence: $presence")
        return when (presence) {
            0 -> {
                opcode = TID_EMPTY_ARGUMENT
                TokenTypeConst.EMPTY_ARGUMENT
            }
            1 -> {
                super.nextToken()
            }
            2 -> {
                opcode = TID_EXPRESSION_GROUP
                TokenTypeConst.EXPRESSION_GROUP
            }
            else -> throw IonException("Invalid presence bits for parameter: $currentParameter")
        }
    }

    override fun expressionGroup(): ListReader {
        val opcode = this.opcode
        // TODO: Consider returning an empty or singleton expression group if it makes the APIs easier and improves perf.
        if (opcode != TID_EXPRESSION_GROUP) throw IonException("Not positioned on an expression group")
        this.opcode = TID_UNSET

        val length = IntHelper.readFlexUInt(source)
        val position = source.position()
        // TODO: Check if we're on a tagless parameter.
        //       For now, we'll assume that they are all tagged.
        return if (length == 0) {
            val maxLength = source.limit() - position
            // TODO: Something more efficient here
            val sacrificialReader = pool.getDelimitedList(position, maxLength, this)
            while (sacrificialReader.nextToken() != TokenTypeConst.END) { sacrificialReader.skip() };
            val endPosition = sacrificialReader.source.position()
            sacrificialReader.close()
            source.position(endPosition)
            pool.getDelimitedList(position, maxLength, this)
        } else {
            source.position(position + length)
            pool.getList(position, length)
        }
    }

    // reuse regular sequence reader for tagged groups
    // returns tagless expression group reader for other groups

    override fun skip() {
        // If we know which parameter we're on, we can check to see if the start of the next parameter is already known.
        when (opcode) {
            TID_EMPTY_ARGUMENT -> {
                // Nothing to skip.
            }
            TID_EXPRESSION_GROUP -> {
                // Assuming tagged.
                // TODO: We can make this more efficient for length-prefixed expression groups.
                source.position(expressionGroup().let {
                    it.close()
                    it.position()
                })
            }
            else -> super.skip()
        }
    }
}
