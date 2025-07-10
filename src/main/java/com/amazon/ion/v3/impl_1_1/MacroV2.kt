// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.ExpressionBuilderDsl.Companion.templateBody
import com.amazon.ion.v3.impl_1_1.TemplateBodyExpressionModel.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * Represents a template macro. A template macro is defined by a signature, and a list of template expressions.
 * A template macro only gains a name and/or ID when it is added to a macro table.
 *
 * A system address of -99 indicates it is not a system macro (or special form).
 */
data class MacroV2 internal constructor(
    val signature: Array<Macro.Parameter>,
    val body: Array<TemplateBodyExpressionModel>?,
    val systemAddress: Int = -99,
    val systemName: SystemSymbols_1_1? = null,
) {

    val tokens = (body ?: TemplateBodyExpressionModel.EMPTY_EXPRESSION_ARRAY).map { it.expressionKind }.toMutableList().also{ it.add(TokenTypeConst.END)}.toIntArray()

    val bytecode: IntArray
    val constants: Array<Any?>

    init {
        val constants = mutableListOf<Any?>()
        val bytecode = mutableListOf<Int>()

        if (body != null) {
            toByteCode(body, bytecode, constants)
            bytecode.add(MacroBytecode.EOF.opToInstruction())
        }

        this.constants = constants.toTypedArray()
        this.bytecode = bytecode.toIntArray()
    }

    companion object {
        @JvmStatic
        fun toByteCode(expressions: Array<TemplateBodyExpressionModel>, bytecode: MutableList<Int>, constants: MutableList<Any?>) {
            // TODO: Deduplicate the constant and primitive pools
            for (expr in expressions) {
                toByteCode(expr, bytecode, constants)
            }
        }

        @JvmStatic
        fun toByteCode(expr: TemplateBodyExpressionModel, bytecode: MutableList<Int>, constants: MutableList<Any?>) {
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
                    bytecode.add((doubleBits shr 32).toInt())
                    bytecode.add(doubleBits.toInt())
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
                    val cpIndex = constants.size
                    constants.add(str)
                    bytecode.add(MacroBytecode.OP_CP_SYMBOL.opToInstruction(cpIndex))
                }

                Kind.BLOB -> TODO()
                Kind.CLOB -> TODO()
                Kind.LIST -> handleContainerLikeThing(MacroBytecode.OP_LIST_START, MacroBytecode.OP_LIST_END, expr, bytecode, constants)
                Kind.STRUCT -> handleContainerLikeThing(MacroBytecode.OP_STRUCT_START, MacroBytecode.OP_STRUCT_END, expr, bytecode, constants)
                Kind.SEXP -> handleContainerLikeThing(MacroBytecode.OP_SEXP_START, MacroBytecode.OP_SEXP_END, expr, bytecode, constants)
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
                    toByteCode(content, bytecode, constants)
                    // handleContainerLikeThing(MacroBytecode.OP_START_ARGUMENT_VALUE, MacroBytecode.OP_END_ARGUMENT_VALUE, expr, bytecode, constants, primitives)
                }
                Kind.VARIABLE -> bytecode.add(MacroBytecode.OP_ARGUMENT_REF_TYPE.opToInstruction(expr.primitiveValue.toInt()))
                Kind.INVOCATION -> {
                    val args = expr.childExpressions
                    for (arg in args) {
                        val argStartIndex = bytecode.size
                        bytecode.add(MacroBytecode.UNSET.opToInstruction())
                        val start = bytecode.size
                        toByteCode(arg, bytecode, constants)
                        bytecode.add(MacroBytecode.OP_END_ARGUMENT_VALUE.opToInstruction())
                        val end = bytecode.size
                        bytecode[argStartIndex] = (MacroBytecode.OP_START_ARGUMENT_VALUE.opToInstruction(end - start))
                    }
                    val macro = expr.valueObject as MacroV2
                    val cpIndex = constants.size
                    constants.add(macro)
                    bytecode.add(MacroBytecode.OP_INVOKE_MACRO.opToInstruction(cpIndex))
                }
                else -> throw IllegalStateException("Invalid Expression: $expr")
            }
        }

        private fun handleContainerLikeThing(startOp: Int, endOp: Int, expr: TemplateBodyExpressionModel, bytecode: MutableList<Int>, constants: MutableList<Any?>) {
            val containerStartIndex = bytecode.size
            bytecode.add(MacroBytecode.UNSET.opToInstruction())
            val start = bytecode.size
            val content = expr.childExpressions
            toByteCode(content, bytecode, constants)
            bytecode.add(endOp.opToInstruction())
            val end = bytecode.size
            bytecode[containerStartIndex] = startOp.opToInstruction(end - start)
        }
    }

    // TODO: Expansion analysis
    //      * Can this produce a system value?
    //      * Can this produce 0, 1, or more values?

    constructor(signature: Array<Macro.Parameter>, body: Array<TemplateBodyExpressionModel>) : this(signature, body, -1, null)
    constructor(signature: List<Macro.Parameter>, body: Array<TemplateBodyExpressionModel>) : this(signature.toTypedArray(), body, -1, null)
    constructor(signature: Array<Macro.Parameter>, body: List<TemplateBodyExpressionModel>) : this(signature, body.toTypedArray(), -1, null)
    constructor(signature: List<Macro.Parameter>, body: List<TemplateBodyExpressionModel>?, systemAddress: Int = -99, systemName: SystemSymbols_1_1? = null) : this(signature.toTypedArray(), body?.toTypedArray(), systemAddress, systemName)

    internal constructor(vararg signature: Macro.Parameter, body: TemplateDsl.() -> Unit) : this(signature.asList(), body)
    internal constructor(signature: List<Macro.Parameter>, body: TemplateDsl.() -> Unit) : this(signature, templateBody(signature, body))

    // TODO: Consider rewriting the body of the macro if we discover that there are any macros invoked using only
    //       constants as arguments—either at compile time or lazily.
    //       For example, the body of: (macro foo (x)  (values (make_string "foo" "bar") x))
    //       could be rewritten as: (values "foobar" x)

    private val cachedHashCode by lazy { signature.contentDeepHashCode() * 31 + body.contentDeepHashCode() }
    override fun hashCode(): Int = cachedHashCode

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MacroV2) return false
        // Check the hashCode as a quick check before we dive into the actual data.
        if (cachedHashCode != other.cachedHashCode) return false
        if (!signature.contentDeepEquals(other.signature)) return false
        if (!body.contentDeepEquals(other.body)) return false
        return true
    }

    fun writeMacroDefinitionToIonString(name: String?): String {
        val sb = StringBuilder()
        val writer = IonEncodingVersion.ION_1_0.textWriterBuilder().build(sb)
        writeMacroDefinition(name, writer)
        writer.close()
        return sb.toString()
    }

    private fun IonWriter.sexp(block: IonWriter.() -> Unit) {
        stepIn(IonType.SEXP)
        block()
        stepOut()
    }

    private fun writeMacroDefinition(name: String?, writer: IonWriter) {
        val macro = this
        writer.sexp {
            writeSymbol(SystemSymbols_1_1.MACRO.text)
            if (name != null) writeSymbol(name) else writeNull()

            // Signature
            sexp {
                macro.signature.forEach { parameter ->
                    if (parameter.type != Macro.ParameterEncoding.Tagged) {
                        setTypeAnnotations(parameter.type.ionTextName)
                    }
                    writeSymbol(parameter.variableName)
                    if (parameter.cardinality != Macro.ParameterCardinality.ExactlyOne) {
                        writeSymbol(parameter.cardinality.sigil.toString())
                    }
                }
            }

            // Template Body

            // TODO: See if there's any benefit to using a smaller number type, if we can
            //       memoize this in the macro definition, or replace it with a list of precomputed
            //       step-out indices.
            /** Tracks where and how many times to step out. */
            if (macro.body != null) {
                val numberOfTimesToStepOut = IntArray(macro.body.size)

                macro.body.forEachIndexed { index, expression ->


                    when (expression.expressionKind) {
                        Kind.FIELD_NAME -> setFieldName(expression.fieldName)
                        Kind.ANNOTATIONS -> setTypeAnnotations(*expression.annotations)
                        Kind.NULL -> writeNull(expression.valueObject as IonType)
                        Kind.BOOL -> writeBool(expression.primitiveValue > 0)
                        Kind.INT -> {
                            if (expression.valueObject != null)
                                writeInt(expression.valueObject as BigInteger)
                            else
                                writeInt(expression.primitiveValue)
                        }

                        Kind.FLOAT -> writeFloat(Double.fromBits(expression.primitiveValue))
                        Kind.DECIMAL -> writeDecimal(expression.valueObject as BigDecimal)
                        Kind.TIMESTAMP -> writeTimestamp(expression.valueObject as Timestamp)
                        Kind.STRING -> writeString(expression.valueObject as String)
                        Kind.SYMBOL -> writeSymbol(expression.valueObject as String?)
                        // The byte buffers aren't guaranteed to be backed by arrays, so this could fail.
                        Kind.BLOB -> writeBlob((expression.valueObject as ByteBuffer).array())
                        Kind.CLOB -> writeClob((expression.valueObject as ByteBuffer).array())
                        Kind.LIST -> {
                            stepIn(IonType.LIST)
                            numberOfTimesToStepOut[index]++
                        }

                        Kind.SEXP -> {
                            stepIn(IonType.SEXP)
                            numberOfTimesToStepOut[index]++
                        }

                        Kind.STRUCT -> {
                            stepIn(IonType.STRUCT)
                            numberOfTimesToStepOut[index]++
                        }

                        Kind.EXPRESSION_GROUP -> {
                            stepIn(IonType.SEXP)
                            writeSymbol("..")
                            numberOfTimesToStepOut[index]++
                        }

                        Kind.VARIABLE -> {
                            stepIn(IonType.SEXP)
                            writeSymbol("%")
                            writeSymbol(expression.valueObject.toString())
                            stepOut()
                        }

                        Kind.INVOCATION -> {
                            stepIn(IonType.SEXP)
                            writeSymbol(".")
                            val m = expression.valueObject as MacroV2
                            writeSymbol(m.systemName?.text)
                            numberOfTimesToStepOut[index]++
                        }
                    }

                    if (numberOfTimesToStepOut[index] > 0) {
                        repeat(numberOfTimesToStepOut[index]) { stepOut() }
                    }
                }
            }
        }
    }


    val dependencies: List<MacroV2> by lazy {
        if (body != null) {
            body.mapNotNull { it.valueObject as? MacroV2 }.distinct()
        } else {
            emptyList()
        }
    }
}
