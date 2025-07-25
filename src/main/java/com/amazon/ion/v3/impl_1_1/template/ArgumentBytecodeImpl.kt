package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.OPERATION_SHIFT_AMOUNT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.Operations
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
 */
class ArgumentBytecode(
    private val bytecode: IntArray,
    private val constantPool: Array<Any?>,
    @JvmField
    val source: ByteBuffer,
    @JvmField
    var signature: Array<Macro.Parameter>,
) {

    companion object {

        @JvmStatic
        val EMPTY_ARG = intArrayOf(MacroBytecode.END_OF_ARGUMENT_SUBSTITUTION.opToInstruction())

        @JvmStatic
        val NO_ARGS = ArgumentBytecode(
            bytecode = intArrayOf(),
            constantPool = arrayOfNulls(0),
            source = ByteBuffer.allocate(0),
            signature = emptyArray(),
        )
    }

    // TODO: Cache start and end indices?

    init {
        var i = 0
        while (i < bytecode.size) {
            val instruction = bytecode[i++]
            val operationInt = instruction ushr OPERATION_SHIFT_AMOUNT
            if (operationInt == 0) {
                continue
            }

            val operation = Operations.entries.singleOrNull() { it.operation == operationInt }

            operation ?: throw IllegalStateException("Unknown operation $operationInt at position ${i-1}.")


            if (operation.dataFormatter == MacroBytecode.DataFormatters.CP_INDEX) {
                val cpIndex = instruction and MacroBytecode.DATA_MASK
                if (cpIndex >= constantPool.size) {
                    throw IllegalArgumentException("Missing cp index $cpIndex.")
                }
            }
            i += operation.numberOfOperands
        }
    }


    fun constantPool(): Array<Any?> {
        return constantPool
    }

    fun getArgument(parameterIndex: Int): IntArray {
        // TODO: See if there's something we can do that is more efficient than allocating a copy of part of the array.
        var start = 0
        var length = bytecode[start++] and MacroBytecode.DATA_MASK
        for (ii in 0 until parameterIndex) {
            start += length
            length = bytecode[start++] and MacroBytecode.DATA_MASK
        }
        if (length == 1) {
            return EMPTY_ARG
        }
        // This copies the arg value with the arg terminator, but not the arg start delimiter.
        val destination = bytecode.copyOfRange(start, start + length)
//        println("getArgument($parameterIndex)")
        destination[length - 1] = MacroBytecode.OP_RETURN.opToInstruction()
//        MacroBytecode.debugString(destination)
        return destination
    }
}
