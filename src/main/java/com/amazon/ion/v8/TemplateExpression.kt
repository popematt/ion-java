package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.v8.*
import java.lang.StringBuilder

/**
 * This class can be used to model macro templates.
 *
 *
 * When expression kind is...
 *  - NULL: objectValue is the [IonType]
 *  - BOOL: primitiveValue is 0 or 1
 *  - INT: value is [BigInteger] OR [primitiveValue] is the [Long] value
 *  - FLOAT: primitiveValue is double as raw bits
 *  - DECIMAL: value is [BigDecimal]
 *  - TIMESTAMP: value is [Timestamp]
 *  - STRING: value is [String]
 *  - SYMBOL: value is `String?`
 *  - CLOB: value is [ByteArraySlice]
 *  - BLOB: value is [ByteArraySlice]
 *  - LIST: children are in [childValues]
 *  - SEXP: children are in [childValues]
 *  - STRUCT: children are in [childValues]
 *  - ANNOTATIONS: annotations is nonEmpty
 *  - FIELD_NAME: fieldName is `String?`
 *  - VARIABLE: default value is in [childValues]
 *
 *  Any other expression kind is illegal.
 */
data class TemplateExpression(
    @JvmField var expressionKind: Int,
    @JvmField var fieldName: String? = null,
    @JvmField var annotations: Array<String?> = EMPTY_ANNOTATIONS_ARRAY,
    @JvmField var primitiveValue: Long = 0,
    @JvmField var objectValue: Any? = null,
    @JvmField var childValues: Array<TemplateExpression> = EMPTY_EXPRESSION_ARRAY,
) {

    companion object {
        @JvmField val EMPTY_EXPRESSION_ARRAY = emptyArray<TemplateExpression>()
        @JvmField val EMPTY_ANNOTATIONS_ARRAY = emptyArray<String?>()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as TemplateExpression
        if (expressionKind != that.expressionKind) return false
        if (fieldName != that.fieldName) return false
        if (!that.annotations.contentDeepEquals(that.annotations)) return false
        if (primitiveValue != that.primitiveValue) return false
        if (objectValue != that.objectValue) return false
        if (!childValues.contentDeepEquals(that.childValues)) return false
        return true
    }

    override fun hashCode(): Int {
        var h = expressionKind
        h = h * 31 + fieldName.hashCode()
        h = h * 31 + annotations.contentDeepHashCode()
        h = h * 31 + primitiveValue.hashCode()
        h = h * 31 + objectValue.hashCode()
        h = h * 31 + childValues.contentDeepHashCode()
        return h
    }

    override fun toString(): String {
        return toPrettyString()
    }

    private fun toPrettyString(indent: String = ""): String {
        val sb = StringBuilder()
        sb.append("TemplateBodyExpression(kind=${Kind(expressionKind)}")
        when (expressionKind) {
            Kind.LIST, Kind.SEXP, Kind.STRUCT,
            Kind.BOOL, Kind.INT, Kind.VARIABLE -> sb.append(", primitiveValue=$primitiveValue")
            Kind.FLOAT -> sb.append(", primitiveValue=$primitiveValue (${Double.fromBits(primitiveValue)})")
            Kind.FIELD_NAME -> sb.append(", fieldName=$fieldName")
            Kind.ANNOTATIONS -> sb.append(", annotations=${annotations.contentToString()}")
        }
        if (objectValue != null || expressionKind == Kind.SYMBOL) {
            sb.append(", valueObject=$objectValue")
        }
        if (childValues.isNotEmpty()) {
            sb.append(", childExpressions=")
            childValues.mapIndexed { i, value -> "$indent    $i. ${value.toPrettyString("$indent    ")}" }
                .joinToString("\n", prefix = "[\n", postfix = "\n$indent]")
                .let(sb::append)
        }
        sb.append(")")
        return sb.toString()
    }

    object Kind {
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
        const val VARIABLE = 23

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
            23 -> "VARIABLE"
            else -> "UNKNOWN"
        } + "($i)"
    }
}
