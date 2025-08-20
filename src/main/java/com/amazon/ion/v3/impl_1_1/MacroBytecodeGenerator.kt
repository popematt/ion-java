package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.impl_1_1.TemplateBodyExpressionModel.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.ArgumentBytecode.Companion.EMPTY_ARG
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

class Environment private constructor(
    @JvmField
    val parent: Environment?,
    @JvmField
    val args: Array<TemplateBodyExpressionModel>?,
    @JvmField
    val signature: Array<Macro.Parameter>,
    @JvmField
    var compiledArgs: IntList? = null,
    @JvmField
    val argIndices: IntArray = IntArray(signature.size) { -1 }
) {
    constructor(args: Array<TemplateBodyExpressionModel>, parent: Environment?, signature: Array<Macro.Parameter>) : this(parent, args, signature, null)
    constructor(compiledArgs: IntList, signature: Array<Macro.Parameter>) : this(null, null, signature, compiledArgs, argIndices = IntArray(signature.size).also {
        var start = 0
        for (ii in 0 until signature.size) {
            val length = compiledArgs[start++] and MacroBytecode.DATA_MASK
            if (length > 1) {
                it[ii] = start - 1
            } else {
                it[ii] = -1
            }
            start += length
        }
    })

    init {
//        if (args != null) {
//            require(args.size == signature.size) {}
//        } else if (compiledArgs != null) {
//
//            if (compiledArgs.isEmpty) {
//                require(signature.size == 0) {}
//            } else {
//                var count = 0
//                var start = 0
//                var length = compiledArgs[start++] and MacroBytecode.DATA_MASK
//                for (ii in 0 until signature.size) {
//                    start += length
//                    length = compiledArgs[start++] and MacroBytecode.DATA_MASK
//                    count++
//                }
//                require(count == signature.size) {}
//            }
//        }
    }


    fun doCompileArgs(constants: MutableList<Any?>): IntList {
        val args = args!!

        val argBytecode = IntList()
        compiledArgs = argBytecode

        for (p in 0 until signature.size) {
            // TODO: Make sure we appropriately handle rest-args.
            val arg = args.getOrElse(p) { TemplateBodyExpressionModel.ABSENT_ARG_EXPRESSION }
            if (arg == TemplateBodyExpressionModel.ABSENT_ARG_EXPRESSION) {
                // Don't add it, and do nothing.
                argIndices[p] = -1
            } else {
                argIndices[p] = argBytecode.size()
                generateBytecodeContainer(
                    MacroBytecode.OP_START_ARGUMENT_VALUE,
                    MacroBytecode.OP_END_ARGUMENT_VALUE,
                    argBytecode
                ) {
                    templateExpressionToBytecode(arg, parent, argBytecode, constants)
                }
            }
        }

        return argBytecode
    }

    /**
     * [Environment.copyArgInto]
     */
    fun copyArgInto(parameterIndex: Int, destination: IntList, constants: MutableList<Any?>) {
        val compiledArgs = compiledArgs ?: doCompileArgs(constants)
        try {
            var start = argIndices[parameterIndex]
            if (start == -1) return
            val length = compiledArgs[start++] and MacroBytecode.DATA_MASK
            // This copies the arg value without the arg wrapper. This might break for structs.
            destination.addSlice(compiledArgs, start, length - 1)
        } catch (e: Exception) {
            println("Requested parameter is $parameterIndex")
            println("signature: ${signature.contentToString()}")
            MacroBytecode.debugString(compiledArgs.toArray())
            throw e
        }
    }
}

fun templateExpressionToBytecode(expressions: Array<TemplateBodyExpressionModel>, env: Environment?, bytecode: IntList, constants: MutableList<Any?>) {
    // TODO: Deduplicate the constant pool
    for (expr in expressions) {
        templateExpressionToBytecode(expr, env, bytecode, constants)
    }
}

