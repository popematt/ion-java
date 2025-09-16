// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.IonEncodingVersion.*
import com.amazon.ion.TestUtils.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.SymbolInliningStrategy
import com.amazon.ion.system.*
import com.amazon.ion.util.*
import com.amazon.ion.v8.ExpressionBuilderDsl.Companion.templateBody
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

internal class IonManagedWriter_1_1_Test {

    companion object {
        // Some symbols that are annoying to use with Kotlin's string substitution.
        val ion_1_1 = "\$ion_1_1"

        // Some symbol tokens so that we don't have to keep declaring them
        private val fooMacro = constantMacro { string("foo") }
        private val barMacro = constantMacro { string("bar") }

        // Helper function that writes to a writer and returns the text Ion
        private fun write(
            topLevelValuesOnNewLines: Boolean = true,
            closeWriter: Boolean = true,
            pretty: Boolean = false,
            symbolInliningStrategy: SymbolInliningStrategy = SymbolInliningStrategy.ALWAYS_INLINE,
            block: IonManagedWriter_1_1.() -> Unit
        ): String {
            val appendable = StringBuilder()
            val writer = ION_1_1.textWriterBuilder()
                .withWriteTopLevelValuesOnNewLines(topLevelValuesOnNewLines)
                .withSymbolInliningStrategy(symbolInliningStrategy)
                .apply { if (pretty) withPrettyPrinting() }
                .withSimplifiedTemplates()
                .build(appendable) as IonManagedWriter_1_1
            writer.apply(block)
            if (closeWriter) writer.close()
            return appendable.toString().trim()
        }

        // Helper function that writes to a writer and returns the binary Ion
        private fun writeBinary(
            closeWriter: Boolean = true,
            symbolInliningStrategy: SymbolInliningStrategy = SymbolInliningStrategy.ALWAYS_INLINE,
            block: IonManagedWriter_1_1.() -> Unit
        ): ByteArray {
            val out = ByteArrayOutputStream()
            val writer = ION_1_1.binaryWriterBuilder()
                .withSymbolInliningStrategy(symbolInliningStrategy)
                .withSimplifiedTemplates()
                .build(out) as IonManagedWriter_1_1
            writer.apply(block)
            if (closeWriter) writer.close()
            return out.toByteArray()
        }

        /** Helper function to create a constant (zero arg) template macro */
        fun constantMacro(body: TemplateDsl.() -> Unit) = MacroV8.create(templateBody(body))
    }

    @Test
    fun `attempting to manually write a symbol table throws an exception`() {
        write(closeWriter = false) {
            addTypeAnnotation(SystemSymbols.ION_SYMBOL_TABLE)
            assertThrows<IonException> { stepIn(IonType.STRUCT) }
        }
    }

    @Test
    fun `attempting to step into a scalar type throws an exception`() {
        write {
            assertThrows<IllegalArgumentException> { stepIn(IonType.NULL) }
        }
    }

    @Test
    fun `write an IVM`() {
        assertEquals(
            """
            $ion_1_1
            $ion_1_1
            """.trimIndent(),
            write { writeIonVersionMarker() }
        )
    }

    @Test
    fun `write an IVM in a container should write a symbol`() {
        assertEquals(
            """
            $ion_1_1
            [$ion_1_1]
            """.trimIndent(),
            write {
                stepIn(IonType.LIST)
                writeIonVersionMarker()
                stepOut()
            }
        )
    }

    private fun newSystemReader(input: ByteArray): IonReader {
        val system = IonSystemBuilder.standard().build() as _Private_IonSystem
        return system.newSystemReader(input)
    }

