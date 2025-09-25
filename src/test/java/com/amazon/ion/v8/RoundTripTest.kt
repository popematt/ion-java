package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.IonEncodingVersion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.bin.IonManagedWriter_1_1_Test.Companion.ion
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.ExpressionBuilderDsl.Companion.templateBody
import com.amazon.ion.impl.macro.ParameterFactory.exactlyOneTagged
import com.amazon.ion.impl.macro.ParameterFactory.zeroOrOneTagged
import com.amazon.ion.impl.macro.ParameterFactory.zeroToManyTagged
import com.amazon.ion.system.*
import com.amazon.ion.util.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class RoundTripTest {

    // Helper function that writes to a writer and returns the binary Ion
    private fun writeBinary(
        closeWriter: Boolean = true,
        symbolInliningStrategy: SymbolInliningStrategy = SymbolInliningStrategy.ALWAYS_INLINE,
        lengthPrefixStrategy: LengthPrefixStrategy = LengthPrefixStrategy.ALWAYS_PREFIXED,
        block: IonManagedWriter_1_1.() -> Unit
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ION_1_1.binaryWriterBuilder()
            .withSymbolInliningStrategy(symbolInliningStrategy)
            .withLengthPrefixStrategy(lengthPrefixStrategy)
            .withSimplifiedTemplates()
            .build(out) as IonManagedWriter_1_1
        writer.apply(block)
        if (closeWriter) writer.close()
        return out.toByteArray()
    }

    private fun write10Binary(
        closeWriter: Boolean = true,
        block: IonWriter.() -> Unit
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ION_1_0.binaryWriterBuilder()
            .build(out)
        writer.apply(block)
        if (closeWriter) writer.close()
        return out.toByteArray()
    }

    private fun writeCurrent11Binary(
        closeWriter: Boolean = true,
        symbolInliningStrategy: SymbolInliningStrategy = SymbolInliningStrategy.ALWAYS_INLINE,
        lengthPrefixStrategy: LengthPrefixStrategy = LengthPrefixStrategy.ALWAYS_PREFIXED,
        block: com.amazon.ion.impl.bin.IonManagedWriter_1_1.() -> Unit
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ION_1_1.binaryWriterBuilder()
            .withSymbolInliningStrategy(symbolInliningStrategy)
            .withLengthPrefixStrategy(lengthPrefixStrategy)
            .build(out) as com.amazon.ion.impl.bin.IonManagedWriter_1_1
        writer.apply(block)
        if (closeWriter) writer.close()
        return out.toByteArray()
    }

    private val ION = IonSystemBuilder.standard().build()

    @Test
    fun booleanT() = checkBinaryRoundTrip(""" true """)

    @Test
    fun booleanF() = checkBinaryRoundTrip(""" false """)

    @Test
    fun int() = checkBinaryRoundTrip(""" 123 """)

    @Test
    fun float() = checkBinaryRoundTrip(""" +inf """)

    @Test
    fun decimal() = checkBinaryRoundTrip(""" 1.23 """)

    @Test
    fun decimalZero() = checkBinaryRoundTrip(""" 0d0 """)

    @Test
    fun decimalNegZero() = checkBinaryRoundTrip(""" -0d0 """)

    @Test
    fun timestampY() = checkBinaryRoundTrip(""" 2025T """)

    @Test
    fun timestampYM() = checkBinaryRoundTrip(""" 2025-09T """)

    @Test
    fun timestampYMD() = checkBinaryRoundTrip(""" 2025-09-08T """)

    @Test
    fun timestampYMDhm() = checkBinaryRoundTrip(""" 2025-09-08T12:34Z """)

    @Test
    fun timestampYMDhms() = checkBinaryRoundTrip(""" 2025-09-08T12:34:56Z """)

    @Test
    fun emptySymbol() = checkBinaryRoundTrip(""" '' """)

    @Test
    fun symbol() = checkBinaryRoundTrip(""" foo """)

    @Test
    fun longSymbol() = checkBinaryRoundTrip(""" abcdefghijklmnopqrstuvwxyz """)

    @Test
    fun string() = checkBinaryRoundTrip(""" "foo" """)

    @Test
    fun emptyString() = checkBinaryRoundTrip(""" "" """)

    @Test
    fun longString() = checkBinaryRoundTrip(""" "abcdefghijklmnopqrstuvwxyz" """)

    @Test
    fun blob() = checkBinaryRoundTrip(""" {{aGVsbG8gd29ybGQ=}} """)

    @Test
    fun clob() = checkBinaryRoundTrip(""" {{"hello world"}} """)

    @Test
    fun annotatedWithUnknownSymbol() = checkBinaryRoundTrip(""" $0::1 """)

    @Test
    fun annotatedValue() = checkBinaryRoundTrip(""" foo::123 """)

    @Test
    fun multiAnnotatedValue() = checkBinaryRoundTrip(""" foo::bar::123 """)

    @Test
    fun multiAnnotatedValue5() = checkBinaryRoundTrip(""" a::b::c::d::e::123 """)

    @Test
    fun emptyList() = checkBinaryRoundTrip(""" [ ] """)

    @Test
    fun smallList() = checkBinaryRoundTrip(""" [ 1 ] """)

    @Test
    fun longMixedList() = checkBinaryRoundTrip(""" [ null, null.list, true, false, -123, foo, "hello world" ] """)

    @Test
    fun smallStruct0() = checkBinaryRoundTrip(""" { transactionId: "txn_12345" } """)

    @Test
    fun smallStruct1() = checkBinaryRoundTrip(""" {foo:1} """)

    @Test
    fun interestingStruct() = checkBinaryRoundTrip("""
            {
              transactionId: "txn_12345",
              timestamp: 2023-12-01T10:30:00Z,
              amount: 99.99d0,
              currency: USD,
              status: PENDING,
              metadata: {
                source: "mobile_app",
                version: "2.1.0"
              }
            }
        """)

    @Test
    fun anotherInterestingStruct() = checkBinaryRoundTrip("""
        {
          recordType: ANALYTICS_EVENT,
          eventId: "evt_abc123",
          sessionId: "sess_xyz789",
          metrics: {
            duration: 1234,
            conversionRate: 0.0567d0,
            score: 8.5e0,
            isSuccess: true
          },
          tags: ["conversion", "mobile", "premium"],
          context: {
            device: {
              type: "mobile",
              os: "iOS",
              version: "16.1"
            },
            location: {
              country: "US",
              region: "CA"
            },
            customDimensions: {
              campaignId: "camp_456",
              experimentGroup: "variant_b",
            }
          },
          payload: {{aGVsbG8gd29ybGQ=}}
        }
    """)

    @Test
    fun simpleConstantMacro() = checkBinaryRoundTrip(""" "abc" """, writeBinary {
        val macro = MacroV8.build {
            string("abc")
        }
        startMacro(macro)
        endMacro()
    })

    @Test
    fun simpleListMacro() = checkBinaryRoundTrip("[1, 2]", writeBinary {
        val macro = MacroV8.build {
            list {
                variable()
                variable()
            }
        }
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
    })

    @Test
    fun listMacroWithTaglessValues() = checkBinaryRoundTrip("[262, 'hello world!']", writeBinary {
        val macro = MacroV8.build {
            list {
                variable(TaglessScalarType.UINT)
                variable(TaglessScalarType.SYMBOL)
            }
        }
        startMacro(macro)
        writeInt(262)
        writeSymbol("hello world!")
        endMacro()
    })

    @Test
    fun sexpMacroWithTaglessValues() = checkBinaryRoundTrip("(262 'hello world!')", writeBinary {
        val macro = MacroV8.build {
            sexp {
                variable(TaglessScalarType.UINT)
                variable(TaglessScalarType.SYMBOL)
            }
        }
        startMacro(macro)
        writeInt(262)
        writeSymbol("hello world!")
        endMacro()
    })

    @Test
    fun simpleStructMacro() = checkBinaryRoundTrip("{x:1, y:2}", writeBinary {
        val pointMacro = MacroV8.build {
            struct {
                fieldName("x")
                variable()
                fieldName("y")
                variable()
            }
        }
        startMacro(pointMacro)
        writeInt(1)
        writeInt(2)
        endMacro()
    })

    @Test
    fun macroWithDefaultValueUsesArgument() = checkBinaryRoundTrip("[234]", writeBinary {
        val macro = MacroV8.build {
            list {
                variable { int(123) }
            }
        }
        startMacro(macro)
        writeInt(234)
        endMacro()
    })

    @Test
    fun macroWithDefaultValueUsesDefault() = checkBinaryRoundTrip("[123]", writeBinary {
        val macro = MacroV8.build {
            list {
                variable { int(123) }
            }
        }
        startMacro(macro)
        absentArgument()
        endMacro()
    })

    @Test
    fun macroWithAnnotatedVariable() = checkBinaryRoundTrip("[foo::123]", writeBinary {
        val macro = MacroV8.build {
            list {
                annotated("foo", ::variable)
            }
        }
        startMacro(macro)
        writeInt(123)
        endMacro()
    })

    @Test
    fun macroWithAbsentAnnotatedVariable() = checkBinaryRoundTrip("[bar::123]", writeBinary {
        val macro = MacroV8.build {
            list {
                annotated("foo", ::variable)
                annotated("bar", ::int, 123)
            }
        }
        startMacro(macro)
        absentArgument()
        endMacro()
    })

    @Test
    fun macroWithAnnotatedArgumentToAnnotatedVariable() = checkBinaryRoundTrip("[foo::bar::1024]", writeBinary {
        val macro = MacroV8.build {
            list {
                annotated("foo", ::variable)
            }
        }
        startMacro(macro)
        setTypeAnnotations(arrayOf("bar"))
        writeInt(1024)
        endMacro()
    })

    @Test
    fun annotatedConstantMacro() = checkBinaryRoundTrip(""" bar::"abc" """, writeBinary {
        val macro = MacroV8.build {
            string("abc")
        }
        setTypeAnnotations(arrayOf("bar"))
        startMacro(macro)
        endMacro()
    })

    @Test
    fun taglessElementListWithInt8() = checkBinaryRoundTrip(""" [1, 2, 3, 4] """, writeBinary {
        stepInTaglessElementList(TaglessScalarType.INT_8)
        writeInt(1)
        writeInt(2)
        writeInt(3)
        writeInt(4)
        stepOut()
    }.also {
        println(it.toPrettyHexString())
    })

    @Test
    fun taglessElementSexpWithInt8() = checkBinaryRoundTrip(""" (1 2 3 4) """, writeBinary {
        stepInTaglessElementSExp(TaglessScalarType.INT_8)
        writeInt(1)
        writeInt(2)
        writeInt(3)
        writeInt(4)
        stepOut()
    }.also {
        println(it.toPrettyHexString())
    })

    @Test
    fun taglessElementListWithInt32() = checkBinaryRoundTrip(""" [1, 2, 3, 4] """, writeBinary {
        stepInTaglessElementList(TaglessScalarType.INT_32)
        writeInt(1)
        writeInt(2)
        writeInt(3)
        writeInt(4)
        stepOut()
    })

    @Test
    fun taglessElementSexpWithInt32() = checkBinaryRoundTrip(""" (1 2 3 4) """, writeBinary {
        stepInTaglessElementSExp(TaglessScalarType.INT_32)
        writeInt(1)
        writeInt(2)
        writeInt(3)
        writeInt(4)
        stepOut()
    })

    @Test
    fun taglessElementListWithSymbol() = checkBinaryRoundTrip(""" [foo, name, version, bar] """, writeBinary {
        stepInTaglessElementList(TaglessScalarType.SYMBOL)
        writeSymbol("foo")
        writeSymbol("name")
        writeSymbol("version")
        writeSymbol("bar")
        stepOut()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray("e0 01 01 ea ec ea 09 f9 66 6f 6f f7 6e 61 6d 65 f1 76 65 72 73 69 6f 6e f9 62 61 72")
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun taglessElementSexpWithSymbol() = checkBinaryRoundTrip(""" (foo name version bar) """, writeBinary {
        stepInTaglessElementSExp(TaglessScalarType.SYMBOL)
        writeSymbol("foo")
        writeSymbol("name")
        writeSymbol("version")
        writeSymbol("bar")
        stepOut()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray("e0 01 01 ea ed ea 09 f9 66 6f 6f f7 6e 61 6d 65 f1 76 65 72 73 69 6f 6e f9 62 61 72")
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroShapedTaglessElementList() = checkBinaryRoundTrip(""" [{x:1,y:2}, {x:3,y:4}] """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_8)
                fieldName("y")
                variable(TaglessScalarType.INT_8)
            }
        }
        stepInTaglessElementList(macro)
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
        startMacro(macro)
        writeInt(3)
        writeInt(4)
        endMacro()
        stepOut()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     FD 78 EB 61   FD 79 EB 61
                  01 F0
               F0
            F0
            EC 00 05
               01 02
               03 04
        """.trimIndent()))
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroShapedTaglessElementSexp() = checkBinaryRoundTrip(""" ({x:1,y:2} {x:3,y:4}) """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_8)
                fieldName("y")
                variable(TaglessScalarType.INT_8)
            }
        }
        stepInTaglessElementSExp(macro)
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
        startMacro(macro)
        writeInt(3)
        writeInt(4)
        endMacro()
        stepOut()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     FD 78
                     EB 61
                     FD 79
                     EB 61
                  01 F0
               F0
            F0
            ED 00 05
               01 02
               03 04
        """.trimIndent()))
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroWithTaglessStringAndFloat32() = checkBinaryRoundTrip(""" {metric:"velocity",value:1.5e0} """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("metric")
                variable(TaglessScalarType.STRING)
                fieldName("value")
                variable(TaglessScalarType.FLOAT_32)
            }
        }
        startMacro(macro)
        writeString("velocity")
        writeFloat(1.5)
        endMacro()
    }.also {

        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     F3 6D 65 74 72 69 63
                     EB F9
                     F5 76 61 6C 75 65
                     EB 6C
                  01 F0
               F0
            F0
            00
               11 76 65 6C 6F 63 69 74 79
               00 00 C0 3F
        """.trimIndent()))
         assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroWithTaglessInt16() = checkBinaryRoundTrip(""" {x:1,y:2} """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_16)
                fieldName("y")
                variable(TaglessScalarType.INT_16)
            }
        }
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     FD 78
                     EB 62
                     FD 79
                     EB 62
                  01 F0
               F0
            F0
            00
               01 00
               02 00
        """.trimIndent()))
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroWithTaglessInt32() = checkBinaryRoundTrip(""" {x:1,y:2} """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_32)
                fieldName("y")
                variable(TaglessScalarType.INT_32)
            }
        }
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     FD 78
                     EB 64
                     FD 79
                     EB 64
                  01 F0
               F0
            F0
            00
               01 00 00 00
               02 00 00 00
        """.trimIndent()))
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroWithTaglessInt64() = checkBinaryRoundTrip(""" {x:1,y:2} """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_64)
                fieldName("y")
                variable(TaglessScalarType.INT_64)
            }
        }
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     FD 78
                     EB 68
                     FD 79
                     EB 68
                  01 F0
               F0
            F0
            00
               01 00 00 00 00 00 00 00
               02 00 00 00 00 00 00 00
        """.trimIndent()))
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroWithTaglessUInt16() = checkBinaryRoundTrip(""" {x:1,y:2} """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.UINT_16)
                fieldName("y")
                variable(TaglessScalarType.UINT_16)
            }
        }
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     FD 78
                     EB E2
                     FD 79
                     EB E2
                  01 F0
               F0
            F0
            00
               01 00
               02 00
        """.trimIndent()))
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroWithTaglessUInt32() = checkBinaryRoundTrip(""" {x:1,y:2} """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.UINT_32)
                fieldName("y")
                variable(TaglessScalarType.UINT_32)
            }
        }
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     FD 78
                     EB E4
                     FD 79
                     EB E4
                  01 F0
               F0
            F0
            00
               01 00 00 00
               02 00 00 00
        """.trimIndent()))
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun macroWithTaglessUInt64() = checkBinaryRoundTrip(""" {x:1,y:2} """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.UINT_64)
                fieldName("y")
                variable(TaglessScalarType.UINT_64)
            }
        }
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
    }.also {
        val expectedBinary = TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes("""
            E0 01 01 EA
            E3
               F2
                  8F
                  F3
                     FD 78
                     EB E8
                     FD 79
                     EB E8
                  01 F0
               F0
            F0
            00
               01 00 00 00 00 00 00 00
               02 00 00 00 00 00 00 00
        """.trimIndent()))
        assertEquals(expectedBinary.toPrettyHexString(), it.toPrettyHexString())
    })

    @Test
    fun taglessElementListWithMacroShapeWithOneTaglessInt8() = checkBinaryRoundTrip(""" [{x:1},{x:2}] """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_8)
            }
        }
        stepInTaglessElementList(macro)
        startMacro(macro)
        writeInt(1)
        endMacro()
        startMacro(macro)
        writeInt(2)
        endMacro()
        stepOut()
    })

    @Test
    fun taglessElementListWithMacroShapeWithTwoTaglessInt8() = checkBinaryRoundTrip(""" [{x:1,y:2},{x:3,y:4}] """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_8)
                fieldName("y")
                variable(TaglessScalarType.INT_8)
            }
        }
        stepInTaglessElementList(macro)
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
        startMacro(macro)
        writeInt(3)
        writeInt(4)
        endMacro()
        stepOut()
    })

    @Test
    fun taglessElementListWithMacroShapeWithOneTaglessInt32() = checkBinaryRoundTrip(""" [{x:1},{x:2}] """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_32)
            }
        }
        stepInTaglessElementList(macro)
        startMacro(macro)
        writeInt(1)
        endMacro()
        startMacro(macro)
        writeInt(2)
        endMacro()
        stepOut()
    })

    @Test
    fun taglessElementListWithMacroShapeWithTwoTaglessInt32() = checkBinaryRoundTrip(""" [{x:1,y:2},{x:3,y:4}] """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("x")
                variable(TaglessScalarType.INT_32)
                fieldName("y")
                variable(TaglessScalarType.INT_32)
            }
        }
        stepInTaglessElementList(macro)
        startMacro(macro)
        writeInt(1)
        writeInt(2)
        endMacro()
        startMacro(macro)
        writeInt(3)
        writeInt(4)
        endMacro()
        stepOut()
    })

    @Test
    fun taglessElementListWithMacroShapeWithOneTaglessString() = checkBinaryRoundTrip(""" [{foo:"bar"},{foo:"baz"}] """, writeBinary {
        val macro = MacroV8.build {
            struct {
                fieldName("foo")
                variable(TaglessScalarType.STRING)
            }
        }
        stepInTaglessElementList(macro)
        startMacro(macro)
        writeString("bar")
        endMacro()
        startMacro(macro)
        writeString("baz")
        endMacro()
        stepOut()
    })

    @Test
