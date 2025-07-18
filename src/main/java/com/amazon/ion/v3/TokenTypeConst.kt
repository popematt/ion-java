package com.amazon.ion.v3

/**
 * Does not represent the Ion type.
 *
 * For example, `null.string` is a Null token, and `(:make_string)` is an EEXP token.
 */
object TokenTypeConst {
    /** There is no current Token Type */
    const val UNSET = 0
    // Types
    const val NULL = 1
    const val BOOL = 2
    const val INT = 3
    const val FLOAT = 4
    const val DECIMAL = 5
    const val TIMESTAMP = 6
    const val STRING = 7
    const val SYMBOL = 8
    const val CLOB = 9
    const val BLOB = 10
    const val LIST = 11
    const val SEXP = 12
    const val STRUCT = 13
    const val ANNOTATIONS = 14
    const val FIELD_NAME = 15
    const val NOP = 16
    /** End of a container */
    const val END = 17
    const val IVM = 18
    /** Reader is positioned on a TypeId or Opcode that is reserved. This should be an error. */
    const val MACRO_INVOCATION = 19
    const val EXPRESSION_GROUP = 20
    const val VARIABLE_REF = 21

    const val ABSENT_ARGUMENT = 22 // Try making this the same number as NOP
    const val RESERVED = 23 // TODO: Remove this?

    operator fun invoke(i: Int) : String {
        return when (i) {
            0 -> "UNSET"
            1 -> "NULL"
            2 -> "BOOL"
            3 -> "INT"
            4 -> "FLOAT"
            5 -> "DECIMAL"
            6 -> "TIMESTAMP"
            7 -> "STRING"
            8 -> "SYMBOL"
            9 -> "CLOB"
            10 -> "BLOB"
            11 -> "LIST"
            12 -> "SEXP"
            13 -> "STRUCT"
            14 -> "ANNOTATIONS"
            15 -> "FIELD_NAME"
            16 -> "NOP"
            17 -> "END"
            18 -> "IVM"
            19 -> "MACRO_INVOCATION"
            20 -> "EXPRESSION_GROUP"
            21 -> "VARIABLE_REF"
            22 -> "EMPTY_ARGUMENT"
            23 -> "RESERVED"
            else -> "UNKNOWN"
        } + "($i)"
    }
}
