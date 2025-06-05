package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.v3.*
import java.lang.IllegalStateException
import java.nio.ByteBuffer

/**
 * Helper class containing info about Ion 1.1 opcodes.
 */
object IdMappings {
    @JvmStatic
    val TOKEN_TYPE_FOR_OPCODE = IntArray(256) { i -> tokenTypeForOpCode(i) }

    @JvmStatic
    private val LENGTH_FOR_OPCODE = IntArray(256) { i -> lengthForOpCode(i) }

    @JvmStatic
    private fun tokenTypeForOpCode(opcode: Int): Int {
        return when (opcode) {
            in 0x00 .. 0x5F -> TokenTypeConst.MACRO_INVOCATION
            in 0x60 .. 0x68 -> TokenTypeConst.INT
            0x69 -> TokenTypeConst.RESERVED
            in 0x6A .. 0x6D -> TokenTypeConst.FLOAT
            in 0x6E .. 0x6F -> TokenTypeConst.BOOL
            in 0x70 .. 0x7F -> TokenTypeConst.DECIMAL
            in 0x80 .. 0x8C -> TokenTypeConst.TIMESTAMP
            in 0x8D .. 0x8F -> TokenTypeConst.RESERVED
            in 0x90 .. 0x9F -> TokenTypeConst.STRING
            in 0xA0 .. 0xAF -> TokenTypeConst.SYMBOL
            in 0xB0 .. 0xBF -> TokenTypeConst.LIST
            in 0xC0 .. 0xCF -> TokenTypeConst.SEXP
            0xD0, in 0xD2 .. 0xDF -> TokenTypeConst.STRUCT
            0xD1 -> TokenTypeConst.RESERVED
            0xE0 -> TokenTypeConst.IVM
            in 0xE1 .. 0xE3 -> TokenTypeConst.SYMBOL
            in 0xE4 .. 0xE9 -> TokenTypeConst.ANNOTATIONS
            in 0xEA .. 0xEB -> TokenTypeConst.NULL
            in 0xEC .. 0xED -> TokenTypeConst.NOP
            0xEE -> TokenTypeConst.SYMBOL
            0xEF -> TokenTypeConst.MACRO_INVOCATION
            0xF0 -> TokenTypeConst.END
            0xF1 ->	TokenTypeConst.LIST
            0xF2 ->	TokenTypeConst.SEXP
            0xF3 ->	TokenTypeConst.STRUCT
            0xF4 ->	TokenTypeConst.MACRO_INVOCATION
            0xF5 ->	TokenTypeConst.MACRO_INVOCATION
            0xF6 ->	TokenTypeConst.INT
            0xF7 ->	TokenTypeConst.DECIMAL
            0xF8 ->	TokenTypeConst.TIMESTAMP
            0xF9 ->	TokenTypeConst.STRING
            0xFA ->	TokenTypeConst.SYMBOL
            0xFB ->	TokenTypeConst.LIST
            0xFC ->	TokenTypeConst.SEXP
            0xFD -> TokenTypeConst.STRUCT
            0xFE ->	TokenTypeConst.BLOB
            0xFF ->	TokenTypeConst.CLOB
            else -> TODO()
        }
    }

    /**
     * Returns >= 0 for known lengths,
     * -1 for not-yet-known-lengths,
     * -2 for incalculable lengths
     * -3 for invalid type codes
     */
    @JvmStatic
    private fun lengthForOpCode(opcode: Int): Int {
        return when (opcode) {
            in 0x00 .. 0x5F -> -2
            in 0x60 .. 0x68 -> opcode and 0xF
            0x69 -> -3
            // Floats
            0x6A -> 0
            0x6B -> 2
            0x6C -> 4
            0x6D -> 8
            in 0x6E .. 0x6F -> 0
            in 0x70 .. 0x7F -> opcode and 0xF

            // Short form timestamps
            0x80 -> 1
            0x81 -> 2
            0x82 -> 2
            0x83 -> 4
            0x84 -> 5
            0x85 -> 6
            0x86 -> 7
            0x87 -> 8
            0x88 -> 5
            0x89 -> 5
            0x8A -> 7
            0x8B -> 8
            0x8C -> 9

            in 0x8D .. 0x8F -> -3
            in 0x90 .. 0x9F -> opcode and 0xF
            in 0xA0 .. 0xAF -> opcode and 0xF
            in 0xB0 .. 0xBF -> opcode and 0xF
            in 0xC0 .. 0xCF -> opcode and 0xF
            0xD0, in 0xD2 .. 0xDF -> opcode and 0xF
            0xD1 -> -3
            0xE0 -> 3
            // One byte SID
            0xE1 -> 1
            // Biased 2-byte SID
            0xE2 -> 2
            // FlexUInt SID
            0xE3 -> -2
            // Annotations with FlexUInt length
            0xE6 -> -1
            0xE9 -> -1
            in 0xE4 .. 0xE9 -> -2
            0xEA -> 0
            0xEB -> 1
            0xEC -> 0 // NOP
            0xED -> -1 // NOP
            0xEE -> 1 // System symbol
            0xEF -> -2 // System e-exp
            in 0xF0 ..  0xF3 ->	-2
            0xF4 ->	-2
            // TODO: This should be calculable
            0xF5 ->	-1
            in 0xF6 .. 0xFF ->	-1
            else -> TODO()
        }
    }

    /**
     * Returns >= 0 for known lengths and -2 for incalculable lengths
     */
    @JvmStatic
    fun length(typeId: Int, buffer: ByteBuffer): Int {
        return when (typeId) {
            ValueReaderBase.TID_EMPTY_ARGUMENT.toInt() -> 0
            ValueReaderBase.TID_EXPRESSION_GROUP.toInt() -> -2
            ValueReaderBase.TID_ON_FIELD_NAME.toInt() -> -2
            ValueReaderBase.TID_UNSET.toInt() -> throw IllegalStateException("Not positioned on an expression or value")
            else -> when (val it = LENGTH_FOR_OPCODE[typeId]) {
                -3 -> throw IonException("Invalid input: illegal typeId: $typeId")
                -2 -> -2
                -1 -> IntHelper.readFlexUInt(buffer)
                else -> it
            }
        }
    }

    /**
     * Returns >= 0 for known lengths, -1 for unknown lengths, and -2 for incalculable lengths
     */
    @JvmStatic
    fun length(typeId: Int): Int {
        return when (typeId) {
            ValueReaderBase.TID_EMPTY_ARGUMENT.toInt() -> 0
            ValueReaderBase.TID_EXPRESSION_GROUP.toInt() -> -2
            else -> when (val it = LENGTH_FOR_OPCODE[typeId]) {
                -3 -> throw IonException("Invalid input: illegal typeId: $typeId")
                else -> it
            }
        }
    }
}