//    @Disabled("Already generated this data.")
    fun polygonSampleData() {

        // val seed = System.currentTimeMillis()
        val seed = 1757482725331L
        println("Seed: $seed")

        val fileSuffix = ""


        // Ion 1.0 Binary
        Random(seed).let { random ->
            val ion11MData = writeCurrent11Binary {
                repeat(2000) {
                    stepIn(IonType.STRUCT)
                    setFieldName("points")
                    stepIn(IonType.LIST)
                    val n = random.nextInt(3, 100)
                    repeat(n) {
                        stepIn(IonType.STRUCT)
                        setFieldName("x")
                        writeInt(random.nextLong())
                        setFieldName("y")
                        writeInt(random.nextLong())
                        stepOut()
                    }
                    stepOut()
                    setFieldName("fill")
                    if (random.nextInt(10) == 0) {
                        writeNull()
                    } else {
                        setTypeAnnotations(arrayOf("rgb"))
                        stepIn(IonType.LIST)
                        writeInt(random.nextLong(255))
                        writeInt(random.nextLong(255))
                        writeInt(random.nextLong(255))
                        stepOut()
                    }
                    stepOut()
                }
            }
            println(ion11MData.size)
            Files.write(Paths.get("polygons.11.ion"), ion11MData)
        }

        /*
        // Ion 1.1 Simplified
        Random(seed).let { random ->
            val point = MacroV8.build {
                struct {
                    fieldName("x")
                    variable()
                    fieldName("y")
                    variable()
                }
            }
            val polygon = MacroV8.build {
                struct {
                    fieldName("points")
                    variable()
                    fieldName("fill")
                    variable { nullValue() }
                }
            }

            val ion11MData = writeBinary(symbolInliningStrategy = SymbolInliningStrategy.ALWAYS_INLINE, lengthPrefixStrategy = LengthPrefixStrategy.NEVER_PREFIXED) {
            repeat(2000) {
                startMacro(polygon)
                stepIn(IonType.LIST)
                val n = random.nextInt(3, 100)
                repeat(n) {
                    startMacro(point)
                    writeInt(random.nextLong())
                    writeInt(random.nextLong())
                    endMacro()
                }
                stepOut()
                if (random.nextInt(10) == 0) {
                    absentArgument()
                } else {
                    setTypeAnnotations(arrayOf("rgb"))
                    stepIn(IonType.LIST)
                    writeInt(random.nextLong(255))
                    writeInt(random.nextLong(255))
                    writeInt(random.nextLong(255))
                    stepOut()
                }
                endMacro()
            }
        }
            println(ion11MData.size)
            Files.write(Paths.get("polygons-$fileSuffix.11m.ion"), ion11MData)
        }
        */
        /*
        // Ion 1.1 Current
        Random(seed).let { random ->
            val point = TemplateMacro(
                listOf(exactlyOneTagged("x"), exactlyOneTagged("y")),
                templateBody {
                    struct {
                        fieldName("x")
                        variable(0)
                        fieldName("y")
                        variable(1)
                    }
                })

            val polygon = TemplateMacro(
                listOf(zeroToManyTagged("points"), zeroOrOneTagged("fill")),
                templateBody {
                    struct {
                        fieldName("points")
                        list {
                            variable(0)
                        }
                        fieldName("fill")
                        macro(SystemMacro.Default) {
                            variable(1)
                            nullValue()
                        }
                    }
                })

            val ion11Data = writeCurrent11Binary(symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE, lengthPrefixStrategy = LengthPrefixStrategy.NEVER_PREFIXED) {
                repeat(2000) {
                    startMacro(polygon)
                    startExpressionGroup()
                    val n = random.nextInt(3, 100)
                    repeat(n) {
                        startMacro(point)
                        writeInt(random.nextLong())
                        writeInt(random.nextLong())
                        endMacro()
                    }
                    endExpressionGroup()
                    if (random.nextInt(10) == 0) {
                        startExpressionGroup()
                        endExpressionGroup()
                    } else {
                        setTypeAnnotations(arrayOf("rgb"))
                        stepIn(IonType.LIST)
                        writeInt(random.nextLong(255))
                        writeInt(random.nextLong(255))
                        writeInt(random.nextLong(255))
                        stepOut()
                    }
                    endMacro()
                }
            }
            println(ion11Data.size)
            Files.write(Paths.get("polygons-$fileSuffix.11.ion"), ion11Data)
        }
         */

        /*
        // Ion 1.1 Simplified With tagless values
        Random(seed).let { random ->
            val point = MacroV8.build {
                struct {
                    fieldName("x")
                    variable(TaglessScalarType.INT)
                    fieldName("y")
                    variable(TaglessScalarType.INT)
                }
            }
            val polygon = MacroV8.build {
                struct {
                    fieldName("points")
                    variable()
                    fieldName("fill")
                    variable { nullValue() }
                }
            }
            val color = MacroV8.build {
                annotated("rgb", ::list) {
                    variable(TaglessScalarType.UINT_8)
                    variable(TaglessScalarType.UINT_8)
                    variable(TaglessScalarType.UINT_8)
                }
            }

            val ion11MData = writeBinary(symbolInliningStrategy = SymbolInliningStrategy.NEVER_INLINE, lengthPrefixStrategy = LengthPrefixStrategy.NEVER_PREFIXED) {
                repeat(2000) {
                    startMacro(polygon)
                    stepInTaglessElementList(point)
                    val n = random.nextInt(3, 100)
                    repeat(n) {
                        startMacro(point)
                        writeInt(random.nextLong().shr(8))
                        writeInt(random.nextLong().shr(8))
                        endMacro()
                    }
                    stepOut()
                    if (random.nextInt(10) == 0) {
                        absentArgument()
                    } else {
                        startMacro(color)
                        writeInt(random.nextLong(255))
                        writeInt(random.nextLong(255))
                        writeInt(random.nextLong(255))
                        endMacro()
                    }
                    endMacro()
                }
            }

            val r = BytecodeIonReader(ion11MData)
            val iter = ION.iterate(r)
            while (iter.hasNext()) {
                iter.next()
            }


            println(ion11MData.size)
            Files.write(Paths.get("polygons-$fileSuffix-tagless.11m.ion"), ion11MData)
        }
        */
    }



    @Test
    @Disabled("Already generated this data.")
    fun manySmallTopLevelSymbolsDegenerateCaseSampleData() {

        // val seed = System.currentTimeMillis()
        val seed = 1757482725331L
        println("Seed: $seed")

        val fileSuffix = "interned-delimited"

        val ion10Data: ByteArray
        // Ion 1.0 Binary
        Random(seed).let { random ->
            ion10Data = write10Binary {
                repeat(2000) {
                    repeat(10) {
                        val s = UUID.nameUUIDFromBytes(random.nextBytes(32)).toString()
                        writeSymbol(s)
                    }
                    flush()
                }
            }
            println(ion10Data.size)
            // Files.write(Paths.get("degenerate-symbols.10.ion"), ion11MData)
        }


        val ion11MData: ByteArray
        // Ion 1.1 Simplified
        Random(seed).let { random ->

            ion11MData = writeBinary(symbolInliningStrategy = SymbolInliningStrategy.ALWAYS_INLINE, lengthPrefixStrategy = LengthPrefixStrategy.NEVER_PREFIXED) {
                repeat(2000) {
                    repeat(10) {
                        val s = UUID.nameUUIDFromBytes(random.nextBytes(32)).toString()
                        writeSymbol(s)
                    }
                    flush()
                }
            }
            println(ion11MData.size)
            // Files.write(Paths.get("degenerate-symbols-$fileSuffix.11m.ion"), ion11MData)
        }

        val ION = IonSystemBuilder.standard().build()
        val iter10 = ION.iterate(ION.newReader(ion10Data))
        val iter11 = ION.iterate(BytecodeIonReader(ion11MData))

        while (iter10.hasNext() && iter11.hasNext()) {
            assertEquals(iter10.next(), iter11.next())
        }
        assertEquals(iter10.hasNext(), iter11.hasNext())


        val iter10b = ION.iterate(ION.newReader(ion10Data))
        val iter11b = ION.iterate(BytecodeIonReaderB(ByteArrayBytecodeGenerator(ion11MData)))

        while (iter10b.hasNext() && iter11b.hasNext()) {
            assertEquals(iter10b.next(), iter11b.next())
        }
        assertEquals(iter10b.hasNext(), iter11b.hasNext())


        val iter10c = ION.iterate(ION.newReader(ion10Data))
        val iter11c = ION.iterate(BytecodeIonReaderB(ByteArray10BytecodeGenerator(ion10Data)))

        while (iter10c.hasNext() && iter11c.hasNext()) {
            assertEquals(iter10c.next(), iter11c.next())
        }
        assertEquals(iter10c.hasNext(), iter11c.hasNext())


    }

    @Test
    @Disabled("Already generated this data.")
    fun manyStringsSampleData() {

        // val seed = System.currentTimeMillis()
        val seed = 1757482725331L
        println("Seed: $seed")

        val fileSuffix = "interned-delimited"

        val ion10Data: ByteArray
        // Ion 1.0 Binary
        Random(seed).let { random ->
            ion10Data = write10Binary {
                repeat(2000) {
                    stepIn(IonType.LIST)
                    repeat(10) {
                        val s = UUID.nameUUIDFromBytes(random.nextBytes(32)).toString()
                        writeString(s)
                    }
                    stepOut()
                }
            }
            println(ion10Data.size)
             Files.write(Paths.get("many-strings.10.ion"), ion10Data)
        }


        val ion11MData: ByteArray
        // Ion 1.1 Simplified
        Random(seed).let { random ->

            ion11MData = writeBinary(symbolInliningStrategy = SymbolInliningStrategy.ALWAYS_INLINE, lengthPrefixStrategy = LengthPrefixStrategy.NEVER_PREFIXED) {
                repeat(2000) {
                    stepIn(IonType.LIST)
                    repeat(10) {
                        val s = UUID.nameUUIDFromBytes(random.nextBytes(32)).toString()
                        writeString(s)
                    }
                    stepOut()
                }
            }
            println(ion11MData.size)
             Files.write(Paths.get("many-strings.11m.ion"), ion11MData)
        }

        val ION = IonSystemBuilder.standard().build()

        val iter10 = ION.iterate(ION.newReader(ion10Data))
        val iter11 = ION.iterate(BytecodeIonReader(ion11MData))

        while (iter10.hasNext() && iter11.hasNext()) {
            assertEquals(iter10.next(), iter11.next())
        }
        assertEquals(iter10.hasNext(), iter11.hasNext())
    }

    @Test
    fun readServiceLogLegacy10() {
        val bytes = Files.readAllBytes(Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_legacy.10n"))

        val ION = IonSystemBuilder.standard().build()
        val iter10 = ION.iterate(ION.newReader(bytes))
        val iter11 = ION.iterate(BytecodeIonReaderB(ByteArray10BytecodeGenerator(bytes)))

        while (iter10.hasNext() && iter11.hasNext()) {
            assertEquals(iter10.next(), iter11.next())
        }
        assertEquals(iter10.hasNext(), iter11.hasNext())
    }

    fun checkBinaryRoundTrip(ionText: String) {
        checkBinaryRoundTripVariant(ionText, SymbolInliningStrategy.NEVER_INLINE, LengthPrefixStrategy.ALWAYS_PREFIXED)
        checkBinaryRoundTripVariant(ionText, SymbolInliningStrategy.ALWAYS_INLINE, LengthPrefixStrategy.ALWAYS_PREFIXED)
        checkBinaryRoundTripVariant(ionText, SymbolInliningStrategy.NEVER_INLINE, LengthPrefixStrategy.NEVER_PREFIXED)
    }

    fun checkBinaryRoundTripVariant(ionText: String, symbolInliningStrategy: SymbolInliningStrategy, lengthPrefixStrategy: LengthPrefixStrategy) {
        val data = ION.singleValue(ionText)
        val binary = writeBinary(symbolInliningStrategy = symbolInliningStrategy, lengthPrefixStrategy = lengthPrefixStrategy) {
            data.writeTo(this)
        }
        val reader = BytecodeIonReader(binary)
        val actual = ION.iterate(reader).next()
        assertEquals(data, actual)
    }

    fun checkBinaryRoundTrip(expected: String, input: ByteArray) {
        val data = ION.singleValue(expected)
        val reader = BytecodeIonReader(input)
        val actual = ION.iterate(reader).next()
        assertEquals(data, actual)
    }
}