fun templateExpressionToBytecode(expr: TemplateBodyExpressionModel, env: Environment?, bytecode: IntList, constants: MutableList<Any?>) {
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
//                val argExpression = env.args!![parameterIndex]
//                templateExpressionToBytecode(argExpression, env.parent, bytecode, constants)
                env.copyArgInto(parameterIndex, bytecode, constants)
            } else {
                bytecode.add(MacroBytecode.OP_PARAMETER.opToInstruction(parameterIndex))
            }
        }
        Kind.INVOCATION -> {
            val macro = expr.valueObject as MacroV2

            if (macro.body != null) {
                templateExpressionToBytecode(macro.body, Environment(expr.childExpressions, env, macro.signature), bytecode, constants)
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

/* UNUSED

fun templateExpressionToBytecode(expressions: Array<TemplateBodyExpressionModel>, bytecode: IntList, constants: MutableList<Any?>, appendArgument: (Int, IntList) -> Unit) {
    // TODO: Deduplicate the constant pool
    for (expr in expressions) {
//        templateExpressionToBytecode(expr, bytecode, constants, appendArgument)
    }
}

fun templateExpressionToBytecode(expr: TemplateBodyExpressionModel, bytecode: IntList, constants: MutableList<Any?>, appendArgument: (Int, IntList) -> Unit) {
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
                // Unnecessary to add the cpIndex, but relatively cheap, and helpful for debugging.
                bytecode.add(MacroBytecode.OP_CP_BIG_INT.opToInstruction(cpIndex))
            } else {
                val longValue = expr.primitiveValue
                if (longValue.toShort().toLong() == longValue) {
                    bytecode.add(MacroBytecode.OP_SMALL_INT.opToInstruction(longValue.toInt() and 0xFFFF))
                } else if (longValue.toInt().toLong() == longValue) {
                    bytecode.add(MacroBytecode.OP_INLINE_INT.opToInstruction())
                    bytecode.add(longValue.toInt())
                } else {
                    bytecode.add(MacroBytecode.OP_INLINE_LONG.opToInstruction())
                    bytecode.add((longValue shr 32).toInt())
                    bytecode.add(longValue.toInt())
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
        Kind.LIST -> generateBytecodeContainer(MacroBytecode.OP_LIST_START, MacroBytecode.OP_LIST_END, bytecode) {
            templateExpressionToBytecode(expr.childExpressions, bytecode, constants, appendArgument)
        }
        Kind.SEXP -> generateBytecodeContainer(MacroBytecode.OP_SEXP_START, MacroBytecode.OP_SEXP_END, bytecode) {
            templateExpressionToBytecode(expr.childExpressions, bytecode, constants, appendArgument)
        }
        Kind.STRUCT -> generateBytecodeContainer(MacroBytecode.OP_STRUCT_START, MacroBytecode.OP_STRUCT_END, bytecode) {
            templateExpressionToBytecode(expr.childExpressions, bytecode, constants, appendArgument)
        }
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
            templateExpressionToBytecode(content, bytecode, constants, appendArgument)
        }
        Kind.VARIABLE -> {
            val parameterIndex = expr.primitiveValue.toInt()
            appendArgument(parameterIndex, bytecode)
        }
        Kind.INVOCATION -> {
            val macro = expr.valueObject as MacroV2

            if (macro.body != null) {
                templateExpressionToBytecode(macro.body, Environment(expr.childExpressions, null, macro.signature), bytecode, constants)
                return
            }

            val args = expr.childExpressions

            for (p in 0 until macro.signature.size) {
                // TODO: Make sure we appropriately handle rest-args.
                val arg = args.getOrElse(p) { TemplateBodyExpressionModel.ABSENT_ARG_EXPRESSION }
                generateBytecodeContainer(MacroBytecode.OP_START_ARGUMENT_VALUE, MacroBytecode.OP_END_ARGUMENT_VALUE, bytecode) {
                    templateExpressionToBytecode(arg, bytecode, constants, appendArgument)
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
        else -> throw IllegalStateException("Invalid Expression: $expr")
    }
}
*/

private fun handleContainerLikeThingWithArgs(startOp: Int, endOp: Int, expr: TemplateBodyExpressionModel, env: Environment?, bytecode: IntList, constants: MutableList<Any?>) {
    generateBytecodeContainer(startOp, endOp, bytecode) {
        val content = expr.childExpressions
        templateExpressionToBytecode(content, env, bytecode, constants)
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
