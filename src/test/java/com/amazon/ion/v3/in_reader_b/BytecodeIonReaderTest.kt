package com.amazon.ion.v3.in_reader_b

import com.amazon.ion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.ParameterFactory.exactlyOneTagged
import com.amazon.ion.system.*
import com.amazon.ion.util.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.ExpressionBuilderDsl.Companion.templateBody
import com.amazon.ion.v3.impl_1_1.SystemMacro
import com.amazon.ion.v3.impl_1_1.TemplateDsl
import com.amazon.ion.v3.ion_reader_b.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BytecodeIonReaderTest {

    @OptIn(ExperimentalStdlibApi::class)
    fun toByteArray(value: String): ByteArray {
        val hexChunks = value.lines()
            .joinToString(" ") { it.takeWhile { c -> c != '|' }.trim() }
            .trim()
            .split(Regex("\\s+"))
        println(hexChunks)
        return ByteArray(hexChunks.size) { hexChunks[it].hexToUByte().toByte() }
    }

    @Test
    fun readBytecode() {
        val data = toByteArray("""
            E0 01 01 EA
            60
            B4                   | List
               61 01             | Int 1
               61 02             | Int 2
            B4
               61 03
               61 04             | Int 4
        """)
        val reader = BytecodeIonReader(data)

        assertEquals(IonType.INT, reader.next())
        assertEquals(0, reader.longValue())
        assertEquals(IonType.LIST, reader.next())
        reader.stepIn()
        assertEquals(IonType.INT, reader.next())
        assertEquals(1, reader.longValue())
        assertEquals(IonType.INT, reader.next())
        assertEquals(2, reader.longValue())
        assertEquals(null, reader.next())
        reader.stepOut()
        assertEquals(IonType.LIST, reader.next())
        reader.stepIn()
        assertEquals(IonType.INT, reader.next())
        assertEquals(3, reader.longValue())
        assertEquals(IonType.INT, reader.next())
        assertEquals(4, reader.longValue())
        assertEquals(null, reader.next())
        reader.stepOut()
        assertEquals(null, reader.next())


    }

    val ION = IonSystemBuilder.standard().build()

    @Test
    fun readSmallLog() {
        val path = Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_small.11.10n")

        // "/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_small.11.10n"

        val actual = ION.newDatagram()

        val bytes = Files.readAllBytes(path)


        BytecodeIonReader(bytes).use {
            val iter = ION.iterate(it)
            while (iter.hasNext()) {
                val value = iter.next()
                println(value)
                actual.add(value)
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
    fun constantScalarTemplateMacro() {
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                int(1)
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 03
                    61 04
                """)

        val r = BytecodeIonReader(data, additionalMacros = listOf(macro))
        r.expect {
            value("0")
            value("1")
            value("3")
        }
    }

    @Test
    fun constantEmptyListTemplateMacro() {
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                list {}
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("[]")
            value("3")
        }
    }

    @Test
    fun constantListTemplateMacro() {
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                list {
                    int(1)
                    int(2)
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("[1, 2]")
            value("3")
        }
    }

    @Test
    fun constantSexpTemplateMacro() {
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                sexp {
                    int(1)
                    int(2)
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("(1 2)")
            value("3")
        }
    }

    @Test
    fun constantStructTemplateMacro() {
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                struct {
                    fieldName("foo")
                    int(1)
                    fieldName("bar")
                    int(2)
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("{foo: 1, bar: 2}")
            value("3")
        }
    }

    @Test
    fun constantNestedInvocationTemplateMacro() {
        val macro2 = MacroV2(
            signature = listOf(),
            body = templateBody {
                int(2)
            }
        )
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                list {
                    int(1)
                    macro(macro2) {}
                    int(3)
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 04
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro, macro2)).expect {
            value("0")
            value("[1, 2, 3]")
            value("4")
        }
    }

    @Test
    fun nestedInvocationTemplateMacroWithOneVariable() {
        val macro2 = MacroV2(
            signature = listOf(exactlyOneTagged("x")),
            body = templateBody {
                list {
                    variable("x", 0)
                }
            }
        )
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                list {
                    int(1)
                    macro(macro2) { int(2) }
                    int(3)
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 04
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro, macro2)).expect {
            value("0")
            value("[1, [2], 3]")
            value("4")
        }
    }

    @Test
    fun nestedInvocationTemplateMacroWithPassedThroughVariable() {
        val macro2 = MacroV2(
            signature = listOf(exactlyOneTagged("x")),
            body = templateBody {
                list {
                    variable("x", 0)
                }
            }
        )
        val macro = MacroV2(
            signature = listOf(exactlyOneTagged("y")),
            body = templateBody {
                list {
                    int(1)
                    macro(macro2) { variable("y", 0) }
                    int(3)
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 61 02
                    61 04
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro, macro2)).expect {
            value("0")
            value("[1, [2], 3]")
            value("4")
        }
    }

    @Test
    fun invocationWithArgThatIsAnEExp() {
        val macro2 = MacroV2(
            signature = listOf(exactlyOneTagged("x")),
            body = templateBody {
                list {
                    variable("x", 0)
                }
            }
        )
        val macro = MacroV2(
            signature = listOf(exactlyOneTagged("y")),
            body = templateBody {
                list {
                    int(1)
                    variable("y", 0)
                    int(3)
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 19 61 02
                    61 04
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro, macro2)).expect {
            value("0")
            value("[1, [2], 3]")
            value("4")
        }
    }

    @Test
    fun simpleInvocationOfDefault() {
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                list {
                    macro(SystemMacro.Default) {
                        int(2)
                        int(3)
                    }
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 04
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("[2]")
            value("4")
        }
    }

    @Test
    fun simpleInvocationOfDefaultThatUsesTheDefaultValue() {
        val macro = MacroV2(
            signature = listOf(),
            body = templateBody {
                list {
                    macro(SystemMacro.Default) {
                        expressionGroup {  }
                        int(3)
                    }
                }
            }
        )
        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18
                    61 04
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("[3]")
            value("4")
        }
    }

    @Test
    fun complexConstantMacro() {
        val macro0 = TemplateMacro(
            signature = listOf(),
            body = com.amazon.ion.impl.macro.ExpressionBuilderDsl.templateBody {
                struct {
                    fieldName("StartTime"); timestamp(Timestamp.valueOf("2020-11-05T07:18:59.968+00:00"))
                    fieldName("EndTime"); timestamp(Timestamp.valueOf("2020-11-05T07:18:59+00:00"))
                    fieldName("Marketplace"); symbol("us-east-1")
                    fieldName("Program"); symbol("LambdaFrontendInvokeService")
                    fieldName("Time"); float(3.267017e6)
                    fieldName("Operation"); symbol("ReserveSandbox2")
                    fieldName("Properties")
                    struct {
                        fieldName("FrontendInstanceId"); symbol("i-0505be8aa9972815b")
                        fieldName("AccountId"); symbol("103403959176")
                        fieldName("RequestId"); symbol("f0bc3259-06e9-5ccb-96f1-6a44af76d4aa")
                        fieldName("PID"); symbol("2812@ip-10-0-16-227")
                        fieldName("WorkerId"); symbol("i-0c891b196c563ba4c")
                        fieldName("FrontendInternalAZ"); symbol("USMA7")
                        fieldName("WorkerManagerInstanceId"); symbol("i-070b7692b9a6aba7e")
                        fieldName("SandboxId"); symbol("61fa4c30-d51d-40bb-82e0-a6195e27ca10")
                        fieldName("Thread"); symbol("coral-orchestrator-136")
                        fieldName("FrontendPublicAZ"); symbol("us-east-1a")
                        fieldName("WorkerConnectPort"); symbol("2503")
                    }
                    fieldName("Timing")
                    list {
                        struct {
                            fieldName("Name"); symbol("Time:Warm")
                            fieldName("Sum"); float(3.267271041870117e0)
                            fieldName("Unit"); symbol("ms")
                            fieldName("Count"); int( 1)
                        }
                    }
                    fieldName("Counters")
                    list {
                        struct {
                            fieldName("Name"); symbol("Attempt")
                            fieldName("Sum"); float(0e0)
                            fieldName("Unit"); symbol("")
                            fieldName("Count"); int( 1)
                        }
                        struct {
                            fieldName("Name"); symbol("Success")
                            fieldName("Sum"); float(1e0)
                            fieldName("Unit"); symbol("")
                            fieldName("Count"); int( 1)
                        }
                    }
                    fieldName("Metrics")
                    list {
                        struct {
                            fieldName("Name"); symbol("Error")
                            fieldName("Samples")
                            list {
                                struct {
                                    fieldName("Value")
                                    float(0e0)
                                    fieldName("Repeat")
                                    int(1)
                                }
                            }
                            fieldName("Unit")
                            symbol("")
                        }
                        struct {
                            fieldName("Name"); symbol("Fault")
                            fieldName("Samples")
                            list {
                                struct {
                                    fieldName("Value")
                                    float(0e0)
                                    fieldName("Repeat")
                                    int(1)
                                }
                            }
                            fieldName("Unit")
                            symbol("")
                        }
                    }
                }
            }
        )


        /*
         * {
         *   StartTime: 2020-11-05T07:18:59.968+00:00,
         *   EndTime: 2020-11-05T07:18:59+00:00,
         *   Marketplace: 'us-east-1',
         *   Program: LambdaFrontendInvokeService,
         *   Time: 3.267017e6,
         *   Operation: ReserveSandbox2,
         *   Properties: {
         *     FrontendInstanceId: 'i-0505be8aa9972815b',
         *     AccountId: '103403959176',
         *     RequestId: 'f0bc3259-06e9-5ccb-96f1-6a44af76d4aa',
         *     PID: '2812@ip-10-0-16-227',
         *     WorkerId: 'i-0c891b196c563ba4c',
         *     FrontendInternalAZ: USMA7,
         *     WorkerManagerInstanceId: 'i-070b7692b9a6aba7e',
         *     SandboxId: '61fa4c30-d51d-40bb-82e0-a6195e27ca10',
         *     Thread: 'coral-orchestrator-136',
         *     FrontendPublicAZ: 'us-east-1a',
         *     WorkerConnectPort: '2503',
         *   },
         *   Timing: [
         *     {
         *       Name: 'Time:Warm',
         *       Sum: 3.267271041870117e0,
         *       Unit: ms,
         *       Count: 1,
         *     },
         *   ],
         *   Counters: [
         *     {
         *       Name: Attempt,
         *       Sum: 0e0,
         *       Unit: '',
         *       Count: 1,
         *     },
         *     {
         *       Name: Success,
         *       Sum: 1e0,
         *       Unit: '',
         *       Count: 1,
         *     },
         *   ],
         *   Metrics: [
         *     {
         *       Name: Error,
         *       Samples: [
         *         {
         *           Value: 0e0,
         *           Repeat: 1,
         *         },
         *       ],
         *       Unit: '',
         *     },
         *     {
         *       Name: Fault,
         *       Samples: [
         *         {
         *           Value: 0e0,
         *           Repeat: 1,
         *         },
         *       ],
         *       Unit: '',
         *     },
         *   ],
         * }
         * {
         *   StartTime: 2020-11-05T07:18:59.971+00:00,
         *   EndTime: 2020-11-05T07:18:59+00:00,
         *   Marketplace: 'us-east-1',
         *   Program: LambdaFrontendInvokeService,
         *   Time: 3e3,
         *   Operation: 'WSKF:GetLatestKeys',
         *   Properties: {
         *     FrontendInstanceId: 'i-0505be8aa9972815b',
         *     FrontendPublicAZ: 'us-east-1a',
         *     PID: '2812@ip-10-0-16-227',
         *     FrontendInternalAZ: USMA7,
         *   },
         *   Timing: [
         *     {
         *       Name: Latency,
         *       Sum: 2.0000000949949026e-3,
         *       Unit: ms,
         *       Count: 1,
         *     },
         *   ],
         * }
         */

        val baos = ByteArrayOutputStream()
        val writer = IonEncodingVersion.ION_1_1.binaryWriterBuilder().apply {
            setLengthPrefixStrategy(LengthPrefixStrategy.ALWAYS_PREFIXED)
            setSymbolInliningStrategy(SymbolInliningStrategy.NEVER_INLINE)
        }.build(baos) as MacroAwareIonWriter

        repeat(1) {
            writer.startMacro(macro0)
            writer.endMacro()
        }
        writer.close()

        val bytes = baos.toByteArray()

        println(bytes.toPrettyHexString())

//                FileChannel.open(File("/Users/popematt/constant_macro.10n").toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { fileChannel ->
//                    fileChannel.write(ByteBuffer.wrap(bytes))
//                }

        BytecodeIonReader(bytes).expect {
            value("""
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
                    """)
        }
    }

    @Test
    fun constant_macro_10n() {
//                FileChannel.open(File("/Users/popematt/constant_macro.10n").toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { fileChannel ->
//                    fileChannel.write(ByteBuffer.wrap(bytes))
//                }
        val bytes = Files.readAllBytes(Paths.get("/Users/popematt/constant_macro.10n"))

        val reader = BytecodeIonReader(bytes)
        val iter = ION.iterate(reader)
        while (iter.hasNext()) {
            println(iter.next())
        }
    }


    @Test
    fun complexNestedInvocationConstantMacro() {
        val propertiesMacro = TemplateMacro(
            signature = listOf(),
            body = com.amazon.ion.impl.macro.ExpressionBuilderDsl.templateBody {
                struct {
                    fieldName("FrontendInstanceId"); symbol("i-0505be8aa9972815b")
                    fieldName("AccountId"); symbol("103403959176")
                    fieldName("RequestId"); symbol("f0bc3259-06e9-5ccb-96f1-6a44af76d4aa")
                    fieldName("PID"); symbol("2812@ip-10-0-16-227")
                    fieldName("WorkerId"); symbol("i-0c891b196c563ba4c")
                    fieldName("FrontendInternalAZ"); symbol("USMA7")
                    fieldName("WorkerManagerInstanceId"); symbol("i-070b7692b9a6aba7e")
                    fieldName("SandboxId"); symbol("61fa4c30-d51d-40bb-82e0-a6195e27ca10")
                    fieldName("Thread"); symbol("coral-orchestrator-136")
                    fieldName("FrontendPublicAZ"); symbol("us-east-1a")
                    fieldName("WorkerConnectPort"); symbol("2503")
                }
            }
        )

        val timingMacro = TemplateMacro(
            signature = listOf(exactlyOneTagged("name"), exactlyOneTagged("value")),
            body = com.amazon.ion.impl.macro.ExpressionBuilderDsl.templateBody {
                struct {
                    fieldName("Name"); variable(0)
                    fieldName("Sum"); variable(1)
                    fieldName("Unit"); symbol("ms")
                    fieldName("Count"); int( 1)
                }
            }
        )

        val macro0 = TemplateMacro(
            signature = listOf(),
            body = com.amazon.ion.impl.macro.ExpressionBuilderDsl.templateBody {
                struct {
                    fieldName("StartTime"); timestamp(Timestamp.valueOf("2020-11-05T07:18:59.968+00:00"))
                    fieldName("EndTime"); timestamp(Timestamp.valueOf("2020-11-05T07:18:59+00:00"))
                    fieldName("Marketplace"); symbol("us-east-1")
                    fieldName("Program"); symbol("LambdaFrontendInvokeService")
                    fieldName("Time"); float(3.267017e6)
                    fieldName("Operation"); symbol("ReserveSandbox2")
                    fieldName("Properties")
                    macro(propertiesMacro) {}

                    fieldName("Timing")
                    list {
                        macro(timingMacro) {
                            symbol("Time:Warm")
                            float(3.267271041870117e0)
                        }
                    }
                    fieldName("Counters")
                    list {
                        struct {
                            fieldName("Name"); symbol("Attempt")
                            fieldName("Sum"); float(0e0)
                            fieldName("Unit"); symbol("")
                            fieldName("Count"); int( 1)
                        }
                        struct {
                            fieldName("Name"); symbol("Success")
                            fieldName("Sum"); float(1e0)
                            fieldName("Unit"); symbol("")
                            fieldName("Count"); int( 1)
                        }
                    }
                    fieldName("Metrics")
                    list {
                        struct {
                            fieldName("Name"); symbol("Error")
                            fieldName("Samples")
                            list {
                                struct {
                                    fieldName("Value")
                                    float(0e0)
                                    fieldName("Repeat")
                                    int(1)
                                }
                            }
                            fieldName("Unit")
                            symbol("")
                        }
                        struct {
                            fieldName("Name"); symbol("Fault")
                            fieldName("Samples")
                            list {
                                struct {
                                    fieldName("Value")
                                    float(0e0)
                                    fieldName("Repeat")
                                    int(1)
                                }
                            }
                            fieldName("Unit")
                            symbol("")
                        }
                    }
                }
            }
        )


        /*
         * {
         *   StartTime: 2020-11-05T07:18:59.971+00:00,
         *   EndTime: 2020-11-05T07:18:59+00:00,
         *   Marketplace: 'us-east-1',
         *   Program: LambdaFrontendInvokeService,
         *   Time: 3e3,
         *   Operation: 'WSKF:GetLatestKeys',
         *   Properties: {
         *     FrontendInstanceId: 'i-0505be8aa9972815b',
         *     FrontendPublicAZ: 'us-east-1a',
         *     PID: '2812@ip-10-0-16-227',
         *     FrontendInternalAZ: USMA7,
         *   },
         *   Timing: [
         *     {
         *       Name: Latency,
         *       Sum: 2.0000000949949026e-3,
         *       Unit: ms,
         *       Count: 1,
         *     },
         *   ],
         * }
         */

        /*
         * {
         *   StartTime: 2020-11-05T07:18:59.968+00:00,
         *   EndTime: 2020-11-05T07:18:59+00:00,
         *   Marketplace: 'us-east-1',
         *   Program: LambdaFrontendInvokeService,
         *   Time: 3.267017e6,
         *   Operation: ReserveSandbox2,
         *   Properties: {
         *     FrontendInstanceId: 'i-0505be8aa9972815b',
         *     AccountId: '103403959176',
         *     RequestId: 'f0bc3259-06e9-5ccb-96f1-6a44af76d4aa',
         *     PID: '2812@ip-10-0-16-227',
         *     WorkerId: 'i-0c891b196c563ba4c',
         *     FrontendInternalAZ: USMA7,
         *     WorkerManagerInstanceId: 'i-070b7692b9a6aba7e',
         *     SandboxId: '61fa4c30-d51d-40bb-82e0-a6195e27ca10',
         *     Thread: 'coral-orchestrator-136',
         *     FrontendPublicAZ: 'us-east-1a',
         *     WorkerConnectPort: '2503',
         *   },
         *   Timing: [
         *     {
         *       Name: 'Time:Warm',
         *       Sum: 3.267271041870117e0,
         *       Unit: ms,
         *       Count: 1,
         *     },
         *   ],
         *   Counters: [
         *     {
         *       Name: Attempt,
         *       Sum: 0e0,
         *       Unit: '',
         *       Count: 1,
         *     },
         *     {
         *       Name: Success,
         *       Sum: 1e0,
         *       Unit: '',
         *       Count: 1,
         *     },
         *   ],
         *   Metrics: [
         *     {
         *       Name: Error,
         *       Samples: [
         *         {
         *           Value: 0e0,
         *           Repeat: 1,
         *         },
         *       ],
         *       Unit: '',
         *     },
         *     {
         *       Name: Fault,
         *       Samples: [
         *         {
         *           Value: 0e0,
         *           Repeat: 1,
         *         },
         *       ],
         *       Unit: '',
         *     },
         *   ],
         * }
         * {
         *   StartTime: 2020-11-05T07:18:59.971+00:00,
         *   EndTime: 2020-11-05T07:18:59+00:00,
         *   Marketplace: 'us-east-1',
         *   Program: LambdaFrontendInvokeService,
         *   Time: 3e3,
         *   Operation: 'WSKF:GetLatestKeys',
         *   Properties: {
         *     FrontendInstanceId: 'i-0505be8aa9972815b',
         *     FrontendPublicAZ: 'us-east-1a',
         *     PID: '2812@ip-10-0-16-227',
         *     FrontendInternalAZ: USMA7,
         *   },
         *   Timing: [
         *     {
         *       Name: Latency,
         *       Sum: 2.0000000949949026e-3,
         *       Unit: ms,
         *       Count: 1,
         *     },
         *   ],
         * }
         */

        val baos = ByteArrayOutputStream()
        val writer = IonEncodingVersion.ION_1_1.binaryWriterBuilder().apply {
            setLengthPrefixStrategy(LengthPrefixStrategy.ALWAYS_PREFIXED)
            setSymbolInliningStrategy(SymbolInliningStrategy.NEVER_INLINE)
        }.build(baos) as MacroAwareIonWriter

        repeat(1) {
            writer.startMacro(macro0)
            writer.endMacro()
        }
        writer.close()

        val bytes = baos.toByteArray()

//                FileChannel.open(File("/Users/popematt/constant_macro.10n").toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { fileChannel ->
//                    fileChannel.write(ByteBuffer.wrap(bytes))
//                }

        val fileBytes = Files.readAllBytes(File("/Users/popematt/constant_macro.10n").toPath())
        BytecodeIonReader(fileBytes).expect {
            repeat(2) {
                value(
                    """
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
                    """
                )
            }
        }

        BytecodeIonReader(bytes).expect {
            value("""
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
                    """)
        }
    }

    @Test
    fun templateMacroWithOneVariable() {
        val macro = template("foo") { variable(0) }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 61 01
                    61 02
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("1")
            value("2")
        }
    }

    @Test
    fun templateMacroWithMultipleVariables() {
        val macro = MacroV2(exactlyOneTagged("foo"), exactlyOneTagged("bar")) {
            struct {
                fieldName("foo")
                variable(0)
                fieldName("bar")
                variable(1)
            }
        }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 61 01 61 02
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("{ foo:1, bar: 2}")
            value("3")
        }
    }

    @Test
    fun templateMacroWithOneVariableAndDelimitedListArgument() {
        val macro = template("foo") { variable(0) }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 F1 61 01 F0
                    61 03
                """)

        val r = BytecodeIonReader(data, additionalMacros = listOf(macro))
        r.expect {
            value("0")
            value("[1]")
            value("3")
        }
    }

    @Test
    fun templateMacroWithOneVariableAndPrefixedListArgument() {
        val macro = template("foo") { variable(0) }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 B2 61 02
                    61 03
                """)

        val r = BytecodeIonReader(data, additionalMacros = listOf(macro))
        r.expect {
            value("0")
            value("[2]")
            value("3")
        }
    }

    @Test
    fun templateMacroWithListOfOneVariable() {
        val macro = template("foo") { list { variable(0) } }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 61 01
                    18 61 02
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("[1]")
            value("[2]")
            value("3")
        }
    }


    @Test
    fun templateMacroWithOneVariableAndSexpArguments() {
        val macro = template("foo") { variable(0) }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 F2 61 01 F0
                    18 C2 61 02
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("(1)")
            value("(2)")
            value("3")
        }
    }

    @Test
    fun templateMacroWithOneVariableAndDelimitedSexpArgument() {
        val macro = template("foo") { variable(0) }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 F2 61 01 F0
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("(1)")
            value("3")
        }
    }

    @Test
    fun templateMacroWithOneVariableAndPrefixedSexpArgument() {
        val macro = template("foo") { variable(0) }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 C2 61 02
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("(2)")
            value("3")
        }
    }


    @Test
    fun templateMacroWithOneVariableAndPrefixedStructArgument() {
        val macro = template("foo") { variable(0) }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 D3 09 61 01
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("{name:1}")
            value("3")
        }
    }

    @Test
    fun templateMacroWithOneVariableAndDelimitedStructArgument() {
        val macro = template("foo") { variable(0) }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 F3 FB 66 6F 6F 61 02 01 F0
                    61 03
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro)).expect {
            value("0")
            value("{foo:2}")
            value("3")
        }
    }

    @Test
    fun templateMacroWithOneVariableAndEExpAsArgument() {
        val macro = template("foo") { variable(0) }
        val macro2 = template("bar") { list { variable(0) } }

        val data = toByteArray("""
                    E0 01 01 EA
                    60
                    18 19 61 01
                    61 02
                """)

        BytecodeIonReader(data, additionalMacros = listOf(macro, macro2)).expect {
            value("0")
            value("[1]")
            value("2")
        }
    }


    private fun BytecodeIonReader.expect(block: Iterator<IonValue>.() -> Unit) {
        use {
            val iter = ION.iterate(this)
            iter.block()
        }
    }

    private fun Iterator<IonValue>.value(ion: String) {
        val value = next()
        val expected = ION.singleValue(ion)
        val actual = ION.singleValue(value.toString())
        assertEquals(expected, actual)
    }

    private fun template(vararg parameters: String, body: TemplateDsl.() -> Unit): MacroV2 {
        val signature = parameters.map {
            val cardinality = Macro.ParameterCardinality.fromSigil("${it.last()}")
            if (cardinality == null) {
                Macro.Parameter(it, Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)
            } else {
                Macro.Parameter(it.dropLast(1), Macro.ParameterEncoding.Tagged, cardinality)
            }
        }
        return MacroV2(signature, body)
    }
}
