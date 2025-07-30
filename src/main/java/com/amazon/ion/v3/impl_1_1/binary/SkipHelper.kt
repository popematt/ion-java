package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.EncodingContextManager
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.IdMappings.LengthCalculator
import com.amazon.ion.v3.impl_1_1.binary.ValueReaderBase.Companion.TID_UNSET
import java.nio.ByteBuffer

object SkipHelper {

    private const val DELIMITED_END = 0xF0


    /**
     * Returns the position of the first byte after the value or expression denoted by `opcode`.
     */
    @JvmStatic
    fun skip(source: ByteBuffer, macroTable: Array<MacroV2>, opcode: Int, nextByteLocation: Int): Int {
        return nextByteLocation + IdMappings.LENGTH_FOR_OPCODE_CALCULATOR[opcode].calculate(source, macroTable, nextByteLocation)
    }

    /**
     * Returns the position of the first byte after this container.
     */
    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    fun seekToEndOfDelimitedContainer(source: ByteBuffer, macroTable: Array<MacroV2>, isStruct: Boolean, startingAt: Int): Int {

        val DELIMITED_END = 0xF0
        var i = startingAt
        var opcode = TID_UNSET.toInt()

        // This is a set of bit flags for tracking which containers are structs.
        var structFlag = if (isStruct) 1 else 0

        // This tracks the current depth as a bit flag. This will fall apart if there's more than 32 nested containers.
        var depth = 1

        while (opcode != DELIMITED_END || depth != 0) {

            // If we're in a struct, check and skip the FlexSym here.
            if (structFlag and depth != 0) {
                val flexSym = IntHelper.readFlexIntAt(source, i)
//                println("Skipping flexsym field name ${flexSym} at ${i}, depth ${depth.countTrailingZeroBits()}")
                i += IntHelper.lengthOfFlexUIntAt(source, i)


                if (flexSym < 0) {
                    i += -flexSym
                } else if (flexSym == 0) {
                    if ((source.get(i++).toInt() and 0xFF) == DELIMITED_END) {
                        opcode = DELIMITED_END
                        depth = depth ushr 1
                        continue
                    }
                    // TODO: macro invocations in field name position?
                }
            }


            opcode = source.get(i++).toInt() and 0xFF
            var length = IdMappings.length(opcode)
            if (opcode == DELIMITED_END) {
                depth = depth ushr 1
                continue
            }

            if (length >= 0) {
                i += length
            } else if (length == -1) {
                // TODO: Create something that can skip the prefix and the value?
                length = IntHelper.readFlexUIntAt(source, i)
                i += IntHelper.lengthOfFlexUIntAt(source, i) + length
            } else {
                when (opcode) {
                    0xE4 -> i += IntHelper.lengthOfFlexUIntAt(source, i)
                    0xE5 -> {
                        i += IntHelper.lengthOfFlexUIntAt(source, i)
                        i += IntHelper.lengthOfFlexUIntAt(source, i)
                    }
                    0xE6 -> {
                        // Read and then skip the FlexUInt indicating the number of annotations
                        val n = IntHelper.readFlexUIntAt(source, i)
                        i += IntHelper.lengthOfFlexUIntAt(source, i)
                        // Now skip all the annotation SIDs
                        for (j in 0 until n) {
                            i += IntHelper.lengthOfFlexUIntAt(source, i)
                        }
                    }
                    0xE7 -> {
                        i = FlexSymHelper.skipFlexSymAt(source, i)
                    }
                    0xE8 -> {
                        i = FlexSymHelper.skipFlexSymAt(source, i)
                        i = FlexSymHelper.skipFlexSymAt(source, i)
                    }
                    0xE9  -> {
                        // Read and then skip the FlexUInt indicating the number of annotations
                        val n = IntHelper.readFlexUIntAt(source, i)
                        i += IntHelper.lengthOfFlexUIntAt(source, i)
                        // Now skip all the annotation SIDs
                        for (j in 0 until n) {
                            i = FlexSymHelper.skipFlexSymAt(source, i)
                        }
                    }
                    0xF1, 0xF2 -> {
                        depth = depth shl 1
                        structFlag = structFlag and depth.inv()
                    }
                    0xF3 -> {
                        depth = depth shl 1
                        structFlag = structFlag or depth
                    }
                    else -> {
                        i = skipMacro(source, macroTable, opcode, i)
                    }
                }
            }
        }
        return i
    }



