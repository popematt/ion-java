package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v8.ExpressionBuilderDsl.*
import java.math.BigDecimal
import java.math.BigInteger

class MacroV8 private constructor(
    // TODO: Signature here is informational only. Make sure it's accurate if we're going to keep it.
    @JvmField
    val signature: Array<Macro.Parameter>,
    // TODO: Body could actually be more than one expression if there are annotations.
    @JvmField
    val body: Array<TemplateExpression>,
) {

//    @JvmField
//    val returnType: IonType = TemplateBodyExpressionModel.Kind.ION_TYPES[body.expressionKind]!!

    fun writeTo(writer: IonRawWriter_1_1) {
        writeTemplateExpressions(body, writer)
    }

    companion object {
        @JvmStatic
        fun create(body: Array<TemplateExpression>): MacroV8 {
            val signature = mutableListOf<Macro.Parameter>()
            body.forEach { addPlaceholders(it, signature) }
            return MacroV8(signature.toTypedArray(), body)
        }

        @JvmStatic
        internal fun build(block: TemplateDsl.() -> Unit): MacroV8 = create(Template().apply(block).build())

        private fun addPlaceholders(body: TemplateExpression, signature: MutableList<Macro.Parameter>) {
            if (body.expressionKind == TemplateExpression.Kind.VARIABLE) {
                // TODO: Optional parameters, default values, tagless encodings
                val n = signature.size
                signature.add(Macro.Parameter("variable$n", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne))
                body.childValues.forEach { addPlaceholders(it, signature) }
            }
        }

        @JvmStatic
        private fun writeTemplateExpressions(body: Array<TemplateExpression>, writer: IonRawWriter_1_1) {
            writer as _Private_IonRawWriter_1_1
            body.forEach { expr ->
                when (expr.expressionKind) {
                    TemplateExpression.Kind.NULL -> writer.writeNull(expr.objectValue as IonType)
                    TemplateExpression.Kind.BOOL -> writer.writeBool(expr.primitiveValue == 1L)
                    TemplateExpression.Kind.INT -> {
                        if (expr.objectValue != null) {
                            writer.writeInt(expr.objectValue as BigInteger)
                        } else {
                            writer.writeInt(expr.primitiveValue)
                        }
                    }
                    TemplateExpression.Kind.FLOAT -> writer.writeFloat(Double.fromBits(expr.primitiveValue))
                    TemplateExpression.Kind.DECIMAL -> writer.writeDecimal(expr.objectValue as BigDecimal)
                    TemplateExpression.Kind.TIMESTAMP -> writer.writeTimestamp(expr.objectValue as Timestamp)
                    TemplateExpression.Kind.STRING -> writer.writeString(expr.objectValue as String)
                    TemplateExpression.Kind.SYMBOL -> (expr.objectValue as String?)?.let(writer::writeSymbol) ?: writer.writeSymbol(0)
                    TemplateExpression.Kind.CLOB -> {
                        val slice = expr.objectValue as ByteArraySlice
                        writer.writeClob(slice.bytes, slice.startInclusive, slice.length)
                    }
                    TemplateExpression.Kind.BLOB -> {
                        val slice = expr.objectValue as ByteArraySlice
                        writer.writeBlob(slice.bytes, slice.startInclusive, slice.length)
                    }
                    TemplateExpression.Kind.LIST -> {
                        writer.stepInList(false)
                        writeTemplateExpressions(expr.childValues, writer)
                        writer.stepOut()
                    }
                    TemplateExpression.Kind.SEXP -> {
                        writer.stepInSExp(false)
                        writeTemplateExpressions(expr.childValues, writer)
                        writer.stepOut()
                    }
                    TemplateExpression.Kind.STRUCT -> {
                        writer.stepInStruct(false)
                        writeTemplateExpressions(expr.childValues, writer)
                        writer.stepOut()
                    }
                    TemplateExpression.Kind.FIELD_NAME -> (expr.fieldName)?.let(writer::writeFieldName) ?: writer.writeFieldName(0)
                    TemplateExpression.Kind.ANNOTATIONS -> expr.annotations.forEach { it?.let(writer::writeAnnotations) ?: writer.writeAnnotations(0) }
                    TemplateExpression.Kind.VARIABLE -> {
                        // TODO: Tagless values
                        if (expr.childValues.isEmpty()) {
                            writer.writeTaggedPlaceholder()
                        } else {
                            writer.writeTaggedPlaceholderWithDefault { writeTemplateExpressions(expr.childValues, writer) }
                        }
                    }
                    else -> TODO("Writing ${TemplateExpression.Kind(expr.expressionKind)}")
                }
            }
        }

    }
}
