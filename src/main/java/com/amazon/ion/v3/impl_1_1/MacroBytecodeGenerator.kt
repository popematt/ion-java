package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.TemplateBodyExpressionModel.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

class Environment(
    val args: Array<TemplateBodyExpressionModel>,
    val parent: Environment? = null,
)

fun templateExpressionToBytecode(expressions: Array<TemplateBodyExpressionModel>, env: Environment?, bytecode: MutableList<Int>, constants: MutableList<Any?>) {
    // TODO: Deduplicate the constant and primitive pools
    for (expr in expressions) {
        templateExpressionToBytecode(expr, env, bytecode, constants)
    }
}

fun templateExpressionToBytecode(expr: TemplateBodyExpressionModel, env: Environment?, bytecode: MutableList<Int>, constants: MutableList<Any?>) {
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
                    bytecode.add(MacroBytecode.OP_SMALL_INT.opToInstruction(longValue.toInt()))
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
                bytecode.add(MacroBytecode.OP_CP_SYMBOL.opToInstruction(cpIndex))

            } else {
                println("Unknown Symbol!")
                bytecode.add(MacroBytecode.OP_UNKNOWN_SYMBOL.opToInstruction())
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
            if (anns.size == 1) {
                val ann = anns[0]
                val cpIndex = constants.size
                constants.add(ann)
                bytecode.add(MacroBytecode.OP_CP_ONE_ANNOTATION.opToInstruction(cpIndex))

            } else {
                bytecode.add(MacroBytecode.OP_CP_N_ANNOTATIONS.opToInstruction(anns.size))
                for (ann in anns) {
                    val cpIndex = constants.size
                    constants.add(ann)
                    bytecode.add(cpIndex)
                }
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
                val argExpression = if (parameterIndex >= env.args.size) {
//                    println("Adding implicit absent argument $parameterIndex")
                    TemplateBodyExpressionModel.ABSENT_ARG_EXPRESSION
                } else {
                    env.args[parameterIndex]
                }
                templateExpressionToBytecode(argExpression, env.parent, bytecode, constants)
            } else {
                bytecode.add(MacroBytecode.OP_ARGUMENT_REF_TYPE.opToInstruction(parameterIndex))
            }
        }
        Kind.INVOCATION -> {
            val macro = expr.valueObject as MacroV2

            if (macro.body != null) {
                templateExpressionToBytecode(macro.body, Environment(expr.childExpressions, env), bytecode, constants)
                return
            }

            val args = expr.childExpressions


//            if (macro.systemAddress == SystemMacro.DEFAULT_ADDRESS) {
////                println("Attempting compile-time resolution of Default")
//                var firstArg = args[0]
//                // TODO: multiple layers of variable resolution
//                if (firstArg.expressionKind == TokenTypeConst.VARIABLE_REF && env != null) {
//                    firstArg = env.args[0]
//                    if (firstArg.expressionKind == TokenTypeConst.VARIABLE_REF && env.parent != null) {
//                        firstArg = env.parent.args[0]
//                    }
//                }
////                println(firstArg)
//                if (firstArg.expressionKind == TokenTypeConst.EXPRESSION_GROUP && firstArg.childExpressions.isEmpty()) {
//                    // It's for sure `none`
//                    templateExpressionToBytecode(args[1], env, bytecode, constants)
//                    return
//                } else if (firstArg.expressionKind < TokenTypeConst.FIELD_NAME) {
//                    // It's for sure a data-model value
//                    templateExpressionToBytecode(firstArg, env, bytecode, constants)
//                    return
//                }
//            }

//            println("Fallback to postfix invocation for macro: $macro")
            for (arg in args) {
                val argStartIndex = bytecode.size
                bytecode.add(MacroBytecode.UNSET.opToInstruction())
                val start = bytecode.size
                templateExpressionToBytecode(arg, env, bytecode, constants)
                bytecode.add(MacroBytecode.OP_END_ARGUMENT_VALUE.opToInstruction())
                val end = bytecode.size
                bytecode[argStartIndex] =
                    (MacroBytecode.OP_START_ARGUMENT_VALUE.opToInstruction(end - start))
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

private fun handleContainerLikeThingWithArgs(startOp: Int, endOp: Int, expr: TemplateBodyExpressionModel, env: Environment?, bytecode: MutableList<Int>, constants: MutableList<Any?>) {
    val containerStartIndex = bytecode.size
    bytecode.add(MacroBytecode.UNSET.opToInstruction())
    val start = bytecode.size
    val content = expr.childExpressions
    templateExpressionToBytecode(content, env, bytecode, constants)
    bytecode.add(endOp.opToInstruction())
    val end = bytecode.size
    bytecode[containerStartIndex] = startOp.opToInstruction(end - start)
}