    @JvmStatic
    private val SINGLE_ARG_LENGTH_CALCULATOR = LengthCalculator { source, macroTable, p ->
        val argOpcode = source.get(p).toInt() and 0xFF
        val calculator = IdMappings.LENGTH_FOR_OPCODE_CALCULATOR[argOpcode]
        return@LengthCalculator calculator.calculate(source, macroTable, p + 1)
    }

    @JvmStatic
    private val ARG_LENGTH_CALCULATORS = Array<LengthCalculator>(4) {
        when (it) {
            0 -> LengthCalculator { _, _, _, -> 0 }
            1 -> SINGLE_ARG_LENGTH_CALCULATOR
            2 -> LengthCalculator { source, macroTable, p ->
                var i = p
                // Expression group
                val length = IntHelper.readFlexUIntAt(source, i)
                i += IntHelper.lengthOfFlexUIntAt(source, i)
                i += length
                // TODO: Check if we're on a tagless parameter.
                //       For now, we'll assume that they are all tagged.
                if (length == 0) {
                    // Delimited expression group
                    i = seekToEndOfDelimitedContainer(source, macroTable, isStruct = false, i)
                }
                return@LengthCalculator i - p
            }
            else -> LengthCalculator { _, _, _ -> throw IonException("Invalid presence bits value") }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    fun skipMacro(source: ByteBuffer, macroTable: Array<MacroV2>, opcode: Int, nextBytePosition: Int): Int {
//        println("Skip Macro $opcode at ${nextBytePosition - 1}")
        if (opcode == 0) TODO()
        var i = nextBytePosition
        val macro = when (opcode shr 4) {
            0x0, 0x1, 0x2, 0x3 -> {
                macroTable[opcode]
            }
            0x4 -> {
                // Opcode with 12-bit address and bias
                val bias = (opcode and 0xF) * 256 + 64
                val unbiasedId = source.get(i).toInt() and 0xFF
                i += 1
                val id: Int = unbiasedId + bias
                macroTable[id]
            }
            0x5 -> {
                // Opcode with 20-bit address and bias
                val bias = (opcode and 0xF) * 256 * 256 + 4160
                val unbiasedId = source.getShort(i).toInt() and 0xFFFF
                i += 2
                val id: Int = unbiasedId + bias
                macroTable[id]
            }
            0xE -> {
                if (opcode == 0xEF) {
                    // System macro
                    val id = source.get(i).toInt()
                    i += 1
                    EncodingContextManager.ION_1_1_SYSTEM_MACROS[id]
                } else {
                    throw IonException("Not positioned on an E-Expression: ${opcode.toByte().toHexString()} @ ${source.position()}")
                }
            }
            0xF -> {
                if (opcode == 0xF4) {
                    // E-expression with FlexUInt macro address
                    val id = IntHelper.readFlexUIntAt(source, i)
                    i += IntHelper.lengthOfFlexUIntAt(source, i)
                    macroTable[id]
                } else if (opcode == 0xF5) {
                    // E-expression with FlexUInt macro address followed by FlexUInt length prefix
                    i += IntHelper.lengthOfFlexUIntAt(source, i)
                    val argsLength = IntHelper.readFlexUIntAt(source, i)
                    i += IntHelper.lengthOfFlexUIntAt(source, i)
                    return i + argsLength
                } else {
                    throw IonException("Not positioned on an E-Expression")
                }
            }
            else -> throw IonException("Not positioned on an E-Expression")
        }
        val signature = macro.signature
        if (signature.isEmpty()) {
            return i
        }

        var presenceBitsPosition = i
        // TODO: Precompute this and store it in the macro definition
        i += macro.numPresenceBytesRequired

        var presenceByteOffset = 8
        var presenceByte = 0

        var j = 0

        while (j < signature.size) {
            val parameter = signature[j]
            if (parameter.iCardinality != 1) {
                // Read a value from the presence bitmap
                // But we might need to "refill" our presence byte first
                if (presenceByteOffset > 7) {
                    presenceByte = source.get(presenceBitsPosition++).toInt() and 0xFF
                    presenceByteOffset = 0
                }
                val presence = (presenceByte ushr presenceByteOffset) and 0b11
                presenceByteOffset += 2

                i += ARG_LENGTH_CALCULATORS[presence].calculate(source, macroTable, i)
            } else {
                i += SINGLE_ARG_LENGTH_CALCULATOR.calculate(source, macroTable, i)
            }

            j++
        }
        return i
    }


}
