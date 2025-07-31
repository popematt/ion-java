// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.IntList
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
    @JvmField
    val signature: Array<Macro.Parameter>,
    @JvmField
    val body: Array<TemplateBodyExpressionModel>?,
    @JvmField
    val systemAddress: Int = -99,
    @JvmField
    val systemName: SystemSymbols_1_1? = null,
    @JvmField
    val bytecode: IntArray,
    @JvmField
    val constants: Array<Any?>,
) {

    @JvmField
    val numPresenceBitsRequired = signature.count { it.iCardinality != 1 } * 2
    @JvmField
    val numPresenceBytesRequired = if (numPresenceBitsRequired == 0) {
        0
    } else {
        (numPresenceBitsRequired / 8) + 1
    }


    // TODO: Expansion analysis
    //      * Can this produce a system value?
    //      * Can this produce 0, 1, or more values?


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


    companion object {
        @JvmStatic
        internal operator fun invoke(signature: List<Macro.Parameter>, body: List<TemplateBodyExpressionModel>): MacroV2 = invoke(signature.toTypedArray(), body.toTypedArray())
        @JvmStatic
        internal operator fun invoke(signature: Array<Macro.Parameter>, body: List<TemplateBodyExpressionModel>): MacroV2 = invoke(signature, body.toTypedArray())
        @JvmStatic
        internal operator fun invoke(signature: List<Macro.Parameter>, body: Array<TemplateBodyExpressionModel>): MacroV2 = invoke(signature.toTypedArray(), body)

        @JvmStatic
        internal operator fun invoke(
            signature: Array<Macro.Parameter>,
            body: Array<TemplateBodyExpressionModel>?,
            systemAddress: Int = -99,
            systemName: SystemSymbols_1_1? = null,
        ): MacroV2 {
            val constants = mutableListOf<Any?>()
            val bytecode = IntList()
            if (body != null) {
                templateExpressionToBytecode(body, null, bytecode, constants)
                bytecode.add(MacroBytecode.EOF.opToInstruction())
            }
            return MacroV2(
                signature,
                body,
                systemAddress,
                systemName,
                bytecode.toArray(),
                constants.toTypedArray()
            )
        }

        @JvmStatic
        fun create(signature: List<Macro.Parameter>, body: List<TemplateBodyExpressionModel>): MacroV2 {
            return MacroV2(signature.toTypedArray(), body.toTypedArray())
        }

        @JvmStatic
        internal operator fun invoke(signature: List<Macro.Parameter>, body: TemplateDsl.() -> Unit): MacroV2 {
            val bodyExpressions = templateBody(signature, body)
            return MacroV2(signature.toTypedArray(), bodyExpressions)
        }

        @JvmStatic
        internal operator fun invoke(vararg signature: Macro.Parameter, body: TemplateDsl.() -> Unit): MacroV2 {
            val bodyExpressions = templateBody(signature.toList(), body)
            signature as Array<Macro.Parameter>
            return invoke(signature, bodyExpressions)
        }
    }
}
