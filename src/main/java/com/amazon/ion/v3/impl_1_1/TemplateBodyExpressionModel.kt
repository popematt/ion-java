package com.amazon.ion.v3.impl_1_1

import java.lang.StringBuilder

/**
 *  - NULL: value is IonType
 *  - BOOL: primitiveValue is 0 or 1
 *  - INT: value is BigInteger OR primitiveValue is the long value
 *  - FLOAT: primitiveValue is double as raw bits
 *  - DECIMAL: value is BigDecimal
 *  - TIMESTAMP: value is Timestamp
 *  - STRING: value is String
 *  - SYMBOL: value is String?
 *  - CLOB: value is ByteBuffer
 *  - BLOB: value is ByteBuffer
 *  - LIST:
 *  - SEXP:
 *  - STRUCT:
 *  - ANNOTATIONS: annotations is nonEmpty
 *  - FIELD_NAME: fieldName is String?
 *  - INVOCATION: value is MacroV2
 *  - EXPRESSION_GROUP:
 *  - VARIABLE:
 *  - LITERAL: (tentative) value is SequenceReader
 */
class TemplateBodyExpressionModel(
    @JvmField
    var expressionKind: Int,
    /**
     * The number of trailing expressions that are part of this value.
     */
    @JvmField
    var length: Int,
    // TODO: Consider consolidating the FieldName and Annotation expressions.
    //       For now, all TemplateBodyExpressions have exactly one of the following field set.
    @JvmField
    var fieldName: String? = null,
    @JvmField
    val annotations: Array<String?> = EMPTY_ANNOTATIONS_ARRAY,
    @JvmField
    var value: Any? = null,
    @JvmField
    var additionalValue: Any? = null,
    @JvmField
    var indices: Any? = null,
    @JvmField
    var primitiveValue: Long = 0,
) {
    companion object {

        @JvmStatic
        val EMPTY_EXPRESSION_ARRAY = emptyArray<TemplateBodyExpressionModel>()

        @JvmStatic
        val EMPTY_ANNOTATIONS_ARRAY = emptyArray<String?>()

        @JvmStatic
        val ABSENT_ARG_EXPRESSION = TemplateBodyExpressionModel(Kind.EXPRESSION_GROUP, length = 0, value = EMPTY_EXPRESSION_ARRAY)
    }

    init {
        when (expressionKind) {
            Kind.INVOCATION -> require(value is MacroV2)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as TemplateBodyExpressionModel
        if (expressionKind != that.expressionKind) return false
        if (length != that.length) return false
        if (fieldName != that.fieldName) return false
        if (!that.annotations.contentDeepEquals(that.annotations)) return false
        if (primitiveValue != that.primitiveValue) return false

        if (value is Array<*> && that.value is Array<*>) {
            if (!(value as Array<*>).contentDeepEquals(that.value as Array<*>)) return false
        } else {
            if (value != that.value) return false
        }

        if (additionalValue is Array<*> && that.additionalValue is Array<*>) {
            if (!(additionalValue as Array<*>).contentDeepEquals(that.additionalValue as Array<*>)) return false
        } else {
            if (additionalValue != that.additionalValue) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = expressionKind
        h = h * 31 + length
        h = h * 31 + fieldName.hashCode()
        val h0: Int = (value as? Array<*>)?.contentDeepHashCode() ?: value.hashCode()
        h = h * 31 + h0
        h = h * 31 + annotations.contentDeepHashCode()
        h = h * 31 + primitiveValue.hashCode()
        val h1: Int = (additionalValue as? Array<*>)?.contentDeepHashCode() ?: additionalValue.hashCode()
        h = h * 31 + h1
        return h
    }

    override fun toString(): String {
        return toPrettyString()
//        val sb = StringBuilder()
//        sb.append("TemplateBodyExpression(kind=${Kind(expressionKind)}")
//        if (value is Array<*>) {
//            sb.append(", value=")
//            (value as Array<TemplateBodyExpressionModel>)
//                .mapIndexed { i, value -> " $i. $value"}
//                .joinToString("\n", prefix = "[\n", postfix = "\n]")
//                .let(sb::append)
//        } else if (value != null || expressionKind == Kind.SYMBOL) sb.append(", value=$value")
//        when (expressionKind) {
//            Kind.LIST, Kind.SEXP, Kind.STRUCT,
//            Kind.INVOCATION, Kind.EXPRESSION_GROUP -> sb.append(", length=$length")
//            Kind.BOOL, Kind.INT, Kind.VARIABLE -> sb.append(", primitiveValue=$primitiveValue")
//            Kind.FLOAT -> sb.append(", primitiveValue=$primitiveValue (${Double.fromBits(primitiveValue)})")
//            Kind.FIELD_NAME -> sb.append(", fieldName=$fieldName")
//            Kind.ANNOTATIONS -> sb.append(", annotations=${annotations.contentToString()}")
//        }
//        sb.append(")")
//        return sb.toString()
    }



    private fun toPrettyString(indent: String = ""): String {
        val sb = StringBuilder()
        sb.append("TemplateBodyExpression(kind=${Kind(expressionKind)}")
        when (expressionKind) {
            Kind.LIST, Kind.SEXP, Kind.STRUCT,
            Kind.INVOCATION, Kind.EXPRESSION_GROUP -> sb.append(", length=$length")
            Kind.BOOL, Kind.INT, Kind.VARIABLE -> sb.append(", primitiveValue=$primitiveValue")
            Kind.FLOAT -> sb.append(", primitiveValue=$primitiveValue (${Double.fromBits(primitiveValue)})")
            Kind.FIELD_NAME -> sb.append(", fieldName=$fieldName")
            Kind.ANNOTATIONS -> sb.append(", annotations=${annotations.contentToString()}")
        }
        if (value is Array<*>) {
            sb.append(", value=")
            (value as Array<TemplateBodyExpressionModel>)
                .mapIndexed { i, value -> "$indent    $i. ${value.toPrettyString("$indent    ")}" }
                .joinToString("\n", prefix = "[\n", postfix = "\n$indent]")
                .let(sb::append)
        } else if (value != null || expressionKind == Kind.SYMBOL) {
            sb.append(", value=$value")
        }
        if (additionalValue is Array<*>) {
            sb.append(", additionalValue=${(additionalValue as Array<*>).contentToString()})")
        } else if (additionalValue != null) {
            sb.append(", additionalValue=$additionalValue")
        }
        sb.append(")")
        return sb.toString()
    }

    fun copy() = TemplateBodyExpressionModel(expressionKind, length, fieldName, annotations, value, primitiveValue)


    object Kind {
        // TODO: Make these align, or merge with TokenTypeConst
        const val NULL = 0
        const val BOOL = 1
        const val INT = 2
        const val FLOAT = 3
        const val DECIMAL = 4
        const val TIMESTAMP = 5
        const val STRING = 6
        const val SYMBOL = 7
        const val CLOB = 8
        const val BLOB = 9
        const val LIST = 10
        const val SEXP = 11
        const val STRUCT = 12
        const val ANNOTATIONS = 13
        const val FIELD_NAME = 14

        const val INVOCATION = 20
        const val EXPRESSION_GROUP = 21
        const val VARIABLE = 23
        const val LITERAL = 30

        operator fun invoke(i: Int) = when (i) {
            0 -> "NULL"
            1 -> "BOOL"
            2 -> "INT"
            3 -> "FLOAT"
            4 -> "DECIMAL"
            5 -> "TIMESTAMP"
            6 -> "STRING"
            7 -> "SYMBOL"
            8 -> "CLOB"
            9 -> "BLOB"
            10 -> "LIST"
            11 -> "SEXP"
            12 -> "STRUCT"
            13 -> "ANNOTATIONS"
            14 -> "FIELD_NAME"
            20 -> "INVOCATION"
            21 -> "EXPRESSION_GROUP"
            23 -> "VARIABLE"
            30 -> "LITERAL"
            else -> "UNKNOWN"
        } + "($i)"
    }

}
