package com.amazon.ion.v3

import com.amazon.ion.*
import com.amazon.ion.TestUtils.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.ExpressionBuilderDsl.Companion.templateBody
import com.amazon.ion.system.*
import com.amazon.ion.v3.TypedReadersTest.TestExpectationVisitor.*
import com.amazon.ion.v3.impl_1_0.StreamReader_1_0
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.ion_reader.*
import com.amazon.ion.v3.visitor.*
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.*
import kotlin.NoSuchElementException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.text.StringBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource


class TypedReadersTest {

    @Nested
    inner class Ion10Tests {
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
        fun `test ListReader with ApplicationDriver`() {
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
        fun `a big one for Ion 1 0 using IonReader API`() {
            val path = Paths.get("/Volumes/brazil-ws/ion-java-benchmark-cli/service_log_legacy.10n")

            val ION = IonSystemBuilder.standard().build()
            FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->

                val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                StreamReaderAsIonReader(mappedByteBuffer).use {
                    val iter = ION.iterate(it)
                    while (iter.hasNext()) {
                        iter.next()
                    }
                }
            }
        }

    }

    @Nested
    inner class Ion11Tests {

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

        @CsvSource(
            "2024T,                               80 36",
            "2023-10T,                            81 35 05",
            "2023-10-15T,                         82 35 7D",
            "2023-10-15T05:04Z,                   83 35 7D 85 08",
            "2023-10-15T05:04:03Z,                84 35 7D 85 38 00",
            "2018-08-06T23:59:59Z,                84 30 34 77 bf 03",
            "2018-08-06T23:59:59.000Z,            85 30 34 77 bf 03 00",
            "2023-10-15T05:04:03.123-00:00,       85 35 7D 85 30 EC 01",
            "2023-10-15T05:04:03.000123-00:00,    86 35 7D 85 30 EC 01 00",
            "2023-10-15T05:04:03.000000123-00:00, 87 35 7D 85 30 EC 01 00 00",
            "2023-10-15T05:04+01:00,              88 35 7D 85 E0 01",
            "2023-10-15T05:04-01:00,              88 35 7D 85 A0 01",
            "2023-10-15T05:04:03+01:00,           89 35 7D 85 E0 0D",
            "2023-10-15T05:04:03.123+01:00,       8A 35 7D 85 E0 0D 7B 00",
            "2023-10-15T05:04:03.000123+01:00,    8B 35 7D 85 E0 0D 7B 00 00",
            "2023-10-15T05:04:03.000000123+01:00, 8C 35 7D 85 E0 0D 7B 00 00 00",
            "1947-12-23T11:22:33.127+01:15, F8 13 9B 07 DF 65 AD 57 08 07 7F",
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
        fun skipDelimitedList() {
            val data = toByteBuffer("""
                    E0 01 01 EA
                    F1                   | List
                       61 01             | Int 1
                       61 02             | Int 2
                    F0
                    F1
                       61 03
                       61 04             | Int 4
                    F0
                    60
                """)
            StreamReaderImpl(data).use { reader ->
                assertEquals(TokenTypeConst.IVM, reader.nextToken())
                reader.ivm()
                assertEquals(TokenTypeConst.LIST, reader.nextToken())
                reader.skip()
                assertEquals(TokenTypeConst.LIST, reader.nextToken())
                reader.skip()
                assertEquals(TokenTypeConst.INT, reader.nextToken())
                assertEquals(0, reader.longValue())
            }
        }

        @Test
        fun skipDelimitedSexp() {
            val data = toByteBuffer("""
                    E0 01 01 EA
                    F2                   | Sexp
                       61 01             | Int 1
                       61 02             | Int 2
                    F0
                    F2
                       61 03
                       61 04             | Int 4
                    F0
                    60
                """)
            StreamReaderImpl(data).use { reader ->
                assertEquals(TokenTypeConst.IVM, reader.nextToken())
                reader.ivm()
                assertEquals(TokenTypeConst.SEXP, reader.nextToken())
                reader.skip()
                assertEquals(TokenTypeConst.SEXP, reader.nextToken())
                reader.skip()
                assertEquals(TokenTypeConst.INT, reader.nextToken())
                assertEquals(0, reader.longValue())
            }
        }

        @Test
        fun skipDelimitedStruct() {
            val bytes0 = """
                    E0 01 01 EA
                    60
                    F3
                       03 61 01            | $ ion : 1
                       01 F0

                    F3
                       03 61 02            | $ ion : 2
                       01 F0
                    60
                """

            val data = toByteBuffer(bytes0)


            StreamReaderImpl(data).use { reader ->
                assertEquals(TokenTypeConst.IVM, reader.nextToken())
                reader.ivm()
                assertEquals(TokenTypeConst.INT, reader.nextToken())
                assertEquals(0, reader.longValue())
                assertEquals(TokenTypeConst.STRUCT, reader.nextToken())
                reader.skip()
                assertEquals(TokenTypeConst.STRUCT, reader.nextToken())
                reader.skip()
                assertEquals(TokenTypeConst.INT, reader.nextToken())
                assertEquals(0, reader.longValue())
            }
        }

        // Ion 1.0
        val smallLog = "$" + """ion_log::"ServiceQueryLog_1_0"
            {
                StartTime:2018-08-06T23:59:59.897Z,
                Marketplace:"us-west-2",
                Program:"Canal_20130901",
                Time:19041583,
                Operation:"PutRecord",
                AccountId:"599497505561",
                AwsAccessKeyId:"AKIAIDCULUG4T7M7YSFA",
                AwsAccountId:"599497505561",
                AwsCallerPrincipal:"AIDAJDEOMPZALKP53LY7E",
                AwsUserArn:"arn:aws:iam::599497505561:user/kinesis-rw-user",
                AwsUserPrincipal:"AIDAJDEOMPZALKP53LY7E",
                ChainId:"Chain_163fa914bda_7",
                EndTime:Instant::2018-08-06T23:59:59.000Z,
                PID:"38207@kinesis-proxy-hailstoneperf-pdx-62002.pdx2.amazon.com",
                RemoteIpAddress:"10.242.81.215",
                RemoteIpAddressSequence:"10.242.81.215",
                RemoteSocket:"127.0.0.1:44700",
                RequestId:"bb1890d7-f941-4277-b183-791205713dc7",
                SN:"49585610451445194228944925219480153390279546138439714930",
                ShardId:"shardId-000000000135",
                Size:"104",
                StreamName:"240-shard-fe-stress",
                Throttler:"Gossip",
                UserAgent:"aws-sdk-java/2.0.0-preview-11-SNAPSHOT Linux/3.2.45-0.6.wd.514.39.260.metal1.x86_64 Java_HotSpot_TM__64-Bit_Server_VM/25.92-b14 Java/1.8.0_92",
                'amz-sdk-invocation-id':"af8e1574-03b1-6e24-597a-7281467821b3",
                'amz-sdk-retry':"0/0/500",
            }
        """

        val smallLog11Binary = """
                01 85 b2 2d 47 ba 23 0f 84 b2 2d 47 ba 03
                e1 04 e1 05 6c 24 67 47 4a e1 06 f3 0f e1 08
                13 e1 0a 17 e1 0c 1b e1 0e 1f e1 10 23 e1 12 27
                e1 14 2b e1 16 2f e1 18 33 e1 1a 37 e1 1c 01 f0 f1 09 00
                e1 1d 6c f8 1a 51 40 f0 f1 0b e1 1e 0c e1 1f
                f0 ef 00 ef 00 f1 07 00 e1 20 6a 07 00 e1 21 6a f0 ef 00
            """.trimIndent()

        private fun template(vararg parameters: String, body: TemplateDsl.() -> Unit): Macro {
            val signature = parameters.map {
                val cardinality = Macro.ParameterCardinality.fromSigil("${it.last()}")
                if (cardinality == null) {
                    Macro.Parameter(it, Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)
                } else {
                    Macro.Parameter(it.dropLast(1), Macro.ParameterEncoding.Tagged, cardinality)
                }
            }
            return TemplateMacro(signature, templateBody(body))
        }

        @Test
        fun readEExpArgs() {
            val data = """
                01
                60
                6E
            """
            val signature = template("foo", "bar?", "baz?"){}.signature

            val source = toByteBuffer(data)

            val reader = EExpArgumentReaderImpl(
                source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),
                ResourcePool(source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)),
                symbolTable = arrayOf(),
                macroTable = arrayOf(),
            )
            reader.initArgs(signature)
            reader.calculateEndPosition()

            reader.seekToBeforeArgument(1)
            reader.assertNextToken(TokenTypeConst.BOOL).skip()

            reader.seekToBeforeArgument(0)
            reader.assertNextToken(TokenTypeConst.INT).skip()

            reader.seekToBeforeArgument(1)
            reader.assertNextToken(TokenTypeConst.BOOL).skip()

            reader.seekToBeforeArgument(2)
            reader.assertNextToken(TokenTypeConst.ABSENT_ARGUMENT)

            reader.seekToBeforeArgument(1)
            reader.assertNextToken(TokenTypeConst.BOOL).skip()

            reader.seekToBeforeArgument(0)
            reader.assertNextToken(TokenTypeConst.INT).skip()
        }

