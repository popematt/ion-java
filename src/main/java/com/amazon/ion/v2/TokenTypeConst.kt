package com.amazon.ion.v2

/**
 * Does not represent the Ion type.
 *
 * For example, `null.string` is a Null token, and `(:make_string)` is an EEXP token.
 */
object TokenTypeConst {
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
    const val NOP = 15
    const val UNSET = 16 // TODO: Change to UNKNOWN and/or UNSET
    const val END = 17
    const val IVM = 18
    const val RESERVED = 19
    const val EEXP = 20
    const val EXPRESSION_GROUP = 21


    operator fun invoke(i: Int) : String {
        return when (i) {
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
            15 -> "NOP"
            16 -> "UNSET"
            17 -> "END"
            18 -> "IVM"
            19 -> "RESERVED"
            20 -> "EEXP"
            21 -> "EXPRESSION_GROUP"
            else -> "UNKNOWN"
        } + "($i)"
    }
}
