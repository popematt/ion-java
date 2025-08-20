package com.amazon.ion.v3.ion_reader_b

import com.amazon.ion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.SystemMacro
import com.amazon.ion.v3.impl_1_1.TemplateBodyExpressionModel.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.OPERATION_SHIFT_AMOUNT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.TOKEN_TYPE_SHIFT_AMOUNT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.instructionToOp
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 *
 */


class Environment(
    @JvmField
    val parent: Environment?,
    @JvmField
    var args: IntArray,
    @JvmField
    var argStart: Int,
) {
    fun findBytecodeIndex(parameterIndex: Int): Int {
        var i = argStart
        repeat(parameterIndex) {
            val instruction = args[i++]
            val argumentLength = instruction and MacroBytecode.DATA_MASK
            i += argumentLength
        }
        return i
    }
}


fun flatten(at: Int, sourceBytecode: IntArray, srcConstantPool: Array<Any?>, environment: Environment?, dest: IntList, destConstantPool: MutableList<Any?>, macroTable: Array<MacroV2>) {

    var i = at

//    val nOperands = MacroBytecode.Operations.N_OPERANDS
//    val isCPIndex = MacroBytecode.Operations.IS_CP_INDEX

    val containerPositions = IntArray(32)
    var containerStackSize = 0

    var argumentsStart = 0 // Stores the start of the arguments, offset by 1 (for bit-twiddling reasons).
    var argumentsFlag = -1 // -1 if not set, 0 if set. Used to avoid branches in the arguments case.
    var argumentsCount = 0



    while (i < sourceBytecode.size) {

        val instruction = sourceBytecode[i++]
        val op = instruction ushr OPERATION_SHIFT_AMOUNT

        when (op) {
            // Data model operations

            MacroBytecode.OP_REF_INT,
            MacroBytecode.OP_REF_DECIMAL,
            MacroBytecode.OP_REF_TIMESTAMP_SHORT,
            MacroBytecode.OP_REF_TIMESTAMP_LONG,
            MacroBytecode.OP_REF_STRING,
            MacroBytecode.OP_REF_SYMBOL_TEXT,
            MacroBytecode.OP_REF_CLOB,
            MacroBytecode.OP_REF_BLOB,
            MacroBytecode.OP_REF_LIST,
            MacroBytecode.OP_REF_SEXP,
            MacroBytecode.OP_REF_SID_STRUCT,
            MacroBytecode.OP_REF_FIELD_NAME_TEXT,
            MacroBytecode.OP_REF_ONE_FLEXSYM_ANNOTATION -> dest.add2(instruction, sourceBytecode[i++])

            MacroBytecode.OP_CP_BIG_INT,
            MacroBytecode.OP_CP_DECIMAL,
            MacroBytecode.OP_CP_TIMESTAMP,
            MacroBytecode.OP_CP_STRING,
            MacroBytecode.OP_CP_SYMBOL_TEXT,
            MacroBytecode.OP_CP_CLOB,
            MacroBytecode.OP_CP_BLOB,
            MacroBytecode.OP_CP_FIELD_NAME,
            MacroBytecode.OP_CP_ONE_ANNOTATION -> {
//                val cpIndex = instruction and MacroBytecode.DATA_MASK
//                val newCpIndex = destConstantPool.size
//                try {
//                    destConstantPool.add(srcConstantPool[cpIndex])
//                } catch (e: ArrayIndexOutOfBoundsException) {
//                    println("Error at ${i-1} ${MacroBytecode(instruction)}")
//                    MacroBytecode.debugString(sourceBytecode)
//
//                    throw IonException("Error at ${i-1} ${MacroBytecode(instruction)}", e)
//                }
//                dest.add(op.opToInstruction(newCpIndex))
                dest.add(instruction)
            }

            MacroBytecode.OP_INLINE_FLOAT,
            MacroBytecode.OP_INLINE_INT -> dest.add2(instruction, sourceBytecode[i++])

            MacroBytecode.OP_INLINE_DOUBLE,
            MacroBytecode.OP_INLINE_LONG -> dest.add3(instruction, sourceBytecode[i++], sourceBytecode[i++])

            MacroBytecode.OP_SYMBOL_SYSTEM_SID,
            MacroBytecode.OP_SYMBOL_SID,
            MacroBytecode.OP_FIELD_NAME_SYSTEM_SID,
            MacroBytecode.OP_FIELD_NAME_SID,
            MacroBytecode.OP_ANNOTATION_SYSTEM_SID,
            MacroBytecode.OP_ANNOTATION_SID -> dest.add(instruction)

            MacroBytecode.OP_BOOL,
            MacroBytecode.OP_SYMBOL_CHAR,
            MacroBytecode.OP_SMALL_INT -> dest.add(instruction)

            MacroBytecode.OP_NULL_TYPED,
            MacroBytecode.OP_NULL_NULL,
            MacroBytecode.OP_NULL_BOOL,
            MacroBytecode.OP_NULL_INT,
            MacroBytecode.OP_NULL_FLOAT,
            MacroBytecode.OP_NULL_DECIMAL,
            MacroBytecode.OP_NULL_TIMESTAMP,
            MacroBytecode.OP_NULL_STRING,
            MacroBytecode.OP_NULL_SYMBOL,
            MacroBytecode.OP_NULL_CLOB,
            MacroBytecode.OP_NULL_BLOB,
            MacroBytecode.OP_NULL_LIST,
            MacroBytecode.OP_NULL_SEXP,
            MacroBytecode.OP_NULL_STRUCT -> dest.add(instruction)

            MacroBytecode.OP_LIST_START,
            MacroBytecode.OP_SEXP_START,
            MacroBytecode.OP_STRUCT_START -> {
                containerPositions[containerStackSize++] = dest.size()
                dest.add(instruction)
            }

            MacroBytecode.OP_CONTAINER_END -> {
                val containerStartIndex = containerPositions[--containerStackSize]
                val start = containerStartIndex + 1
                dest.add(instruction)
                val containerEndIndex = dest.size()
                val startInstruction = dest[containerStartIndex]
                dest[containerStartIndex] = startInstruction.instructionToOp().opToInstruction(containerEndIndex - start)
            }

            // Relating to Macros

            MacroBytecode.OP_START_ARGUMENT_VALUE -> {
                val length = instruction and MacroBytecode.DATA_MASK
                argumentsStart = (argumentsStart or ((i - 1) and argumentsFlag))
                argumentsCount++
                argumentsFlag = 0

                // FIXME: We should be looking at the content of the argument too, in order adjust CP Indexes, etc.
                //        Except that we don't want to be copying them over to the destination, we want to evaluate them.
                //        So, do we need to pass
                i += length
            }

            MacroBytecode.OP_END_ARGUMENT_VALUE -> if (containerStackSize == 0) return

            MacroBytecode.OP_PARAMETER -> {
                val parameterIndex = instruction and MacroBytecode.DATA_MASK
                environment ?: TODO("Somehow we have a parameter with no environment.")
                val sourceIndex = environment.findBytecodeIndex(parameterIndex) + 1

                // TODO: What is the correct constant pool?
                try {
                    flatten(sourceIndex, environment.args, emptyArray(), environment.parent, dest, destConstantPool, macroTable)
                } catch (e: Throwable) {
                    println("Error at ${i-1} ${MacroBytecode(instruction)}")
                    MacroBytecode.debugString(sourceBytecode)

                    throw IonException("Error at ${i-1} ${MacroBytecode(instruction)}", e)
                }
            }

            MacroBytecode.OP_CP_MACRO_INVOCATION -> TODO("Not supported by this implementation")

            MacroBytecode.OP_INVOKE_MACRO -> {
                // TODO: What if this ends up being a hard-coded macro?
                val cpIndex = instruction and MacroBytecode.DATA_MASK
                val macro1 = srcConstantPool[cpIndex] as MacroV2

                val childEnvironment = Environment(environment, sourceBytecode, argumentsStart + 1)
                try {
                flatten(0, macro1.bytecode, macro1.constants, childEnvironment, dest, destConstantPool, macroTable)
                } catch (e: Throwable) {
                    println("Error at ${i-1} ${MacroBytecode(instruction)}")
                    MacroBytecode.debugString(sourceBytecode)

                    throw IonException("Error at ${i-1} ${MacroBytecode(instruction)}", e)
                }

                argumentsFlag = -1
                argumentsStart = 0
                argumentsCount = 0
            }

            MacroBytecode.OP_INVOKE_MACRO_ID -> {
                // TODO: What if this ends up being a hard-coded macro?
                val macroId = instruction and MacroBytecode.DATA_MASK
                val macro1 = macroTable[macroId]

                val childEnvironment = Environment(environment, sourceBytecode, argumentsStart + 1)
                try {
                    flatten(0, macro1.bytecode, macro1.constants, childEnvironment, dest, destConstantPool, macroTable)
                } catch (e: Throwable) {
                    println("Error at ${i-1} ${MacroBytecode(instruction)}")
                    MacroBytecode.debugString(sourceBytecode)

                    throw IonException("Error at ${i-1} ${MacroBytecode(instruction)}", e)
                }
                argumentsFlag = -1
                argumentsStart = 0
                argumentsCount = 0
            }
            MacroBytecode.OP_INVOKE_SYS_MACRO -> {
                val macId = instruction and MacroBytecode.DATA_MASK
                when (macId) {
                    SystemMacro.NONE_ADDRESS -> if (argumentsCount > 0) throw IonException("none macro may not have arguments")
                    SystemMacro.META_ADDRESS -> {}
                    SystemMacro.DEFAULT_ADDRESS -> {
                        val currentPosition = dest.size()

                        // Write out the first arg
                        // environment ?: TODO("Somehow we have a parameter with no environment.")
                        val paramIndex0 = argumentsStart + 1
                        val paramLength0 = sourceBytecode[argumentsStart] and MacroBytecode.DATA_MASK
                        // TODO: What is the correct constant pool?
                        try {
                            flatten(paramIndex0, sourceBytecode, srcConstantPool, environment, dest, destConstantPool, macroTable)
                        } catch (e: Throwable) {
                            println("Error at ${i-1} ${MacroBytecode(instruction)}")
                            MacroBytecode.debugString(sourceBytecode)

                            throw IonException("Error at ${i-1} ${MacroBytecode(instruction)}", e)
                        }
                        if (dest.size() == currentPosition) {
                            // write out the second arg
                            val paramIndex1 = paramIndex0 + paramLength0 + 1
                            // val paramLength1 = sourceBytecode[paramIndex1 - 1] and MacroBytecode.DATA_MASK
                            try {
                                flatten(paramIndex1, sourceBytecode, srcConstantPool, environment, dest, destConstantPool, macroTable)
                            } catch (e: Throwable) {
                                println("Error at ${i-1} ${MacroBytecode(instruction)}")
                                MacroBytecode.debugString(sourceBytecode)

                                throw IonException("Error at ${i-1} ${MacroBytecode(instruction)}", e)
                            }
                        }
                    }
                    else -> TODO()
                }
                argumentsStart = 0
                argumentsCount = 0
                argumentsFlag = -1
            }

            // Other

            MacroBytecode.DIRECTIVE_SYSTEM_MACRO,
            MacroBytecode.IVM -> dest.add(instruction)

            MacroBytecode.EOF -> return

            MacroBytecode.REFILL -> TODO("Unreachable: ${MacroBytecode(instruction)}")

            else -> {
                MacroBytecode.debugString(sourceBytecode)
                TODO("${MacroBytecode(instruction)} at ${i - 1}")
            }
        }
    }
}