    private fun `transform symbol IDS`(writeValuesFn: _Private_IonWriter.(IonReader) -> Unit) {
        // Craft the input data: {a: b::c}, encoded as {$10: $11::$12}
        val input = ByteArrayOutputStream()
        ION_1_0.binaryWriterBuilder().build(input).use {
            it.stepIn(IonType.STRUCT)
            it.setFieldName("a")
            it.addTypeAnnotation("b")
            it.writeSymbol("c")
            it.stepOut()
        }
        // Do a system-level transcode of the Ion 1.0 data to Ion 1.1, adding 32 to each local symbol ID.
        val output = ByteArrayOutputStream()
        newSystemReader(input.toByteArray()).use { reader ->
            (ION_1_1.binaryWriterBuilder().build(output) as _Private_IonWriter).use {
                it.writeValuesFn(reader)
            }
        }
        // Verify the transformed symbol IDs using another system read.
        newSystemReader(output.toByteArray()).use {
            while (it.next() == IonType.SYMBOL) {
                assertEquals("\$ion_1_1", it.stringValue())
            }
            assertEquals(IonType.STRUCT, it.next())
            it.stepIn()
            assertEquals(IonType.SYMBOL, it.next())
            assertEquals(42, it.fieldNameSymbol.sid)
            assertEquals(43, it.typeAnnotationSymbols[0].sid)
            assertEquals(44, it.symbolValue().sid)
            assertNull(it.next())
            it.stepOut()
        }
    }

    @Test
    fun `use writeValues to transform symbol IDS`() {
        `transform symbol IDS` { reader ->
            writeValues(reader) { sid -> sid + 32 }
        }
    }

    @Test
    fun `use writeValue to transform symbol IDS`() {
        `transform symbol IDS` { reader ->
            while (reader.next() != null) {
                writeValue(reader) { sid -> sid + 32 }
            }
        }
    }

