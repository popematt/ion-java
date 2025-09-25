package com.amazon.ion.v8

import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.macro.*
import java.nio.ByteBuffer

/**
 * Converts from current Ion 1.1 spec to simplified templates spec.
 *
 * WARNING, This was used for converting a specific piece of data. It is not yet suitable for general purpose use.
 * However, it may be useful as the basis for a general purpose converter at some point.
 */
object UpgradeToV8 {

    private fun ByteBuffer.transferN(n: Int, to: ByteBuffer) { repeat(n) { to.put(get()) } }


    @OptIn(ExperimentalStdlibApi::class)
    fun copyValue(readBuffer: ByteBuffer, writeBuffer: ByteBuffer): Boolean {
        fun copyBytes(n: Int) = readBuffer.transferN(n, writeBuffer)
        fun writeOpcodeAndCopyBytes(opcode: Byte, nBytes: Int) {
            writeBuffer.put(opcode)
            copyBytes(nBytes)
        }


        if (writeBuffer.position() > 9480) {
            println()
        }


        val byte = readBuffer.get()

        when (byte.toInt() and 0xFF) {
            // one
            0x00 -> writeBuffer.put(byte)
            // entry [all required parameters]
            // Declared: start_time end_time marketplace program time operation properties timing          counters levels   service_metrics metrics groups
            // Used:     start_time end_time marketplace program time operation properties service_metrics timing   counters levels          metrics groups

            0x01 -> {
                writeBuffer.put(byte)
                repeat(7) { copyValue(readBuffer, writeBuffer) }

                val intermediateBuffer = ByteBuffer.wrap(ByteArray(1024*64))

                repeat(3) { copyValue(readBuffer, intermediateBuffer) }
                repeat(1) { copyValue(readBuffer, writeBuffer) }
                intermediateBuffer.flip()
                writeBuffer.put(intermediateBuffer)
                repeat(2) { copyValue(readBuffer, writeBuffer) }
            }
            // summary (name sum unit? count?)
            0x02 -> {
                writeBuffer.put(byte)
                // presence bits
                val presenceBits = readBuffer.get().toInt() and 0xFF
                copyValue(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)

                when (presenceBits and 0b11) {
                    0b00 -> writeBuffer.put(0xE8.toByte())
                    0b01 -> copyValue(readBuffer, writeBuffer)
                    else -> TODO("Unsupported presence bits")
                }

                when (presenceBits and 0b1100) {
                    0b0000 -> writeBuffer.put(0xE8.toByte())
                    0b0100 -> copyValue(readBuffer, writeBuffer)
                    else -> TODO("Unsupported presence bits")
                }
            }
            // group (timing? counters? levels? service_name? operation? attributes?)
            //  but the order of use is: service_name, operation, attributes, timing, counters, levels
            //
            0x03 -> {
                writeBuffer.put(byte)
                val presenceBitsLSB = readBuffer.get().toInt() and 0xFF
                val presenceBitsMSB = readBuffer.get().toInt() and 0xFF
                if (presenceBitsLSB != 0b01010101 || presenceBitsMSB != 0b0101 ) {
                    TODO("Missing optional parameters for `group` macro")
                }

                val intermediateBuffer = ByteBuffer.wrap(ByteArray(1024))

                repeat(3) { copyValue(readBuffer, intermediateBuffer) }
                repeat(3) { copyValue(readBuffer, writeBuffer) }
                intermediateBuffer.flip()
                writeBuffer.put(intermediateBuffer)



//                repeat(6) { copyValue(readBuffer, writeBuffer) }
            }
            // sample (value repeat?)
            0x04 -> {
                writeBuffer.put(byte)
                // presence bits
                val presenceBits = readBuffer.get().toInt() and 0xFF
                copyValue(readBuffer, writeBuffer)
                when (presenceBits and 0b11) {
                    0b00 -> writeBuffer.put(0xE8.toByte())
                    0b01 -> copyValue(readBuffer, writeBuffer)
                    else -> TODO("Unsupported presence bits")
                }
            }
            // dimension (class instance)
            0x05 -> {
                writeBuffer.put(byte)
                copyValue(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)
            }
            // metric (name value unit? dimensions?)
            0x06 -> TODO("metric macro")
            // metric_single (name value repeat? unit? dimensions?)
            0x07 -> {
                writeBuffer.put(byte)
                // presence bits
                val presenceBits = readBuffer.get().toInt() and 0xFF
                copyValue(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)

                when (presenceBits and 0b11) {
                    0b00 -> writeBuffer.put(0xE8.toByte())
                    0b01 -> copyValue(readBuffer, writeBuffer)
                    else -> TODO("Unsupported presence bits")
                }
                when (presenceBits and 0b1100) {
                    0b0000 -> writeBuffer.put(0xE8.toByte())
                    0b0100 -> copyValue(readBuffer, writeBuffer)
                    else -> TODO("Unsupported presence bits")
                }
                when (presenceBits and 0b110000) {
                    0b000000 -> writeBuffer.put(0xE8.toByte())
                    0b010000 -> copyValue(readBuffer, writeBuffer)
                    else -> TODO("Unsupported presence bits")
                }
            }
            // summary_ONE (name value count)
            0x08 -> {
                writeBuffer.put(byte)
                copyValue(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)
            }
            // summary_ms (name value count?)
            0x09 -> {
                writeBuffer.put(byte)
                // presence bits
                val presenceBits = readBuffer.get().toInt() and 0xFF
                copyValue(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)
                when (presenceBits and 0b11) {
                    0b00 -> writeBuffer.put(0xE8.toByte())
                    0b01 -> copyValue(readBuffer, writeBuffer)
                    else -> TODO("Unsupported presence bits")
                }
            }
            // summary_B (name value count?)
            0x0A -> {
                writeBuffer.put(byte)
                // presence bits
                val presenceBits = readBuffer.get().toInt() and 0xFF
                copyValue(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)
                when (presenceBits and 0b11) {
                    0b00 -> writeBuffer.put(0xE8.toByte())
                    0b01 -> copyValue(readBuffer, writeBuffer)
                    else -> TODO("Unsupported presence bits")
                }
            }
            // summary_0 (name)
            0x0B -> {
                writeBuffer.put(byte)
                copyValue(readBuffer, writeBuffer)
            }
            // summary_1 (name)
            0x0C -> {
                writeBuffer.put(byte)
                copyValue(readBuffer, writeBuffer)
            }
            // summary_2 (name)
            0x0D -> {
                writeBuffer.put(byte)
                copyValue(readBuffer, writeBuffer)
            }
            // summary_3 (name)
            0x0E -> {
                writeBuffer.put(byte)
                copyValue(readBuffer, writeBuffer)
            }
            // sample0 ()
            0x0F -> writeBuffer.put(byte)
            // sample1 ()
            0x10 -> writeBuffer.put(byte)
            // sample2 ()
            0x11 -> writeBuffer.put(byte)
            // sample3 ()
            0x12 -> writeBuffer.put(byte)



            in 0x00..0x3F -> {

                TODO("Unknown macro: ${byte.toHexString()} at ${readBuffer.position()}")
            }

            in 0x40..0x4f -> TODO()
            in 0x50..0x5f -> TODO()

            in 0x60..0x68 -> writeOpcodeAndCopyBytes(byte, nBytes = byte.toInt() and 0xF)
            0x69 -> TODO()
            0x6A -> writeBuffer.put(byte)
            0x6B -> writeOpcodeAndCopyBytes(byte, 2)
            0x6C -> writeOpcodeAndCopyBytes(byte, 4)
            0x6D -> writeOpcodeAndCopyBytes(byte, 8)
            0x6E, 0x6F -> writeBuffer.put(byte)

            in 0x70..0x7F -> writeOpcodeAndCopyBytes(byte, nBytes = byte.toInt() and 0xF)

            in 0x80..0x8C -> {
                writeBuffer.put(byte)
                val n = TimestampHelper.lengthOfShortTimestamp(byte.toInt() and 0xFF)
                copyBytes(n)
            }

            0x90 -> {
                writeBuffer.put(0xF9.toByte())
                writeBuffer.put(0x01)
            }
            in 0x91..0x9F -> writeOpcodeAndCopyBytes(byte, nBytes = byte.toInt() and 0xF)
            0xA0 -> {
                writeBuffer.put(0xFA.toByte())
                writeBuffer.put(0x01)
            }
            in 0xA1..0xAF -> writeOpcodeAndCopyBytes(byte, nBytes = byte.toInt() and 0xF)

            in 0xB0..0xBF,
            in 0xC0..0xCF -> {
                // TODO: The length could change. :/
                val length = byte.toInt() and 0xF
                val end = readBuffer.position() + length
                while (readBuffer.position() < end) {
                    copyValue(readBuffer, writeBuffer)
                }
                TODO()
            }

            0xD0 -> {
                writeBuffer.put(byte)
                val length = byte.toInt() and 0xF
                copyBytes(length)
            }
            in 0xD2..0xDF -> {
                TODO("Prefixed struct")
            }



            // IVM
            0xE0 -> {
                writeBuffer.put(byte)
                copyBytes(3)
            }
            // 1-3	Symbols with symbol address
            0xE1 -> {
                writeBuffer.put(0xA0.toByte())
                val sid = 9 + (readBuffer.get().toInt() and 0xFF)
                FlexInt.writeFlexIntOrUIntInto(writeBuffer, sid.toLong())
            }
            0xE2 -> {
                writeBuffer.put(0xA0.toByte())
                val lsb = readBuffer.get().toInt() and 0xFF
                val msb = readBuffer.get().toInt() and 0xFF
                val sid = 256 + (msb.shl(8).or(lsb))
                FlexInt.writeFlexIntOrUIntInto(writeBuffer, sid.toLong() + 9L)
            }
            0xE3 -> {
                TODO()
            }

            // 4-6 Annotations with symbol address
            0xE4 -> {
                writeBuffer.put(0x8D.toByte())
                val sid = 9 + IntHelper.readFlexUInt(readBuffer)
                FlexInt.writeFlexIntOrUIntInto(writeBuffer, sid.toLong())
                copyValue(readBuffer, writeBuffer)
            }
            0xE5 -> {
                writeBuffer.put(0x8D.toByte())
                val sid0 = 9 + IntHelper.readFlexUInt(readBuffer)
                FlexInt.writeFlexIntOrUIntInto(writeBuffer, sid0.toLong())
                writeBuffer.put(0x8D.toByte())
                val sid1 = 9 + IntHelper.readFlexUInt(readBuffer)
                FlexInt.writeFlexIntOrUIntInto(writeBuffer, sid1.toLong())
                copyValue(readBuffer, writeBuffer)

            }
            0xE6 -> {
                val annEnd = IntHelper.readFlexUInt(readBuffer) + readBuffer.position()
                while (readBuffer.position() < annEnd) {
                    writeBuffer.put(0x8D.toByte())
                    val sid0 = 9 + IntHelper.readFlexUInt(readBuffer)
                    FlexInt.writeFlexIntOrUIntInto(writeBuffer, sid0.toLong())
                }
                copyValue(readBuffer, writeBuffer)
            }
//                        7-9	Annotations with FlexSym text
            0xE7 -> {
                copyFlexSymAnnotation(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)
            }
            0xE8 -> {
                copyFlexSymAnnotation(readBuffer, writeBuffer)
                copyFlexSymAnnotation(readBuffer, writeBuffer)
                copyValue(readBuffer, writeBuffer)
            }
            0xE9 -> {
                TODO("Multiple flexSym annotations")
            }

            0xEA -> writeBuffer.put(byte)

            0xEB -> {
                writeBuffer.put(byte)
                copyBytes(1)
            }

            0xEC -> writeBuffer.put(0xEE.toByte())
            0xED -> {
                writeBuffer.put(0xEF.toByte())
                val n = IntHelper.getLengthPlusValueOfFlexUIntAt(readBuffer, readBuffer.position())
                copyBytes(n)
            }
            0xEE -> {
                TODO("System symbol")
            }
            0xEF -> {
                val macid = readBuffer.get().toInt() and 0xFF
                when (macid) {
                    SystemMacro.SetSymbols.id.toInt() -> {
                        writeBuffer.put(0xE1.toByte())
                        val presenceBits = readBuffer.get().toInt() and 0xFF
                        when (presenceBits) {
                            0 -> {
                                writeBuffer.put(0xF0.toByte())
                            }
                            1 -> {
                                copyValue(readBuffer, writeBuffer)
                                writeBuffer.put(0xF0.toByte())
                            }
                            2 -> {
                                val exprGroupLength = IntHelper.readFlexUInt(readBuffer)
                                if (exprGroupLength == 0) {
                                    while (copyValue(readBuffer, writeBuffer)) {
                                        // continue
                                    }
                                } else {
                                    val end = exprGroupLength + readBuffer.position()
                                    while (readBuffer.position() < end) {
                                        copyValue(readBuffer, writeBuffer)
                                    }
                                    writeBuffer.put(0xF0.toByte())
                                }
                            }
                        }
                    }
                    SystemMacro.AddSymbols.id.toInt() -> {
                        writeBuffer.put(0xE2.toByte())
                        val presenceBits = readBuffer.get().toInt() and 0xFF
                        when (presenceBits) {
                            0 -> {
                                writeBuffer.put(0xF0.toByte())
                            }
                            1 -> {
                                copyValue(readBuffer, writeBuffer)
                                writeBuffer.put(0xF0.toByte())
                            }
                            2 -> {
                                val exprGroupLength = IntHelper.readFlexUInt(readBuffer)
                                if (exprGroupLength == 0) {
                                    while (copyValue(readBuffer, writeBuffer)) {
                                        // continue
                                    }
                                } else {
                                    val end = exprGroupLength + readBuffer.position()
                                    while (readBuffer.position() < end) {
                                        copyValue(readBuffer, writeBuffer)
                                    }
                                    writeBuffer.put(0xF0.toByte())
                                }
                            }
                        }
                    }
                    SystemMacro.None.id.toInt() -> {
                        writeBuffer.put(0xE8.toByte())
                    }
                    else -> TODO("System macro $macid")
                }
            }



            0xF0 -> {
                writeBuffer.put(byte)
                return false
            }
            0xF1,
            0xF2 -> {
                writeBuffer.put(byte)
                while (copyValue(readBuffer, writeBuffer)) {
                    // Continue.
                }
            }
            0xF3 -> {
                writeBuffer.put(byte)
                while (copyFlexSymFieldName(readBuffer, writeBuffer)) {
                    copyValue(readBuffer, writeBuffer)
                }
                writeBuffer.put(0x01)
                writeBuffer.put(0xF0.toByte())
            }
            0xF4 -> TODO("EExp w/ FlexUInt address")
            0xF5 -> TODO("EExp w/ FlexUInt address and FlexUInt length")

            0xFB, 0xFC -> {
                TODO("Var prefixed list/sexp")
            }
            0xFD -> {
                TODO("Var prefixed structs")
            }
            in 0xF6..0xFF -> {
                writeBuffer.put(byte)
                val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(readBuffer, readBuffer.position())
                val lengthOfLength = valueAndLength.toInt() and 0xFF
                val lengthOfText = valueAndLength.shr(8).toInt()
                copyBytes(lengthOfLength)
                copyBytes(lengthOfText)
            }
        }
        return true
    }

