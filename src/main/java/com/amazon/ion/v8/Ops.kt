package com.amazon.ion.v8

import com.amazon.ion.IonType

object Ops {
    const val MACRO_0 = 0x00
    const val MACRO_3F = 0x3F

    const val BIASED_E_EXPRESSION_ONE_BYTE_FIXED_INT = 0x40
    const val BIASED_E_EXPRESSION_TWO_BYTE_FIXED_INT = 0x50

    const val INT_0 = 0x60
    const val INT_8 = 0x61
    const val INT_16 = 0x62
    const val INT_24 = 0x63
    const val INT_32 = 0x64
    const val INT_40 = 0x65
    const val INT_48 = 0x66
    const val INT_56 = 0x67
    const val INT_64 = 0x68
    const val RESERVED_0x69 = 0x69

    const val FLOAT_0 = 0x6A
    const val FLOAT_16 = 0x6B
    const val FLOAT_32 = 0x6C
    const val FLOAT_64 = 0x6D

    const val BOOL_TRUE = 0x6E
    const val BOOL_FALSE = 0x6F

    const val DECIMAL_0 = 0x70
    const val DECIMAL_LENGTH_15 = 0x7F

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

    const val ANNOTATION_SID = 0x8D
    const val ANNOTATION_TEXT = 0x8E

    const val NULL_NULL = 0x8F
    const val TYPED_NULL = 0x90

    const val STRING_LENGTH_1 = 0x91
    const val STRING_LENGTH_15 = 0x9F

    const val SYMBOL_VALUE_SID = 0xA0

    const val SYMBOL_VALUE_TEXT_ONE_LENGTH = 0xA1
    const val SYMBOL_LENGTH_15 = 0xAF

    const val LIST_ZERO_LENGTH = 0xB0
    const val LIST_LENGTH_15 = 0xBF

    const val SEXP_ZERO_LENGTH = 0xC0
    const val SEXP_LENGTH_15 = 0xCF

    const val STRUCT_ZERO_LENGTH = 0xD0
    const val RESERVED_0xD1 = 0xD1
    const val STRUCT_LENGTH_15 = 0xDF

    const val IVM = 0xE0
    const val SET_SYMBOLS = 0xE1
    const val ADD_SYMBOLS = 0xE2
    const val SET_MACROS = 0xE3
    const val ADD_MACROS = 0xE4
    const val USE = 0xE5
    const val MODULE = 0xE6
    const val ENCODING = 0xE7
    const val NOTHING_ARGUMENT = 0xE8
    const val TAGGED_PLACEHOLDER = 0xE9
    const val TAGGED_PLACEHOLDER_WITH_DEFAULT = 0xEA
    const val TAGLESS_PLACEHOLDER = 0xEB

    const val TAGLESS_ELEMENT_LIST = 0xEC
    const val TAGLESS_ELEMENT_SEXP = 0xED

    const val NOP = 0xEE
    const val NOP_L = 0xEF

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


    // When dealing with tagless types, there are a few supplemental/replacement opcodes
    // See [TaglessScalarType]

    const val TE_FLEX_INT = 0x60
    const val TE_FLEX_UINT = 0xE0
    const val TE_UINT_8 = 0xE1
    const val TE_UINT_16 = 0xE2
    const val TE_UINT_32 = 0xE4
    const val TE_UINT_64 = 0xE8
    const val TE_ANY_SYMBOL = 0xEA

