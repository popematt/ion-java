package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigInteger
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(ExperimentalUnsignedTypes::class)
class TemplateReaderImplTest {

    fun instructionTestCases() = listOf(
        testCase(MacroBytecode.OP_NULL_NULL.opToInstruction()) {
            assertEquals(TokenTypeConst.NULL, nextToken())
            assertEquals(1, i)
            assertEquals(IonType.NULL, nullValue())
        },
        testCase(MacroBytecode.OP_NULL_TYPED.opToInstruction(2)) {
            assertEquals(TokenTypeConst.NULL, nextToken())
            assertEquals(1, i)
            assertEquals(IonType.INT, nullValue())
        },
        testCase(MacroBytecode.OP_BOOL.opToInstruction(1)) {
            assertEquals(TokenTypeConst.BOOL, nextToken())
            assertEquals(1, i)
            assertEquals(true, booleanValue())
        },
        testCase(MacroBytecode.OP_SMALL_INT.opToInstruction(123)) {
            assertEquals(TokenTypeConst.INT, nextToken())
            assertEquals(1, i)
            assertEquals(123, longValue())
        },
        testCase("negative number",
            instructions(MacroBytecode.OP_SMALL_INT.opToInstruction(-123))
        ) {
            assertEquals(TokenTypeConst.INT, nextToken())
            assertEquals(1, i)
            assertEquals(-123, longValue())
        },
        testCase(MacroBytecode.OP_INLINE_INT.opToInstruction(),
            123
        ) {
            assertEquals(TokenTypeConst.INT, nextToken())
            assertEquals(1, i)
            assertEquals(123, longValue())
        },
        testCase("negative number",
            instructions(
                MacroBytecode.OP_INLINE_INT.opToInstruction(),
                -123
            )
        ) {
            assertEquals(TokenTypeConst.INT, nextToken())
            assertEquals(1, i)
            assertEquals(-123, longValue())
        },
        testCase(
            MacroBytecode.OP_INLINE_LONG.opToInstruction(),
            0,
            123
        ) {
            assertEquals(TokenTypeConst.INT, nextToken())
            assertEquals(1, i)
            assertEquals(123, longValue())
        },
        testCase("negative number",
            instructions(
                MacroBytecode.OP_INLINE_LONG.opToInstruction(),
                -1,
                -123
            )
        ) {
            assertEquals(TokenTypeConst.INT, nextToken())
            assertEquals(1, i)
            assertEquals(-123, longValue())
        },
        testCase(
            constant = BigInteger.ONE,
            instructionsToTest = instructions(MacroBytecode.OP_CP_BIG_INT.opToInstruction(0))
        ) {
            assertEquals(TokenTypeConst.INT, nextToken())
            assertEquals(1, i)
            assertEquals(1, longValue())
        },
        // TODO: INT_REF
        testCase(
            MacroBytecode.OP_INLINE_FLOAT.opToInstruction(0),
            (123f).toRawBits()
        ) {
            assertEquals(TokenTypeConst.FLOAT, nextToken())
            assertEquals(1, i)
            assertEquals(123.0, doubleValue())
        },
        testCase(
            MacroBytecode.OP_INLINE_DOUBLE.opToInstruction(0),
            ((123.0).toRawBits() and 0xFFFFFFFF).toInt(),
            ((123.0).toRawBits() ushr 32).toInt(),
        ) {
            assertEquals(TokenTypeConst.FLOAT, nextToken())
            assertEquals(1, i)
            assertEquals(123.0, doubleValue())
        },
        // TODO: DEC_CNST
        // TODO: DEC_REF
        testCase(
            constant = Timestamp.forDay(1, 2, 3),
            instructionsToTest = instructions(MacroBytecode.OP_CP_TIMESTAMP.opToInstruction(0))
        ) {
            assertEquals(TokenTypeConst.TIMESTAMP, nextToken())
            assertEquals(1, i)
            assertEquals(Timestamp.forDay(1, 2, 3), timestampValue())
        },
        testCase(
            binaryIon = ubyteArrayOf(
                0x80u,
                30u,
            ),
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_TIMESTAMP_SHORT.opToInstruction(0x80),
                1,
            )
        ) {
            assertEquals(TokenTypeConst.TIMESTAMP, nextToken())
            assertEquals(1, i)
            assertEquals(Timestamp.forYear(2000), timestampValue())
        },
//        testCase(
//            binaryIon = ubyteArrayOf(
//                0xF8u,
//                0x05u,
//                100u,
//                0u,
//            ),
//            instructionsToTest = instructions(
//                MacroBytecode.OP_REF_TIMESTAMP_LONG.opToInstruction(0x2),
//                2,
//            )
//        ) {
//            assertEquals(TokenTypeConst.TIMESTAMP, nextToken())
//            assertEquals(1, i)
//            assertEquals(Timestamp.forYear(100), timestampValue())
//        },
        testCase(
            constant = "foo",
            instructionsToTest = instructions(MacroBytecode.OP_CP_STRING.opToInstruction(0))
        ) {
            assertEquals(TokenTypeConst.STRING, nextToken())
            assertEquals(1, i)
            assertEquals("foo", stringValue())
        },
        testCase(
            binaryIon = ubyteArrayOf(
                0x93u,
                'a'.code.toUByte(),
                'b'.code.toUByte(),
                'c'.code.toUByte(),
            ),
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_STRING.opToInstruction(3),
                1,
            )
        ) {
            assertEquals(TokenTypeConst.STRING, nextToken())
            assertEquals(1, i)
            assertEquals("abc", stringValue())
        },
        testCase(
            constant = "bar",
            instructionsToTest = instructions(MacroBytecode.OP_CP_SYMBOL_TEXT.opToInstruction(0))
        ) {
            assertEquals(TokenTypeConst.SYMBOL, nextToken())
            assertEquals(1, i)
            assertEquals(-1, symbolValueSid())
            assertEquals("bar", symbolValue())
        },
        testCase(
            binaryIon = ubyteArrayOf(
                0xA3u,
                'A'.code.toUByte(),
                'B'.code.toUByte(),
                'C'.code.toUByte(),
            ),
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_SYMBOL_TEXT.opToInstruction(3),
                1,
            )
        ) {
            assertEquals(TokenTypeConst.SYMBOL, nextToken())
            assertEquals(1, i)
            assertEquals(-1, symbolValueSid())
            assertEquals("ABC", symbolValue())
        },
        testCase(
            MacroBytecode.OP_SYMBOL_SYSTEM_SID.opToInstruction(1),
        ) {
            assertEquals(TokenTypeConst.SYMBOL, nextToken())
            assertEquals(1, i)
            assertEquals(-1, symbolValueSid())
            assertEquals("\$ion", symbolValue())
        },
        testCase(
            "using symbolValueSid(), unknown symbol text",
            instructionsToTest = instructions(
                MacroBytecode.OP_SYMBOL_SYSTEM_SID.opToInstruction(0),
            )
        ) {
            assertEquals(TokenTypeConst.SYMBOL, nextToken())
            assertEquals(1, i)
            assertEquals(0, symbolValueSid())
        },
        // TODO: SYM_SID
        testCase(
            "using symbolValueSid(), unknown text",
            instructionsToTest = instructions(
                MacroBytecode.OP_SYMBOL_SID.opToInstruction(0),
            )
        ) {
            assertEquals(TokenTypeConst.SYMBOL, nextToken())
            assertEquals(1, i)
            assertEquals(0, symbolValueSid())
        },
        testCase(
            "using symbolValue(), unknown text",
            instructionsToTest = instructions(
                MacroBytecode.OP_SYMBOL_SID.opToInstruction(0),
            )
        ) {
            assertEquals(TokenTypeConst.SYMBOL, nextToken())
            assertEquals(1, i)
            assertEquals(null, symbolValue())
        },

        // TODO: CLOB_CNST
        // TODO: CLOB_REF
        // TODO: BLOB_CNST
        // TODO: BLOB_REF
        testCase(
            note = "empty list",
            instructionsToTest = instructions(
                MacroBytecode.OP_LIST_START.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.LIST, nextToken())
            val l = listValue()
            l as TemplateReaderImpl
            assertEquals(1, l.i)
            assertEquals(TokenTypeConst.END, l.nextToken())
            assertEquals(2, l.i)
        },
        testCase(
            instructionsToTest = instructions(
                MacroBytecode.OP_LIST_START.opToInstruction(2),
                MacroBytecode.OP_BOOL.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.LIST, nextToken())
            val l = listValue()
            l as TemplateReaderImpl
            assertEquals(1, l.i)
            assertEquals(TokenTypeConst.BOOL, l.nextToken())
            assertEquals(true, l.booleanValue())
            assertEquals(2, l.i)
            assertEquals(TokenTypeConst.END, l.nextToken())
            assertEquals(3, l.i)
        },

        testCase(
            note = "empty list",
            binaryIon = ubyteArrayOf(),
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_LIST.opToInstruction(0),
                0,
            )
        ) {
            assertEquals(TokenTypeConst.LIST, nextToken())
            val l = listValue()
            assertEquals(TokenTypeConst.END, l.nextToken())
        },
        testCase(
            binaryIon = ubyteArrayOf(0xB1u, 0x6Eu),
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_LIST.opToInstruction(1),
                1,
            )
        ) {
            assertEquals(TokenTypeConst.LIST, nextToken())
            val l = listValue()
            assertEquals(TokenTypeConst.BOOL, l.nextToken())
            assertEquals(true, l.booleanValue())
            assertEquals(TokenTypeConst.END, l.nextToken())
        },


        testCase(
            note = "empty sexp",
            instructionsToTest = instructions(
                MacroBytecode.OP_SEXP_START.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.SEXP, nextToken())
            val l = sexpValue()
            l as TemplateReaderImpl
            assertEquals(1, l.i)
            assertEquals(TokenTypeConst.END, l.nextToken())
            assertEquals(2, l.i)
        },
        testCase(
            instructionsToTest = instructions(
                MacroBytecode.OP_SEXP_START.opToInstruction(2),
                MacroBytecode.OP_BOOL.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.SEXP, nextToken())
            val l = sexpValue()
            l as TemplateReaderImpl
            assertEquals(1, l.i)
            assertEquals(TokenTypeConst.BOOL, l.nextToken())
            assertEquals(true, l.booleanValue())
            assertEquals(2, l.i)
            assertEquals(TokenTypeConst.END, l.nextToken())
            assertEquals(3, l.i)
        },

        testCase(
            note = "empty sexp",
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_SEXP.opToInstruction(0),
                0,
            )
        ) {
            assertEquals(TokenTypeConst.SEXP, nextToken())
            val l = sexpValue()
            assertEquals(TokenTypeConst.END, l.nextToken())
        },
        testCase(
            binaryIon = ubyteArrayOf(0xB1u, 0x6Eu),
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_SEXP.opToInstruction(1),
                1,
            )
        ) {
            assertEquals(TokenTypeConst.SEXP, nextToken())
            val l = sexpValue()
            assertEquals(TokenTypeConst.BOOL, l.nextToken())
            assertEquals(true, l.booleanValue())
            assertEquals(TokenTypeConst.END, l.nextToken())
        },



        testCase(
            note = "empty struct",
            instructionsToTest = instructions(
                MacroBytecode.OP_STRUCT_START.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.STRUCT, nextToken())
            val l = structValue()
            l as TemplateReaderImpl
            assertEquals(1, l.i)
            assertEquals(TokenTypeConst.END, l.nextToken())
            assertEquals(2, l.i)
        },

        testCase(
            note = "using FN_CNST",
            constant = "foo",
            instructionsToTest = instructions(
                MacroBytecode.OP_STRUCT_START.opToInstruction(3),
                MacroBytecode.OP_CP_FIELD_NAME.opToInstruction(0),
                MacroBytecode.OP_BOOL.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.STRUCT, nextToken())
            structValue().use {
                it as TemplateStructReader
                assertEquals(1, it.i)
                assertEquals(TokenTypeConst.FIELD_NAME, it.nextToken())
                assertEquals(2, it.i)
                assertEquals(-1, it.fieldNameSid())
                assertEquals(2, it.i)
                assertEquals("foo", it.fieldName())
                assertEquals(2, it.i)
                assertEquals(TokenTypeConst.BOOL, it.nextToken())
                assertEquals(true, it.booleanValue())
                assertEquals(3, it.i)
                assertEquals(TokenTypeConst.END, it.nextToken())
                assertEquals(4, it.i)
            }
        },

        testCase(
            note = "using FN_SID",
            symbolTable = arrayOf(null, "foo"),
            instructionsToTest = instructions(
                MacroBytecode.OP_STRUCT_START.opToInstruction(3),
                MacroBytecode.OP_FIELD_NAME_SID.opToInstruction(1),
                MacroBytecode.OP_BOOL.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.STRUCT, nextToken())
            structValue().use {
                it as TemplateStructReader
                assertEquals(1, it.i)
                assertEquals(TokenTypeConst.FIELD_NAME, it.nextToken())
                assertEquals(2, it.i)
                assertEquals("foo", it.fieldName())
                assertEquals(2, it.i)
                assertEquals(TokenTypeConst.BOOL, it.nextToken())
                assertEquals(true, it.booleanValue())
                assertEquals(3, it.i)
                assertEquals(TokenTypeConst.END, it.nextToken())
                assertEquals(4, it.i)
            }
        },

        testCase(
            note = "using FN_SYS",
            instructionsToTest = instructions(
                MacroBytecode.OP_STRUCT_START.opToInstruction(3),
                MacroBytecode.OP_FIELD_NAME_SYSTEM_SID.opToInstruction(1),
                MacroBytecode.OP_BOOL.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.STRUCT, nextToken())
            structValue().use {
                it as TemplateStructReader
                assertEquals(1, it.i)
                assertEquals(TokenTypeConst.FIELD_NAME, it.nextToken())
                assertEquals(2, it.i)
                assertEquals(-1, it.fieldNameSid())
                assertEquals(2, it.i)
                assertEquals("\$ion", it.fieldName())
                assertEquals(2, it.i)
                assertEquals(TokenTypeConst.BOOL, it.nextToken())
                assertEquals(true, it.booleanValue())
                assertEquals(3, it.i)
                assertEquals(TokenTypeConst.END, it.nextToken())
                assertEquals(4, it.i)
            }
        },

        testCase(
            note = "using FN_REF",
            binaryIon = ubyteArrayOf(
                'f'.code.toUByte(),
                'o'.code.toUByte(),
                'o'.code.toUByte(),
            ),
            instructionsToTest = instructions(
                MacroBytecode.OP_STRUCT_START.opToInstruction(4),
                MacroBytecode.OP_REF_FIELD_NAME_TEXT.opToInstruction(3),
                0,
                MacroBytecode.OP_BOOL.opToInstruction(1),
                MacroBytecode.OP_CONTAINER_END.opToInstruction(),
            )
        ) {
            assertEquals(TokenTypeConst.STRUCT, nextToken())
            structValue().use {
                it as TemplateStructReader
                assertEquals(1, it.i)
                assertEquals(TokenTypeConst.FIELD_NAME, it.nextToken())
                assertEquals(2, it.i)
                assertEquals(-1, it.fieldNameSid())
                assertEquals(2, it.i)
                assertEquals("foo", it.fieldName())
                assertEquals(3, it.i)
                assertEquals(TokenTypeConst.BOOL, it.nextToken())
                assertEquals(true, it.booleanValue())
                assertEquals(4, it.i)
                assertEquals(TokenTypeConst.END, it.nextToken())
                assertEquals(5, it.i)
            }
        },

        testCase(
            note = "empty struct",
            binaryIon = ubyteArrayOf(0xE0u),
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_SID_STRUCT.opToInstruction(0),
                0,
            )
        ) {
            assertEquals(TokenTypeConst.STRUCT, nextToken())
            structValue().use {
                assertEquals(TokenTypeConst.END, it.nextToken())
            }
        },

        testCase(
            note = "using fieldNameSid()",
            binaryIon = ubyteArrayOf(0xD1u, 0x03u, 0x6Eu),
            instructionsToTest = instructions(
                MacroBytecode.OP_REF_SID_STRUCT.opToInstruction(2),
                1,
            )
        ) {
            assertEquals(TokenTypeConst.STRUCT, nextToken())
            structValue().use {
                assertEquals(TokenTypeConst.FIELD_NAME, it.nextToken())
                assertEquals(1, it.fieldNameSid())
                assertEquals(TokenTypeConst.BOOL, it.nextToken())
                assertEquals(true, it.booleanValue())
                assertEquals(TokenTypeConst.END, it.nextToken())
            }
        },

        testCase(
            instructionsToTest = instructions(
                MacroBytecode.OP_ANNOTATION_SYSTEM_SID.opToInstruction(1),
                MacroBytecode.OP_BOOL.opToInstruction(0),
            )
        ) {
            assertEquals(0, i)
            assertEquals(TokenTypeConst.ANNOTATIONS, nextToken())
            assertEquals(1, i)
            val a = annotations()
            assertEquals(1, i)
            assertEquals("\$ion", a.next())
            assertEquals(false, a.hasNext())
            assertEquals(1, i)
            assertEquals(TokenTypeConst.BOOL, nextToken())
            assertEquals(2, i)
            assertEquals(false, booleanValue())
        },


        // TODO: ANN_CNST
        // TODO: ANN_REF
        // TODO: ANN_SID
        // TODO: ANN_SYS
        // TODO: ARG_END
        // TODO: RETURN
        // TODO: MAC_CNST
        // TODO: MAC_SYS
        // TODO: CP_MACRO_INVOCATION
        // TODO: ARG_START
        // TODO: PARAM
        // TODO: MAC_START
    )



    fun instructions(vararg i: UInt): IntArray = i.toIntArray()
    fun instructions(vararg i: Int): IntArray = i

    fun testCase(note: String, constant: Any? = null, constants: Array<Any?> = arrayOf(constant), binaryIon: UByteArray = ubyteArrayOf(), symbolTable: Array<String?> = arrayOf(null), instructionsToTest: IntArray, expectations: TemplateReaderImpl.() -> Unit, ): Arguments {
        return arguments(MacroBytecode(instructionsToTest[0]) + ", " + note, constants, binaryIon.toByteArray(), symbolTable, instructionsToTest, expectations,)
    }

    fun testCase(constant: Any? = null, constants: Array<Any?> = arrayOf(constant), binaryIon: UByteArray = ubyteArrayOf(), symbolTable: Array<String?> = arrayOf(null), instructionsToTest: IntArray, expectations: TemplateReaderImpl.() -> Unit, ): Arguments {
        return arguments(MacroBytecode(instructionsToTest[0]), constants, binaryIon.toByteArray(), symbolTable, instructionsToTest, expectations)
    }

    fun testCase(note: String, instructionsToTest: IntArray, expectations: TemplateReaderImpl.() -> Unit, ): Arguments {
        return arguments(MacroBytecode(instructionsToTest[0]) + ", " + note, arrayOf<Any?>(), byteArrayOf(), arrayOf<String?>(null), instructionsToTest, expectations,)
    }

    fun testCase(vararg instructionsToTest: Int, expectations: TemplateReaderImpl.() -> Unit, ): Arguments {
        return arguments(MacroBytecode(instructionsToTest[0]), arrayOf<Any?>(), byteArrayOf(), arrayOf<String?>(null), instructionsToTest, expectations)
    }

    @MethodSource("instructionTestCases")
    @ParameterizedTest(name = "{0}")
    fun instructions(
        name: String,
        constants: Array<Any?>,
        binaryIon: ByteArray,
        symbolTable: Array<String?>,
        instructionsToTest: IntArray,
        expectations: TemplateReaderImpl.() -> Unit,
    ) {
        testInstructionIsReadCorrectly(
            instructionsToTest = instructionsToTest,
            constantPool = constants,
            binaryIon = binaryIon,
            symbolTable = symbolTable,
            expectations = expectations
        )
    }

    fun testInstructionIsReadCorrectly(
        instructionsToTest: IntArray,
        constantPool: Array<Any?> = arrayOf(),
        binaryIon: ByteArray = byteArrayOf(),
        symbolTable: Array<String?> = arrayOf(null),
        expectations: TemplateReaderImpl.() -> Unit,
    ) {

        val rp = ResourcePool(ByteBuffer.wrap(binaryIon))

        val args = ArgumentBytecode.NO_ARGS
        val macroTable = arrayOf<MacroV2>()

        var afterInstructionPosition = 0

        val bytecode: IntArray = with(mutableListOf<Int>()) {
            instructionsToTest.forEach { add(it) }
            afterInstructionPosition = size
            add(MacroBytecode.EOF.opToInstruction(0))
            toIntArray()
        }

        MacroBytecode.debugString(bytecode)

        // Test that the instruction is read correctly
        val r = rp.getSequence(args, bytecode, 0, constantPool, symbolTable, macroTable)

        assertEquals(0, r.i)
        r.expectations()
        assertEquals(afterInstructionPosition, r.i)
        assertEquals(TokenTypeConst.END, r.nextToken())
        assertEquals(afterInstructionPosition + 1, r.i)

        // Also test that the instruction can be skipped correctly.
        val r2 = rp.getSequence(args, bytecode, 0, constantPool, symbolTable, macroTable)

        assertEquals(0, r2.i)
        while (r2.nextToken() != TokenTypeConst.END) {
            r2.skip()
        }
        assertEquals(afterInstructionPosition + 1, r2.i)
    }

}
