package com.amazon.ion.v2

import com.amazon.ion.Decimal
import com.amazon.ion.IonEncodingVersion
import com.amazon.ion.IonException
import com.amazon.ion.IonType
import com.amazon.ion.IonValue
import com.amazon.ion.TestUtils
import com.amazon.ion.TestUtils.cleanCommentedHexBytes
import com.amazon.ion.TestUtils.hexStringToByteArray
import com.amazon.ion.Timestamp
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ion.v2.impl_1_0.StreamReader_1_0
import com.amazon.ion.v2.impl_1_1.StreamReaderImpl
import com.amazon.ion.v2.impl_1_1.SystemStreamReaderImpl
import com.amazon.ion.v2.visitor.ApplicationTranscoderVisitor
import com.amazon.ion.v2.visitor.SplitterVisitor
import com.amazon.ion.v2.visitor.VisitingReaderCallback
import com.amazon.ion.v2.visitor.VisitingReaderDriver
import java.io.ByteArrayOutputStream
import java.lang.StringBuilder
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource


class TypedReadersTest {

    @OptIn(ExperimentalStdlibApi::class)
    fun toByteBuffer(value: String): ByteBuffer {
        val hexChunks = value.lines()
            .joinToString(" ") { it.takeWhile { c -> c != '|' }.trim() }
            .trim()
            .split(Regex("\\s+"))
        println(hexChunks)
        return ByteBuffer.wrap(ByteArray(hexChunks.size) { hexChunks[it].hexToUByte().toByte() })
    }

