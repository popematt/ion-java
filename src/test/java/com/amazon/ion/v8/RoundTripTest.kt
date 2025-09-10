package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.IonEncodingVersion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.ExpressionBuilderDsl.Companion.templateBody
import com.amazon.ion.impl.macro.ParameterFactory.exactlyOneTagged
import com.amazon.ion.impl.macro.ParameterFactory.zeroOrOneTagged
import com.amazon.ion.impl.macro.ParameterFactory.zeroToManyTagged
import com.amazon.ion.system.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random
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
    @Disabled("Already generated this data.")
    fun polygonSampleData() {

        // val seed = System.currentTimeMillis()
        val seed = 1757482725331L
        println("Seed: $seed")

        val fileSuffix = "exprgroup-interned-delimited"

        /*
        Random(seed).let { random ->
            val ion11MData = write10Binary {
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
                        setTypeAnnotations("rgb")
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
            Files.write(Paths.get("polygons.10.ion"), ion11MData)
        }
         */
        /*
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