    @Test
    fun `write an encoding directive with a non-empty macro table`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (null "foo"))
        """.trimIndent()

        val actual = write {
            getOrAssignMacroAddress(constantMacro { string("foo") })
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `write an e-expression by name`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (a "foo"))
            (:a)
        """.trimIndent()

        val actual = write {
            startMacro("a", constantMacro { string("foo") })
            endMacro()
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `write an e-expression by address`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (null "foo"))
            (:0)
        """.trimIndent()

        val actual = write {
            startMacro(constantMacro { string("foo") })
            endMacro()
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `write an e-expression with tagless parameter in text`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (null [(:? \${'$'}int8\)]))
            (:0 1)
        """.trimIndent()

        val macro =  MacroV8.build {
            list {
                variable(TaglessScalarType.INT_8)
            }
        }

        val actual = write {
            startMacro(macro)
            writeInt(1)
            endMacro()
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `write an e-expression with tagless parameter in binary`() {
        val expected = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3                 | set_macros
               F2              | delim sexp start
                  8F           | null
                  F1 EB 61 F0  | [(:<$ int8>?)]
               F0
            F0
            00 01
        """.trimIndent()))

        val macro =  MacroV8.build {
            list {
                variable(TaglessScalarType.INT_8)
            }
        }

        val actual = writeBinary {
            startMacro(macro)
            writeInt(1)
            endMacro()
        }

        println("Expected: ${expected.toPrettyHexString()}")
        println("Actual: ${actual.toPrettyHexString()}")

        assertArrayEquals(expected, actual)
    }

    @Test
    fun `write a tagless-element list with scalar type`() {
        val expectedText = """
            $ion_1_1
            [\${'$'}int8\ 1,2]
        """.trimIndent()

        val expectedBinary = hexStringToByteArray(
            cleanCommentedHexBytes("""
            E0 01 01 EA
            EC 61 05 01 02
        """.trimIndent()))

        val writeFn: IonManagedWriter_1_1.() -> Unit = {
            stepInTaglessElementList(TaglessScalarType.INT_8)
            writeInt(1)
            writeInt(2)
            stepOut()
        }
        val actualText = write(block = writeFn)
        assertEquals(expectedText, actualText)

        val actualBinary = writeBinary(block = writeFn)
        assertEquals(expectedBinary.toPrettyHexString(), actualBinary.toPrettyHexString())
    }

    @Test
    fun `write a tagless-element list with macro shape`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (null [(:? \${'$'}int8\)]))
            [\0\ (1),(2),(3)]
        """.trimIndent()

        val macro =  MacroV8.build {
            list {
                variable(TaglessScalarType.INT_8)
            }
        }

        val actual = write {
            stepInTaglessElementList(macro)
            startMacro(macro)
            writeInt(1)
            endMacro()
            startMacro(macro)
            writeInt(2)
            endMacro()
            startMacro(macro)
            writeInt(3)
            endMacro()
            stepOut()
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `write an encoding directive with a non-empty symbol table`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_symbols "foo")
            $10
        """.trimIndent()

        val actual = write(symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE) {
            writeSymbol("foo")
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `calling flush() causes the next encoding directive to append to a macro table`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (null "foo"))
            (:0)
            (:${'$'}add_macros (null "bar"))
            (:0)
            (:1)
        """.trimIndent()

        val actual = write {
            val fooMacro = constantMacro { string("foo") }
            startMacro(fooMacro)
            endMacro()
            flush()
            startMacro(fooMacro)
            endMacro()
            startMacro(constantMacro { string("bar") })
            endMacro()
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `calling flush() causes the next encoding directive to append to the symbol table`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_symbols "foo")
            $10
            (:${'$'}add_symbols "bar")
            $11
        """.trimIndent()

        val actual = write(symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE) {
            writeSymbol("foo")
            flush()
            writeSymbol("bar")
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `calling finish() causes the next encoding directive to NOT append to a macro table`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (null "foo"))
            (:0)
            $ion_1_1
            (:${'$'}set_macros (null "bar"))
            (:0)
        """.trimIndent()

        val actual = write {
            startMacro(constantMacro { string("foo") })
            endMacro()
            finish()
            startMacro(constantMacro { string("bar") })
            endMacro()
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `calling finish() causes the next encoding directive to NOT append to the symbol table`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_symbols "foo")
            $10
            $ion_1_1
            (:${'$'}set_symbols "bar")
            $10
        """.trimIndent()

        val actual = write(symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE) {
            writeSymbol("foo")
            finish()
            writeSymbol("bar")
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `adding to the macro table should preserve existing symbols`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_symbols "foo")
            $10
            (:${'$'}set_macros (null "foo"))
        """.trimIndent()

        val actual = write(symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE) {
            writeSymbol("foo")
            flush()
            getOrAssignMacroAddress(constantMacro { string("foo") })
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `adding to the symbol table should preserve existing macros`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (null "foo"))
            (:${'$'}set_symbols "foo")
            $10
            (:0)
        """.trimIndent()

        val actual = write(symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE) {
            val theMacro = constantMacro { string("foo") }
            getOrAssignMacroAddress(theMacro)
            flush()
            writeSymbol("foo")
            startMacro(theMacro)
            endMacro()
        }

        assertEquals(expected, actual)
    }

    /** Holds a static factory method with the test cases for [testWritingMacroDefinitions]. */
    object TestWritingMacroDefinitions {
        const val THE_METHOD = "com.amazon.ion.v8.IonManagedWriter_1_1_Test\$TestWritingMacroDefinitions#cases"

        @JvmStatic
        fun cases(): List<Arguments> {
            fun case(
                name: String,
                body: TemplateDsl.() -> Unit = { nullValue() },
                expectedBody: String = "null"
            ) = arguments(name, MacroV8.build(body), expectedBody)

            return listOf(
                case(
                    "null",
                    body = { nullValue() },
                    expectedBody = "null"
                ),
                // Annotations on `null` are representative for all types that don't have special annotation logic
                case(
                    "annotated null",
                    body = {
                        annotated("foo", ::nullValue, IonType.NULL)
                    },
                    expectedBody = "foo::null"
                ),
                case(
                    "null annotated with $0",
                    body = {
                        annotated(null, ::nullValue, IonType.NULL)
                    },
                    expectedBody = "$0::null"
                ),
                case(
                    "bool",
                    body = { bool(true) },
                    expectedBody = "true"
                ),
                case(
                    "int",
                    body = { int(1) },
                    expectedBody = "1"
                ),
                case(
                    "(big) int",
                    body = { int(BigInteger.ONE) },
                    expectedBody = "1"
                ),
                case(
                    "float",
                    body = { float(Double.POSITIVE_INFINITY) },
                    expectedBody = "+inf"
                ),
                case(
                    "decimal",
                    body = { decimal(Decimal.valueOf(1.1)) },
                    expectedBody = "1.1"
                ),
                case(
                    "timestamp",
                    body = { timestamp(Timestamp.valueOf("2024T")) },
                    expectedBody = "2024T"
                ),
                case(
                    "symbol",
                    body = { symbol("foo") },
                    expectedBody = "foo"
                ),
                case(
                    "unknown symbol",
                    body = { symbol(null) },
                    expectedBody = "$0"
                ),
                case(
                    "annotated symbol",
                    body = {
                        annotated("foo", ::symbol, "bar")
                    },
                    expectedBody = "foo::bar"
                ),
                case(
                    "symbol annotated with $0",
                    body = {
                        annotated(null, ::symbol, "bar")
                    },
                    expectedBody = "$0::bar"
                ),
                case(
                    "string",
                    body = { string("abc") },
                    expectedBody = "\"abc\""
                ),
                case(
                    "blob",
                    body = { blob(byteArrayOf()) },
                    expectedBody = "{{}}"
                ),
                case(
                    "clob",
                    body = { clob(byteArrayOf()) },
                    expectedBody = "{{\"\"}}"
                ),
                case(
                    "list",
                    body = { list { int(1) } },
                    expectedBody = "[1]"
                ),
                case(
                    "sexp",
                    body = { sexp { int(1) } },
                    expectedBody = "(1)"
                ),
                case(
                    "empty sexp",
                    body = { sexp { } },
                    expectedBody = "()"
                ),
                case(
                    "annotated sexp",
                    body = { annotated("foo", ::sexp) { int(1) } },
                    expectedBody = "foo::(1)"
                ),
                case(
                    "sexp with $0 annotation",
                    body = { annotated(null, ::sexp) { int(1) } },
                    expectedBody = "$0::(1)"
                ),
                case(
                    "struct",
                    body = { struct { fieldName("foo"); int(1) } },
                    expectedBody = "{foo:1}"
                ),
                case(
                    "struct with $0 field name",
                    body = { struct { fieldName(null); int(1) } },
                    expectedBody = "{$0:1}"
                ),
                case(
                    "variable",
                    body = {
                        list {
                            variable()
                        }
                    },
                    expectedBody = "[(:?)]"
                ),

                case(
                    "variable with default value",
                    body = {
                        list {
                            variable {
                                struct {
                                    fieldName("foo")
                                    int(1)
                                }
                            }
                        }
                    },
                    expectedBody = "[(:? {foo:1})]"
                ),
                case(
                    "multiple variables",
                    body = {
                        list {
                            variable()
                            variable()
                            variable()
                        }
                    },
                    expectedBody = "[(:?),(:?),(:?)]"
                ),
                case(
                    "nested expressions in body",
                    body = {
                        list {
                            sexp { int(1) }
                            struct {
                                fieldName("foo")
                                int(2)
                            }
                        }
                    },
                    expectedBody = "[(1),{foo:2}]"
                ),

            )
        }
    }

    @MethodSource(TestWritingMacroDefinitions.THE_METHOD)
    @ParameterizedTest(name = "a macro definition with {0}")
    fun testWritingMacroDefinitions(description: String, macro: MacroV8, expectedSignatureAndBody: String) {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (foo "foo") (null "bar") (null $expectedSignatureAndBody))
        """.trimIndent()

        val actual = write {
            getOrAssignMacroAddressAndName("foo", fooMacro)
            getOrAssignMacroAddress(barMacro)
            getOrAssignMacroAddress(macro)
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `when pretty printing, directive expressions should have the clause name on the first line`() {
        // ...and look reasonably pleasant.
        // However, this should be held loosely.
        val expected = """
            $ion_1_1
            (:${'$'}set_symbols "foo" "bar" "baz")
            (:${'$'}set_macros
              (null
                "foo"
              )
            )
            ${'$'}10
            ${'$'}11
            ${'$'}12
            (:0)
            (:${'$'}add_symbols "a" "b" "c")
            (:${'$'}add_macros
              (null
                "abc"
              )
            )
            ${'$'}13
            ${'$'}14
            ${'$'}15
            (:1)
        """.trimIndent()

        val fooMacro = constantMacro { string("foo") }

        val actual = write(symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE, pretty = true) {
            writeSymbol("foo")
            writeSymbol("bar")
            writeSymbol("baz")
            startMacro(fooMacro)
            endMacro()
            flush()
            writeSymbol("a")
            writeSymbol("b")
            writeSymbol("c")
            startMacro(constantMacro { string("abc") })
            endMacro()
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `writeObject() should write something with a macro representation`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (Point2D {x:(:?),y:(:?)}))
            (:Point2D 2 4)
        """.trimIndent()

        val actual = write {
            writeObject(Point2D(2, 4))
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `writeObject() should write something without a macro representation`() {
        val expected = """
            $ion_1_1
            Red
            Yellow
            Green
            Blue
        """.trimIndent()

        val actual = write {
            Colors.entries.forEach {
                    color ->
                writeObject(color)
            }
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `writeObject() should write something with nested macro representation`() {
        val expected = """
            $ion_1_1
            (:${'$'}set_macros (Polygon {vertices:(:?),fill:(:?)}) (Point2D {x:(:?),y:(:?)}))
            (:Polygon [(:Point2D 0 0),(:Point2D 0 1),(:Point2D 1 1),(:Point2D 1 0)] Blue)
        """.trimIndent()

        val data = Polygon(
            listOf(
                Point2D(0, 0),
                Point2D(0, 1),
                Point2D(1, 1),
                Point2D(1, 0),
            ),
            Colors.Blue,
        )

        val actual = write {
            writeObject(data)
        }

        assertEquals(expected, actual)
    }

    private data class Polygon(val vertices: List<Point2D>, val fill: Colors?) : WriteAsIon {
        init { require(vertices.size >= 3) { "A polygon must have at least 3 edges and 3 vertices" } }

        companion object {
            // Using the qualified class name would be verbose, but may be safer for general
            // use so that there is almost no risk of having a name conflict with another macro.
            private val MACRO_NAME = Polygon::class.simpleName!!.replace(".", "_")
            private val MACRO = MacroV8.build {
                    struct {
                        fieldName("vertices")
                        variable()
                        fieldName("fill")
                        variable()
                    }
                }
        }

        override fun writeTo(writer: IonWriter) {
            with(writer) {
                stepIn(IonType.STRUCT)
                setFieldName("vertices")
                stepIn(IonType.LIST)
                vertices.forEach { writeObject(it) }
                stepOut()
                if (fill != null) {
                    setFieldName("fill")
                    writeObject(fill)
                }
                stepOut()
            }
        }

        override fun writeToMacroAware(writer: MacroV8AwareIonWriter) {
            with(writer) {
                startMacro(MACRO_NAME, MACRO)
                stepIn(IonType.LIST)
                vertices.forEach { writer.writeObject(it) }
                stepOut()
                fill?.let { writeObject(it) } ?: absentArgument()
                endMacro()
            }
        }
    }

    private data class Point2D(val x: Long, val y: Long) : WriteAsIon {
        companion object {
            // This is a very long macro name, but by using the qualified class name,
            // there is almost no risk of having a name conflict with another macro.
            private val MACRO_NAME = Point2D::class.simpleName!!.replace(".", "_")
            private val MACRO = MacroV8.build {
                    struct {
                        fieldName("x")
                        variable()
                        fieldName("y")
                        variable()
                    }
                }
        }

        override fun writeToMacroAware(writer: MacroV8AwareIonWriter) {
            with(writer) {
                startMacro(MACRO_NAME, MACRO)
                writeInt(x)
                writeInt(y)
                endMacro()
            }
        }

        override fun writeTo(writer: IonWriter) {
            with(writer) {
                stepIn(IonType.STRUCT)
                setFieldName("x")
                writeInt(x)
                setFieldName("y")
                writeInt(y)
                stepOut()
            }
        }
    }

    private enum class Colors : WriteAsIon {
        Red,
        Yellow,
        Green,
        Blue,
        ;
        override fun writeTo(writer: IonWriter) {
            writer.writeSymbol(this.name)
        }
    }
}
