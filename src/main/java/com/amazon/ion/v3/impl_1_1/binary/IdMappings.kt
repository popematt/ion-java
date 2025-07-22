package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer
import kotlin.IllegalStateException

/**
 * Helper class containing info about Ion 1.1 opcodes.
 */
object IdMappings {
    @JvmStatic
    val TOKEN_TYPE_FOR_OPCODE = IntArray(256) { i -> tokenTypeForOpCode(i) }

    @JvmStatic
    private val LENGTH_FOR_OPCODE = IntArray(256) { i -> lengthForOpCode(i) }

    fun interface LengthCalculator {
        fun calculate(source: ByteBuffer, macroTable: Array<MacroV2>, positionAfterOpcode: Int): Int
    }

    @JvmStatic
    val LENGTH_FOR_OPCODE_CALCULATOR = Array(256) { opcode ->
        when (opcode) {
            in 0..0x5F -> LengthCalculator { s, m, p -> SkipHelper.skipMacro(s, m, opcode, p) - p }
            in 0x60 .. 0x68 -> LengthCalculator { _, _, _ -> (opcode and 0xF) }
            0x69 -> LengthCalculator { _, _, _ -> throw IonException("0x69 is reserved and not a valid opcode.")  }
            // Floats
            0x6A -> LengthCalculator { _, _, _ -> 0 }
            0x6B -> LengthCalculator { _, _, _ -> 2 }
            0x6C -> LengthCalculator { _, _, _ -> 4 }
            0x6D -> LengthCalculator { _, _, _ -> 8 }
            in 0x6E .. 0x6F -> LengthCalculator { _, _, _ -> 0 }
            in 0x70 .. 0x7F -> LengthCalculator { _, _, _ -> (opcode and 0xF) }

            // Short form timestamps
            0x80 -> LengthCalculator { _, _, _ -> 1 }
            0x81 -> LengthCalculator { _, _, _ -> 2 }
            0x82 -> LengthCalculator { _, _, _ -> 2 }
            0x83 -> LengthCalculator { _, _, _ -> 4 }
            0x84 -> LengthCalculator { _, _, _ -> 5 }
            0x85 -> LengthCalculator { _, _, _ -> 6 }
            0x86 -> LengthCalculator { _, _, _ -> 7 }
            0x87 -> LengthCalculator { _, _, _ -> 8 }
            0x88 -> LengthCalculator { _, _, _ -> 5 }
            0x89 -> LengthCalculator { _, _, _ -> 5 }
            0x8A -> LengthCalculator { _, _, _ -> 7 }
            0x8B -> LengthCalculator { _, _, _ -> 8 }
            0x8C -> LengthCalculator { _, _, _ -> 9 }

            in 0x8D .. 0x8F -> LengthCalculator { _, _, _ -> throw IonException("Reserved opcode") }
            in 0x90 .. 0x9F -> LengthCalculator { _, _, _ -> (opcode and 0xF) }
            in 0xA0 .. 0xAF -> LengthCalculator { _, _, _ -> (opcode and 0xF) }
            in 0xB0 .. 0xBF -> LengthCalculator { _, _, _ -> (opcode and 0xF) }
            in 0xC0 .. 0xCF -> LengthCalculator { _, _, _ -> (opcode and 0xF) }
            0xD0 -> LengthCalculator { _, _, _ -> 0 }
            0xD1 -> LengthCalculator { _, _, _ -> throw IonException("Invalid opcode") }
            in 0xD2 .. 0xDF -> LengthCalculator { _, _, _ -> (opcode and 0xF) }
            // IVM
            0xE0 -> LengthCalculator { _, _, _ -> 3 }
            // One byte SID
            0xE1 -> LengthCalculator { _, _, _ -> 1 }
            // Biased 2-byte SID
            0xE2 -> LengthCalculator { _, _, _ -> 2 }
            // FlexUInt SID
            0xE3 -> LengthCalculator { s, _, p -> p + IntHelper.lengthOfFlexUIntAt(s, p) }
            // SID Annotations
            0xE4 -> LengthCalculator { s, _, p -> p + IntHelper.lengthOfFlexUIntAt(s, p) }
            0xE5 -> LengthCalculator { s, _, p ->
                var i = p
                i += IntHelper.lengthOfFlexUIntAt(s, i)
                i += IntHelper.lengthOfFlexUIntAt(s, i)
                i - p
            }
            // Annotations with FlexUInt length
            0xE6 -> LengthCalculator { s, _, p ->
                val n = IntHelper.readFlexUIntAt(s, p)
                var i = p + IntHelper.lengthOfFlexUIntAt(s, p)
                repeat(n) {
                    i += IntHelper.lengthOfFlexUIntAt(s, i)
                }
                i - p
            }
            0xE7 -> LengthCalculator { s, _, p -> FlexSymHelper.lengthOfFlexSymAt(s, p) }
            0xE8 -> LengthCalculator { s, _, p ->
                val l0 = FlexSymHelper.lengthOfFlexSymAt(s, p)
                val l1 = FlexSymHelper.lengthOfFlexSymAt(s, p + l0)
                l0 + l1
            }
            0xE9 -> LengthCalculator { s, _, p ->
                val n = IntHelper.readFlexUIntAt(s, p)
                var i = p + IntHelper.lengthOfFlexUIntAt(s, p)
                repeat(n) {
                    i += FlexSymHelper.lengthOfFlexSymAt(s, i)
                }
                i - p
            }
            0xEA -> LengthCalculator { _, _, _ -> 0 }
            0xEB -> LengthCalculator { _, _, _ -> 1 }
            0xEC -> LengthCalculator { _, _, _ -> 0 }
            0xED -> LengthCalculator { s, _, p -> IntHelper.getLengthPlusValueOfFlexUIntAt(s, p) }
            0xEE -> LengthCalculator { _, _, _ -> 1 }
            0xEF -> LengthCalculator { s, m, p -> SkipHelper.skipMacro(s, m, opcode, p) - p }
            0xF0 -> LengthCalculator { _, _, _, -> throw IonException("Delimited End is not a value or expression.") }
            0xF1,
            0xF2 -> LengthCalculator { s, m, p -> SkipHelper.seekToEndOfDelimitedContainer(s, m, isStruct = false, p) - p }
            0xF3 ->	LengthCalculator { s, m, p -> SkipHelper.seekToEndOfDelimitedContainer(s, m, isStruct = true, p) - p }
            0xF4 ->	LengthCalculator { s, m, p -> SkipHelper.skipMacro(s, m, opcode, p) - p }
            0xF5 ->	LengthCalculator { s, _, p ->
                // Macro Address
                var l = IntHelper.readFlexUIntAt(s, p)
                // Length of length prefix and arguments
                l += IntHelper.getLengthPlusValueOfFlexUIntAt(s, p + l)
                l
            }
            in 0xF6 .. 0xFF -> LengthCalculator { s, _, p -> IntHelper.getLengthPlusValueOfFlexUIntAt(s, p) }
            else -> throw IllegalStateException("Unreachable")
        }
    }


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
            ValueReaderBase.TID_EXPRESSION_GROUP.toInt() -> IntHelper.readFlexUInt(buffer)
            ValueReaderBase.TID_ON_FIELD_NAME.toInt() -> -2
            ValueReaderBase.TID_UNSET.toInt() -> throw IllegalStateException("Not positioned on an expression or value")
            else -> {
                val it = LENGTH_FOR_OPCODE[typeId]
                when (it) {
                    -3 -> throw IonException("Invalid input: illegal typeId: $typeId")
                    -2 -> -2
                    -1 -> IntHelper.readFlexUInt(buffer)
                    else -> it
                }
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
