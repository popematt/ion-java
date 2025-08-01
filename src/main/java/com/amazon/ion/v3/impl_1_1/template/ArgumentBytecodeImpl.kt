package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.nio.ByteBuffer

/**
 * If we want the macro evaluation code to be shareable for text and binary Ion, we need to be
 * able to evaluate "REF" bytecodes. The three options are:
 *   1. Make this class have methods for reading them, and then have implementations for text and binary
 *   2. Inject a "Reference Handler" into the bytecode reader some other way.
 *   3. Have a "IonTextTemplateReaderImpl" and "IonTextTemplateReaderImpl" that extend "TemplateReaderBase".
 *
 * For now, I think that 3 is probably the simplest option.
 *
 * Can we consolidate this class with [Environment][com.amazon.ion.v3.impl_1_1.Environment]?
 */
class ArgumentBytecode(
    private val bytecode: IntArray,
    private val constantPool: Array<Any?>,
    @JvmField
    val source: ByteBuffer,
    @JvmField
    var signature: Array<Macro.Parameter>,
    firstArgIndex: Int = 0,
    /** These indices should point to the ArgStart instruction */
    indices: IntArray? = null,
) {

    companion object {

        @JvmField
        val EMPTY_ARG = intArrayOf(MacroBytecode.OP_END_ARGUMENT_VALUE.opToInstruction())

        @JvmField
        val NO_ARGS = ArgumentBytecode(
            bytecode = intArrayOf(),
            constantPool = arrayOfNulls(0),
            source = ByteBuffer.allocate(0),
            signature = emptyArray(),
            indices = IntArray(0)
        )
    }

    private val indices: IntArray = indices ?: let {


        val signature = this.signature
        val signatureSize = signature.size
        val argIndices = IntArray(signatureSize)

        var start = firstArgIndex

        for (p in 0 until signatureSize) {
            val length = bytecode[start++] and MacroBytecode.DATA_MASK
            if (length == 1) {
                argIndices[p] = -1
            } else {
                argIndices[p] = start - 1
            }
            start += length
        }
        argIndices
    }

    fun constantPool(): Array<Any?> {
        return constantPool
    }

//    fun getArgument(parameterIndex: Int): IntArray {
//        // TODO: See if there's something we can do that is more efficient than allocating a copy of part of the array.
//        var start = indices[parameterIndex]
//        val length = bytecode[start++] and MacroBytecode.DATA_MASK
//        if (start == -1) {
//            return EMPTY_ARG
//        }
//        // This copies the arg value with the arg terminator, but not the arg start delimiter.
//        val destination = bytecode.copyOfRange(start, start + length)
//        destination[length - 1] = MacroBytecode.OP_RETURN.opToInstruction()
//        return destination
//    }

    class ArgSlice(@JvmField var bytecode: IntArray, @JvmField var startIndex: Int) {
        companion object {
            @JvmField
            val EMPTY = ArgSlice(bytecode = EMPTY_ARG, startIndex = 0)
            @JvmField
            val NON_EMPTY = ArgSlice(bytecode = EMPTY_ARG, startIndex = 0)
        }
    }

    fun getArgumentSlice(parameterIndex: Int): ArgSlice {
        // TODO: See if there's something we can do that is more efficient than allocating a copy of part of the array.
        val start = indices[parameterIndex]
        if (start == -1) {
            return ArgSlice.EMPTY
        } else {
            val slice = ArgSlice.NON_EMPTY
            slice.bytecode = bytecode
            slice.startIndex = start + 1
            return slice
        }
//        return ArgSlice(bytecode, start + 1)
    }
}
