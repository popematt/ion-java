package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.system.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpgradeToV8Test {

    @Test
    fun upgrade() {


        val reader = BytecodeIonReader(Files.readAllBytes(Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11m.10n")))

        val ION = IonSystemBuilder.standard().build()
        val iter11m = ION.iterate(reader)
        val iter11 = ION.iterate(ION.newReader(Files.readAllBytes(Paths.get("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11.10n"))))


        while (iter11.hasNext() && iter11m.hasNext()) {
            assertEquals(iter11.next(), iter11m.next())
        }
        assertEquals(iter11.hasNext(), iter11m.hasNext())
    }


    @Test
    fun serviceLogDataMed() {

        val writePath = Path("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11asdf.10n")
        val readPath = Path("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11.10n")

        Files.deleteIfExists(writePath)

        FileChannel.open(readPath, StandardOpenOption.READ).use { fileChannel ->
            val readBuffer: ByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            FileChannel.open(writePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { writeChannel ->

                println("Files are open")

                writeChannel.write(ByteBuffer.wrap(TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes(ServiceLogSmallData.ivm))))
                writeChannel.write(ByteBuffer.wrap(TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes(ServiceLogSmallData.symTab0))))
                writeChannel.write(ByteBuffer.wrap(TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes(ServiceLogSmallData.macTab0))))
                writeChannel.write(ByteBuffer.wrap(TestUtils.hexStringToByteArray(TestUtils.cleanCommentedHexBytes(ServiceLogSmallData.symTab0_b))))

                println("Total bytes written: ${writeChannel.position()}")

                readBuffer.position(1681)

                val array = ByteArray(1024 * 1024)
                val writeBuffer = ByteBuffer.wrap(array)

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
                    try {
                        UpgradeToV8.copyValue(readBuffer, writeBuffer)
                    } catch (e: Throwable) {
                        InspectorV8.inspect(array, range = 0..writeBuffer.position())
                        throw e
                    }
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

        InspectorV8.inspect(data, range = 18..19)

        println("Done Inspecting.")


        val reader = BytecodeIonReaderB(ByteArrayLabeledBytecodeGenerator(data))

        val expected = IonReaderBuilder.standard().build(Files.readAllBytes(readPath))

//        val consume: (Any?) -> Unit = ::print
        val consume: (Any?) -> Unit = { }

        var t = 0
        while (true) {
            val expectedNextType = expected.next()
            val nextType = reader.next()

            reader.typeAnnotations.forEach { consume(it); consume("::") }

            if (reader.isInStruct && nextType != null) {
                consume(reader.fieldName)
                consume(": ")

                assertEquals(expected.fieldNameSymbol, reader.fieldNameSymbol, "Unexpected field name at tlv=$t, depth=${reader.depth}")

            }

            assertEquals(expectedNextType, nextType, "Unexpected value type at tlv=$t, depth=${reader.depth}, bytecodeI=${reader.bytecodeI}")
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

                    if (expected.stringValue() != reader.stringValue()) {
                        Bytecode.debugString(reader.bytecode)

                        println("Unexpected symbol value at tlv=$t, depth=${reader.depth}")
                        println("Expected : ${expected.symbolValue()}")
                        println("Actual   : ${reader.symbolValue()}")
                        println("Last known offset : ${reader.lastSeenLabel()}")

                    }

                    assertEquals(expected.symbolValue(), reader.symbolValue(), "Unexpected symbol value at tlv=$t, depth=${reader.depth}")
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

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun serviceLogDataMedNoMacros() {

        val writePath = Path("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium-nomacros-pre.11m.10n")
        val readPath = Path("/Users/popematt/Library/Application Support/JetBrains/IntelliJIdea2024.3/scratches/service_log_medium.11.10n")

        Files.deleteIfExists(writePath)

        val r = IonReaderBuilder.standard().build(Files.readAllBytes(readPath))

        val baos = ByteArrayOutputStream()
        val w = IonEncodingVersion.ION_1_1.binaryWriterBuilder()
            .withLengthPrefixStrategy(LengthPrefixStrategy.NEVER_PREFIXED)
            .withSymbolInliningStrategy(SymbolInliningStrategy.NEVER_INLINE)
            .withSimplifiedTemplates()
            .build(baos)


        while (true) {
            val nextType = r.next()

            nextType ?: if (r.depth == 0) {
                    break
                } else {
                    r.stepOut()
                    w.stepOut()
                    continue
                }

            if (r.isInStruct) w.setFieldName(r.fieldName)

            w.setTypeAnnotations(*r.typeAnnotations)

            if (r.isNullValue) {
                w.writeNull(nextType)
            } else when (nextType) {
                IonType.NULL -> w.writeNull()
                IonType.BOOL -> w.writeBool(r.booleanValue())
                IonType.INT -> w.writeInt(r.longValue())
                IonType.FLOAT -> w.writeFloat(r.doubleValue())
                IonType.DECIMAL -> w.writeDecimal(r.decimalValue())
                IonType.TIMESTAMP -> w.writeTimestamp(r.timestampValue())
                IonType.SYMBOL -> w.writeSymbol(r.stringValue())
                IonType.STRING -> w.writeString(r.stringValue())
                IonType.CLOB -> TODO()
                IonType.BLOB -> TODO()
                IonType.LIST,
                IonType.SEXP,
                IonType.STRUCT -> {
                    r.stepIn()
                    w.stepIn(nextType)
                }
                IonType.DATAGRAM -> TODO("Unreachable")
            }
        }


//        w.writeValues(r)

        r.close()
        w.close()

        println(baos.size())

        val data = baos.toByteArray()
        println("Original Size: ${Files.size(readPath)} B")
        println("New Size: ${data.size} B")

        Files.write(writePath, data)


        val reader = BytecodeIonReaderB(ByteArrayLabeledBytecodeGenerator(data))
//        val reader = IonReaderBuilder.standard().build(data)

        val expected = IonReaderBuilder.standard().build(Files.readAllBytes(readPath))

//        val consume: (Any?) -> Unit = ::print
        val consume: (Any?) -> Unit = { }

        var t = 0
        while (true) {
            val expectedNextType = expected.next()
            val nextType = reader.next()

            reader.typeAnnotations.forEach { consume(it); consume("::") }

            if (reader.isInStruct && nextType != null) {
                consume(reader.fieldName)
                consume(": ")

                assertEquals(expected.fieldNameSymbol, reader.fieldNameSymbol, "Unexpected field name at tlv=$t, depth=${reader.depth}")

            }

            assertEquals(expectedNextType, nextType, "Unexpected value type at tlv=$t, depth=${reader.depth}")// + ", bytecodeI=${reader.bytecodeI}")
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

                    if (expected.stringValue() != reader.stringValue()) {
//                        Bytecode.debugString(reader.bytecode)

                        println("Unexpected symbol value at tlv=$t, depth=${reader.depth}")
                        println("Expected : ${expected.symbolValue()}")
                        println("Actual   : ${reader.symbolValue()}")
//                        println("Last known offset : ${reader.lastSeenLabel()}")

                    }

                    assertEquals(expected.symbolValue(), reader.symbolValue(), "Unexpected symbol value at tlv=$t, depth=${reader.depth}")
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

//        InspectorV8.inspect(data)

    }



}
