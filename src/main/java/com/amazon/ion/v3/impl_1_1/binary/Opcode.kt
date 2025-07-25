package com.amazon.ion.v3.impl_1_1.binary

object Opcode {


    const val BIASED_E_EXPRESSION_ONE_BYTE_FIXED_INT = 0x40
    const val BIASED_E_EXPRESSION_TWO_BYTE_FIXED_INT = 0x50

    const val INT_ZERO = 0x60
    const val INT_8 = 0x61
    const val INT_16 = 0x62
    const val INT_24 = 0x63
    const val INT_32 = 0x64
    const val INT_40 = 0x65
    const val INT_48 = 0x66
    const val INT_56 = 0x67
    const val INT_64 = 0x68
    const val RESERVED_0x69 = 0x69
    const val FLOAT_ZERO = 0x6A
    const val FLOAT_16 = 0x6B
    const val FLOAT_32 = 0x6C
    const val FLOAT_64 = 0x6D
    const val BOOL_TRUE = 0x6E
    const val BOOL_FALSE = 0x6F

    const val DECIMAL_0 = 0x70

    const val TIMESTAMP_YEAR_PRECISION = 0x80
    const val TIMESTAMP_MONTH_PRECISION = 0x81
    const val TIMESTAMP_DAY_PRECISION = 0x82
    const val TIMESTAMP_MINUTE_PRECISION = 0x83
    const val TIMESTAMP_SECOND_PRECISION = 0x84
    const val TIMESTAMP_MILLIS_PRECISION = 0x85
    const val TIMESTAMP_MICROS_PRECISION = 0x86
    const val TIMESTAMP_NANOS_PRECISION = 0x87
    const val TIMESTAMP_MINUTE_PRECISION_WITH_OFFSET = 0x88
    const val TIMESTAMP_SECOND_PRECISION_WITH_OFFSET = 0x89
    const val TIMESTAMP_MILLIS_PRECISION_WITH_OFFSET = 0x8A
    const val TIMESTAMP_MICROS_PRECISION_WITH_OFFSET = 0x8B
    const val TIMESTAMP_NANOS_PRECISION_WITH_OFFSET = 0x8C
    const val RESERVED_0x8D = 0x8D
    const val RESERVED_0x8E = 0x8E
    const val RESERVED_0x8F = 0x8F

    const val STRING_VALUE_ZERO_LENGTH = 0x90

    const val SYMBOL_VALUE_TEXT_ZERO_LENGTH = 0xA0

    const val LIST_ZERO_LENGTH = 0xB0

    const val SEXP_ZERO_LENGTH = 0xC0

    const val STRUCT_ZERO_LENGTH = 0xD0
    const val RESERVED_0xD1 = 0xD1

    const val IVM = 0xE0
    const val SYMBOL_VALUE_SID_U8 = 0xE1
    const val SYMBOL_VALUE_SID_U16 = 0xE2
    const val SYMBOL_VALUE_SID_FLEXUINT = 0xE3
    const val ANNOTATION_1_SID = 0xE4
    const val ANNOTATION_2_SID = 0xE5
    const val ANNOTATION_N_SID = 0xE6
    const val ANNOTATION_1_FLEXSYM = 0xE7
    const val ANNOTATION_2_FLEXSYM = 0xE8
    const val ANNOTATION_N_FLEXSYM = 0xE9
    const val NULL_VALUE = 0xEA
    const val TYPED_NULL_VALUE = 0xEB
    const val NOP = 0xEC
    const val NOP_WITH_LENGTH = 0xED
    const val SYSTEM_SYMBOL = 0xEE
    const val SYSTEM_MACRO_EEXP = 0xEF

    const val DELIMITED_CONTAINER_END = 0xF0
    const val DELIMITED_LIST = 0xF1
    const val DELIMITED_SEXP = 0xF2
    const val DELIMITED_STRUCT = 0xF3
    const val E_EXPRESSION_WITH_FLEX_UINT_ADDRESS = 0xF4
    const val LENGTH_PREFIXED_MACRO_INVOCATION = 0xF5
    const val VARIABLE_LENGTH_INTEGER = 0xF6
    const val VARIABLE_LENGTH_DECIMAL = 0xF7
    const val VARIABLE_LENGTH_TIMESTAMP = 0xF8
    const val VARIABLE_LENGTH_STRING = 0xF9
    const val VARIABLE_LENGTH_INLINE_SYMBOL = 0xFA
    const val VARIABLE_LENGTH_LIST = 0xFB
    const val VARIABLE_LENGTH_SEXP = 0xFC
    const val VARIABLE_LENGTH_STRUCT_WITH_SIDS = 0xFD
    const val VARIABLE_LENGTH_BLOB = 0xFE
    const val VARIABLE_LENGTH_CLOB = 0xFF
}
