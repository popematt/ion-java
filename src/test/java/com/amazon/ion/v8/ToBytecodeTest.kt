package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.system.*
import com.amazon.ion.v8.Bytecode.opToInstruction
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ToBytecodeTest {
    companion object {
        init {
            Bytecode.Operations.dump()
            println()
        }
    }

    @Test
    fun ivm() = checkIonBinaryCompilesToBytecode("E0 01 01 EA", "IVM")

    @Test
    fun nullNull() = checkIonBinaryCompilesToBytecode("8F", "NULL_NULL")

    @Test
    fun typedNull() = checkIonBinaryCompilesToBytecode("90 00", "NULL_BOOL")

    @Test
    fun int0() = checkIonBinaryCompilesToBytecode("60", "INT16 0")

    @Test
    fun int8() = checkIonBinaryCompilesToBytecode("61 01", "INT16 1")

    @Test
    fun int16() = checkIonBinaryCompilesToBytecode("62 FE FF", "INT16 -2")

    @Test
    fun string() = checkIonBinaryCompilesToBytecode("93 66 6F 6F", "STR_REF 3; 1")

    @Test
    fun symbolText() = checkIonBinaryCompilesToBytecode("A4 41 42 43 44", "SYM_REF 4; 1")

    @Test
    fun topLevelAnnotatedValue() = checkIonBinaryTopLevelCompilesToBytecode("8D 03 60", "ANN_SID 1; INT16 0; EOF")

    @Test
    fun topLevelMultiAnnotatedValue() = checkIonBinaryTopLevelCompilesToBytecode("8D 03 8D 05 60", "ANN_SID 1; ANN_SID 2; INT16 0; EOF")

    @Test
    fun emptyList() = checkIonBinaryCompilesToBytecode(
        "B0",
        """
            LIST_START 1
            CONTAINER_END
        """.trimIndent()
    )

    @Test
    fun listWithNop() = checkIonBinaryCompilesToBytecode(
        "B1 EE",
        """
            LIST_START 1
            CONTAINER_END
        """.trimIndent()
    )

    @Test
    fun listWithLongNop() = checkIonBinaryCompilesToBytecode(
        "B3 EF 03 69",
        """
            LIST_START 1
            CONTAINER_END
        """.trimIndent()
    )

    @Test
    fun listWithInt0() = checkIonBinaryCompilesToBytecode(
        "B1 60",
        """
            LIST_START 2
            INT16 0
            CONTAINER_END
        """.trimIndent()
    )


    @Test
    fun emptyStruct() = checkIonBinaryCompilesToBytecode(
        "D0",
        """
            STRUCT_START 1
            CONTAINER_END
        """.trimIndent()
    )

    @Test
    fun prefixedStructWithOneMemberAndSidFieldName() = checkIonBinaryCompilesToBytecode(
        "D2 01 60",
        """
            STRUCT_START 3
            FNAME_SID 0
            INT16 0
            CONTAINER_END
        """.trimIndent()
    )

    @Test
    fun prefixedStructWithOneMemberAndInlineFieldName() = checkIonBinaryCompilesToBytecode(
        "E0 01 01 EA " +
                "D3 FD 41 60",
        """
            STRUCT_START 4
            FNAME_REF 1
            6
            INT16 0
            CONTAINER_END
        """.trimIndent(),
        pos = 4
    )



    @Test
    fun addSymbolsDirective() = checkIonBinaryCompilesToBytecode(
        "E1 93 66 6F 6F F0",
        """
            SET_SYMBOLS
            STR_CNST 0
            CONTAINER_END
        """.trimIndent()
    )

    @Test
    fun addSymbolsDirectiveWithMultipleSymbols() = checkIonBinaryCompilesToBytecode(
        "E1 93 66 6F 6F A1 55 8F A4 41 42 43 44 F0",
        """
            SET_SYMBOLS
            STR_CNST 0
            SYM_CNST 1
            NULL_NULL
            SYM_CNST 2
            CONTAINER_END
        """.trimIndent()
    )



    @Test
    fun constantScalarMacroEvaluation() = checkIonBinaryCompilesToBytecode(
        "00",
        """
            INT16 1
        """,
        macTab = intArrayOf(
            Bytecode.OP_SMALL_INT.opToInstruction(1),
            Bytecode.EOF.opToInstruction(),
        ),
        macOffsets = intArrayOf(0, 2)
    )

    @Test
    fun constantContainerMacroEvaluation() = checkIonBinaryCompilesToBytecode(
        "00",
        """
            LIST_START 3
            INT16 1
            INT16 2
            CONTAINER_END
        """,
        macTab = intArrayOf(
            Bytecode.OP_LIST_START.opToInstruction(3),
            Bytecode.OP_SMALL_INT.opToInstruction(1),
            Bytecode.OP_SMALL_INT.opToInstruction(2),
            Bytecode.OP_CONTAINER_END.opToInstruction(),
            Bytecode.EOF.opToInstruction(),
        ),
        macOffsets = intArrayOf(0, 5)
    )

    @Test
    fun simpleTaggedParameterMacroEvaluationWithNullArg() = checkIonBinaryCompilesToBytecode(
        "00 8F",
        """
            LIST_START 2
            NULL_NULL
            CONTAINER_END
        """,
        macTab = intArrayOf(
            Bytecode.OP_LIST_START.opToInstruction(2),
            Bytecode.OP_PARAMETER.opToInstruction(),
            Bytecode.OP_CONTAINER_END.opToInstruction(),
            Bytecode.EOF.opToInstruction(),
        ),
        macOffsets = intArrayOf(0, 4)
    )

    @Test
    fun simpleTaggedParameterMacroEvaluationWithListArg() = checkIonBinaryCompilesToBytecode(
        "00 B2 6E 6F",
        """
            LIST_START 5
            LIST_START 3
            BOOL true
            BOOL false
            CONTAINER_END
            CONTAINER_END
        """,
        macTab = intArrayOf(
            Bytecode.OP_LIST_START.opToInstruction(2),
            Bytecode.OP_PARAMETER.opToInstruction(),
            Bytecode.OP_CONTAINER_END.opToInstruction(),
            Bytecode.EOF.opToInstruction(),
        ),
        macOffsets = intArrayOf(0, 4)
    )

    @Test
    fun taggedParameterMacroEvaluationWithMacroInvocationArg() = checkIonBinaryCompilesToBytecode(
        "00 01",
         """
            LIST_START 2
            INT16 1
            CONTAINER_END
        """,
        macTab = intArrayOf(
            Bytecode.OP_LIST_START.opToInstruction(2),
            Bytecode.OP_PARAMETER.opToInstruction(),
            Bytecode.OP_CONTAINER_END.opToInstruction(),
            Bytecode.EOF.opToInstruction(),
            Bytecode.OP_SMALL_INT.opToInstruction(1),
            Bytecode.EOF.opToInstruction(),
        ),
        macOffsets = intArrayOf(0, 4, 6)
    )

    @Test
    fun multipleTopLevelValuesCanBeCompiled() = checkIonBinaryTopLevelCompilesToBytecode("60 61 01 61 02", "INT16 0; INT16 1; INT16 2; EOF")

    @Test
    fun topLevelCompilationStopsWhenIvmIsEncountered() = checkIonBinaryTopLevelCompilesToBytecode("60 61 01 E0 01 01 EA 60 60", "INT16 0; INT16 1; IVM; REFILL")


    @ParameterizedTest
    @CsvSource(


        // -4231 | 66 03
        //  3257 | 6A 03

        // 00000001 00111010
        // 10011011 11100110
        //


        " -8, 11110001",
        " -12, 11101001",
        "                  64, 00000010 00000001", // 02 01
        "                3257, 11100110 00110010", // E6 32
        "               -3257, 00011110 11001101",
        "                  78, 00111010 00000001", // 3A 01
        "               -6407, 11100110 10011011", // E6 9B
        "                   0, 00000001",
        "                   1, 00000011",
        "                   2, 00000101",
        "                   3, 00000111",
        "                   4, 00001001",
        "                   5, 00001011",
        "                  14, 00011101",
        "                  63, 01111111",
        "                  64, 00000010 00000001",
        "                 729, 01100110 00001011",
        "                8191, 11111110 01111111",
        "                8192, 00000100 00000000 00000001",
        "             1048575, 11111100 11111111 01111111",
        "             1048576, 00001000 00000000 00000000 00000001",
        "           134217727, 11111000 11111111 11111111 01111111",
        "           134217728, 00010000 00000000 00000000 00000000 00000001",
        "    ${Int.MAX_VALUE}, 11110000 11111111 11111111 11111111 00001111",
        "                  -1, 11111111",
        "                  -2, 11111101",
        "                  -3, 11111011",
        "                 -14, 11100101",
        "                 -64, 10000001",
        "                 -65, 11111110 11111110",
        "                -729, 10011110 11110100",
        "               -8192, 00000010 10000000",
        "               -8193, 11111100 11111111 11111110",
        "            -1048576, 00000100 00000000 10000000",
        "            -1048577, 11111000 11111111 11111111 11111110",
        "          -134217728, 00001000 00000000 00000000 10000000",
        "    ${Int.MIN_VALUE}, 00010000 00000000 00000000 00000000 11110000",
    )
    fun testReadFlexIntValueAndLengthAt(expectedValue: Int, input: String) {

        val data = ("0 0 0 0 $input").split(" ").map { it.toInt(2).toByte() }.toByteArray()

        val valueAndLength = IntHelper.readFlexIntValueAndLengthAt(data, 4)
        val value = (valueAndLength shr 8).toInt()
        val length = (valueAndLength and 0xFF).toInt()

        Assertions.assertEquals(expectedValue, value)
        Assertions.assertEquals((input.length + 1) / 9, length)
    }


    fun checkIonBinaryCompilesToBytecode(
        ionBinary: String,
        expectedBytecode: String,
        cp: UnsafeObjectList<Any?> = UnsafeObjectList<Any?>(),
        symTab: Array<String?> = arrayOf<String?>(null),
        macTab: IntArray = IntArray(0),
        macOffsets: IntArray = intArrayOf(0),
        pos: Int = 0
    ) {
        val data = toByteArray(ionBinary)
        val dest = IntList()
        var p = pos
        val op = data[p++].toInt() and 0xFF
        testCompileValue(data, p, op, dest, cp, macTab, macOffsets, symTab)
        val bytecode = Bytecode.toDebugString(dest.toArray(), useIndent = false, useNumbers = false, lax = true).split("\n").joinToString("\n") { it.trim() }.trim()
        val expected = expectedBytecode.replace(";", "\n").split("\n").joinToString("\n") { it.trim() }.trim()
        assertEquals(expected, bytecode)
        Bytecode.debugString(dest.toArray(), constantPool = cp.toArray())
        println()
    }


    fun checkIonBinaryTopLevelCompilesToBytecode(
        ionBinary: String,
        expectedBytecode: String,
        cp: UnsafeObjectList<Any?> = UnsafeObjectList<Any?>(),
        symTab: Array<String?> = arrayOf<String?>(null),
        macTab: IntArray = IntArray(0),
        macOffsets: IntArray = intArrayOf(0),
        pos: Int = 0
    ) {
        val data = toByteArray(ionBinary)
        val dest = IntList()
        compileTopLevel(data, pos, dest, cp, macTab, macOffsets, symTab, data.size)
        val bytecode = Bytecode.toDebugString(dest.toArray(), useIndent = false, useNumbers = false, lax = true).split("\n").joinToString("\n") { it.trim() }.trim()
        val expected = expectedBytecode.replace(";", "\n").split("\n").joinToString("\n") { it.trim() }.trim()
        assertEquals(expected, bytecode)
        Bytecode.debugString(dest.toArray(), constantPool = cp.toArray())
        println()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun toByteArray(value: String): ByteArray {
        val hexChunks = value.lines()
            .joinToString(" ") { it.takeWhile { c -> c != '|' }.trim() }
            .trim()
            .split(Regex("\\s+"))
        println(hexChunks)
        return ByteArray(hexChunks.size) { hexChunks[it].hexToUByte().toByte() }
    }
}
