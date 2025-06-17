// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.SystemMacro
import com.amazon.ion.v3.impl_1_1.TemplateBodyExpressionModel.*
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * Represents a template macro. A template macro is defined by a signature, and a list of template expressions.
 * A template macro only gains a name and/or ID when it is added to a macro table.
 *
 * An address of -99 indicates it is not a system macro (or special form).
 */
data class MacroV2 internal constructor(
    val signature: Array<Macro.Parameter>,
    val body: Array<TemplateBodyExpressionModel>?,
    val systemAddress: Int = -99,
    val systemName: SystemSymbols_1_1? = null,
) {

    constructor(signature: Array<Macro.Parameter>, body: Array<TemplateBodyExpressionModel>) : this(signature, body, -1, null)
    constructor(signature: List<Macro.Parameter>, body: Array<TemplateBodyExpressionModel>) : this(signature.toTypedArray(), body, -1, null)
    constructor(signature: Array<Macro.Parameter>, body: List<TemplateBodyExpressionModel>) : this(signature, body.toTypedArray(), -1, null)
    constructor(signature: List<Macro.Parameter>, body: List<TemplateBodyExpressionModel>?, systemAddress: Int = -99, systemName: SystemSymbols_1_1? = null) : this(signature.toTypedArray(), body?.toTypedArray(), systemAddress, systemName)

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
                        Kind.NULL -> writeNull(expression.value as IonType)
                        Kind.BOOL -> writeBool(expression.primitiveValue > 0)
                        Kind.INT -> {
                            if (expression.value != null)
                                writeInt(expression.value as BigInteger)
                            else
                                writeInt(expression.primitiveValue)
                        }

                        Kind.FLOAT -> writeFloat(Double.fromBits(expression.primitiveValue))
                        Kind.DECIMAL -> writeDecimal(expression.value as BigDecimal)
                        Kind.TIMESTAMP -> writeTimestamp(expression.value as Timestamp)
                        Kind.STRING -> writeString(expression.value as String)
                        Kind.SYMBOL -> writeSymbol(expression.value as String?)
                        // The byte buffers aren't guaranteed to be backed by arrays, so this could fail.
                        Kind.BLOB -> writeBlob((expression.value as ByteBuffer).array())
                        Kind.CLOB -> writeClob((expression.value as ByteBuffer).array())
                        Kind.LIST -> {
                            stepIn(IonType.LIST)
                            numberOfTimesToStepOut[index + expression.length]++
                        }

                        Kind.SEXP -> {
                            stepIn(IonType.SEXP)
                            numberOfTimesToStepOut[index + expression.length]++
                        }

                        Kind.STRUCT -> {
                            stepIn(IonType.STRUCT)
                            numberOfTimesToStepOut[index + expression.length]++
                        }

                        Kind.EXPRESSION_GROUP -> {
                            stepIn(IonType.SEXP)
                            writeSymbol("..")
                            numberOfTimesToStepOut[index + expression.length]++
                        }

                        Kind.VARIABLE -> {
                            stepIn(IonType.SEXP)
                            writeSymbol("%")
                            writeSymbol(expression.value.toString())
                            stepOut()
                        }

                        Kind.INVOCATION -> {
                            stepIn(IonType.SEXP)
                            writeSymbol(".")
                            val m = expression.value as MacroV2
                            writeSymbol(m.systemName?.text)
                            numberOfTimesToStepOut[index + expression.length]++
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
            body.mapNotNull { it.value as? MacroV2 }.distinct()
        } else {
            emptyList()
        }
    }
}