fun flatten(source: Array<TemplateBodyExpressionModel>, environment: Environment?, dest: IntList, constantPool: MutableList<Any?>, macroTable: Array<MacroV2>) {

    source.forEach { expression ->
        when (expression.expressionKind) {
            Kind.NULL -> {
                val ionType = expression.valueObject as IonType
                val op = (ionType.ordinal shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
                dest.add(op.opToInstruction())
            }

            Kind.BOOL -> dest.add(MacroBytecode.OP_BOOL.opToInstruction(expression.primitiveValue.toInt()))

            Kind.INT -> {
                if (expression.valueObject != null) {
                    val bigInt = expression.valueObject as BigInteger
                    val cpIndex = constantPool.size
                    constantPool.add(bigInt)
                    dest.add(MacroBytecode.OP_CP_BIG_INT.opToInstruction(cpIndex))
                } else {
                    val longValue = expression.primitiveValue
                    if (longValue.toShort().toLong() == longValue) {
                        dest.add(MacroBytecode.OP_SMALL_INT.opToInstruction(longValue.toInt() and 0xFFFF))
                    } else if (longValue.toInt().toLong() == longValue) {
                        dest.add2(MacroBytecode.OP_INLINE_INT.opToInstruction(), longValue.toInt())
                    } else {
                        dest.add3(MacroBytecode.OP_INLINE_LONG.opToInstruction(), (longValue shr 32).toInt(), longValue.toInt())
                    }
                }
            }
            Kind.FLOAT -> {
                dest.add(MacroBytecode.OP_INLINE_DOUBLE.opToInstruction())
                val doubleBits = expression.primitiveValue
                dest.add(doubleBits.toInt())
                dest.add((doubleBits shr 32).toInt())
            }
            Kind.DECIMAL -> {
                // TODO: Special case for zero
                val decimal = expression.valueObject as BigDecimal
                val cpIndex = constantPool.size
                constantPool.add(decimal)
                dest.add(MacroBytecode.OP_CP_DECIMAL.opToInstruction(cpIndex))
            }
            Kind.TIMESTAMP -> {
                val ts = expression.valueObject as Timestamp
                val cpIndex = constantPool.size
                constantPool.add(ts)
                dest.add(MacroBytecode.OP_CP_TIMESTAMP.opToInstruction(cpIndex))
            }
            Kind.STRING -> {
                val str = expression.valueObject as String
                val cpIndex = constantPool.size
                constantPool.add(str)
                dest.add(MacroBytecode.OP_CP_STRING.opToInstruction(cpIndex))
            }
            Kind.SYMBOL -> {
                val str = expression.valueObject as String?
                if (str != null) {
                    val cpIndex = constantPool.size
                    constantPool.add(str)
                    dest.add(MacroBytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
                } else {
                    dest.add(MacroBytecode.OP_SYMBOL_SID.opToInstruction(0))
                }
            }
            Kind.BLOB -> {
                val blob = expression.valueObject as ByteBuffer
                val cpIndex = constantPool.size
                constantPool.add(blob)
                dest.add(MacroBytecode.OP_CP_BLOB.opToInstruction(cpIndex))

            }
            Kind.CLOB -> {
                val clob = expression.valueObject as ByteBuffer
                val cpIndex = constantPool.size
                constantPool.add(clob)
                dest.add(MacroBytecode.OP_CP_CLOB.opToInstruction(cpIndex))

            }
            Kind.LIST -> flattenContainer(MacroBytecode.OP_LIST_START, MacroBytecode.OP_LIST_END, expression, environment, dest, constantPool, macroTable)
            Kind.STRUCT -> flattenContainer(MacroBytecode.OP_STRUCT_START, MacroBytecode.OP_STRUCT_END, expression, environment, dest, constantPool, macroTable)
            Kind.SEXP -> flattenContainer(MacroBytecode.OP_SEXP_START, MacroBytecode.OP_SEXP_END, expression, environment, dest, constantPool, macroTable)
            Kind.ANNOTATIONS -> {
                val anns = expression.annotations
                for (ann in anns) {
                    val cpIndex = constantPool.size
                    constantPool.add(ann)
                    dest.add(MacroBytecode.OP_CP_ONE_ANNOTATION.opToInstruction(cpIndex))
                }
            }
            Kind.FIELD_NAME -> {
                val fn = expression.fieldName
                val cpIndex = constantPool.size
                constantPool.add(fn)
                dest.add(MacroBytecode.OP_CP_FIELD_NAME.opToInstruction(cpIndex))
            }
            Kind.VARIABLE -> {
                val parameterIndex = expression.primitiveValue.toInt()
                environment ?: TODO("No environment?")
                val sourceIndex = environment.findBytecodeIndex(parameterIndex) + 1
                flatten(sourceIndex, environment.args, emptyArray(), environment.parent, dest, constantPool, macroTable)
            }

            Kind.INVOCATION -> {
                val macro = expression.valueObject as MacroV2
                // TODO: Consider adding special handling for the directive system macros.

                if (macro.body != null) {
                    val args = IntList()
                    flatten(expression.childExpressions, environment, args, constantPool, macroTable)
                    flatten(macro.body, Environment(environment, args.unsafeGetArray(), 0), dest, constantPool, macroTable)
                } else {
                    val args = IntList()
                    flatten(expression.childExpressions, environment, args, constantPool, macroTable)
                    when (macro.systemAddress) {
                        SystemMacro.DEFAULT_ADDRESS -> {
                            val currentPosition = dest.size()

                            // Write out the first arg
                            // environment ?: TODO("Somehow we have a parameter with no environment.")
                            val paramIndex0 = 1
                            // TODO: What is the correct constant pool?
                            flatten(paramIndex0, args.unsafeGetArray(), emptyArray(), environment, dest, constantPool, macroTable)
                            if (dest.size() == currentPosition) {
                                // write out the second arg
                                val paramLength0 = args[0] and MacroBytecode.DATA_MASK
                                val paramIndex1 = paramIndex0 + paramLength0 + 1
                                flatten(paramIndex1, args.unsafeGetArray(), emptyArray(), environment, dest, constantPool, macroTable)
                            }
                        }
                        SystemMacro.NONE_ADDRESS -> {
                            if (!args.isEmpty()) throw IonException("none macro does not accept any arguments")
                        }
                        SystemMacro.META_ADDRESS -> {
                            // Do nothing.
                        }
                        else -> TODO(macro.systemName!!.text)
                    }
                }
            }

            Kind.EXPRESSION_GROUP -> {
                TODO()
            }

            else -> TODO("Kind = ${expression.expressionKind}")
        }
    }
}

private fun flattenContainer(startOp: Int, endOp: Int, expr: TemplateBodyExpressionModel, env: Environment?, bytecode: IntList, constants: MutableList<Any?>, macroTable: Array<MacroV2>) {
    generateBytecodeContainer(startOp, endOp, bytecode) {
        val content = expr.childExpressions
        flatten(content, env, bytecode, constants, macroTable)
    }
}



/*
fun evalMacro(macro: MacroV2, args: IntArray, dest: IntList, constantPool: MutableList<Any?>, macroTable: Array<MacroV2>) {
    val macroBytecode = macro.bytecode
    val macroCP = macro.constants

//    println("\nEvaluating macro...")
//    MacroBytecode.debugString(macroBytecode)
//    println()

    var i = 0

    val nOperands = MacroBytecode.Operations.N_OPERANDS
    val isCPIndex = MacroBytecode.Operations.IS_CP_INDEX

    val containerPositions = IntArray(32)
    var containerStackSize = 0

    val argStack = IntArray(256)
    var argStackSize = 0

    // TODO: Fix the performance in this loop.

    while (i < macroBytecode.size) {
        val instruction = macroBytecode[i++]
        val operationInt = instruction ushr OPERATION_SHIFT_AMOUNT
        if (operationInt == 0) continue // It's a NOP. No need to copy it over

//        if (operationInt == MacroBytecode.OP_RETURN) {
//            return
//        }

        if (operationInt == MacroBytecode.EOF) {
            // Don't copy it.
        } else if (operationInt == MacroBytecode.OP_START_ARGUMENT_VALUE) {
            val length = instruction and MacroBytecode.DATA_MASK
            System.arraycopy(macroBytecode, i - 1, argStack, argStackSize, length + 1)
            argStackSize += length + 1
            i += length
        } else if (operationInt ushr MacroBytecode.TOKEN_TYPE_SHIFT_AMOUNT == TokenTypeConst.MACRO_INVOCATION) {
            when (operationInt) {
                MacroBytecode.OP_CP_MACRO_INVOCATION -> TODO("Not supported by this implementation")
                MacroBytecode.OP_INVOKE_MACRO -> {
                    val cpIndex = instruction and MacroBytecode.DATA_MASK
                    val macro1 = macroCP[cpIndex] as MacroV2
                    evalMacro(macro1, argStack, dest, constantPool, macroTable)
                    argStackSize = 0
                }
                MacroBytecode.OP_INVOKE_SYS_MACRO -> {
                    val macId = instruction and MacroBytecode.DATA_MASK
                    when (macId) {
                        SystemMacro.NONE_ADDRESS -> if (argStackSize > 0) throw IonException("none macro may not have arguments")
                        SystemMacro.META_ADDRESS -> {
                            argStackSize = 0
                        }
                        SystemMacro.DEFAULT_ADDRESS -> {
                            val currentPosition = dest.size()
                            // Write out the first arg
                            insertParameter(0, argStack, dest, constantPool)
                            if (dest.size() == currentPosition) {
                                // write out the second arg
                                insertParameter(1, argStack, dest, constantPool)
                            }
                            argStackSize = 0
                        }
                        else -> TODO()
                    }
                }
                MacroBytecode.OP_INVOKE_MACRO_ID -> {
                    val macroId = instruction and MacroBytecode.DATA_MASK
                    val macro1 = macroTable[macroId] as MacroV2
                    evalMacro(macro1, argStack, dest, constantPool, macroTable)
                    argStackSize = 0
                }
            }
        } else if (operationInt == MacroBytecode.OP_PARAMETER) {
            val parameterIndex = instruction and MacroBytecode.DATA_MASK
            insertParameter(parameterIndex, args, dest, constantPool)
        } else if (operationInt == MacroBytecode.OP_CONTAINER_END) {
            val containerStartIndex = containerPositions[--containerStackSize]
            val start = containerStartIndex + 1
            dest.add(instruction)
            val containerEndIndex = dest.size()
            val startInstruction = dest[containerStartIndex]
            dest[containerStartIndex] = startInstruction.instructionToOp().opToInstruction(containerEndIndex - start)
//            println("Adjusting container: ${MacroBytecode(startInstruction)} to ${MacroBytecode(dest[containerStartIndex])}")
        } else if (isCPIndex[operationInt]) {
            val cpIndex = instruction and MacroBytecode.DATA_MASK
            val newCpIndex = constantPool.size
            constantPool.add(macroCP[cpIndex])
            dest.add(operationInt.opToInstruction(newCpIndex))
        } else when (nOperands[operationInt].toInt()) {
            -1 -> {
                containerPositions[containerStackSize++] = dest.size()
                dest.add(instruction)
            }
            0 -> dest.add(instruction)
            1 -> dest.add2(instruction, macroBytecode[i++])
            2 -> dest.add3(instruction, macroBytecode[i++], macroBytecode[i++])
            else -> TODO("Unreachable")
        }
    }
}

fun insertParameter(parameterIndex: Int, args: IntArray, dest: IntList, constantPool: MutableList<Any?>) {

    MacroBytecode.debugString(args)

    var i = 0
    repeat(parameterIndex) {
        val instruction = args[i++]
        println(MacroBytecode(instruction))
        val argumentLength = instruction and MacroBytecode.DATA_MASK
        i += argumentLength
    }

    val argLength = args[i] and MacroBytecode.DATA_MASK
    val argCopy = args.copyOfRange(i, i + argLength + 1)
    println("Found parameter $parameterIndex at $i")
    println("Inserting parameter #$parameterIndex")
    MacroBytecode.debugString(argCopy)
    println()
    // Position on the first value within the argument
    i++


    val nOperands = MacroBytecode.Operations.N_OPERANDS

    val containerPositions = IntArray(32)
    var containerStackSize = 0

    // TODO: Fix the performance in this loop.
    while (true) {


        val instruction = args[i++]
        val operationInt = instruction ushr OPERATION_SHIFT_AMOUNT
        if (operationInt == 0) continue // It's a NOP. No need to copy it over


        if (operationInt == MacroBytecode.OP_INVOKE_SYS_MACRO && instruction and MacroBytecode.DATA_MASK == 0) {
            // None macro
            continue
        }

        if (operationInt ushr MacroBytecode.TOKEN_TYPE_SHIFT_AMOUNT == TokenTypeConst.MACRO_INVOCATION) {
            TODO(MacroBytecode(instruction))
        }

        if (operationInt == MacroBytecode.OP_END_ARGUMENT_VALUE || operationInt == MacroBytecode.OP_RETURN) {
            return
        }

        if (operationInt == MacroBytecode.OP_START_ARGUMENT_VALUE) {
            // Do nothing.
        } else if (operationInt == MacroBytecode.OP_CONTAINER_END) {
            val containerStartIndex = containerPositions[--containerStackSize]
            val start = containerStartIndex + 1
            dest.add(instruction)
            val containerEndIndex = dest.size()
            val startInstruction = dest[containerStartIndex]
            dest[containerStartIndex] = startInstruction.instructionToOp().opToInstruction(containerEndIndex - start)
        } else when (nOperands[operationInt].toInt()) {
            -1 -> {
                containerPositions[containerStackSize++] = dest.size()
                dest.add(instruction)
            }
            0 -> dest.add(instruction)
            1 -> dest.add2(instruction, args[i++])
            2 -> dest.add3(instruction, args[i++], args[i++])
            else -> TODO("Unreachable")
        }
    }
}
*/

private fun templateExpressionToBytecode(expressions: Array<TemplateBodyExpressionModel>, env: Environment?, bytecode: IntList, constants: MutableList<Any?>) {
    // TODO: Deduplicate the constant pool
    for (expr in expressions) {
        // templateExpressionToBytecode(expr, env, bytecode, constants)
    }
}

private fun templateExpressionToBytecode(expr: TemplateBodyExpressionModel, env: Environment?, bytecode: IntList, constants: MutableList<Any?>) {
    when (expr.expressionKind) {
        Kind.NULL -> when (expr.valueObject) {
            IonType.NULL -> bytecode.add(MacroBytecode.OP_NULL_NULL.opToInstruction())
            else -> bytecode.add(MacroBytecode.OP_NULL_TYPED.opToInstruction((expr.valueObject as IonType).ordinal))
        }

        Kind.BOOL -> bytecode.add(MacroBytecode.OP_BOOL.opToInstruction(expr.primitiveValue.toInt()))

        Kind.INT -> {
            if (expr.valueObject != null) {
                val bigInt = expr.valueObject as BigInteger
                val cpIndex = constants.size
                constants.add(bigInt)
                bytecode.add(MacroBytecode.OP_CP_BIG_INT.opToInstruction(cpIndex))
            } else {
                val longValue = expr.primitiveValue
                if (longValue.toShort().toLong() == longValue) {
                    bytecode.add(MacroBytecode.OP_SMALL_INT.opToInstruction(longValue.toInt() and 0xFFFF))
                } else if (longValue.toInt().toLong() == longValue) {
                    bytecode.add2(MacroBytecode.OP_INLINE_INT.opToInstruction(), longValue.toInt())
                } else {
                    bytecode.add3(MacroBytecode.OP_INLINE_LONG.opToInstruction(), (longValue shr 32).toInt(), longValue.toInt())
                }
            }
        }
        Kind.FLOAT -> {
            bytecode.add(MacroBytecode.OP_INLINE_DOUBLE.opToInstruction())
            val doubleBits = expr.primitiveValue
            bytecode.add(doubleBits.toInt())
            bytecode.add((doubleBits shr 32).toInt())
        }
        Kind.DECIMAL -> {
            // TODO: Special case for zero
            val decimal = expr.valueObject as BigDecimal
            val cpIndex = constants.size
            constants.add(decimal)
            bytecode.add(MacroBytecode.OP_CP_DECIMAL.opToInstruction(cpIndex))
        }
        Kind.TIMESTAMP -> {
            val ts = expr.valueObject as Timestamp
            val cpIndex = constants.size
            constants.add(ts)
            bytecode.add(MacroBytecode.OP_CP_TIMESTAMP.opToInstruction(cpIndex))
        }
        Kind.STRING -> {
            val str = expr.valueObject as String
            val cpIndex = constants.size
            constants.add(str)
            bytecode.add(MacroBytecode.OP_CP_STRING.opToInstruction(cpIndex))
        }
        Kind.SYMBOL -> {
            val str = expr.valueObject as String?
            if (str != null) {
                val cpIndex = constants.size
                constants.add(str)
                bytecode.add(MacroBytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
            } else {
                bytecode.add(MacroBytecode.OP_SYMBOL_SID.opToInstruction(0))
            }
        }
        Kind.BLOB -> {
            val blob = expr.valueObject as ByteBuffer
            val cpIndex = constants.size
            constants.add(blob)
            bytecode.add(MacroBytecode.OP_CP_BLOB.opToInstruction(cpIndex))

        }
        Kind.CLOB -> {
            val clob = expr.valueObject as ByteBuffer
            val cpIndex = constants.size
            constants.add(clob)
            bytecode.add(MacroBytecode.OP_CP_CLOB.opToInstruction(cpIndex))

        }
        Kind.LIST -> handleContainerLikeThingWithArgs(MacroBytecode.OP_LIST_START, MacroBytecode.OP_LIST_END, expr, env, bytecode, constants)
        Kind.STRUCT -> handleContainerLikeThingWithArgs(MacroBytecode.OP_STRUCT_START, MacroBytecode.OP_STRUCT_END, expr, env, bytecode, constants)
        Kind.SEXP -> handleContainerLikeThingWithArgs(MacroBytecode.OP_SEXP_START, MacroBytecode.OP_SEXP_END, expr, env, bytecode, constants)
        Kind.ANNOTATIONS -> {
            val anns = expr.annotations
            for (ann in anns) {
                val cpIndex = constants.size
                constants.add(ann)
                bytecode.add(MacroBytecode.OP_CP_ONE_ANNOTATION.opToInstruction(cpIndex))
            }
        }
        Kind.FIELD_NAME -> {
            val fn = expr.fieldName
            val cpIndex = constants.size
            constants.add(fn)
            bytecode.add(MacroBytecode.OP_CP_FIELD_NAME.opToInstruction(cpIndex))
        }
        Kind.EXPRESSION_GROUP -> {
            val content = expr.childExpressions
            templateExpressionToBytecode(content, env, bytecode, constants)
        }
        Kind.VARIABLE -> {
            val parameterIndex = expr.primitiveValue.toInt()
            if (env != null) {
                val sourceIndex = env.findBytecodeIndex(parameterIndex) + 1
                flatten(sourceIndex, env.args, emptyArray(), env.parent, bytecode, constants, emptyArray())
            } else {
                bytecode.add(MacroBytecode.OP_PARAMETER.opToInstruction(parameterIndex))
            }
        }
        Kind.INVOCATION -> {
            val macro = expr.valueObject as MacroV2

            if (macro.body != null) {
                val args = IntList()
                templateExpressionToBytecode(expr.childExpressions, env, args, constants)
                templateExpressionToBytecode(macro.body, Environment(env, args.unsafeGetArray(), 0), bytecode, constants)
                return
            } else {
                val args = expr.childExpressions
                for (p in 0 until macro.signature.size) {
                    // TODO: Make sure we appropriately handle rest-args.
                    val arg = args.getOrElse(p) { TemplateBodyExpressionModel.ABSENT_ARG_EXPRESSION }
                    generateBytecodeContainer(MacroBytecode.OP_START_ARGUMENT_VALUE, MacroBytecode.OP_END_ARGUMENT_VALUE, bytecode) {
                        templateExpressionToBytecode(arg, env, bytecode, constants)
                    }
                }
                if (macro.systemAddress != SystemMacro.DEFAULT_ADDRESS) {
                    val cpIndex = constants.size
                    constants.add(macro)
                    bytecode.add(MacroBytecode.OP_INVOKE_MACRO.opToInstruction(cpIndex))
                } else {
                    bytecode.add(MacroBytecode.OP_INVOKE_SYS_MACRO.opToInstruction(macro.systemAddress))
                }
            }
        }
        else -> throw IllegalStateException("Invalid Expression: $expr")
    }
}

private fun handleContainerLikeThingWithArgs(startOp: Int, endOp: Int, expr: TemplateBodyExpressionModel, env: Environment?, bytecode: IntList, constants: MutableList<Any?>) {
    generateBytecodeContainer(startOp, endOp, bytecode) {
        val content = expr.childExpressions
        //templateExpressionToBytecode(content, env, bytecode, constants)
    }
}

inline fun generateBytecodeContainer(startOp: Int, endOp: Int, bytecode: IntList, content: () -> Unit) {
    val containerStartIndex = bytecode.reserve()
    val start = containerStartIndex + 1
    content()
    bytecode.add(endOp.opToInstruction())
    val end = bytecode.size()
    bytecode[containerStartIndex] = startOp.opToInstruction(end - start)
}