        @Test
        fun readPrefixedExprGroup() {
            val data = """
                02  | Presence Bits -- one group arg
                05  | Group length = 2
                60
                6E
            """
            val signature = template("foo*"){}.signature

            val source = toByteBuffer(data)

            val reader = EExpArgumentReaderImpl(
                source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),
                ResourcePool(source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)),
                symbolTable = arrayOf(),
                macroTable = arrayOf(),
            )
            reader.initArgs(signature)
            reader.calculateEndPosition()

            reader.seekToBeforeArgument(0)
            reader.assertNextToken(TokenTypeConst.EXPRESSION_GROUP)
                .expressionGroup()
                .use { eg ->
                    eg.assertNextToken(TokenTypeConst.INT).skip()
                    eg.assertNextToken(TokenTypeConst.BOOL).skip()
                    eg.assertNextToken(TokenTypeConst.END)
                }

        }

        @Test
        fun readTemplateArgs() {
            val data = "00"
            val signature = template("foo", "bar?", "baz?"){}.signature

            val source = toByteBuffer(data)

            val reader = TemplateArgumentReaderImpl(
                TemplateResourcePool.getInstance(),
                TemplateResourcePool.TemplateInvocationInfo(
                    listOf(
                        Expression.LongIntValue(value = 0),
                        Expression.BoolValue(value = false),
                    ),
                    signature,
                    EExpArgumentReaderImpl(source, ResourcePool(source), emptyArray(), emptyArray())
                ),
                startInclusive = 0,
                endExclusive = 2,
                isArgumentOwner = false,
            )
            reader.initArgs(signature)

            reader.seekToBeforeArgument(1)
            reader.assertNextToken(TokenTypeConst.BOOL).skip()

            reader.seekToBeforeArgument(0)
            reader.assertNextToken(TokenTypeConst.INT).skip()

            reader.seekToBeforeArgument(1)
            reader.assertNextToken(TokenTypeConst.BOOL).skip()

            reader.seekToBeforeArgument(2)
            reader.assertNextToken(TokenTypeConst.ABSENT_ARGUMENT)

            reader.seekToBeforeArgument(1)
            reader.assertNextToken(TokenTypeConst.BOOL).skip()

            reader.seekToBeforeArgument(0)
            reader.assertNextToken(TokenTypeConst.INT).skip()
        }


        @Test
        fun readVariableThatReferencesEExpArgs() {
            val identity = template("x") { variable(0) }
            val foo = template("f") {
                variable(0)
            }

            val data = """
                00
                01 60
            """.trimIndent()

            val pool = TemplateResourcePool.getInstance()

            StreamReaderImpl(toByteBuffer(data)).use { stream ->
                stream.initTables(
                    symbolTable = arrayOf(null),
                    macroTable = arrayOf(identity, foo)
                )
                stream.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                stream.startMacroEvaluation(pool).use { m1 ->
                    m1.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                        .startMacroEvaluation(pool).use { m2 ->
                            m2.assertNextToken(TokenTypeConst.INT).skip()
                            m2.assertNextToken(TokenTypeConst.END)
                        }
                    m1.assertNextToken(TokenTypeConst.END)
                }
                stream.assertNextToken(TokenTypeConst.END)
            }
        }

        @Test
        fun readVariableThatReferencesAnotherVariable() {
            val identity = template("x") { variable(0) }
            val foo = template("f") {
                macro(identity) {
                    variable(0)
                }
            }

            val data = """
                00 60
            """

            val pool = TemplateResourcePool.getInstance()

            StreamReaderImpl(toByteBuffer(data)).use { stream ->
                stream.initTables(
                    symbolTable = arrayOf(null),
                    macroTable = arrayOf(foo)
                )
                stream.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                stream.startMacroEvaluation(pool).use { m1 ->
                    m1.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                        .startMacroEvaluation(pool).use { m2 ->
                            m2.assertNextToken(TokenTypeConst.INT).skip()
                            m2.assertNextToken(TokenTypeConst.END)
                        }
                    m1.assertNextToken(TokenTypeConst.END)
                }
                stream.assertNextToken(TokenTypeConst.END)
            }
        }

        @Test
        fun smallLog() {
            val placeholder = TemplateMacro(emptyList(), listOf(Expression.LongIntValue(value = 0)))

            // (macro one ( ) '' )
            val one = template() { symbol("") }

            // (macro entry
            //        (
            //        0 start_time
            //        1 end_time
            //        2 marketplace
            //        3 program
            //        4 time
            //        5 operation
            //        6 properties
            //        7 timing
            //        8 counters
            //        9 levels
            //        10 service_metrics
            //        11 metrics
            //        12 groups )
            //        {
            //          StartTime: ( '%' start_time ),
            //          EndTime: (%end_time),
            //          Marketplace: ( '%' marketplace ),
            //          Program: ( '%' program ),
            //          Time: ( '%' time ),
            //          Operation: ( '%' operation ),
            //          Properties: ( '%' properties ),
            //          ServiceMetrics: ( '%' service_metrics ),
            //          Timing: ( '%' timing ),
            //          Counters: ( '%' counters ),
            //          Levels: ( '%' levels ),
            //          Metrics: ( '%' metrics ),
            //          Groups: ( '%' groups ),
            //        }
            //      )
            val entry = template(*"start_time end_time marketplace program time operation properties timing counters levels service_metrics metrics groups".split(" ").toTypedArray()) {
                struct {
                    fieldName("StartTime"); variable(0)
                    fieldName("EndTime"); variable(1)
                    fieldName("Marketplace"); variable(2)
                    fieldName("Program"); variable(3)
                    fieldName("Time"); variable(4)
                    fieldName("Operation"); variable(5)
                    fieldName("Properties"); variable(6)
                    fieldName("ServiceMetrics"); variable(10)
                    fieldName("Timing"); variable(7)
                    fieldName("Counters"); variable(8)
                    fieldName("Levels"); variable(9)
                    fieldName("Metrics"); variable(11)
                    fieldName("Groups"); variable(12)
                }
            }

            // ( macro summary ( name sum unit '\?' count '\?' )
            //         { Name: ( '%' name ), Sum: ( '%' sum ), Unit: ( '.' $ion:: default ( '%' unit ) ( '.' 0 ) ), Count: ( '.' $ion:: default ( '%' count ) 1 ), } )
            val summary = template("name", "sum", "unit?", "count?") {
                struct {
                    fieldName("Name"); variable(0)
                    fieldName("Sum"); variable(1)
                    fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(one) {} }
                    fieldName("Count"); macro(SystemMacro.Default) { variable(3); int(1) }
                }
            }

            // ( macro sample ( value repeat ? )
            //         { Value: ( '%' value ), Repeat: ( '.' $ion:: default ( '%' repeat ) 1 ), } )
            val sample = template("value", "repeat?") {
                struct {
                    fieldName("Value"); variable(0)
                    fieldName("Repeat"); macro(SystemMacro.Default) { variable(1); int(1) }
                }
            }

            // ( macro metric
            //         ( name value unit '\?' dimensions '\?' )
            //         { Name: ( '%' name ), Samples: ( '%' value ), Unit: ( '.' $ion:: default ( '%' unit ) ( '.' 0 ) ), Dimensions: ( '%' dimensions ), } )
            val metric = template("name", "value", "unit?", "dimensions?") {
                struct {
                    fieldName("Name"); variable(0)
                    fieldName("Samples"); variable(1)
                    fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(one) {} }
                    fieldName("Dimensions"); variable(3)
                }
            }

            // ( macro metric_single ( name value repeat '?' unit '?' dimensions '?' )
            //         ( . 6 ( '%' name ) [ ( '.' 4 ( '%' value ) ( '%' repeat ) ), ] ( % unit ) ( '%' dimensions ) )
            // )
            val metricSingle = template("name", "value", "repeat?", "unit?", "dimension?") {
                macro(metric) {
                    variable(0)
                    list {
                        macro(sample) {
                            variable(1)
                            variable(2)
                        }
                    }
                    variable(3)
                    variable(4)
                }
            }
            // ( macro summary_ms ( name value count '\?' ) (. 2 (%name) (%value) ms (%count)) )
            val summaryMs = template("name", "value", "count?") {
                macro(summary) {
                    variable(0)
                    variable(1)
                    symbol("ms")
                    variable(2)
                }
            }
            // ( macro summary0 (name) (. 2 (%name) 0e0 ) )
            val summary0 = template("name") {
                macro(summary) {
                    variable(0)
                    float(0.0)
                }
            }
            // ( macro summary1 ( name ) (. 2 (%name) 1e0 ) )
            val summary1 = template("name") {
                macro(summary) {
                    variable(0)
                    float(1.0)
                }
            }

            val macroTable = arrayOf<Macro>(
                /* 00 */ one,
                /* 01 */ entry,
                /* 02 */ summary,
                /* 03 */ placeholder,
                /* 04 */ sample,
                /* 05 */ placeholder,
                /* 06 */ metric,
                /* 07 */ metricSingle,
                /* 08 */ placeholder,
                /* 09 */ summaryMs,
                /* 0a */ placeholder,
                /* 0b */ summary0,
                /* 0c */ summary1,
                /* 0d */ placeholder,
                /* 0e */ placeholder,
                *EncodingContextManager.ION_1_1_SYSTEM_MACROS,
            )
            val symbolTable = arrayOf<String?>(null,
                "ms", "B", "s", "us-east-1", "LambdaFrontendInvokeService",
                "ReserveSandbox2", "FrontendInstanceId", "i-0505be8aa9972815b", "AccountId", "103403959176",
                "RequestId", "f0bc3259-06e9-5ccb-96f1-6a44af76d4aa", "PID", "2812@ip-10-0-16-227", "WorkerId",
                "i-0c891b196c563ba4c", "FrontendInternalAZ", "USMA7", "WorkerManagerInstanceId", "i-070b7692b9a6aba7e",
                "SandboxId", "61fa4c30-d51d-40bb-82e0-a6195e27ca10", "Thread", "coral-orchestrator-136", "FrontendPublicAZ",
                "us-east-1a", "WorkerConnectPort", "2503", "Time:Warm", "Attempt",
                "Success", "Error", "Fault",
                )

            val templatePool = TemplateResourcePool.getInstance()

            val data = toByteBuffer(smallLog11Binary)

            StreamReaderImpl(data).use { reader ->
                reader.initTables(symbolTable, macroTable)
                reader.nextToken()
                val macro = reader.macroValue()
                assertEquals(entry, macro)
                val args = reader.macroArguments(macro.signature)
                templatePool.startEvaluation(macro, args).useWith {
                    assertNextToken(TokenTypeConst.STRUCT)
                    structValue().useWith {
                        assertFieldNameAndType("StartTime", TokenTypeConst.TIMESTAMP).skip()

                        assertFieldNameAndType("EndTime", TokenTypeConst.TIMESTAMP).skip()

                        assertFieldNameAndType("Marketplace", TokenTypeConst.SYMBOL).skip()

                        assertFieldNameAndType("Program", TokenTypeConst.SYMBOL).skip()

                        assertFieldNameAndType("Time", TokenTypeConst.FLOAT).skip()

                        assertFieldNameAndType("Operation", TokenTypeConst.SYMBOL).skip()

                        assertFieldNameAndType("Properties", TokenTypeConst.STRUCT)
                            .structValue()
                            .use { struct ->
                                struct.assertFieldNameAndType("FrontendInstanceId", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("AccountId", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("RequestId", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("PID", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("WorkerId", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("FrontendInternalAZ", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("WorkerManagerInstanceId", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("SandboxId", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("Thread", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("FrontendPublicAZ", TokenTypeConst.SYMBOL).skip()
                                struct.assertFieldNameAndType("WorkerConnectPort", TokenTypeConst.SYMBOL).skip()
                                struct.assertNextToken(TokenTypeConst.END)
                            }

                        assertFieldNameAndType("ServiceMetrics", TokenTypeConst.MACRO_INVOCATION)
                        val m1 = macroValue()
                        templatePool.startEvaluation(m1, macroArguments(m1.signature)).use { i1 ->
                            i1.assertNextToken(TokenTypeConst.END)
                        }

                        assertFieldNameAndType("Timing", TokenTypeConst.LIST)
                            .listValue()
                            .use { l ->
                                l.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                                l.startMacroEvaluation(templatePool).use { i3 ->
                                    i3.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                                    i3.startMacroEvaluation(templatePool).use { i4 ->
                                        i4.assertNextToken(TokenTypeConst.STRUCT)
                                            .structValue()
                                            .use { s ->
                                                s.assertFieldNameAndType("Name", TokenTypeConst.SYMBOL)
                                                    .skip()
                                                s.assertFieldNameAndType("Sum", TokenTypeConst.FLOAT)
                                                    .skip()
                                                s.assertFieldNameAndType("Unit", TokenTypeConst.MACRO_INVOCATION)
                                                    .startMacroEvaluation(templatePool)
                                                    .use { i5 ->
                                                        i5.assertNextToken(TokenTypeConst.SYMBOL).skip()
                                                        i5.assertNextToken(TokenTypeConst.END)
                                                    }

                                                s.assertFieldNameAndType("Count", TokenTypeConst.MACRO_INVOCATION)
                                                    .startMacroEvaluation(templatePool)
                                                    .use { i6 ->
                                                        i6.assertNextToken(TokenTypeConst.INT)
                                                            .skip()
                                                        i6.assertNextToken(TokenTypeConst.END)
                                                    }
                                                s.assertNextToken(TokenTypeConst.END)
                                            }
                                        i4.assertNextToken(TokenTypeConst.END)
                                    }
                                    i3.assertNextToken(TokenTypeConst.END)
                                }
                                l.assertNextToken(TokenTypeConst.END)
                            }

                        assertFieldNameAndType("Counters", TokenTypeConst.LIST).skip()

                        assertFieldNameAndType("Levels", TokenTypeConst.MACRO_INVOCATION)
                        val m2 = macroValue()
                        templatePool.startEvaluation(m2, macroArguments(m2.signature)).use { i2 ->
                            i2.assertNextToken(TokenTypeConst.END)
                        }


                        assertFieldNameAndType("Metrics", TokenTypeConst.LIST).skip()

                        assertFieldNameAndType("Groups", TokenTypeConst.MACRO_INVOCATION)
                        val m3 = macroValue()
                        templatePool.startEvaluation(m3, macroArguments(m3.signature)).use { i3 ->
                            i3.assertNextToken(TokenTypeConst.END)
                        }

                        assertNextToken(TokenTypeConst.END)

                    }
                }
            }
        }

        /*
        │         2212 │            9 │ 09 00                   │ · · (:summary_ms
        │         2214 │            2 │ e1 1d                   │ · · · 'Time:Warm' // <$29> name
        │         2216 │            5 │ 6c f8 1a 51 40          │ · · · 3.267271041870117e0 // value
        │              │              │                         │ · · · (::) // count
        │              │              │                         │ · · ),
        */
        @Test
        fun summaryMs() {
            // (macro one ( ) '' )
            val one = template { symbol("") }

            val summary = template("s_name", "s_sum", "s_unit?", "s_count?") {
                struct {
                    fieldName("Name"); variable(0)
                    fieldName("Sum"); variable(1)
                    fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(one) {} }
                    fieldName("Count"); macro(SystemMacro.Default) { variable(3); int(1) }
                }
            }
            // ( macro summary_ms ( name value count? ) (.summary (%name) (%value) ms (%count)) )
            val summaryMs = template("name", "value", "count?") {
                macro(summary) {
                    variable(0)
                    variable(1)
                    symbol("ms")
                    variable(2)
                }
            }

            val data = """
                02 00                   | · · (:summary_ms
                e1 01                   | · · · 'Time:Warm' // <${'$'}29> name
                6c f8 1a 51 40          | · · · 3.267271041870117e0 // value
                                        | · · · (::) // count
                                        | · · ),
            """.trimIndent()

            val pool = TemplateResourcePool.getInstance()

            StreamReaderImpl(toByteBuffer(data)).use {
                (it as ValueReaderBase).initTables(
                    symbolTable = arrayOf(null, "Time:Warm"),
                    macroTable = arrayOf(one, summary, summaryMs),
                )

                it.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                it.startMacroEvaluation(pool).use { m ->
                    m.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                    m.startMacroEvaluation(pool).use { i4 ->
                        i4.assertNextToken(TokenTypeConst.STRUCT)
                            .structValue()
                            .use { s ->
                                s.assertFieldNameAndType("Name", TokenTypeConst.SYMBOL)
                                    .skip()
                                s.assertFieldNameAndType("Sum", TokenTypeConst.FLOAT)
                                    .skip()
                                s.assertFieldNameAndType("Unit", TokenTypeConst.MACRO_INVOCATION)
                                    .startMacroEvaluation(pool)
                                    .use { i5 ->
                                        i5.assertNextToken(TokenTypeConst.SYMBOL).skip()
                                        i5.assertNextToken(TokenTypeConst.END)
                                    }

                                s.assertFieldNameAndType("Count", TokenTypeConst.MACRO_INVOCATION)
                                    .startMacroEvaluation(pool)
                                    .use { i6 ->
                                        i6.assertNextToken(TokenTypeConst.INT).skip()
                                        i6.assertNextToken(TokenTypeConst.END)
                                    }
                                s.assertNextToken(TokenTypeConst.END)
                            }
                        i4.assertNextToken(TokenTypeConst.END)
                    }
                    m.assertNextToken(TokenTypeConst.END)
                }
                it.assertNextToken(TokenTypeConst.END)
            }



        }

        /*
        │         2235 │            5 │ 07 00                   │ · · (:metric_single
        │         2237 │            2 │ e1 20                   │ · · · Error // <$32> name
        │         2239 │            1 │ 6a                      │ · · · 0e0 // value
        │              │              │                         │ · · · (::) // repeat
        │              │              │                         │ · · · (::) // unit
        │              │              │                         │ · · · (::) // dimensions
        │              │              │                         │ · · ),
        │              │              │                         │ · · {
        │              │              │                         │ · · · Name: Error,
        │              │              │                         │ · · · Samples:
        │              │              │                         │ · · · [
        │              │              │                         │ · · · · {
        │              │              │                         │ · · · · · Value:
        │              │              │                (%value) │ · · · · · 0e0,
        │              │              │                         │ · · · · · Repeat:
        │              │              │                         │ · · · · · 1,
        │              │              │                         │ · · · · },
        │              │              │                         │ · · · ],
        │              │              │                         │ · · · Unit: '',
        │              │              │                         │ · · · Dimensions:
        │              │              │                         │ · · },
         */
        @Test
        fun metricSingle() {
            // (macro one ( ) '' )
            val one = template { symbol("") }

            //( macro sample ( value repeat '\?' ) { Value: ( '%' value ), Repeat: ( '.' $ion:: default ( '%' repeat ) 1 ), } )
            val sample = template("value", "repeat?") {
                struct {
                    fieldName("Value"); variable(0)
                    fieldName("Repeat"); macro(SystemMacro.Default) {
                        variable(1)
                        int(1)
                    }
                }
            }

            //( macro metric ( name value unit '\?' dimensions '\?' ) { Name: ( '%' name ), Samples: ( '%' value ), Unit: ( '.' $ion:: default ( '%' unit ) (.one) ), Dimensions: ( '%' dimensions ), } )
            val metric = template("name", "value", "unit?", "dimensions?") {
                struct {
                    fieldName("Name"); variable(0)
                    fieldName("Samples"); variable(1)
                    fieldName("Unit"); macro(SystemMacro.Default) {
                        variable(3)
                        macro(one) {}
                    }
                    fieldName("Dimensions"); variable(3)
                }
            }

            //  (macro metric_single (name value repeat? unit? dimensions?) (.metric (%name) [ (.sample (%value) (%repeat)),] (%unit) (%dimensions)))
            val metricSingle = template("name", "value", "repeat?", "unit?", "dimensions?") {
                macro(metric) {
                    variable(0)
                    list {
                        macro(sample) {
                            variable(1)
                            variable(2)
                        }
                    }
                    variable(3)
                    variable(4)
                }
            }

            val data = """
                03 00                   | · · (:metric_single
                e1 01                   | · · · Error // <$ 32> name
                6a                      | · · · 0e0 // value
                                        | · · · (::) // repeat
                                        | · · · (::) // unit
                                        | · · · (::) // dimensions
                                        | · · ),
            """.trimIndent()

            // { Name: Error, Samples: [ { Value: 0e0, Repeat: 1, }, ], Unit: '' },

            val pool = TemplateResourcePool.getInstance()

            StreamReaderImpl(toByteBuffer(data)).use {
                (it as ValueReaderBase).initTables(
                    symbolTable = arrayOf(null, "Error"),
                    macroTable = arrayOf(one, sample, metric, metricSingle),
                )

                it.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                it.startMacroEvaluation(pool).use { m ->
                    m.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                    m.startMacroEvaluation(pool).use { i4 ->
                        i4.assertNextToken(TokenTypeConst.STRUCT)
                            .structValue()
                            .use { s ->
                                s.assertFieldNameAndType("Name", TokenTypeConst.SYMBOL)
                                    .skip()
                                s.assertFieldNameAndType("Samples", TokenTypeConst.LIST)
                                    .listValue()
                                    .use { l ->
                                        l.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                                            .startMacroEvaluation(pool)
                                            .use { m2 ->
                                                m2.assertNextToken(TokenTypeConst.STRUCT)
                                                    .structValue()
                                                    .use { s2 ->
                                                        s2.assertFieldNameAndType("Value", TokenTypeConst.FLOAT).skip()
                                                        s2.assertFieldNameAndType("Repeat", TokenTypeConst.MACRO_INVOCATION)
                                                            .startMacroEvaluation(pool)
                                                            .use { m3 ->
                                                                m3.assertNextToken(TokenTypeConst.INT).skip()
                                                                m3.assertNextToken(TokenTypeConst.END)
                                                            }
                                                        s2.assertNextToken(TokenTypeConst.END)
                                                    }
                                                m2.assertNextToken(TokenTypeConst.END)
                                            }
                                        l.assertNextToken(TokenTypeConst.END)
                                    }
                                s.assertFieldNameAndType("Unit", TokenTypeConst.MACRO_INVOCATION)
                                    .startMacroEvaluation(pool)
                                    .use { i5 ->
                                        println("======== Looking for 'Unit' value. ========")
                                        i5.assertNextToken(TokenTypeConst.MACRO_INVOCATION)
                                            .startMacroEvaluation(pool)
                                            .use { m4 ->
                                                m4.assertNextToken(TokenTypeConst.SYMBOL).skip()
                                                m4.assertNextToken(TokenTypeConst.END)
                                            }
                                        i5.assertNextToken(TokenTypeConst.END)
                                    }

                                s.assertFieldName("Dimensions")
                                s.assertNextToken(TokenTypeConst.ABSENT_ARGUMENT)
                                s.assertNextToken(TokenTypeConst.END)
                            }
                        i4.assertNextToken(TokenTypeConst.END)
                    }
                    m.assertNextToken(TokenTypeConst.END)
                }
                it.assertNextToken(TokenTypeConst.END)
            }



        }


        private fun ValueReader.startMacroEvaluation(pool: TemplateResourcePool): ValueReader {
            val macro = macroValue()
            val args = macroArguments(macro.signature)
            return pool.startEvaluation(macro, args)
        }

        private fun StructReader.assertFieldNameAndType(name: String, token: Int) = apply {
            assertFieldName(name)
            assertNextToken(token)
        }

        private fun ValueReader.assertNextToken(token: Int): ValueReader = apply {
            assertEquals(TokenTypeConst(token), TokenTypeConst(this.nextToken()))
        }

        private fun StructReader.assertFieldName(name: String): StructReader = apply {
            assertNextToken(TokenTypeConst.FIELD_NAME)
            val sid = this.fieldNameSid()
            val text = if (sid < 0) {
                this.fieldName()
            } else {
                lookupSid(sid)
            }
            assertEquals(name, text)
        }

        @OptIn(ExperimentalContracts::class)
        inline fun <T:AutoCloseable?, R> T.useWith(block: T.() -> R): R {
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }
            var exception: Throwable? = null
            try {
                return this.block()
            } catch (e: Throwable) {
                exception = e
                throw e
            } finally {
                when {
                    this == null -> {}
                    exception == null -> close()
                    else ->
                        try {
                            close()
                        } catch (closeException: Throwable) {
                            exception?.addSuppressed(closeException)
                        }
                }
            }
        }

        @Nested
        inner class VisitingReaderTests {
            @Test
            fun prefixedList() {
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            B4                   | List
               61 01             | Int 1
               61 02             | Int 2
            B4
               61 03
               61 04             | Int 4
        """)

                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(
                        TestExpectationVisitor(mutableListOf(
                            Expectation.IntValue(0),
                            Expectation.ListStart,
                            Expectation.IntValue(1),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.ListStart,
                            Expectation.IntValue(3),
                            Expectation.IntValue(4),
                            Expectation.End,
                            Expectation.End,
                        ))
                    )
                }
            }

            @Test
            fun delimitedList() {
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            F1                   | List
               61 01             | Int 1
               61 02             | Int 2
            F0
            F1
               61 03
               61 04             | Int 4
            F0
        """)

                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(
                        TestExpectationVisitor(mutableListOf(
                            Expectation.IntValue(0),
                            Expectation.ListStart,
                            Expectation.IntValue(1),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.ListStart,
                            Expectation.IntValue(3),
                            Expectation.IntValue(4),
                            Expectation.End,
                            Expectation.End,
                        ))
                    )
                }
            }

            @Test
            fun prefixedSexp() {
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            C4                   | Sexp
               61 01             | Int 1
               61 02             | Int 2
            C4
               61 03
               61 04             | Int 4
        """)

                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(
                        TestExpectationVisitor(mutableListOf(
                            Expectation.IntValue(0),
                            Expectation.SexpStart,
                            Expectation.IntValue(1),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.SexpStart,
                            Expectation.IntValue(3),
                            Expectation.IntValue(4),
                            Expectation.End,
                            Expectation.End,
                        ))
                    )
                }
            }

            @Test
            fun delimitedSexp() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    F2                   | Sexp
                       61 01             | Int 1
                       61 02             | Int 2
                    F0
                    F2
                       61 03
                       61 04             | Int 4
                    F0
                """)

                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(
                        TestExpectationVisitor(mutableListOf(
                            Expectation.IntValue(0),
                            Expectation.SexpStart,
                            Expectation.IntValue(1),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.SexpStart,
                            Expectation.IntValue(3),
                            Expectation.IntValue(4),
                            Expectation.End,
                            Expectation.End,
                        ))
                    )
                }
            }

            @Test
            fun prefixedStruct() {
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            D6                   | Struct
               03
               61 01             | Int 1
               05
               61 02             | Int 2
            D6
               07
               61 03
               09
               61 04             | Int 4
        """)

                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(
                        TestExpectationVisitor(mutableListOf(
                            Expectation.IntValue(0),
                            Expectation.StructStart,
                            Expectation.FieldName("\$ion", 1),
                            Expectation.IntValue(1),
                            Expectation.FieldName(sid = 2),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.StructStart,
                            Expectation.FieldName(sid = 3),
                            Expectation.IntValue(3),
                            Expectation.FieldName(sid = 4),
                            Expectation.IntValue(4),
                            Expectation.End,
                            Expectation.End,
                        ))
                    )
                }
            }

            @Test
            fun delimitedStruct() {
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            F3                   | Struct
               03                | FlexSym SID 1
               61 01             | Int 1
               05                | FlexSym SID 2
               61 02             | Int 2
               01 F0
            F3
               07                | FlexSym SID 3
               61 03             | Int 3
               09                | FlexSym SID 4
               61 04             | Int 4
               01 F0
        """)

                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(
                        TestExpectationVisitor(mutableListOf(
                            Expectation.IntValue(0),
                            Expectation.StructStart,
                            Expectation.FieldName("\$ion", 1),
                            Expectation.IntValue(1),
                            Expectation.FieldName(sid = 2),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.StructStart,
                            Expectation.FieldName(sid = 3),
                            Expectation.IntValue(3),
                            Expectation.FieldName(sid = 4),
                            Expectation.IntValue(4),
                            Expectation.End,
                            Expectation.End,
                        ))
                    )
                }

                data.rewind()

                StreamReaderImpl(data).use {
                    it.nextToken()
                    it.ivm()
                    it.nextToken()
                    it.longValue()
                    it.nextToken()
                    it.skip()
                    it.nextToken()
                    it.skip()
                }
            }

            @Test
            fun oneSidAnnotation() {
                val data = toByteBuffer("""
            E0 01 01 EA
            E4 03        | $ ion ::
            60           | 0
        """)
                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(expect("\$ion::0"))
                }
            }

            @Test
            fun twoSidAnnotations() {
                val data = toByteBuffer("""
            E0 01 01 EA
            E5 03 05     | $ ion :: $ ion_1_1
            60           | 0
        """)
                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(expect("\$ion::\$ion_1_1::0"))
                }
            }

            @Test
            fun `add_symbols Ion 1 1 test`() {
                val bytes = hexStringToByteArray(cleanCommentedHexBytes("""
            E0 01 01 EA
            EF                 | System E-Expression
               14              | (:add_symbols
               02              | Presence Bits: argument is an expression group
               01              | expression group length = DELIMITED
               93 66 6F 6F     | "foo"
               93 62 61 72     | "bar"
               F0              |      )
            | TODO: Change these when we fix the ordering for "append"
            E1 3F              | foo
            B2                 | [
            E1 40              | bar]
            E1 01              | $ ion
        """))

                ApplicationReaderDriver(ByteBuffer.wrap(bytes)).use {
                    it.readAll(
                        expect(
                            Expectation.Symbol(text = "foo"),
                            Expectation.ListStart,
                            Expectation.Symbol(text = "bar"),
                            Expectation.End,
                            Expectation.Symbol(text = "\$ion"),
                        )
                    )
                }
            }

            @Test
            fun `set_symbols Ion 1 1 test`() {
                val bytes = hexStringToByteArray(cleanCommentedHexBytes("""
                E0 01 01 EA
                EF                 | System E-Expression
                   13              | (:set_symbols
                   02              | Presence Bits: argument is an expression group
                   01              | expression group length = DELIMITED
                   93 66 6F 6F     | "foo"
                   93 62 61 72     | "bar"
                   F0              |      )
                E1 01              | foo
                B2                 | [
                E1 02              | bar]
            """))

                ApplicationReaderDriver(ByteBuffer.wrap(bytes)).use {
                    it.readAll(
                        expect(
                            Expectation.Symbol(text = "foo"),
                            Expectation.ListStart,
                            Expectation.Symbol(text = "bar"),
                            Expectation.End
                        )
                    )
                }
            }

            @Test
            fun constantScalarTemplateMacro() {
                val macro = TemplateMacro(
                    signature = listOf(),
                    body = ExpressionBuilderDsl.templateBody {
                        int(1)
                    }
                )
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            18
            61 02
        """)

                ApplicationReaderDriver(data, listOf(macro)).use { driver ->
                    driver.readAll(
                        expect(
                            Expectation.IntValue(0),
                            Expectation.IntValue(1),
                            Expectation.IntValue(2),
                        )
                    )
                }
            }

            @Test
            fun constantListTemplateMacro() {
                val macro = TemplateMacro(
                    signature = listOf(),
                    body = ExpressionBuilderDsl.templateBody {
                        list {
                            int(1)
                            int(2)
                        }
                    }
                )
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            18
            61 03
        """)

                ApplicationReaderDriver(data.asReadOnlyBuffer(), listOf(macro)).use { driver ->
                    driver.readAll(
                        expect(
                            Expectation.IntValue(0),
                            Expectation.ListStart,
                            Expectation.IntValue(1),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.IntValue(3),
                        )
                    )
                }
            }

            @Test
            fun constantSexpTemplateMacro() {
                val macro = TemplateMacro(
                    signature = listOf(),
                    body = ExpressionBuilderDsl.templateBody {
                        sexp {
                            int(1)
                            int(2)
                        }
                    }
                )
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            18
            61 03
        """)

                ApplicationReaderDriver(data, listOf(macro)).use { driver ->
                    driver.readAll(
                        expect(
                            Expectation.IntValue(0),
                            Expectation.SexpStart,
                            Expectation.IntValue(1),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.IntValue(3),
                        )
                    )
                }
            }

            @Test
            fun constantStructTemplateMacro() {
                val macro = TemplateMacro(
                    signature = listOf(),
                    body = ExpressionBuilderDsl.templateBody {
                        struct {
                            fieldName("foo")
                            int(1)
                            fieldName("bar")
                            int(2)
                        }
                    }
                )
                val data = toByteBuffer("""
            E0 01 01 EA
            60
            18
            61 03
        """)

                ApplicationReaderDriver(data, listOf(macro)).use { driver ->
                    driver.readAll(
                        expect(
                            Expectation.IntValue(0),
                            Expectation.StructStart,
                            Expectation.FieldName("foo"),
                            Expectation.IntValue(1),
                            Expectation.FieldName("bar"),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.IntValue(3),
                        )
                    )
                }
            }


            @Test
            fun templateMacroWithOneVariable() {
                val macro = template("foo") {
                    variable(0)
                }
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    18 61 01
                    61 02
                """)

                ApplicationReaderDriver(data, listOf(macro)).use { driver ->
                    driver.readAll(
                        expect(
                            Expectation.IntValue(0),
                            Expectation.IntValue(1),
                            Expectation.IntValue(2),
                        )
                    )
                }
            }

            @Test
            fun templateMacroWithMultipleVariables() {
                val macro = TemplateMacro(
                    signature = listOf(
                        Macro.Parameter("foo", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
                        Macro.Parameter("bar", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
                    ),
                    body = ExpressionBuilderDsl.templateBody {
                        struct {
                            fieldName("foo")
                            variable(0)
                            fieldName("bar")
                            variable(1)
                        }
                    }
                )
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    18 61 01 61 02
                    61 03
                """)

                ApplicationReaderDriver(data, listOf(macro)).use { driver ->
                    driver.readAll(
                        expect(
                            Expectation.IntValue(0),
                            Expectation.StructStart,
                            Expectation.FieldName("foo"),
                            Expectation.IntValue(1),
                            Expectation.FieldName("bar"),
                            Expectation.IntValue(2),
                            Expectation.End,
                            Expectation.IntValue(3),
                        )
                    )
                }
            }

            @Test
            fun templateMacroWithMultipleVariablesOutOfOrder() {
                val macro = template("foo", "bar") {
                    struct {
                        fieldName("foo")
                        variable(1)
                        fieldName("bar")
                        variable(0)
                    }
                }

                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    18 61 01 61 02
                    61 03
                """)

                ApplicationReaderDriver(data, listOf(macro)).use { driver ->
                    driver.readAll(
                        expect(
                            Expectation.IntValue(0),
                            Expectation.StructStart,
                            Expectation.FieldName("foo"),
                            Expectation.IntValue(2),
                            Expectation.FieldName("bar"),
                            Expectation.IntValue(1),
                            Expectation.End,
                            Expectation.IntValue(3),
                        )
                    )
                }
            }


            @Test
            fun smallLog() {
                val placeholder = TemplateMacro(emptyList(), listOf(Expression.LongIntValue(value = 0)))

                // (macro one ( ) '' )
                val one = template() { symbol("") }

                val entry = template(*"start_time end_time marketplace program time operation properties timing counters levels service_metrics metrics groups".split(" ").toTypedArray()) {
                    struct {
                        fieldName("StartTime"); variable(0)
                        fieldName("EndTime"); variable(1)
                        fieldName("Marketplace"); variable(2)
                        fieldName("Program"); variable(3)
                        fieldName("Time"); variable(4)
                        fieldName("Operation"); variable(5)
                        fieldName("Properties"); variable(6)
                        fieldName("ServiceMetrics"); variable(10)
                        fieldName("Timing"); variable(7)
                        fieldName("Counters"); variable(8)
                        fieldName("Levels"); variable(9)
                        fieldName("Metrics"); variable(11)
                        fieldName("Groups"); variable(12)
                    }
                }

                // ( macro summary ( name sum unit '\?' count '\?' )
                //         { Name: ( '%' name ), Sum: ( '%' sum ), Unit: ( '.' $ion:: default ( '%' unit ) ( '.' 0 ) ), Count: ( '.' $ion:: default ( '%' count ) 1 ), } )
                val summary = template("name", "sum", "unit?", "count?") {
                    struct {
                        fieldName("Name"); variable(0)
                        fieldName("Sum"); variable(1)
                        fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(one) {} }
                        fieldName("Count"); macro(SystemMacro.Default) { variable(3); int(1) }
                    }
                }

                // ( macro sample ( value repeat ? )
                //         { Value: ( '%' value ), Repeat: ( '.' $ion:: default ( '%' repeat ) 1 ), } )
                val sample = template("value", "repeat?") {
                    struct {
                        fieldName("Value"); variable(0)
                        fieldName("Repeat"); macro(SystemMacro.Default) { variable(1); int(1) }
                    }
                }

                // ( macro metric
                //         ( name value unit '\?' dimensions '\?' )
                //         { Name: ( '%' name ), Samples: ( '%' value ), Unit: ( '.' $ion:: default ( '%' unit ) ( '.' 0 ) ), Dimensions: ( '%' dimensions ), } )
                val metric = template("name", "value", "unit?", "dimensions?") {
                    struct {
                        fieldName("Name"); variable(0)
                        fieldName("Samples"); variable(1)
                        fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(one) {} }
                        fieldName("Dimensions"); variable(3)
                    }
                }

                // ( macro metric_single ( name value repeat '?' unit '?' dimensions '?' )
                //         ( . 6 ( '%' name ) [ ( '.' 4 ( '%' value ) ( '%' repeat ) ), ] ( % unit ) ( '%' dimensions ) )
                // )
                val metricSingle = template("name", "value", "repeat?", "unit?", "dimension?") {
                    macro(metric) {
                        variable(0)
                        list {
                            macro(sample) {
                                variable(1)
                                variable(2)
                            }
                        }
                        variable(3)
                        variable(4)
                    }
                }
                // ( macro summary_ms ( name value count '\?' ) (. 2 (%name) (%value) ms (%count)) )
                val summaryMs = template("name", "value", "count?") {
                    macro(summary) {
                        variable(0)
                        variable(1)
                        symbol("ms")
                        variable(2)
                    }
                }
                // ( macro summary0 (name) (. 2 (%name) 0e0 ) )
                val summary0 = template("name") {
                    macro(summary) {
                        variable(0)
                        float(0.0)
                    }
                }
                // ( macro summary1 ( name ) (. 2 (%name) 1e0 ) )
                val summary1 = template("name") {
                    macro(summary) {
                        variable(0)
                        float(1.0)
                    }
                }

                val macroTable = arrayOf<Macro>(
                    /* 00 */ one,
                    /* 01 */ entry,
                    /* 02 */ summary,
                    /* 03 */ placeholder,
                    /* 04 */ sample,
                    /* 05 */ placeholder,
                    /* 06 */ metric,
                    /* 07 */ metricSingle,
                    /* 08 */ placeholder,
                    /* 09 */ summaryMs,
                    /* 0a */ placeholder,
                    /* 0b */ summary0,
                    /* 0c */ summary1,
                    /* 0d */ placeholder,
                    /* 0e */ placeholder,
                    *EncodingContextManager.ION_1_1_SYSTEM_MACROS,
                )
                val symbolTable = arrayOf<String?>(null,
                    "ms", "B", "s", "us-east-1", "LambdaFrontendInvokeService",
                    "ReserveSandbox2", "FrontendInstanceId", "i-0505be8aa9972815b", "AccountId", "103403959176",
                    "RequestId", "f0bc3259-06e9-5ccb-96f1-6a44af76d4aa", "PID", "2812@ip-10-0-16-227", "WorkerId",
                    "i-0c891b196c563ba4c", "FrontendInternalAZ", "USMA7", "WorkerManagerInstanceId", "i-070b7692b9a6aba7e",
                    "SandboxId", "61fa4c30-d51d-40bb-82e0-a6195e27ca10", "Thread", "coral-orchestrator-136", "FrontendPublicAZ",
                    "us-east-1a", "WorkerConnectPort", "2503", "Time:Warm", "Attempt",
                    "Success", "Error", "Fault",
                )


                ApplicationReaderDriver(toByteBuffer("E0 01 01 EA 60 " + smallLog11Binary)).use {

                    it.read(NoOpVisitor())

                    it.ion11Reader.initTables(symbolTable, macroTable)

                    it.readAll(PrinterVisitorTop)
                }
            }

            @Test
            fun strings() {
                val data = toByteBuffer("""
            E0 01 01 EA
            93 66 6F 6F        | "foo"
            F9 1F
               66 6F 6F 6F 6F  | "foooofoooofoooo"
               66 6F 6F 6F 6F
               66 6F 6F 6F 6F
        """)

                ApplicationReaderDriver(data).use { driver ->
                    driver.readAll(
                        TestExpectationVisitor(mutableListOf(
                            Expectation.StringValue("foo"),
                            Expectation.StringValue("foooofoooofoooo")
                        ))
                    )
                }
            }

            @Test
            fun `small log file no macros`() {
                val reader = IonReaderBuilder.standard().build(smallLog)
                val baos = ByteArrayOutputStream()
                val binaryWriter = IonEncodingVersion.ION_1_1.binaryWriterBuilder().build(baos)
                transfer(reader, binaryWriter)
                reader.close()
                binaryWriter.close()

                val bytes = baos.toByteArray()

                ApplicationReaderDriver(ByteBuffer.wrap(bytes)).use { driver ->
                    driver.readAll(expect(smallLog))
                }
            }

            @Test
            fun `a big one for Ion 1 1 no macros and conversion using IonJava`() {
                val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy.10n")
                val baos = ByteArrayOutputStream()

                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    ApplicationReaderDriver(mappedByteBuffer).use {
                        val binaryWriter = IonEncodingVersion.ION_1_1.binaryWriterBuilder().build(baos)
                        repeat(10) { _ -> it.read(TranscoderVisitor(binaryWriter)) }
                        binaryWriter.close()
                    }
                }
                val expected = expect(IonReaderBuilder.standard().build(path.toFile().inputStream()), 10000)

                ApplicationReaderDriver(baos).use { driver -> driver.readAll(expected) }
            }


            @Test
            fun `a big one for Ion 1 1 conversion using IonJavaBenchmarkCli`() {
                val path = Paths.get("/Volumes/brazil-ws/ion-java-benchmark-cli/service_log_legacy_1_1.10n")

                val expected = expect(IonReaderBuilder.standard().build(path.toFile().inputStream()), 10000)
                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())

                    ApplicationReaderDriver(mappedByteBuffer).use { driver -> driver.readAll(NoOpVisitor()) }
                }
            }


            val smallLog11 = """
            {
  StartTime: 2020-11-05T07:18:59.968+00:00,
  EndTime: 2020-11-05T07:18:59+00:00,
  Marketplace: 'us-east-1',
  Program: LambdaFrontendInvokeService,
  Time: 3.267017e6,
  Operation: ReserveSandbox2,
  Properties: {
    FrontendInstanceId: 'i-0505be8aa9972815b',
    AccountId: '103403959176',
    RequestId: 'f0bc3259-06e9-5ccb-96f1-6a44af76d4aa',
    PID: '2812@ip-10-0-16-227',
    WorkerId: 'i-0c891b196c563ba4c',
    FrontendInternalAZ: USMA7,
    WorkerManagerInstanceId: 'i-070b7692b9a6aba7e',
    SandboxId: '61fa4c30-d51d-40bb-82e0-a6195e27ca10',
    Thread: 'coral-orchestrator-136',
    FrontendPublicAZ: 'us-east-1a',
    WorkerConnectPort: '2503',
  },
  Timing: [
    {
      Name: 'Time:Warm',
      Sum: 3.267271041870117e0,
      Unit: ms,
      Count: 1,
    },
  ],
  Counters: [
    {
      Name: Attempt,
      Sum: 0e0,
      Unit: '',
      Count: 1,
    },
    {
      Name: Success,
      Sum: 1e0,
      Unit: '',
      Count: 1,
    },
  ],
  Metrics: [
    {
      Name: Error,
      Samples: [
        {
          Value: 0e0,
          Repeat: 1,
        },
      ],
      Unit: '',
    },
    {
      Name: Fault,
      Samples: [
        {
          Value: 0e0,
          Repeat: 1,
        },
      ],
      Unit: '',
    },
  ],
}
{
  StartTime: 2020-11-05T07:18:59.971+00:00,
  EndTime: 2020-11-05T07:18:59+00:00,
  Marketplace: 'us-east-1',
  Program: LambdaFrontendInvokeService,
  Time: 3e3,
  Operation: 'WSKF:GetLatestKeys',
  Properties: {
    FrontendInstanceId: 'i-0505be8aa9972815b',
    FrontendPublicAZ: 'us-east-1a',
    PID: '2812@ip-10-0-16-227',
    FrontendInternalAZ: USMA7,
  },
  Timing: [
    {
      Name: Latency,
      Sum: 2.0000000949949026e-3,
      Unit: ms,
      Count: 1,
    },
  ],
}
            """

            @Test
            fun `a small log for Ion 1 1 with macros`() {
                val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_small.11.10n")


                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    ApplicationReaderDriver(mappedByteBuffer).use {
                        it.readAll(PrinterVisitorTop)
                    }
                }

                val ion = IonSystemBuilder.standard().build()
                val actual = ion.newDatagram()
                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    ApplicationReaderDriver(mappedByteBuffer).use {
                        it.readAll(IonDatagramHydrator(actual))
                    }
                }

                val expected = ion.loader.load(path.toFile())

                val ei = expected.iterator()
                val ai = actual.iterator()
                var i = 0
                var allMatch = true
                println()
                while (ei.hasNext() && ai.hasNext()) {
                    val e = ei.next()
                    val a = ai.next()
                    if (e.toPrettyString() != a.toPrettyString()) {
                        allMatch = false
                        println("Value $i does not match")
                    } else {
                        println("Value $i does match")
                    }
//                    assertEquals(e.toPrettyString(), a.toPrettyString(), "Value $i does not match")
                    i++
                }
                assertTrue(allMatch)
                assertEquals(expected.size, actual.size)
            }

            @Test
            fun `medium log for Ion 1 1 with macros`() {
                val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11.10n")

                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    ApplicationReaderDriver(mappedByteBuffer).use {
                        it.readAll(PrinterVisitorTop)
                    }
                }

                val ion = IonSystemBuilder.standard().build()
                val actual = ion.newDatagram()
                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    ApplicationReaderDriver(mappedByteBuffer).use {
                        it.readAll(IonDatagramHydrator(actual))
                    }
                }

                val expected = ion.loader.load(path.toFile())

                val ei = expected.iterator()
                val ai = actual.iterator()
                var i = 0
                while (ei.hasNext() && ai.hasNext()) {
                    val e = ei.next()
                    val a = ai.next()
                    assertEquals(e.toPrettyString(), a.toPrettyString(), "Value $i does not match")
                    i++
                }
                assertEquals(expected.size, actual.size)
            }
        }

        @Nested
        inner class IonReaderTests {

            val ION = IonSystemBuilder.standard().build()

            @Test
            fun `test read null`() {
                val data = toByteBuffer("""
                    E0 01 01 EA

                    EA      | null.null
                """)

                StreamReaderAsIonReader(data).use { reader ->
                    val iter = ION.iterate(reader)
                    assertTrue(iter.hasNext())
                    val value0 = iter.next()
                    println(value0)
                    assertEquals(ION.newNull(), value0)
                    assertFalse(iter.hasNext())
                }
            }

            @Test
            fun `test read typed null`() {
                val data = toByteBuffer("""
                    E0 01 01 EA

                    EB 00   | null.bool
                """)

                StreamReaderAsIonReader(data).use { reader ->
                    val iter = ION.iterate(reader)
                    assertTrue(iter.hasNext())
                    val value1 = iter.next()
                    println(value1)
                    assertEquals(ION.newNull(IonType.BOOL), value1)
                    assertFalse(iter.hasNext())
                }
            }


            @Test
            fun `test read integers`() {
                val data = toByteBuffer("""
                    E0 01 01 EA

                    60      | 0
                    61 01   | 1
                """)

                StreamReaderAsIonReader(data).use { reader ->
                    val iter = ION.iterate(reader)
                    assertTrue(iter.hasNext())
                    val value0 = iter.next()
                    println(value0)
                    assertEquals(ION.newInt(0), value0)
                    assertTrue(iter.hasNext())
                    val value1 = iter.next()
                    println(value1)
                    assertEquals(ION.newInt(1), value1)
                    assertFalse(iter.hasNext())
                }
            }

            @Test
            fun prefixedList() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    B4                   | List
                       61 01             | Int 1
                       61 02             | Int 2
                    B4
                       61 03
                       61 04             | Int 4
                """)

                StreamReaderAsIonReader(data).expect {
                    value("0")
                    value("[1, 2]")
                    value("[3, 4]")
                }
            }

            @Test
            fun delimitedList() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    F1                   | List
                       61 01             | Int 1
                       61 02             | Int 2
                    F0
                    F1
                       61 03
                       61 04             | Int 4
                    F0
                """)

                StreamReaderAsIonReader(data).expect {
                    value("0")
                    value("[1, 2]")
                    value("[3, 4]")
                }
            }

            @Test
            fun prefixedSexp() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    C4                   | Sexp
                       61 01             | Int 1
                       61 02             | Int 2
                    C4
                       61 03
                       61 04             | Int 4
                """)
                StreamReaderAsIonReader(data).expect {
                    value("0")
                    value("(1 2)")
                    value("(3 4)")
                }
            }

            @Test
            fun delimitedSexp() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    F2                   | Sexp
                       61 01             | Int 1
                       61 02             | Int 2
                    F0
                    F2
                       61 03
                       61 04             | Int 4
                    F0
                """)
                StreamReaderAsIonReader(data).expect {
                    value("0")
                    value("(1 2)")
                    value("(3 4)")
                }
            }

            @Test
            fun prefixedStruct() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    D6                   | Struct
                       03
                       61 01             | Int 1
                       05
                       61 02             | Int 2
                    D6
                       07
                       61 03
                       09
                       61 04             | Int 4
                """)
                StreamReaderAsIonReader(data).use {
                    StreamReaderAsIonReader(data).expect {
                        value("0")
                        value("{ $1: 1, $2: 2 }")
                        value("{ $3: 3, $4: 4 }")
                    }
                }
            }

            @Test
            fun delimitedStruct() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    F3                   | Struct
                       03                | FlexSym SID 1
                       61 01             | Int 1
                       05                | FlexSym SID 2
                       61 02             | Int 2
                       01 F0
                    F3
                       07                | FlexSym SID 3
                       61 03             | Int 3
                       09                | FlexSym SID 4
                       61 04             | Int 4
                       01 F0
                """)
                StreamReaderAsIonReader(data).expect {
                    value("0")
                    value("{ $1: 1, $2: 2 }")
                    value("{ $3: 3, $4: 4 }")
                }
            }

            @Test
            fun oneSidAnnotation() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    E4 03        | $ ion ::
                    60           | 0
                """)
                StreamReaderAsIonReader(data).expect {
                    value("\$ion::0")
                }
            }

            @Test
            fun twoSidAnnotations() {
                val data = toByteBuffer("""
                    E0 01 01 EA
                    E5 03 05     | $ ion :: $ ion_1_0
                    60           | 0
                """)
                StreamReaderAsIonReader(data).expect {
                    value("\$ion::\$ion_1_0::0")
                }
            }

            @Test
            fun `a big one for Ion 1 1 conversion using IonJavaBenchmarkCli and the IonReader API`() {
                val path = Paths.get("/Volumes/brazil-ws/ion-java-benchmark-cli/service_log_legacy_1_1.10n")

                val expected = expect(IonReaderBuilder.standard().build(path.toFile().inputStream()), 10000)
                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->

                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    StreamReaderAsIonReader(mappedByteBuffer).use {
                        val iter = ION.iterate(it)
                        while (iter.hasNext()) {
                            iter.next()
                        }
                    }
                }
            }


            @Test
            fun `add_symbols Ion 1 1 test`() {
                val bytes = hexStringToByteArray(cleanCommentedHexBytes("""
            E0 01 01 EA
            EF                 | System E-Expression
               14              | (:add_symbols
               02              | Presence Bits: argument is an expression group
               01              | expression group length = DELIMITED
               93 66 6F 6F     | "foo"
               93 62 61 72     | "bar"
               F0              |      )
            | TODO: Change these when we fix the ordering for "append"
            E1 3F              | foo
            B2                 | [
            E1 40              | bar]
            E1 01              | $ ion
        """))

                StreamReaderAsIonReader(ByteBuffer.wrap(bytes)).expect {
                    value("foo")
                    value("[bar]")
                    value("\$ion")
                }
            }

            @Test
            fun `set_symbols Ion 1 1 test`() {
                val bytes = hexStringToByteArray(cleanCommentedHexBytes("""
                E0 01 01 EA
                EF                 | System E-Expression
                   13              | (:set_symbols
                   02              | Presence Bits: argument is an expression group
                   01              | expression group length = DELIMITED
                   93 66 6F 6F     | "foo"
                   93 62 61 72     | "bar"
                   F0              |      )
                E1 01              | foo
                B2                 | [
                E1 02              | bar]
            """))

                StreamReaderAsIonReader(ByteBuffer.wrap(bytes)).expect {
                    value("foo")
                    value("[bar]")
                }
            }


            @Test
            fun constantListTemplateMacro() {
                val macro = TemplateMacro(
                    signature = listOf(),
                    body = ExpressionBuilderDsl.templateBody {
                        list {
                            int(1)
                            int(2)
                        }
                    }
                )
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    18
                    61 03
                """)

                StreamReaderAsIonReader(data, additionalMacros = listOf(macro)).expect {
                    value("0")
                    value("[1, 2]")
                    value("3")
                }
            }

            @Test
            fun constantSexpTemplateMacro() {
                val macro = TemplateMacro(
                    signature = listOf(),
                    body = ExpressionBuilderDsl.templateBody {
                        sexp {
                            int(1)
                            int(2)
                        }
                    }
                )
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    18
                    61 03
                """)

                StreamReaderAsIonReader(data, additionalMacros = listOf(macro)).expect {
                    value("0")
                    value("(1 2)")
                    value("3")
                }
            }

            @Test
            fun constantStructTemplateMacro() {
                val macro = TemplateMacro(
                    signature = listOf(),
                    body = ExpressionBuilderDsl.templateBody {
                        struct {
                            fieldName("foo")
                            int(1)
                            fieldName("bar")
                            int(2)
                        }
                    }
                )
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    18
                    61 03
                """)

                StreamReaderAsIonReader(data, additionalMacros = listOf(macro)).expect {
                    value("0")
                    value("{foo: 1, bar: 2}")
                    value("3")
                }
            }


            @Test
            fun templateMacroWithOneVariable() {
                val macro = template("foo") { variable(0) }

                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    18 61 01
                    61 02
                """)

                StreamReaderAsIonReader(data, additionalMacros = listOf(macro)).expect {
                    value("0")
                    value("1")
                    value("2")
                }
            }


            @Test
            fun templateMacroWithMultipleVariables() {
                val macro = TemplateMacro(
                    signature = listOf(
                        Macro.Parameter("foo", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
                        Macro.Parameter("bar", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
                    ),
                    body = ExpressionBuilderDsl.templateBody {
                        struct {
                            fieldName("foo")
                            variable(0)
                            fieldName("bar")
                            variable(1)
                        }
                    }
                )
                val data = toByteBuffer("""
                    E0 01 01 EA
                    60
                    18 61 01 61 02
                    61 03
                """)

                StreamReaderAsIonReader(data, additionalMacros = listOf(macro)).expect {
                    value("0")
                    value("{ foo:1, bar: 2}")
                    value("3")
                }
            }

            @Test
            fun smallLog() {
                val placeholder = TemplateMacro(emptyList(), listOf(Expression.LongIntValue(value = 0)))

                // (macro one ( ) '' )
                val one = template() { symbol("") }

                val entry = template(*"start_time end_time marketplace program time operation properties timing counters levels service_metrics metrics groups".split(" ").toTypedArray()) {
                    struct {
                        fieldName("StartTime"); variable(0)
                        fieldName("EndTime"); variable(1)
                        fieldName("Marketplace"); variable(2)
                        fieldName("Program"); variable(3)
                        fieldName("Time"); variable(4)
                        fieldName("Operation"); variable(5)
                        fieldName("Properties"); variable(6)
                        fieldName("ServiceMetrics"); variable(10)
                        fieldName("Timing"); variable(7)
                        fieldName("Counters"); variable(8)
                        fieldName("Levels"); variable(9)
                        fieldName("Metrics"); variable(11)
                        fieldName("Groups"); variable(12)
                    }
                }

                // ( macro summary ( name sum unit '\?' count '\?' )
                //         { Name: ( '%' name ), Sum: ( '%' sum ), Unit: ( '.' $ion:: default ( '%' unit ) ( '.' 0 ) ), Count: ( '.' $ion:: default ( '%' count ) 1 ), } )
                val summary = template("name", "sum", "unit?", "count?") {
                    struct {
                        fieldName("Name"); variable(0)
                        fieldName("Sum"); variable(1)
                        fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(one) {} }
                        fieldName("Count"); macro(SystemMacro.Default) { variable(3); int(1) }
                    }
                }

                // ( macro sample ( value repeat ? )
                //         { Value: ( '%' value ), Repeat: ( '.' $ion:: default ( '%' repeat ) 1 ), } )
                val sample = template("value", "repeat?") {
                    struct {
                        fieldName("Value"); variable(0)
                        fieldName("Repeat"); macro(SystemMacro.Default) { variable(1); int(1) }
                    }
                }

                // ( macro metric
                //         ( name value unit '\?' dimensions '\?' )
                //         { Name: ( '%' name ), Samples: ( '%' value ), Unit: ( '.' $ion:: default ( '%' unit ) ( '.' 0 ) ), Dimensions: ( '%' dimensions ), } )
                val metric = template("name", "value", "unit?", "dimensions?") {
                    struct {
                        fieldName("Name"); variable(0)
                        fieldName("Samples"); variable(1)
                        fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(one) {} }
                        fieldName("Dimensions"); variable(3)
                    }
                }

                // ( macro metric_single ( name value repeat '?' unit '?' dimensions '?' )
                //         ( . 6 ( '%' name ) [ ( '.' 4 ( '%' value ) ( '%' repeat ) ), ] ( % unit ) ( '%' dimensions ) )
                // )
                val metricSingle = template("name", "value", "repeat?", "unit?", "dimension?") {
                    macro(metric) {
                        variable(0)
                        list {
                            macro(sample) {
                                variable(1)
                                variable(2)
                            }
                        }
                        variable(3)
                        variable(4)
                    }
                }
                // ( macro summary_ms ( name value count '\?' ) (. 2 (%name) (%value) ms (%count)) )
                val summaryMs = template("name", "value", "count?") {
                    macro(summary) {
                        variable(0)
                        variable(1)
                        symbol("ms")
                        variable(2)
                    }
                }
                // ( macro summary0 (name) (. 2 (%name) 0e0 ) )
                val summary0 = template("name") {
                    macro(summary) {
                        variable(0)
                        float(0.0)
                    }
                }
                // ( macro summary1 ( name ) (. 2 (%name) 1e0 ) )
                val summary1 = template("name") {
                    macro(summary) {
                        variable(0)
                        float(1.0)
                    }
                }

                val macroTable = arrayOf<Macro>(
                    /* 00 */ one,
                    /* 01 */ entry,
                    /* 02 */ summary,
                    /* 03 */ placeholder,
                    /* 04 */ sample,
                    /* 05 */ placeholder,
                    /* 06 */ metric,
                    /* 07 */ metricSingle,
                    /* 08 */ placeholder,
                    /* 09 */ summaryMs,
                    /* 0a */ placeholder,
                    /* 0b */ summary0,
                    /* 0c */ summary1,
                    /* 0d */ placeholder,
                    /* 0e */ placeholder,
                    *EncodingContextManager.ION_1_1_SYSTEM_MACROS,
                )
                val symbolTable = arrayOf<String?>(null,
                    "ms", "B", "s", "us-east-1", "LambdaFrontendInvokeService",
                    "ReserveSandbox2", "FrontendInstanceId", "i-0505be8aa9972815b", "AccountId", "103403959176",
                    "RequestId", "f0bc3259-06e9-5ccb-96f1-6a44af76d4aa", "PID", "2812@ip-10-0-16-227", "WorkerId",
                    "i-0c891b196c563ba4c", "FrontendInternalAZ", "USMA7", "WorkerManagerInstanceId", "i-070b7692b9a6aba7e",
                    "SandboxId", "61fa4c30-d51d-40bb-82e0-a6195e27ca10", "Thread", "coral-orchestrator-136", "FrontendPublicAZ",
                    "us-east-1a", "WorkerConnectPort", "2503", "Time:Warm", "Attempt",
                    "Success", "Error", "Fault",
                )

                val actual = ION.newDatagram()

                StreamReaderAsIonReader(toByteBuffer("E0 01 01 EA 60 " + smallLog11Binary)).use {

                    val iter = ION.iterate(it)
                    iter.next()

                    it.ion11Reader.initTables(symbolTable, macroTable)

                    while (iter.hasNext()) {
                        val value = iter.next()
                        println(value.toPrettyString())
                        actual.add(value)
                    }
                }

                val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_small.11.10n")

                val expected = ION.loader.load(path.toFile())
                val ei = expected.iterator()
                val ai = actual.iterator()
                var i = 0
                while (ei.hasNext() && ai.hasNext()) {
                    val e = ei.next()
                    val a = ai.next()
                    assertEquals(e.toPrettyString(), a.toPrettyString(), "Value $i does not match")
                    i++
                }
            }


            @Test
            fun usingMacrosDefinedInTheDataStream() {
                val macro = TemplateMacro(
                    signature = listOf(
                        Macro.Parameter("foo", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
                        Macro.Parameter("bar", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
                    ),
                    body = ExpressionBuilderDsl.templateBody {
                        struct {
                            fieldName("foo")
                            variable(0)
                            fieldName("bar")
                            variable(1)
                        }
                    }
                )
                val baos = ByteArrayOutputStream()
                val writer = IonManagedWriter_1_1.binaryWriter(
                    output = baos,
                    managedWriterOptions = ManagedWriterOptions_1_1(
                        internEncodingDirectiveSymbols = false,
                        invokeTdlMacrosByName = true,
                        symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE,
                        lengthPrefixStrategy = LengthPrefixStrategy.ALWAYS_PREFIXED,
                        eExpressionIdentifierStrategy = ManagedWriterOptions_1_1.EExpressionIdentifierStrategy.BY_NAME,
                    ),
                    binaryOptions = _Private_IonBinaryWriterBuilder_1_1.standard()
                )

                writer.writeInt(0)
                writer.startMacro(macro)
                writer.writeInt(1)
                writer.writeInt(2)
                writer.endMacro()
                writer.writeInt(3)
                writer.close()

                val data = ByteBuffer.wrap(baos.toByteArray())
                //  E0 01 01 EA
                //
                //  EF 15                       | System Macro "set_macros"
                //     02                       | Presence: expression group
                //     01                       | Delimited expression group
                //        F2                    | Delimited Sexp start
                //           EE 0D              | 'macro'
                //           EA                 | null
                //           F2                 | delimited sexp start
                //             A3 66 6F 6F      | 'foo'
                //             A3 62 61 72      | 'bar'
                //           F0
                //           F3
                //             FB 66 6F 6F
                //             F2 A1 25 A3 66 6F 6F F0
                //             FB 62 61 72
                //             F2 A1 25 A3 62 61 72 F0
                //           01 F0
                //        F0
                //     F0
                //
                //  60
                //
                //  F5 01 09 61 01 61 02
                //
                //  61 03
                StreamReaderAsIonReader(data).expect {
                    value("0")
                    value("{ foo:1, bar: 2}")
                    value("3")
                }
            }

            @Test
            fun `a small log for Ion 1 1 with macros`() {
                val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_small.11.10n")

                // "/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_small.11.10n"

                val actual = ION.newDatagram()

                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    StreamReaderAsIonReader(mappedByteBuffer).use {
                        val iter = ION.iterate(it)
                        while (iter.hasNext()) {
                            val value = iter.next()
                            println(value)
                            actual.add(value)
                        }
                    }
                }

                val expected = ION.loader.load(path.toFile())
                val ei = expected.iterator()
                val ai = actual.iterator()
                var i = 0
                while (ei.hasNext() && ai.hasNext()) {
                    val e = ei.next()
                    val a = ai.next()
                    assertEquals(e.toPrettyString(), a.toPrettyString(), "Value $i does not match")
                    i++
                }
                assertEquals(expected.size, actual.size)
            }

            @Test
            fun `a medium one log Ion 1 1 with macros`() {
                val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11.10n")

                val actual = ION.newDatagram()

                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    StreamReaderAsIonReader(mappedByteBuffer).use {
                        val iter = ION.iterate(it)
                        while (iter.hasNext()) {
                            val value = iter.next()
                            actual.add(value)
                        }
                    }
                }

                val expected = ION.loader.load(path.toFile())
                val ei = expected.iterator()
                val ai = actual.iterator()
                var i = 0
                while (ei.hasNext() && ai.hasNext()) {
                    val e = ei.next()
                    val a = ai.next()
                    assertEquals(e.toPrettyString(), a.toPrettyString(), "Value $i does not match")
                    i++
                }
                assertEquals(expected.size, actual.size)
            }

            @Disabled
            @Test
            fun `a big log for Ion 1 1 with macros`() {
                val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_large.11.10n")

                val actual = ION.newDatagram()


                println("Starting V2 reader... " + Instant.now())
                FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
                    val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                    StreamReaderAsIonReader(mappedByteBuffer).use {
                        val iter = ION.iterate(it)
                        while (iter.hasNext()) {
                            val value = iter.next()
                            actual.add(value)
                        }
                    }
                }
                println("Starting baseline... " + Instant.now())
                val expected = ION.loader.load(path.toFile())
                println("Finished reading at: " + Instant.now())
                val ei = expected.iterator()
                val ai = actual.iterator()
                var i = 0
                while (ei.hasNext() && ai.hasNext()) {
                    val e = ei.next()
                    val a = ai.next()
                    assertEquals(e.toPrettyString(), a.toPrettyString(), "Value $i does not match")
                    i++
                }
                assertEquals(expected.size, actual.size)
            }


            fun StreamReaderAsIonReader.expect(block: Iterator<IonValue>.() -> Unit) {
                use {
                    val iter = ION.iterate(this)
                    iter.block()
                }
            }

            fun Iterator<IonValue>.value(ion: String) {
                assertTrue(hasNext())
                val value = next()
                println(value)
                val expected = ION.singleValue(ion)
                val actual = ION.singleValue(value.toString())
                assertEquals(expected, actual)
            }
        }


    }

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
    fun `the big one`() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy.10n")

        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())


            ApplicationReaderDriver(mappedByteBuffer).use {
                it.readAll(NoOpVisitor())
            }
        }
    }

    @Disabled("uses ion-rust conversion which has misaligned symbol table relative to ion-java")
    @Test
    fun `a big one for Ion 1 1 no macros`() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy_no_macros.10n")

        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())

            ApplicationReaderDriver(mappedByteBuffer).use {
                it.readAll(NoOpVisitor())
            }
        }
    }



    @Disabled
    @Test
    fun `Ion 1 1 test`() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy.10n")

        val baos = ByteArrayOutputStream()
        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            println("Starting transcoder...")
            ApplicationReaderDriver(mappedByteBuffer).use {
                val binaryWriter = IonEncodingVersion.ION_1_1.binaryWriterBuilder().build(baos)
                it.readAll(TranscoderVisitor(binaryWriter))
                binaryWriter.close()
                println(baos.size())
            }
            println("Transcoding complete.")
        }
        val bytes = baos.toByteArray()
        val buffer = ByteBuffer.wrap(bytes)

        ApplicationReaderDriver(buffer).use {
            val sb = StringBuilder()
            val textWriter = IonEncodingVersion.ION_1_1.textWriterBuilder()
                .withWriteTopLevelValuesOnNewLines(true)
                .build(sb)

            val visitor = TranscoderVisitor(textWriter)
            it.readAll(visitor)
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

            ApplicationReaderDriver(mappedByteBuffer).use {

                val binaryWriter = IonEncodingVersion.ION_1_1.binaryWriterBuilder().build(baos)
                it.readAll(TranscoderVisitor(binaryWriter))
                binaryWriter.close()
                println(baos.size())
            }
        }

        val bytes = baos.toByteArray()
        val buffer = ByteBuffer.wrap(bytes)

        ApplicationReaderDriver(buffer).use { driver ->
            val sb = StringBuilder()
            val textWriter = IonEncodingVersion.ION_1_1.textWriterBuilder()
                .withWriteTopLevelValuesOnNewLines(true)
                .build(sb)

            val visitor = TranscoderVisitor(textWriter)
            repeat(20) { driver.read(visitor) }
            textWriter.close()
            println(sb)
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

        ApplicationReaderDriver(data).use { driver ->
            driver.readAll(
                TestExpectationVisitor(mutableListOf(
                    Expectation.IntValue(0),
                    Expectation.ListStart,
                    Expectation.IntValue(1),
                    Expectation.IntValue(2),
                    Expectation.End,
                    Expectation.ListStart,
                    Expectation.IntValue(3),
                    Expectation.IntValue(4),
                    Expectation.End,
                    Expectation.End,
                ))
            )
        }
    }

    fun transfer(reader: IonReader, writer: IonWriter, max: Int = -1) {

        var n = max
        while (n != 0) {
            if (n > 0) n--
            val next = reader.next()
            if (next == null) {
                if (reader.depth > 0) {
                    reader.stepOut()
                    writer.stepOut()
                    continue
                } else {
                    break
                }
            }

            if (reader.isInStruct) {
                writer.setFieldName(reader.fieldName)
            }
            if (reader.typeAnnotations.isNotEmpty()) {
                writer.setTypeAnnotations(*reader.typeAnnotations)
            }
            if (reader.isNullValue) {
                writer.writeNull(reader.type)
            }
            when (next) {
                IonType.NULL -> TODO("Unreachable")
                IonType.BOOL -> writer.writeBool(reader.booleanValue())
                IonType.INT -> writer.writeInt(reader.longValue())
                IonType.FLOAT -> writer.writeFloat(reader.doubleValue())
                IonType.DECIMAL -> writer.writeDecimal(reader.decimalValue())
                IonType.TIMESTAMP -> writer.writeTimestamp(reader.timestampValue())
                IonType.SYMBOL -> writer.writeSymbolToken(reader.symbolValue())
                IonType.STRING -> writer.writeString(reader.stringValue())
                IonType.CLOB -> TODO()
                IonType.BLOB -> TODO()
                IonType.LIST -> {
                    writer.stepIn(IonType.LIST)
                    reader.stepIn()
                }
                IonType.SEXP -> {
                    writer.stepIn(IonType.SEXP)
                    reader.stepIn()
                }
                IonType.STRUCT -> {
                    writer.stepIn(IonType.STRUCT)
                    reader.stepIn()
                }
                IonType.DATAGRAM -> TODO("Unreachable")
            }
        }
    }

//    @Test
//    fun `a small one for Ion 1 1 no macros and conversion using IonJava`() {
//        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy.10n")
//        // val reader = IonReaderBuilder.standard().build(path.toFile().inputStream())
//
//        val reader = IonReaderBuilder.standard().build(smallLog)
//
//        val baos = ByteArrayOutputStream()
//        val binaryWriter = IonEncodingVersion.ION_1_1.binaryWriterBuilder().build(baos)
//
//        transfer(reader, binaryWriter)
//
//        reader.close()
//        binaryWriter.close()
//
//
////        FileChannel.open(path, StandardOpenOption.READ).use { fileChannel ->
////            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
////            ApplicationReaderDriver(mappedByteBuffer).use { driver ->
////                val binaryWriter = IonEncodingVersion.ION_1_1.binaryWriterBuilder().build(baos)
////                val visitor = TranscoderVisitor(binaryWriter)
////                driver.readAll(visitor)
////                binaryWriter.close()
////                println(baos.size())
////            }
////        }
//
//        val bytes = baos.toByteArray()
//
//
//        val ION = IonSystemBuilder.standard().build()
//        val valueIterator = ION.iterate(IonReaderBuilder.standard().build(bytes))
//        var i = 0
//        while (valueIterator.hasNext()) {
//            println(valueIterator.next())
//            if (i++ > 20) break;
//        }
//
//
//
//        val buffer = ByteBuffer.wrap(bytes)
//        ApplicationReaderDriver(buffer).use { driver ->
//
//            val sb = StringBuilder()
//            val textWriter = IonEncodingVersion.ION_1_1.textWriterBuilder()
//                .withWriteTopLevelValuesOnNewLines(true)
//                .build(sb)
//
//            val visitor = TranscoderVisitor(textWriter)
//            driver.readAll(visitor)
//            textWriter.close()
//
//            sb.lines().take(200).forEach { println(it) }
//        }
//
//
//        /*
//        $ion_log::"ServiceQueryLog_1_0"
//        {
//          StartTime:2018-08-06T23:59:59.897Z,
//          Marketplace:"us-west-2",
//          Program:"Canal_20130901",
//          Time:19041583,
//          Operation:"PutRecord",
//          AccountId:"599497505561",
//          AwsAccessKeyId:"AKIAIDCULUG4T7M7YSFA",
//          AwsAccountId:"599497505561",
//          AwsCallerPrincipal:"AIDAJDEOMPZALKP53LY7E",
//          AwsUserArn:"arn:aws:iam::599497505561:user/kinesis-rw-user",
//          AwsUserPrincipal:"AIDAJDEOMPZALKP53LY7E",
//          ChainId:"Chain_163fa914bda_7",
//          EndTime:Instant::2018-08-06T23:59:59.000Z,
//          PID:"38207@kinesis-proxy-hailstoneperf-pdx-62002.pdx2.amazon.com",
//          RemoteIpAddress:"10.242.81.215",
//          RemoteIpAddressSequence:"10.242.81.215",
//          RemoteSocket:"127.0.0.1:44700",
//          RequestId:"bb1890d7-f941-4277-b183-791205713dc7",
//          SN:"49585610451445194228944925219480153390279546138439714930",
//          ShardId:"shardId-000000000135",
//          Size:"104",
//          StreamName:"240-shard-fe-stress",
//          Throttler:"Gossip",
//          UserAgent:"aws-sdk-java/2.0.0-preview-11-SNAPSHOT Linux/3.2.45-0.6.wd.514.39.260.metal1.x86_64 Java_HotSpot_TM__64-Bit_Server_VM/25.92-b14 Java/1.8.0_92",
//          'amz-sdk-invocation-id':"af8e1574-03b1-6e24-597a-7281467821b3",
//          'amz-sdk-retry':"0/0/500",
//          chainHost:"kinesis-backend-d24x-hailstoneperf-60001.pdx1.amazon.com:7080",
//          Metrics:{
//            ActivityTime:{ Value:17.564464569091797e0, Unit:ms },
//            AllocatedTokens:5,
//            'AuthClient:Authenticate':{ Value:0.36827701330184937e0, Unit:ms },
//            'AuthClient:AuthenticateCacheHit':1,
//            'AuthClient:AuthenticateSuccess':1,
//            'AuthClient:AuthenticateUser':{Value:0.36827701330184937e0,Unit:ms},
//            'AuthClient:AuthorizeAction':{Value:0.058118000626564026e0,Unit:ms},
//            'AuthClient:AuthorizePolicies':2,
//            'AuthClient:DerefCachedNamedPolicies':1,
//            'AuthClient:DerefNamedPoliciesCounter':1,
//            'AuthClient:DerefNamedPoliciesTimer':{Value:0.0861940011382103e0,Unit:ms},
//            'AuthClient:EvaluateRequest':{Value:0.0389229990541935e0,Unit:ms},
//            'AuthClient:NamedPolicyCacheHitAll':1,
//            'AuthClient:SubscriptionCacheHit':1,
//            'AuthClient:SubscriptionCheck':{Value:0.07734400033950806e0,Unit:ms},
//            'AuthClient:SubscriptionValid':1,
//            AwsSignature_Version4:1,
//            CacheRefresherActiveThreadCount:0,
//            CacheRefresherBlockingQueueSize:0,
//            CacheRefresherThreadPoolSize:100,
//            'CachedStreamInfo.FailureRate':[{Value:0,Count:5}],
//            'CachedStreamInfo.Fault':0,
//            'CachedStreamInfo.IllegalCacheState':0,
//            'CachedStreamInfo.ProcessedUpdaters':0,
//            'CachedStreamInfo.Time':{Value:0,Unit:ms},
//            'CachedStreamInfoLoadingLock.FailureRate':[{Value:0,Count:5}],
//            'CachedStreamInfoLoadingLock.Fault':0,
//            'CachedStreamInfoLoadingLock.Time':{Value:0,Unit:ms},
//            ChainPostprocessingError:0,
//            ChainPostprocessingFault:0,
//            ChainPreprocessingError:0,
//            ChainPreprocessingFault:0,
//            'ChainRedirectExceptionHelper.SHARDED_CHAIN_PUT_SASSY.CoralFailure':0,
//            'ChainRedirectExceptionHelper.SHARDED_CHAIN_PUT_SASSY.CoralFailureFaulting':0,
//            'ChainRedirectExceptionHelper.SHARDED_CHAIN_PUT_SASSY.Redirects':0,
//            'ChainRedirectExceptionHelper.SHARDED_CHAIN_PUT_SASSY.RemoteCall.Time':{Value:17.176225662231445e0,Unit:ms},
//            'ChainRedirectExceptionHelper.SHARDED_CHAIN_PUT_SASSY.Retries':0,
//            'ChainStorage.Hostset:Failure:I:f8829f97-29fe-49fe-bd17-ed6e9d3e7426':0,
//            'ChainStorage.Hostset:Latency:I:f8829f97-29fe-49fe-bd17-ed6e9d3e7426':{Value:17,Unit:ms},
//            'ChainStorage.SHARDED_CHAIN_PUT_SASSY.Failure':0,
//            'ChainStorage.SHARDED_CHAIN_PUT_SASSY.Latency':{Value:17,Unit:ms},
//            'CloudWatch/Aggregator/Aggregate':{Value:9.079999872483313E-4,Unit:ms},
//            'CoreChain:CapacityUsed':24,
//            'CoreChain:InFlight':73,
//            'CoreChain:LoadShed':0,
//            'CoreChain:MaxRequestsQueueSize':0,
//            'CoreChain:MaxRequestsQueueTime':{Value:8.640000014565885E-4,Unit:ms},
//            'CryptoHelper.OptionallyEncrypt.EncryptionType:NONE':1,
//            'CryptoHelper.OptionallyEncrypt.FailureRate':[{Value:0,Count:2}],
//            'CryptoHelper.OptionallyEncrypt.Fault':0,
//            'CryptoHelper.OptionallyEncrypt.Time':{Value:0,Unit:ms},
//            Entropy:0.9737259745597839e0,
//            EntropyCalculation:{Value:0.01040599960833788e0,Unit:ms},
//            Error:0,
//            ExceptionFromThrottler:0,ExpiredIteratorException:0,ExpiredShards:0,FailedRecordRate:[{Value:0,Count:2}],Failure:0,Fault:0,FeaturePutEvenIfOutOfSync:2,FeatureSecuredBackendEndpoint:1,FeatureServerSideEncryption:1,FeatureShardLevelMetrics:0,'Http1.1Request':0,'Http2.0Request':1,InvalidArgumentException:0,KMSAccessDeniedException:0,KMSDisabledException:0,KMSException:0,KMSInvalidStateException:0,KMSNotFoundException:0,KMSOptInRequired:0,KMSThrottlingException:0,Kinesis_20131202:1,LimitExceededException:0,MeteringUnits:1,MisalignedStartEpochAndFrontendEpoch:0,'NonMutatingAPI.Fault':0,OutstandingRequests:87,'Put.50K.Latency':{Value:17,Unit:ms},'Put.50K.Success':1,'PutRecord.Bytes':{Value:4944,Unit:B},QueueTime:{Value:0.013337000273168087e0,Unit:ms},RegularThrottlingLatency:{Value:0,Unit:ms},RequestPayloadReadTime:{Value:0.46063798666000366e0,Unit:ms},ResourceInUseException:0,ResourceNotFoundException:0,'ShardSizeN.FailedRecordRate':[{Value:0,Count:2}],'ShardSizeN.Latency':{Value:17,Unit:ms},'ShardSizeN.ThrottledByTPS':0,'ShardSizeN.ThrottledByThroughput':0,'ShardSizeN.ThrottlingException':0,'StreamsCacheLock.FailureRate':[{Value:0,Count:5}],'StreamsCacheLock.Fault':0,'StreamsCacheLock.Time':{Value:0,Unit:ms},'ThreadpoolFairnessEnforcer.OverThreadQuota':0,'ThreadpoolFairnessEnforcer.OverThreadQuota.Chain':0,'ThreadpoolFairnessEnforcer.OverThreadQuota.Hostset':0,'ThreadpoolFairnessEnforcer.ThreadConsumption.Chain':9,'ThreadpoolFairnessEnforcer.ThreadConsumption.Hostset':58,'ThreadpoolFairnessEnforcer.ThreadCountLeak':0,ThrottledByTPS:0,ThrottledByThroughput:0,ThrottlingException:0,Tokens:1273.074951171875e0,TotalTime:{Value:19.234561920166016e0,Unit:ms},TransmuterTime:{Value:0.023936999961733818e0,Unit:ms},UnknownHttpVersionRequest:0,UnreportedHits:49,VPCERequest:0,'VPCERequest.ParsingError':0,ValidationFailures:0,ValidationTime:{Value:0.008293000049889088e0,Unit:ms},WasProxyOutOfSyncAfterCall:0,WasProxyOutOfSyncBeforeCall:0,chainHostAz:[{Value:[1.0000010206567822E-6,1],Dimensions:[AZ::"pdx1",NONE::NONE]}],hostScoreMean:{Value:100,Unit:ms},hostScoreStdDev:{Value:0,Unit:ms},meterSuccess:1,numBadHosts:[{Value:[1.0000010206567822E-6,1]}],numGoodHosts:[{Value:[2.000000904445187E-6,2]}],protocol_AwsCbor11:1,sampleBadHost:0,selectAvoidHost:0,selectBadHost:0,selectedHostRank:{Value:2,Unit:ms}
//          }
//        }
//
//         */
//
//        ApplicationReaderDriver(ByteBuffer.wrap(bytes)).use { driver ->
//
//        }
//    }

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

    private class NoOpVisitor(): VisitingReaderCallbackBase() {

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
                TokenTypeConst.MACRO_INVOCATION -> {
                    val sexp = ion.newEmptySexp()
//                    val address = reader.eexpValue()
//                    sexp.add().newSymbol(":$address")
                    while (true) {
                        val value = readValue(reader) ?: break
                        sexp.add(value)
                    }
                    sexp
                    TODO()
                }
                TokenTypeConst.IVM -> readValue(reader)
                TokenTypeConst.NOP-> readValue(reader)
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


    private fun expect(reader: IonReader, max: Int = -1): TestExpectationVisitor {
        // TODO: This is valid only as long as we have alternative reader implementations that we trust
        val expectations = mutableListOf<Expectation>()
        val expectationWriter = ExpectationWriter(expectations)
        transfer(reader, expectationWriter, max)
        reader.close()
        return TestExpectationVisitor(expectations)
    }

    private fun expect(ion: String): TestExpectationVisitor {
        // TODO: This is valid only as long as we have alternative reader implementations that we trust
        val reader = IonReaderBuilder.standard().build(ion)
        val expectations = mutableListOf<Expectation>()
        val expectationWriter = ExpectationWriter(expectations)
        transfer(reader, expectationWriter)
        reader.close()
        return TestExpectationVisitor(expectations)
    }

    private class ExpectationWriter(val expectations: MutableList<Expectation>): IonWriter {
        override fun close() = TODO("Not yet implemented")
        override fun flush() = TODO("Not yet implemented")
        override fun <T : Any?> asFacet(facetType: Class<T>?): T = TODO("Not yet implemented")
        override fun getSymbolTable(): SymbolTable = TODO("Not yet implemented")
        override fun finish() = TODO("Not yet implemented")

        override fun setFieldName(name: String) {
            expectations.add(Expectation.FieldName(text = name))
        }

        override fun setFieldNameSymbol(name: SymbolToken) {
            expectations.add(Expectation.FieldName(text = name.text, sid = name.sid))
        }

        override fun setTypeAnnotations(vararg annotations: String?) {
            expectations.add(Expectation.Annotation(annotations.toList()))
        }

        override fun setTypeAnnotationSymbols(vararg annotations: SymbolToken?) {
            TODO("Not yet implemented")
        }

        override fun addTypeAnnotation(annotation: String?) {
            TODO("Not yet implemented")
        }

        override fun stepIn(containerType: IonType?) {
            val e = when (containerType) {
                IonType.LIST -> Expectation.ListStart
                IonType.SEXP -> Expectation.SexpStart
                IonType.STRUCT -> Expectation.StructStart
                else -> TODO()
            }
            expectations.add(e)
        }

        override fun stepOut() {
            expectations.add(Expectation.End)
        }

        override fun isInStruct(): Boolean {
            TODO("Not yet implemented")
        }

        override fun writeValue(value: IonValue?) = TODO("Not yet implemented")
        override fun writeValue(reader: IonReader?) = TODO("Not yet implemented")

        override fun writeValues(reader: IonReader?) = TODO("Not yet implemented")

        override fun writeNull() {
            expectations.add(Expectation.NullValue(IonType.NULL))
        }

        override fun writeNull(type: IonType) {
            expectations.add(Expectation.NullValue(type))
        }

        override fun writeBool(value: Boolean) {
            TODO("Not yet implemented")
        }

        override fun writeInt(value: Long) {
            expectations.add(Expectation.IntValue(value))
        }

        override fun writeInt(value: BigInteger?) {
            TODO("Not yet implemented")
        }

        override fun writeFloat(value: Double) {
            expectations.add(Expectation.FloatValue(value))
        }

        override fun writeDecimal(value: BigDecimal) {
            TODO("Not yet implemented")
        }

        override fun writeTimestamp(value: Timestamp) {
            expectations.add(Expectation.TimestampValue(value))
        }

        override fun writeTimestampUTC(value: Date?) {
            TODO("Not yet implemented")
        }

        override fun writeSymbol(content: String?) {
            expectations.add(Expectation.Symbol(content))
        }

        override fun writeSymbolToken(content: SymbolToken) {
            if (content.text != null) {
                expectations.add(Expectation.Symbol(content.text))
            } else {
                expectations.add(Expectation.Symbol(sid = content.sid))
            }
        }

        override fun writeString(value: String) {
            expectations.add(Expectation.StringValue(value))
        }

        override fun writeClob(value: ByteArray?) {
            TODO("Not yet implemented")
        }

        override fun writeClob(value: ByteArray?, start: Int, len: Int) {
            TODO("Not yet implemented")
        }

        override fun writeBlob(value: ByteArray?) {
            TODO("Not yet implemented")
        }

        override fun writeBlob(value: ByteArray?, start: Int, len: Int) {
            TODO("Not yet implemented")
        }

    }

    private fun expect(vararg expectation: Expectation, assertionsEnabled: Boolean = true): TestExpectationVisitor {
        return TestExpectationVisitor(mutableListOf(*expectation), assertionsEnabled)
    }

    private class TestExpectationVisitor(
        val expectations: MutableList<Expectation>,
        var assertionsEnabled: Boolean = true
    ): VisitingReaderCallback {
        private val results = mutableListOf<String>()

        sealed class Expectation {
            open val expected: Any? = Unit
            open fun check(actual: Any?) {
                if (expected != Unit) {
                    assertEquals(expected, actual)
                }
            }

            data class Annotation(override val expected: List<String?>): Expectation() {
                override fun check(actual: Any?) {
                    // FIXME:
                    // actual as AnnotationIterator
                    // val actualAnnotations = actual.toStringArray().toList()
                    // assertEquals(expected, actualAnnotations)
                }
            }

            data class FieldName(val text: String? = null, val sid: Int = -1): Expectation() {
                override fun check(actual: Any?) {
                    actual as Pair<*, *>
                    if (sid >= 0) {
                        assertEquals(sid, actual.second)
                    }
                    if (text != null) {
                        assertEquals(text, actual.first)
                    }
                }
            }

            data class Symbol(val text: String? = null, val sid: Int = -1): Expectation() {
                override fun check(actual: Any?) {
                    actual as Pair<*, *>
                    if (text != null) {
                        assertEquals(text, actual.first)
                    }
                    if (sid >= 0) {
                        assertEquals(sid, actual.second)
                    }
                }
            }

            data object ListStart: Expectation()
            data object SexpStart: Expectation()
            data object StructStart: Expectation()
            data object End : Expectation()
            data object EOF : Expectation()

            data class NullValue(override val expected: IonType): Expectation()
            data class BoolValue(override val expected: Boolean): Expectation()
            data class IntValue(override val expected: Long): Expectation()
            data class FloatValue(override val expected: Double): Expectation()
            data class DecimalValue(override val expected: Decimal): Expectation()
            data class TimestampValue(override val expected: Timestamp): Expectation() {
                constructor(ts: String): this(Timestamp.valueOf(ts))
            }
            data class StringValue(override val expected: String): Expectation()

        }

        private inline fun <reified T: Expectation> next(actual: Any?) {
            try {
                val ex = expectations.removeFirstOrNull() ?: Expectation.EOF
                if (ex !is T) {
                    results.add("[x][${results.size}] $ex; found ${T::class.simpleName}: $actual")
                    if (assertionsEnabled) fail<Nothing>("Expected ${ex::class.simpleName} but found ${T::class.simpleName}: $actual")
                } else {
                    try {
                        ex.check(actual)
                        results.add("[✓][${results.size}] $ex")
                    } catch (e: Throwable) {
                        results.add("[x][${results.size}] $ex; found $actual")
                        if (assertionsEnabled) throw e
                    }
                }
            } catch (e: Throwable) {
                println(results.takeLast(50).forEach(::println))
                throw e
            }
        }

        override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback? {
            next<Expectation.Annotation>(annotations)
            return this
        }

        override fun onField(fieldName: String?, fieldSid: Int): VisitingReaderCallback? {
            next<Expectation.FieldName>(fieldName to fieldSid)
            return this
        }

        override fun onValue(type: TokenType): VisitingReaderCallback = this

        override fun onListStart() {
            next<Expectation.ListStart>(Unit)
        }

        override fun onListEnd() {
            next<Expectation.End>(Unit)
        }

        override fun onSexpStart() {
            next<Expectation.SexpStart>(Unit)
        }

        override fun onSexpEnd() {
            next<Expectation.End>(Unit)
        }

        override fun onStructStart() {
            next<Expectation.StructStart>(Unit)
        }

        override fun onStructEnd() {
            next<Expectation.End>(Unit)
        }

        override fun onNull(value: IonType) {
            next<Expectation.NullValue>(value)
        }

        override fun onBoolean(value: Boolean) {
            next<Expectation.BoolValue>(value)
        }

        override fun onLongInt(value: Long) {
            next<Expectation.IntValue>(value)
        }

        override fun onBigInt(value: BigInteger) {
            TODO("Not yet implemented")
        }

        override fun onFloat(value: Double) {
            next<Expectation.FloatValue>(value)
        }

        override fun onDecimal(value: Decimal) {
            next<Expectation.DecimalValue>(value)
        }

        override fun onTimestamp(value: Timestamp) {
            next<Expectation.TimestampValue>(value)
        }

        override fun onString(value: String) {
            next<Expectation.StringValue>(value)
        }

        override fun onSymbol(value: String?, sid: Int) {
            next<Expectation.Symbol>(value to sid)
        }

        override fun onClob(value: ByteBuffer) {
            TODO("Not yet implemented")
        }

        override fun onBlob(value: ByteBuffer) {
            TODO("Not yet implemented")
        }
    }
}