    private fun copyFlexSymFieldName(readBuffer: ByteBuffer, writeBuffer: ByteBuffer): Boolean {
        val p = readBuffer.position()
        val flexInt = IntHelper.readFlexInt(readBuffer)

        if (flexInt == 0) {
            val sysSid = readBuffer.get().toInt() and 0xFF
            if (sysSid == 0xF0) {
                return false
            }
            val text = SystemSymbols_1_1[sysSid]!!.text.toByteArray()
            FlexInt.writeFlexIntOrUIntInto(writeBuffer, text.size.toLong())
            text.forEach { writeBuffer.put(it) }
        } else if (flexInt < 0) {
            val textLength = -flexInt
            FlexInt.writeFlexIntOrUIntInto(writeBuffer, (-1 - textLength).toLong())
            repeat(textLength) { writeBuffer.put(readBuffer.get()) }
        } else {
            FlexInt.writeFlexIntOrUIntInto(writeBuffer, flexInt.toLong() + 9)
        }

        return true
    }

    private fun copyFlexSymAnnotation(readBuffer: ByteBuffer, writeBuffer: ByteBuffer) {
        val flexInt = IntHelper.readFlexInt(readBuffer)
        if (flexInt == 0) {
            val sysSid = readBuffer.get().toInt() and 0xFF
            val text = SystemSymbols_1_1[sysSid]!!.text.toByteArray()
            writeBuffer.put(0x8E.toByte())
            FlexInt.writeFlexIntOrUIntInto(writeBuffer, text.size.toLong())
            text.forEach { writeBuffer.put(it) }
        } else if (flexInt < 0) {
            writeBuffer.put(0x8E.toByte())
            val textLength = -flexInt
            FlexInt.writeFlexIntOrUIntInto(writeBuffer, textLength.toLong())
            repeat(textLength) { writeBuffer.put(readBuffer.get()) }
        } else {
            writeBuffer.put(0x8D.toByte())
            FlexInt.writeFlexIntOrUIntInto(writeBuffer, flexInt.toLong() + 9)
        }
    }
}