    @Test
    fun `test read null`() {
        val data = toByteBuffer("""
            E0 01 00 EA

            0F   | null.null
            1F   | null.bool
        """)

        StreamReader_1_0(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()
            assertEquals(TokenTypeConst.NULL, reader.nextToken())
            assertEquals(IonType.NULL, reader.nullValue())
            assertEquals(TokenTypeConst.NULL, reader.nextToken())
            assertEquals(IonType.BOOL, reader.nullValue())
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }

    @Test
    fun `test read null Ion 1 1`() {
        val data = toByteBuffer("""
            E0 01 01 EA

            EA      | null.null
            EB 00   | null.bool
        """)

        StreamReaderImpl(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()
            assertEquals(TokenTypeConst.NULL, reader.nextToken())
            assertEquals(IonType.NULL, reader.nullValue())
            assertEquals(TokenTypeConst.NULL, reader.nextToken())
            assertEquals(IonType.BOOL, reader.nullValue())
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }

    @Test
    fun `test read string`() {
        val data = toByteBuffer("""
            E0 01 00 EA

            83 66 6F 6F  | "foo"
            8E 8F
               66 6F 6F 6F 6F
               6F 6F 6F 6F 6F
               6F 6F 6F 6F 6F
        """)

        StreamReader_1_0(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()
            assertEquals(TokenTypeConst.STRING, reader.nextToken())
            assertEquals("foo", reader.stringValue())
            assertEquals(TokenTypeConst.STRING, reader.nextToken())
            assertEquals("foooooooooooooo", reader.stringValue())
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }


    @Test
    fun `test read string Ion 1 1`() {
        val data = toByteBuffer("""
            E0 01 01 EA

            93 66 6F 6F  | "foo"
            F9 25
               66 6F 6F 6F 6F 6F
               6F 6F 6F 6F 6F 6F
               6F 6F 6F 6F 6F 6F
        """)

        StreamReaderImpl(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()
            assertEquals(TokenTypeConst.STRING, reader.nextToken())
            assertEquals("foo", reader.stringValue())
            assertEquals(TokenTypeConst.STRING, reader.nextToken())
            assertEquals("fooooooooooooooooo", reader.stringValue())
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }

    @Test
    fun `test ListReader`() {
        val data = toByteBuffer("""
            E0 01 00 EA
            20                   | Int 0
            B4                   | List
               21 01             | Int 1
               21 02             | Int 2
            B4                   | List
               21 03             | Int 3
               21 04             | Int 4
        """)

        StreamReader_1_0(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()
            assertEquals(TokenTypeConst.INT, reader.nextToken())
            assertEquals(0, reader.longValue())
            assertEquals(TokenTypeConst.LIST, reader.nextToken())
            reader.listValue().use { list ->
                assertEquals(TokenTypeConst.INT, list.nextToken())
                assertEquals(1, list.longValue())
                assertEquals(TokenTypeConst.INT, list.nextToken())
                assertEquals(2, list.longValue())
                assertEquals(TokenTypeConst.END, list.nextToken())
            }
            assertEquals(TokenTypeConst.LIST, reader.nextToken())
            reader.listValue().use { list ->
                assertEquals(TokenTypeConst.INT, list.nextToken())
                assertEquals(3, list.longValue())
                assertEquals(TokenTypeConst.INT, list.nextToken())
                assertEquals(4, list.longValue())
                assertEquals(TokenTypeConst.END, list.nextToken())
            }
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }

    @Test
    fun `test SexpReader`() {
        val data = toByteBuffer("""
            E0 01 00 EA
            C4                   | Sexp
               21 01             | Int 1
               21 02             | Int 2
            C4                   | Sexp
               21 03             | Int 3
               21 04             | Int 4
        """)

        StreamReader_1_0(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()

            assertEquals(TokenTypeConst.SEXP, reader.nextToken())
            reader.sexpValue().use {
                assertEquals(TokenTypeConst.INT, it.nextToken())
                assertEquals(1, it.longValue())
                assertEquals(TokenTypeConst.INT, it.nextToken())
                assertEquals(2, it.longValue())
                assertEquals(TokenTypeConst.END, it.nextToken())
            }
            assertEquals(TokenTypeConst.SEXP, reader.nextToken())
            reader.sexpValue().use { sexp ->
                assertEquals(TokenTypeConst.INT, sexp.nextToken())
                assertEquals(3, sexp.longValue())
                assertEquals(TokenTypeConst.INT, sexp.nextToken())
                assertEquals(4, sexp.longValue())
                assertEquals(TokenTypeConst.END, sexp.nextToken())
            }
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }

    @Test
    fun `test StructReader`() {
        val data = toByteBuffer("""
            E0 01 00 EA
            D5                   | {
               84 20             |   name: 0,
               85 21 05          |   version: 5
                                 | }
        """)

        StreamReader_1_0(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()

            assertEquals(TokenTypeConst.STRUCT, reader.nextToken())
            reader.structValue().use { struct ->
                assertEquals(TokenTypeConst.FIELD_NAME, struct.nextToken())
                assertEquals("name", struct.fieldName())
                assertEquals(TokenTypeConst.INT, struct.nextToken())
                assertEquals(0, struct.longValue())
                assertEquals(TokenTypeConst.FIELD_NAME, struct.nextToken())
                assertEquals("version", struct.fieldName())
                assertEquals(TokenTypeConst.INT, struct.nextToken())
                assertEquals(5, struct.longValue())
                assertEquals(TokenTypeConst.END, struct.nextToken())
            }
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }

    @Test
    fun `test SymbolTable reader`() {
        val data = toByteBuffer("""
            E0 01 00 EA
            EE 8D                | Annotations, Length = 13
               81                | 1 byte of annotation sids
               83                | $ ion_symbol_table::
               DA                | {
                 87              |   symbols:
                 B8              |   [
                    83 66 6F 6F  |     "foo",
                    83 62 61 72  |     "bar",
                                 |   ]
                                 | }
            71 0A                | foo
            B2 71 0B             | [bar]
        """)

        StreamReader_1_0(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()

            assertEquals(TokenTypeConst.SYMBOL, reader.nextToken())
            assertEquals("foo", reader.symbolValue())
            assertEquals(TokenTypeConst.LIST, reader.nextToken())
            reader.listValue().use { list ->
                assertEquals(TokenTypeConst.SYMBOL, list.nextToken())
                assertEquals("bar", list.symbolValue())
                assertEquals(TokenTypeConst.END, list.nextToken())
            }
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }

    @Test
    fun `test reading annotations`() {
        val data = toByteBuffer("""
            E0 01 00 EA
            E3 81 84 20                   | name::0
            EE 94 81 84                   | name::"foooooooooooooo"
               8E 8F
               66 6F 6F 6F 6F
               6F 6F 6F 6F 6F
               6F 6F 6F 6F 6F
        """)

        StreamReader_1_0(data).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()
            assertEquals(TokenTypeConst.ANNOTATIONS, reader.nextToken())
            val ann = reader.annotations()
            ann.next()
            assertEquals(4, ann.getSid())
            assertEquals(false, ann.hasNext())
            assertEquals(TokenTypeConst.INT, reader.nextToken())
            assertEquals(0, reader.longValue())

            assertEquals(TokenTypeConst.ANNOTATIONS, reader.nextToken())
            val ann2 = reader.annotations()
            ann2.next()
            assertEquals(4, ann2.getSid())
            assertEquals(false, ann.hasNext())
            assertEquals(TokenTypeConst.STRING, reader.nextToken())
            assertEquals("foooooooooooooo", reader.stringValue())

            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }


    @Test
    fun `the big one`() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy.10n")

        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())

            val iter = IonValueIterator(StreamReader_1_0(mappedByteBuffer))

            var count = 0
            while (iter.hasNext() && count++ < 100) {
                println(iter.next())
            }
        }
    }


    @Test
    fun `a big one for Ion 1 1 with macros`() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_large.11.10n")

        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())

            val iter = IonValueIterator(StreamReaderImpl(mappedByteBuffer))

            var count = 0
            while (iter.hasNext() && count++ < 100) {
                println(iter.next())
            }
        }
    }

    class PrintingVisitor(
        private val whiteSpace: String = "",
        private val separator: String = "\n",
        private var isPendingWhitespace: Boolean = false,
        private val stepInWsTransform: (String) -> String = { "$it  " }
    ): VisitingReaderCallback {
        private var isPendingSeparator = false

        private fun printSeparator() {
            if (isPendingWhitespace) {
                print(whiteSpace)
                isPendingWhitespace = false
            }
            if (isPendingSeparator) {
                print(separator)
                isPendingSeparator = false
            }
        }
        private fun afterValue() {
            isPendingSeparator = true
            isPendingWhitespace = true
        }

        override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback {
            printSeparator()
            while (annotations.hasNext()) {
                annotations.next()
                val text = annotations.getText() ?: "$${annotations.getSid()}"
                print(text)
                print("::")
            }
            return this
        }

        override fun onField(fieldName: String?, fieldSid: Int): VisitingReaderCallback? {
            printSeparator()
            val text = fieldName ?: "$$fieldSid"
            print(text)
            print(":")
            return this
        }

        override fun onList(): VisitingReaderCallback? {
            printSeparator()
            print("[")
            return PrintingVisitor(stepInWsTransform(whiteSpace), ", ", isPendingWhitespace = true, stepInWsTransform = stepInWsTransform)
        }

        override fun onListEnd() {
            printSeparator()
            print("]")
            afterValue()
        }

        override fun onSexp(): VisitingReaderCallback? {
            printSeparator()
            print("(")
            return PrintingVisitor(stepInWsTransform(whiteSpace), " ", isPendingWhitespace = true, stepInWsTransform = stepInWsTransform)
        }

        override fun onSexpEnd() {
            printSeparator()
            print(")")
            afterValue()
        }

        override fun onStruct(): VisitingReaderCallback? {
            printSeparator()
            print("{")
            return PrintingVisitor(stepInWsTransform(whiteSpace), ", ", isPendingWhitespace = true, stepInWsTransform = stepInWsTransform)
        }

        override fun onStructEnd() {
            printSeparator()
            print("}")
            afterValue()
        }

        override fun onScalar(type: TokenType): VisitingReaderCallback? {
            printSeparator()
            return this
        }

        override fun onNull(value: IonType) {
            print("null")
            if (value != IonType.NULL) {
                print(".${value.toString().lowercase()}")
            }
            afterValue()
        }

        override fun onBoolean(value: Boolean) {
            print(value.toString())
            afterValue()
        }

        override fun onLongInt(value: Long) {
            print(value.toString())
            afterValue()
        }

        override fun onBigInt(value: BigInteger) {
            print(value.toString())
            afterValue()
        }

        override fun onDouble(value: Double) {
            print(value.toString())
            afterValue()
        }

        override fun onDecimal(value: Decimal) {
            print(value.toString())
            afterValue()
        }

        override fun onTimestamp(value: Timestamp) {
            print(value.toString())
            afterValue()
        }

        override fun onString(value: String) {
            print("\"$value\"")
            afterValue()
        }

        override fun onSymbol(value: String?, sid: Int) {
            print(value ?: "$$sid")
            afterValue()
        }

        override fun onClob(value: ByteBuffer) {
            print("{{ /* CLOB */ }}")
            afterValue()
        }

        override fun onBlob(value: ByteBuffer) {
            print("{{ /* BLOB */ }}")
            afterValue()
        }

    }

    @Test
    fun `a big one for Ion 1 1 no macros`() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy_no_macros.10n")

        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())

            val streamReader = StreamReaderImpl(mappedByteBuffer)

            val driver = VisitingReaderDriver()

            val visitor = PrintingVisitor(stepInWsTransform = { "" })
            repeat(100) {
                driver.read(streamReader, visitor)
            }
        }
    }


    @Test
    fun `set_symbols Ion 1 1 test`() {
        val bytes = hexStringToByteArray(cleanCommentedHexBytes("""
            E0 01 01 EA
            EF 13 02           | (:set_symbols ...
               11              | expression group
               93 66 6F 6F     | "foo"
               93 62 61 72     | "bar" )
            E1 01              | foo
            B2                 | [
            E1 02              | bar]
        """))

        StreamReaderImpl(ByteBuffer.wrap(bytes)).use { reader ->
            val driver = VisitingReaderDriver()

            val sb = StringBuilder()
            val textWriter = IonEncodingVersion.ION_1_1.textWriterBuilder()
                .withWriteTopLevelValuesOnNewLines(true)
                .build(sb)

//            val visitor = PrintingVisitor(stepInWsTransform = { "" })
            val visitor = ApplicationTranscoderVisitor(textWriter)
            driver.readAll(reader, visitor)
            textWriter.close()

            sb.lines().take(20).forEach { println(it) }
        }

    }


    @Test
    fun `a big one for Ion 1 1 no macros and conversion using IonJava`() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy.10n")
        val baos = ByteArrayOutputStream()

        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            val streamReader = StreamReader_1_0(mappedByteBuffer)
            val driver = VisitingReaderDriver()

            val binaryWriter = IonEncodingVersion.ION_1_1.binaryWriterBuilder().build(baos)
            driver.readAll(streamReader, ApplicationTranscoderVisitor(binaryWriter))
            binaryWriter.close()
            println(baos.size())
        }

        val bytes = baos.toByteArray()
        val buffer = ByteBuffer.wrap(bytes)
        StreamReaderImpl(buffer).use { reader ->
            val driver = VisitingReaderDriver()

            val sb = StringBuilder()
            val textWriter = IonEncodingVersion.ION_1_1.textWriterBuilder()
                .withWriteTopLevelValuesOnNewLines(true)
                .build(sb)

//            val visitor = PrintingVisitor(stepInWsTransform = { "" })
            val visitor = ApplicationTranscoderVisitor(textWriter)
            repeat(20) { driver.read(reader, visitor) }
            // driver.readAll(reader, visitor)
            textWriter.close()

            println(sb)

        }
    }



    /*

     */

    @CsvSource(
        //                                                 v
        "2024T,                               80 36",
        "2023-10T,                            81 35 05",
        "2023-10-15T,                         82 35 7D",// v
        "2023-10-15T05:04Z,                   83 35 7D 85 08",
        "2023-10-15T05:04:03Z,                84 35 7D 85 38 00",
        "2023-10-15T05:04:03.123-00:00,       85 35 7D 85 30 EC 01",
        "2023-10-15T05:04:03.000123-00:00,    86 35 7D 85 30 EC 01 00",
        "2023-10-15T05:04:03.000000123-00:00, 87 35 7D 85 30 EC 01 00 00",
        "2023-10-15T05:04+01:00,              88 35 7D 85 E0 01",
        "2023-10-15T05:04-01:00,              88 35 7D 85 A0 01",
        "2023-10-15T05:04:03+01:00,           89 35 7D 85 E0 0D",
        "2023-10-15T05:04:03.123+01:00,       8A 35 7D 85 E0 0D 7B 00",
        "2023-10-15T05:04:03.000123+01:00,    8B 35 7D 85 E0 0D 7B 00 00",
        "2023-10-15T05:04:03.000000123+01:00, 8C 35 7D 85 E0 0D 7B 00 00 00",
    )
    @ParameterizedTest
    fun readTimestamp_1_1(expected: String, bytesString: String) {
        println(bytesString)
        val buffer = ByteBuffer.wrap(hexStringToByteArray("E0 01 01 EA $bytesString"))
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val expected = Timestamp.valueOf(expected)

        StreamReaderImpl(buffer).use { reader ->
            assertEquals(TokenTypeConst.IVM, reader.nextToken())
            reader.ivm()
            assertEquals(TokenTypeConst.TIMESTAMP, reader.nextToken())
            assertEquals(expected, reader.timestampValue())
        }
    }


    @Test
    fun changeVersions() {
        val data = toByteBuffer("""
            E0 01 00 EA
            20                   | Int 0
            B4                   | List
               21 01             | Int 1
               21 02             | Int 2

            E0 01 01 EA
            B4
               61 03
               61 04             | Int 4
        """)

        StreamReaderAnyVersion(StreamReader_1_0(data)).use { reader ->
            assertEquals(TokenTypeConst.INT, reader.nextToken())
            assertEquals(0, reader.longValue())
            assertEquals(TokenTypeConst.LIST, reader.nextToken())
            reader.listValue().use { list ->
                assertEquals(TokenTypeConst.INT, list.nextToken())
                assertEquals(1, list.longValue())
                assertEquals(TokenTypeConst.INT, list.nextToken())
                assertEquals(2, list.longValue())
                assertEquals(TokenTypeConst.END, list.nextToken())
            }
            assertEquals(TokenTypeConst.LIST, reader.nextToken())
            reader.listValue().use { list ->
                assertEquals(TokenTypeConst.INT, list.nextToken())
                assertEquals(3, list.longValue())
                assertEquals(TokenTypeConst.INT, list.nextToken())
                assertEquals(4, list.longValue())
                assertEquals(TokenTypeConst.END, list.nextToken())
            }
            assertEquals(TokenTypeConst.END, reader.nextToken())
        }
    }

    @Test
    fun `a small one for Ion 1 1 no macros and conversion using IonJava`() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy.10n")
        val baos = ByteArrayOutputStream()

        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            val streamReader = StreamReader_1_0(mappedByteBuffer)
            val driver = VisitingReaderDriver()
            val binaryWriter = IonEncodingVersion.ION_1_1.binaryWriterBuilder().build(baos)
            val visitor = ApplicationTranscoderVisitor(binaryWriter)
            driver.readAll(streamReader, visitor)
            binaryWriter.close()
            println(baos.size())
        }

        val bytes = baos.toByteArray()

        val buffer = ByteBuffer.wrap(bytes)
        StreamReaderImpl(buffer).use { streamReader ->
            val driver = VisitingReaderDriver()

            val sb = StringBuilder()
            val textWriter = IonEncodingVersion.ION_1_1.textWriterBuilder()
                .withWriteTopLevelValuesOnNewLines(true)
                .build(sb)

            // val visitor = PrintingVisitor(stepInWsTransform = { "" })
            val visitor = ApplicationTranscoderVisitor(textWriter)
            driver.readAll(streamReader, visitor)
            textWriter.close()

            sb.lines().take(20).forEach { println(it) }
        }
    }

    class IonValueIterator(private val streamReader: StreamReader) : Iterator<IonValue> {

        private val ion = IonValueReader()

        private var _queued: IonValue? = null

        private val queued: IonValue?
            get() {
                if (_queued == null) {
                    _queued = ion.readValue(streamReader)
                }
                return _queued
            }

        override fun hasNext(): Boolean {
            return queued != null
        }

        override fun next(): IonValue {
            hasNext()
            val next = queued ?: throw NoSuchElementException()
            _queued = null
            return next
        }
    }


    private class IonValueReader {

        val ion = IonSystemBuilder.standard().build()

        fun readValue(reader: ValueReader): IonValue? {
            val token = reader.nextToken()
            return when (token) {
                TokenTypeConst.NULL -> ion.newNull(reader.nullValue())
                TokenTypeConst.BOOL -> ion.newBool(reader.booleanValue())
                TokenTypeConst.INT -> ion.newInt(reader.longValue())
                TokenTypeConst.FLOAT -> ion.newFloat(reader.doubleValue())
                TokenTypeConst.DECIMAL -> ion.newDecimal(reader.decimalValue())
                TokenTypeConst.TIMESTAMP -> ion.newTimestamp(reader.timestampValue())
                TokenTypeConst.STRING -> ion.newString(reader.stringValue())
                TokenTypeConst.SYMBOL -> ion.newSymbol(reader.symbolValue())
                TokenTypeConst.CLOB -> TODO("clobs")
                TokenTypeConst.BLOB -> TODO("blobs")
                TokenTypeConst.LIST -> reader.listValue().use { readList(it) }
                TokenTypeConst.SEXP -> reader.sexpValue().use { readSexp(it) }
                TokenTypeConst.STRUCT -> reader.structValue().use { readStruct(it) }
                TokenTypeConst.ANNOTATIONS -> {
                    val ann = reader.annotations()
                    val value = readValue(reader) ?: throw IonException("Annotations without a value.")
                    value.setTypeAnnotations(*ann.use { it.toStringArray() })
                    value
                }
                TokenTypeConst.END -> null
                TokenTypeConst.EEXP -> reader.eexpValue().use {
                    readValue(it)
                }
                TokenTypeConst.IVM -> readValue(reader)
                else -> TODO("Unreachable? ${TokenTypeConst(token)}")
            }
        }

        private fun readList(reader: ListReader): IonValue {
            val list = ion.newEmptyList()
            while (true) {
                val value = readValue(reader) ?: return list
                list.add(value)
            }
        }

        private fun readSexp(reader: SexpReader): IonValue {
            val sexp = ion.newEmptySexp()
            while (true) {
                val value = readValue(reader) ?: return sexp
                sexp.add(value)
            }
        }

        private fun readStruct(reader: StructReader): IonValue {
            val struct = ion.newEmptyStruct()
            while (true) {
                val token = reader.nextToken()
                if (token == TokenTypeConst.END) return struct
                val fieldName = reader.fieldName()
                val value = readValue(reader) ?: continue
                struct.add(fieldName, value)
            }
        }

    }
}
