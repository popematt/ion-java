package com.amazon.ion.v3.impl_1_1

class TemplateBodyExpressionModel(
    @JvmField
    var expressionKind: Int,
    @JvmField
    var length: Int,
    @JvmField
    var fieldName: String?,
    @JvmField
    val annotations: Array<String?>,
    @JvmField
    var value: Any?
) {


    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as TemplateBodyExpressionModel?
        if (expressionKind != that!!.expressionKind) return false
        if (length != that.length) return false
        if (fieldName != that.fieldName) return false
        if (value != that.value) return false
        if (!that.annotations.contentDeepEquals(that.annotations)) return false
        return true
    }

    override fun hashCode(): Int {
        var h = expressionKind
        h = h * 31 + length
        h = h * 31 + fieldName.hashCode()
        h = h * 31 + value.hashCode()
        h = h * 31 + annotations.contentDeepHashCode()
        return h
    }

    override fun toString(): String {
        return "TemplateBodyExpression(kind=${Kind(expressionKind)}, length=$length, fieldName=$fieldName, annotations=${annotations.toList()}, value=$value)"
    }

    fun copy() = TemplateBodyExpressionModel(expressionKind, length, fieldName, annotations, value)


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

        const val EXPRESSION_GROUP = 13
        const val INVOCATION = 14
        const val VARIABLE = 15
        const val LITERAL = 16

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
            13 -> "EXPRESSION_GROUP"
            14 -> "INVOCATION"
            15 -> "VARIABLE"
            16 -> "LITERAL"
            else -> "UNKNOWN"
        } + "($i)"
    }

}
