package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.system.*
import com.amazon.ion.util.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.*
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


    @Test
    fun serviceLogDataSmall() {
        var p = 0
        val data = toByteArray(ServiceLogSmallData.serviceLogDataSmall)

//        println("ivm:             ${toByteArray(ServiceLogSmallData.ivm).size}")
//        println("symtab0:         ${toByteArray(ServiceLogSmallData.symTab0).size}")
//        println("mactab0:         ${toByteArray(ServiceLogSmallData.macTab0).size}")
//        println("symtab1:         ${toByteArray(ServiceLogSmallData.symTab1).size}")
//        println("tlv0:            ${toByteArray(ServiceLogSmallData.tlv0).size}")
//        println("symtab2:         ${toByteArray(ServiceLogSmallData.symTab2).size}")
//        println("tlv1:            ${toByteArray(ServiceLogSmallData.tlv1).size}")
        println("Total Data Size: ${data.size}")

        /*
                         MinT   Ion 1.1
        ivm:                4         4
        symtab0:            9        12
        mactab0:          879      1665
        symtab1:          466       469
        tlv0:              98        98
        symtab2:           30        33
        tlv1:              55        61
        -------------------------------
        Total:           1541      2342

        34% size savings, although most of that is in the macro table (which is amortized).
        Excluding the macro table, it's about 2% smaller, which is statistically insignificant to say anything about the encoding in general.
         */


        val writePath = Path("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_small.11m.10n")

//        Files.write(writePath, data)


        val reader = BytecodeIonReader(data)

        val readPath = Path("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_small.11.10n")
        val expected = IonReaderBuilder.standard().build(Files.readAllBytes(readPath))

        while (true) {
            val expectedNextType = expected.next()
            val nextType = reader.next()
            assertEquals(expectedNextType, nextType)
            when (nextType) {
                IonType.NULL -> {}
                IonType.BOOL -> reader.booleanValue()
                IonType.INT -> reader.longValue()
                IonType.FLOAT -> reader.doubleValue()
                IonType.DECIMAL -> reader.decimalValue()
                IonType.TIMESTAMP -> reader.timestampValue()
                IonType.SYMBOL -> assertEquals(expected.stringValue(), reader.stringValue())
                IonType.STRING -> assertEquals(expected.stringValue(), reader.stringValue())
                IonType.CLOB -> reader.newBytes()
                IonType.BLOB -> reader.newBytes()
                IonType.LIST,
                IonType.SEXP,
                IonType.STRUCT -> {
                    expected.stepIn()
                    reader.stepIn()
                }
                IonType.DATAGRAM -> TODO("Unreachable")
                null -> {
                    if (reader.depth > 0) {
                        expected.stepOut()
                        reader.stepOut()
                    } else {
                        break
                    }
                }
            }

        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun serviceLogDataMed() {

        serviceLogDataSmall()

        val writePath = Path("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11m.10n")
        val readPath = Path("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11.10n")

        Files.deleteIfExists(writePath)

        FileChannel.open(readPath, StandardOpenOption.READ).use { fileChannel ->
            val readBuffer: ByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            FileChannel.open(writePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { writeChannel ->

                println("Files are open")

                writeChannel.write(ByteBuffer.wrap(toByteArray(ServiceLogSmallData.serviceLogDataSmall)))
                println("Total bytes written: ${writeChannel.position()}")

                readBuffer.position(2342)

                val writeBuffer = ByteBuffer.wrap(ByteArray(1024 * 1024))

                require(!writeBuffer.isReadOnly)


                while (readBuffer.hasRemaining()) {
                    if (writeBuffer.remaining() < 10000) {
                        println("Attempting to write ${writeBuffer.position()} bytes.")
                        writeBuffer.limit(writeBuffer.position())
                        writeBuffer.position(0)
                        // InspectorV8.inspect(writeBuffer.array().sliceArray(0 until writeBuffer.position()))
                        val b = writeChannel.write(writeBuffer)
                        println("Successfully wrote $b bytes")
                        writeBuffer.limit(writeBuffer.capacity())
                        println("Total bytes written: ${writeChannel.position()}")
                    }
                    UpgradeToV8.copyValue(readBuffer, writeBuffer)
                }

                println("Attempting to write ${writeBuffer.position()} bytes.")
                writeBuffer.limit(writeBuffer.position())
                writeBuffer.position(0)
                // InspectorV8.inspect(writeBuffer.array().sliceArray(0 until writeBuffer.position()))
                val b = writeChannel.write(writeBuffer)
                println("Successfully wrote $b bytes")

                println("Total bytes written: ${writeChannel.position()}")
                println("Done")

            }
        }

        val data = Files.readAllBytes(writePath)


        println("Read ${data.size} bytes")
        // println(data.toPrettyHexString())



//        println(data.slice(1545..1565).joinToString(separator = " ") {it.toHexString()})


        InspectorV8.inspect(data, range = 18..19)

        println("Done Inspecting.")


        val reader = BytecodeIonReader(data)

        val expected = IonReaderBuilder.standard().build(Files.readAllBytes(readPath))

        val consume: (Any?) -> Unit = ::print
//        val consume: (Any?) -> Unit = { }

        var t = 0
        while (true) {
            val expectedNextType = expected.next()
            val nextType = reader.next()

            reader.typeAnnotations.forEach { consume(it); consume("::") }

            if (reader.isInStruct && nextType != null) {
                consume(reader.fieldName)
                consume(": ")
            }

            assertEquals(expectedNextType, nextType, "Unexpected value type at tlv=$t, depth=${reader.depth}")
            when (nextType) {
                IonType.NULL -> consume("null")
                IonType.BOOL -> consume(reader.booleanValue())
                IonType.INT -> consume(reader.longValue())
                IonType.FLOAT -> consume(reader.doubleValue())
                IonType.DECIMAL -> reader.decimalValue()
                IonType.TIMESTAMP -> {
                    consume(reader.timestampValue())
                    assertEquals(expected.timestampValue(), reader.timestampValue())
                }
                IonType.SYMBOL -> {
                    consume("'" + reader.stringValue() + "'")
//                    assertEquals(expected.stringValue(), reader.stringValue())
                }
                IonType.STRING -> {
                    consume("\"" + reader.stringValue() + "\"")
                    assertEquals(expected.stringValue(), reader.stringValue())
                }
                IonType.CLOB -> reader.newBytes()
                IonType.BLOB -> reader.newBytes()
                IonType.LIST -> {
                    consume("[")
                    expected.stepIn()
                    reader.stepIn()
                    continue
                }
                IonType.SEXP -> {
                    consume("(")
                    expected.stepIn()
                    reader.stepIn()
                    continue
                }
                IonType.STRUCT -> {
                    consume("{")
                    expected.stepIn()
                    reader.stepIn()
                    continue
                }
                IonType.DATAGRAM -> TODO("Unreachable")
                null -> {
                    if (reader.depth > 0) {
                        if (reader.isInStruct) {
                            consume("}")
                        } else {
                            consume("]")
                        }

                        expected.stepOut()
                        reader.stepOut()
                    } else {
                        break
                    }
                }
            }
            consume(", ")
            if (reader.depth == 0) {
                t++
                consume("\n")
            }
            if (reader.depth == 1) {
                consume("\n  ")
            }
        }
    }



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
//        "         17179869184, 00100000 00000000 00000000 00000000 00000000 00000001",
//        "       2199023255552, 01000000 00000000 00000000 00000000 00000000 00000000 00000001",
//        "     281474976710655, 11000000 11111111 11111111 11111111 11111111 11111111 01111111",
//        "     281474976710656, 10000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001",
//        "   36028797018963967, 10000000 11111111 11111111 11111111 11111111 11111111 11111111 01111111",
//        "   36028797018963968, 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 00000001",
//        "   72624976668147840, 00000000 00000001 10000001 01000000 00100000 00010000 00001000 00000100 00000010",
//        " 4611686018427387903, 00000000 11111111 11111111 11111111 11111111 11111111 11111111 11111111 01111111",
//        " 4611686018427387904, 00000000 00000010 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001",
//        "   ${Long.MAX_VALUE}, 00000000 11111110 11111111 11111111 11111111 11111111 11111111 11111111 11111111 00000001",
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
        "          -134217729, 11110000 11111111 11111111 11111111 11111110",
//        "        -17179869184, 00010000 00000000 00000000 00000000 10000000",
//        "        -17179869185, 11100000 11111111 11111111 11111111 11111111 11111110",
//        "    -281474976710656, 01000000 00000000 00000000 00000000 00000000 00000000 10000000",
//        "    -281474976710657, 10000000 11111111 11111111 11111111 11111111 11111111 11111111 11111110",
//        "  -36028797018963968, 10000000 00000000 00000000 00000000 00000000 00000000 00000000 10000000",
//        "  -36028797018963969, 00000000 11111111 11111111 11111111 11111111 11111111 11111111 11111111 11111110",
//        "  -72624976668147841, 00000000 11111111 01111110 10111111 11011111 11101111 11110111 11111011 11111101",
//        "-4611686018427387904, 00000000 00000001 00000000 00000000 00000000 00000000 00000000 00000000 10000000",
//        "-4611686018427387905, 00000000 11111110 11111111 11111111 11111111 11111111 11111111 11111111 11111111 11111110",
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
        cp: UnsafeArrayList<Any?> = UnsafeArrayList<Any?>(),
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
        cp: UnsafeArrayList<Any?> = UnsafeArrayList<Any?>(),
        symTab: Array<String?> = arrayOf<String?>(null),
        macTab: IntArray = IntArray(0),
        macOffsets: IntArray = intArrayOf(0),
        pos: Int = 0
    ) {
        val data = toByteArray(ionBinary)
        val dest = IntList()
        compileTopLevel(data, pos, dest, cp, macTab, macOffsets, symTab)
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