    data class Info(
        /** ordinal of IonType, or -1 */
        @JvmField val type: Byte,
        /**
         * `-1` == length prefix as flexuint
         * `-2` == delimited
         * `-3` == value is a flexuint
         */
        @JvmField val length: Byte,
        @JvmField val tokenType: Byte,
    ) {

        constructor(type: IonType?, length: Int, tokenType: Int) : this(type?.ordinal?.toByte() ?: -1, length.toByte(), tokenType.toByte())

        fun toInt(): Int {
            return type.toInt().and(0xFF).shl(24) or
                    length.toInt().and(0xFF).shl(16) or
                    tokenType.toInt().and(0xFF).shl(8)
        }

        companion object {
            @JvmStatic fun ionType(info: Int) = info.ushr(24).and(0xFF)
            @JvmStatic fun length(info: Int) = info.ushr(16).and(0xFF).toByte().toInt()
            @JvmStatic fun tokenType(info: Int) = info.ushr(8).and(0xFF)
        }
    }


    fun opInfo(opcode: Int): Info {
        return when (opcode) {
            in 0..0x5F -> Info(null, -2, TokenTypeConst.MACRO_INVOCATION)
            in 0x60..0x68 -> Info(IonType.INT, (opcode and 0xF), TokenTypeConst.INT)
            0x69 -> Info(null, 0, TokenTypeConst.RESERVED)
            0x6A -> Info(IonType.FLOAT, 0, TokenTypeConst.FLOAT)
            0x6B -> Info(IonType.FLOAT, 2, TokenTypeConst.FLOAT)
            0x6C -> Info(IonType.FLOAT, 4, TokenTypeConst.FLOAT)
            0x6D -> Info(IonType.FLOAT, 8, TokenTypeConst.FLOAT)
            0x6E,
            0x6F -> Info(IonType.BOOL, 0, TokenTypeConst.BOOL)
            in 0x70..0x7F -> Info(IonType.DECIMAL, (opcode and 0xF), TokenTypeConst.DECIMAL)

            0x80 -> Info(IonType.TIMESTAMP, 1, TokenTypeConst.TIMESTAMP)
            0x81 -> Info(IonType.TIMESTAMP, 2, TokenTypeConst.TIMESTAMP)
            0x82 -> Info(IonType.TIMESTAMP, 2, TokenTypeConst.TIMESTAMP)
            0x83 -> Info(IonType.TIMESTAMP, 4, TokenTypeConst.TIMESTAMP)
            0x84 -> Info(IonType.TIMESTAMP, 5, TokenTypeConst.TIMESTAMP)
            0x85 -> Info(IonType.TIMESTAMP, 6, TokenTypeConst.TIMESTAMP)
            0x86 -> Info(IonType.TIMESTAMP, 8, TokenTypeConst.TIMESTAMP)
            0x87 -> Info(IonType.TIMESTAMP, 5, TokenTypeConst.TIMESTAMP)
            0x88 -> Info(IonType.TIMESTAMP, 5, TokenTypeConst.TIMESTAMP)
            0x89 -> Info(IonType.TIMESTAMP, 7, TokenTypeConst.TIMESTAMP)
            0x8A -> Info(IonType.TIMESTAMP, 8, TokenTypeConst.TIMESTAMP)
            0x8B -> Info(IonType.TIMESTAMP, 9, TokenTypeConst.TIMESTAMP)

            ANNOTATION_SID -> Info(null, -3, TokenTypeConst.ANNOTATIONS)
            ANNOTATION_TEXT -> Info(null, -1, TokenTypeConst.ANNOTATIONS)

            in 0x91..0x9F -> Info(IonType.STRING, (opcode and 0xF), TokenTypeConst.STRING)
            0xA0 -> Info(IonType.SYMBOL, -3, TokenTypeConst.SYMBOL)
            in 0xA1..0xAF -> Info(IonType.SYMBOL, (opcode and 0xF), TokenTypeConst.SYMBOL)
            in 0xB0..0xBF -> Info(IonType.LIST, (opcode and 0xF), TokenTypeConst.LIST)
            in 0xC0..0xCF -> Info(IonType.SEXP, (opcode and 0xF), TokenTypeConst.SEXP)
            0xD1 -> Info(null, 0, TokenTypeConst.RESERVED)
            in 0xD0..0xDF -> Info(IonType.STRUCT, (opcode and 0xF), TokenTypeConst.STRUCT)

            IVM -> Info(null, 3, TokenTypeConst.IVM)
            SET_SYMBOLS -> Info(null, -2, TokenTypeConst.SYSTEM_VALUE)
            ADD_SYMBOLS -> Info(null, -2, TokenTypeConst.SYSTEM_VALUE)
            SET_MACROS -> Info(null, -2, TokenTypeConst.SYSTEM_VALUE)
            ADD_MACROS -> Info(null, -2, TokenTypeConst.SYSTEM_VALUE)
            USE -> Info(null, -2, TokenTypeConst.SYSTEM_VALUE)
            MODULE -> Info(null, -2, TokenTypeConst.SYSTEM_VALUE)
            ENCODING -> Info(null, -2, TokenTypeConst.SYSTEM_VALUE)
            NOTHING_ARGUMENT -> Info(null, -2, TokenTypeConst.ABSENT_ARGUMENT)
            TAGGED_PLACEHOLDER -> Info(null, 0, TokenTypeConst.VARIABLE_REF)
            TAGGED_PLACEHOLDER_WITH_DEFAULT -> Info(null, -2, TokenTypeConst.VARIABLE_REF)

            TAGLESS_ELEMENT_LIST -> Info(IonType.LIST,-4, TokenTypeConst.LIST)
            NOP -> Info(null, 0, TokenTypeConst.NOP)
            NOP_L -> Info(null, -1, TokenTypeConst.NOP)
            NULL_NULL -> Info(IonType.NULL, 0, TokenTypeConst.NULL)
            TYPED_NULL -> Info(null, 1, TokenTypeConst.NULL)

            DELIMITED_CONTAINER_END -> Info(null, 0, TokenTypeConst.END)
            DELIMITED_LIST -> Info(IonType.LIST, -2, TokenTypeConst.LIST)
            DELIMITED_SEXP -> Info(IonType.SEXP, -2, TokenTypeConst.SEXP)
            DELIMITED_STRUCT -> Info(IonType.STRUCT, -2, TokenTypeConst.STRUCT)
            E_EXPRESSION_WITH_FLEX_UINT_ADDRESS -> Info(null, -2, TokenTypeConst.MACRO_INVOCATION)
            LENGTH_PREFIXED_MACRO_INVOCATION -> Info(null, -1, TokenTypeConst.MACRO_INVOCATION)
            VARIABLE_LENGTH_INTEGER -> Info(IonType.INT, -1, TokenTypeConst.INT)
            VARIABLE_LENGTH_DECIMAL -> Info(IonType.DECIMAL, -1, TokenTypeConst.DECIMAL)
            VARIABLE_LENGTH_TIMESTAMP -> Info(IonType.TIMESTAMP, -1, TokenTypeConst.TIMESTAMP)
            VARIABLE_LENGTH_STRING -> Info(IonType.STRING, -1, TokenTypeConst.STRING)
            VARIABLE_LENGTH_INLINE_SYMBOL -> Info(IonType.SYMBOL, -1, TokenTypeConst.SYMBOL)
            VARIABLE_LENGTH_LIST -> Info(IonType.LIST, -1, TokenTypeConst.LIST)
            VARIABLE_LENGTH_SEXP -> Info(IonType.SEXP, -1, TokenTypeConst.SEXP)
            VARIABLE_LENGTH_STRUCT_WITH_SIDS -> Info(IonType.STRUCT, -1, TokenTypeConst.STRUCT)
            VARIABLE_LENGTH_BLOB -> Info(IonType.BLOB, -1, TokenTypeConst.BLOB)
            VARIABLE_LENGTH_CLOB -> Info(IonType.CLOB, -1, TokenTypeConst.CLOB)
            else -> TODO(opcode.toString())
        }
    }

    @JvmField
    val OP_INFO = IntArray(256) { opInfo(it).toInt() }
}
