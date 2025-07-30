package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.lang.IllegalStateException
import java.nio.ByteBuffer




fun writeMacroBodyAsByteCode(macro: MacroV2, eexpArgs: IntList, bytecode: IntList, constantPool: MutableList<Any?>) {
//    fun appendArg(parameterIndex: Int, bc: IntList) {
//        var start = 0
//        var length = eexpArgs[start++] and MacroBytecode.DATA_MASK
//        for (ii in 0 until parameterIndex) {
//            start += length
//            length = eexpArgs[start++] and MacroBytecode.DATA_MASK
//        }
//        // This copies the arg value without the arg wrapper. This might break for structs.
//        bc.addSlice(eexpArgs, start, length - 1)
//    }

    val env = Environment(eexpArgs, macro.signature)

    templateExpressionToBytecode(macro.body!!, env, bytecode, constantPool)
}

/**
 * Returns the number of bytes consumed while reading the arguments.
 */
fun readEExpArgsAsByteCode(src: ByteBuffer, position: Int, bytecode: IntList, constantPool: MutableList<Any?>, macro: MacroV2, macTab: Array<MacroV2>): Int {

    var presenceBitsPosition = position
    var p = presenceBitsPosition + macro.numPresenceBytesRequired

    val signature = macro.signature

    var presenceByteOffset = 8
    var presenceByte = 0

    for (parameter in signature) {
        MacroBytecodeHelper.emitArgumentValue(bytecode) {

            if (parameter.iCardinality == 1) {
                val opcode = src.get(p++).toInt() and 0xFF
                p += VALUE_TRANSFORMERS[opcode].toBytecode(bytecode, constantPool, src, p, macTab)
            } else {
                // Read a value from the presence bitmap
                // But we might need to "refill" our presence byte first
                if (presenceByteOffset > 7) {
                    presenceByte = src.get(presenceBitsPosition++).toInt() and 0xFF
                    presenceByteOffset = 0
                }
                val presence = (presenceByte ushr presenceByteOffset) and 0b11
                presenceByteOffset += 2
                when (presence) {
                    0 -> {}
                    1 -> {
                        val opcode = src.get(p++).toInt() and 0xFF
                        p += VALUE_TRANSFORMERS[opcode].toBytecode(bytecode, constantPool, src, p, macTab)
                    }
                    2 -> {
                        // TODO: See if we can optimize based on checking the first byte only.

                        // Read expression group
                        // TODO: Tagless types.
                        val lengthOfLength = IntHelper.lengthOfFlexUIntAt(src, p)
                        val expressionGroupLength = IntHelper.readFlexUIntWithLengthAt(src, p, lengthOfLength)
                        p += lengthOfLength
                        // MacroBytecodeHelper.emitArgumentValue(bytecode) {
                            if (expressionGroupLength == 0) {
                                p += readTaggedDelimitedExpressionGroupContent(src, p, bytecode, constantPool, macTab)
                            } else {
                                readTaggedPrefixedExpressionGroupContent(src, p, expressionGroupLength, bytecode, constantPool, macTab)
                                p += expressionGroupLength
                            }
                        // }
                    }
                    else -> throw IonException("Invalid presence bits value at position $presenceBitsPosition")
                }
            }
        }
    }
    return p - position
}



fun interface ToBytecodeTransformer {
    /**
     * Reads data off of [src], starting at [position], to emit bytecode into [dest], returning the number of
     * bytes consumed.
     */
    fun toBytecode(dest: IntList, constantPool: MutableList<Any?>, src: ByteBuffer, pos: Int, macTab: Array<MacroV2>): Int
}



@OptIn(ExperimentalStdlibApi::class)
private fun readTaggedDelimitedExpressionGroupContent(src: ByteBuffer, position: Int, dest: IntList, constantPool: MutableList<Any?>, macTab: Array<MacroV2>): Int {
    var p = position
    var opcode = src.get(p++).toInt() and 0xFF
    while (opcode != Opcode.DELIMITED_CONTAINER_END) {
//        println("Read opcode ${opcode.toUByte().toHexString()} at position ${p - 1}")
        val consumedBytes = VALUE_TRANSFORMERS[opcode].toBytecode(dest, constantPool, src, p, macTab)
        if (consumedBytes > 10000 || consumedBytes < 0) {
            throw Exception("consumedBytes=$consumedBytes; opcode=${opcode.toHexString()}, position=$position, $p=p")
        }
        p += consumedBytes
        opcode = src.get(p++).toInt() and 0xFF
    }
    return p - position
}

private fun readTaggedPrefixedExpressionGroupContent(src: ByteBuffer, start: Int, length: Int, dest: IntList, constantPool: MutableList<Any?>, macTab: Array<MacroV2>) {
    var p = start
    val end = start + length
    while (p < end) {
        val opcode = src.get(p++).toInt() and 0xFF
        p += VALUE_TRANSFORMERS[opcode].toBytecode(dest, constantPool, src, p, macTab)
    }
}



@OptIn(ExperimentalStdlibApi::class)
private fun noBytecodeTransformer(opcode: Int): ToBytecodeTransformer {
    return object : ToBytecodeTransformer {
        override fun toBytecode(dest: IntList, constantPool: MutableList<Any?>, src: ByteBuffer, pos: Int, macTab: Array<MacroV2>): Int {
            throw IllegalStateException("No mapping for opcode ${opcode.toUByte().toHexString()}. This should be unreachable.")
        }
    }
}




private val TX_INT_ZERO = ToBytecodeTransformer { dest, _, _, _, _, -> MacroBytecodeHelper.emitInt16Value(dest, 0); 0}
@OptIn(ExperimentalStdlibApi::class)
private val TX_INT_8 = ToBytecodeTransformer { dest, _, src, pos, _ -> MacroBytecodeHelper.emitInt16Value(dest, src.get(pos).toShort()); 1 }
private val TX_INT_16 = ToBytecodeTransformer { dest, _, src, pos, _ -> MacroBytecodeHelper.emitInt16Value(dest, src.getShort(pos)); 2 }
private val TX_INT_24 = ToBytecodeTransformer { dest, _, src, pos, _ -> MacroBytecodeHelper.emitInt32Value(dest, src.getInt(pos - 1) shr 8); 3 }
private val TX_INT_32 = ToBytecodeTransformer { dest, _,  src, pos, _ -> MacroBytecodeHelper.emitInt32Value(dest, src.getInt(pos)); 4 }
private val TX_INT_40 = ToBytecodeTransformer { dest, _,  src, pos, _ -> MacroBytecodeHelper.emitInt64Value(dest, src.getLong(pos - 3) shr 24); 5 }
private val TX_INT_48 = ToBytecodeTransformer { dest, _,  src, pos, _ -> MacroBytecodeHelper.emitInt64Value(dest, src.getLong(pos - 2) shr 16); 6 }
private val TX_INT_56 = ToBytecodeTransformer { dest, _,  src, pos, _ -> MacroBytecodeHelper.emitInt64Value(dest, src.getLong(pos - 1) shr 8); 7 }
private val TX_INT_64 = ToBytecodeTransformer { dest, _,  src, pos, _ -> MacroBytecodeHelper.emitInt64Value(dest, src.getLong(pos)); 8 }
private val TX_RESERVED_0x69 = noBytecodeTransformer(Opcode.RESERVED_0x69)
private val TX_FLOAT_ZERO = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitFloatValue(dest, 0.0f); 0 }
private val TX_FLOAT_16 = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitFloatValue(dest, toFloat(src.getShort(pos))); 2 }
private val TX_FLOAT_32 = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitFloatValue(dest, src.getFloat(pos)); 4 }
private val TX_FLOAT_64 = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitDoubleValue(dest, src.getDouble(pos)); 8 }
private val TX_BOOL_TRUE = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitBooleanValue(dest, true); 0 }
private val TX_BOOL_FALSE = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitBooleanValue(dest, false); 0 }
private val TX_DECIMAL_0 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0); 0 }
private val TX_0x71 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x1); 0x1 }
private val TX_0x72 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x2); 0x2 }
private val TX_0x73 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x3); 0x3 }
private val TX_0x74 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x4); 0x4 }
private val TX_0x75 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x5); 0x5 }
private val TX_0x76 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x6); 0x6 }
private val TX_0x77 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x7); 0x7 }
private val TX_0x78 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x8); 0x8 }
private val TX_0x79 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0x9); 0x9 }
private val TX_0x7A = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0xA); 0xA }
private val TX_0x7B = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0xB); 0xB }
private val TX_0x7C = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0xC); 0xC }
private val TX_0x7D = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0xD); 0xD }
private val TX_0x7E = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0xE); 0xE }
private val TX_0x7F = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitDecimalReference(dest, pos, 0xF); 0xF }
private val TX_TIMESTAMP_YEAR_PRECISION = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_YEAR_PRECISION, pos); 1 }
private val TX_TIMESTAMP_MONTH_PRECISION = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_MONTH_PRECISION, pos); 2 }
private val TX_TIMESTAMP_DAY_PRECISION = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_DAY_PRECISION, pos); 2 }
private val TX_TIMESTAMP_MINUTE_PRECISION = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_MINUTE_PRECISION, pos); 4 }
private val TX_TIMESTAMP_SECOND_PRECISION = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_SECOND_PRECISION, pos); 5 }
private val TX_TIMESTAMP_MILLIS_PRECISION = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_MILLIS_PRECISION, pos); 6 }
private val TX_TIMESTAMP_MICROS_PRECISION = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_MICROS_PRECISION, pos); 7 }
private val TX_TIMESTAMP_NANOS_PRECISION = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_NANOS_PRECISION, pos); 8 }
private val TX_TIMESTAMP_MINUTE_PRECISION_WITH_OFFSET = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_MINUTE_PRECISION_WITH_OFFSET, pos); 5 }
private val TX_TIMESTAMP_SECOND_PRECISION_WITH_OFFSET = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_SECOND_PRECISION_WITH_OFFSET, pos); 5 }
private val TX_TIMESTAMP_MILLIS_PRECISION_WITH_OFFSET = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_MILLIS_PRECISION_WITH_OFFSET, pos); 7 }
private val TX_TIMESTAMP_MICROS_PRECISION_WITH_OFFSET = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_MICROS_PRECISION_WITH_OFFSET, pos); 8 }
private val TX_TIMESTAMP_NANOS_PRECISION_WITH_OFFSET = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitShortTimestampReference(dest, Opcode.TIMESTAMP_NANOS_PRECISION_WITH_OFFSET, pos); 9 }
private val TX_RESERVED_0x8D = noBytecodeTransformer(Opcode.RESERVED_0x8D)
private val TX_RESERVED_0x8E = noBytecodeTransformer(Opcode.RESERVED_0x8E)
private val TX_RESERVED_0x8F = noBytecodeTransformer(Opcode.RESERVED_0x8F)
private val TX_STRING_VALUE_ZERO_LENGTH = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0); 0 }
private val TX_0x91 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x1); 0x1 }
private val TX_0x92 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x2); 0x2 }
private val TX_0x93 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x3); 0x3 }
private val TX_0x94 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x4); 0x4 }
private val TX_0x95 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x5); 0x5 }
private val TX_0x96 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x6); 0x6 }
private val TX_0x97 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x7); 0x7 }
private val TX_0x98 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x8); 0x8 }
private val TX_0x99 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0x9); 0x9 }
private val TX_0x9A = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0xA); 0xA }
private val TX_0x9B = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0xB); 0xB }
private val TX_0x9C = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0xC); 0xC }
private val TX_0x9D = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0xD); 0xD }
private val TX_0x9E = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0xE); 0xE }
private val TX_0x9F = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStringReference(dest, pos, 0xF); 0xF }
private val TX_SYMBOL_VALUE_TEXT_ZERO_LENGTH = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0); 0 }
// private val TX_0xA1 = ToBytecodeTransformer { dest, _, _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x1); 0x1 }
private val TX_0xA1 = ToBytecodeTransformer { dest,  _, src, pos, _ ->
    val data = src.get(pos).toInt() and 0xFF
    dest.add(MacroBytecode.OP_SYMBOL_CHAR.opToInstruction(data))
    0x1
}
private val TX_0xA2 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x2); 0x2 }
private val TX_0xA3 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x3); 0x3 }
private val TX_0xA4 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x4); 0x4 }
private val TX_0xA5 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x5); 0x5 }
private val TX_0xA6 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x6); 0x6 }
private val TX_0xA7 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x7); 0x7 }
private val TX_0xA8 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x8); 0x8 }
private val TX_0xA9 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0x9); 0x9 }
private val TX_0xAA = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0xA); 0xA }
private val TX_0xAB = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0xB); 0xB }
private val TX_0xAC = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0xC); 0xC }
private val TX_0xAD = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0xD); 0xD }
private val TX_0xAE = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0xE); 0xE }
private val TX_0xAF = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSymbolTextReference(dest, pos, 0xF); 0xF }
private val TX_LIST_ZERO_LENGTH = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitInlineList(dest, {}); 0 }
private val TX_0xB1 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x1); 0x1 }
private val TX_0xB2 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x2); 0x2 }
private val TX_0xB3 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x3); 0x3 }
private val TX_0xB4 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x4); 0x4 }
private val TX_0xB5 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x5); 0x5 }
private val TX_0xB6 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x6); 0x6 }
private val TX_0xB7 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x7); 0x7 }
private val TX_0xB8 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x8); 0x8 }
private val TX_0xB9 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0x9); 0x9 }
private val TX_0xBA = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0xA); 0xA }
private val TX_0xBB = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0xB); 0xB }
private val TX_0xBC = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0xC); 0xC }
private val TX_0xBD = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0xD); 0xD }
private val TX_0xBE = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0xE); 0xE }
private val TX_0xBF = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitListReference(dest, pos, 0xF); 0xF }
private val TX_SEXP_ZERO_LENGTH = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitInlineSexp(dest, {}); 0 }
private val TX_0xC1 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x1); 0x1 }
private val TX_0xC2 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x2); 0x2 }
private val TX_0xC3 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x3); 0x3 }
private val TX_0xC4 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x4); 0x4 }
private val TX_0xC5 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x5); 0x5 }
private val TX_0xC6 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x6); 0x6 }
private val TX_0xC7 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x7); 0x7 }
private val TX_0xC8 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x8); 0x8 }
private val TX_0xC9 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0x9); 0x9 }
private val TX_0xCA = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0xA); 0xA }
private val TX_0xCB = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0xB); 0xB }
private val TX_0xCC = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0xC); 0xC }
private val TX_0xCD = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0xD); 0xD }
private val TX_0xCE = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0xE); 0xE }
private val TX_0xCF = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitSexpReference(dest, pos, 0xF); 0xF }
private val TX_STRUCT_ZERO_LENGTH = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitInlineSexp(dest, {}); 0 }
private val TX_RESERVED_0xD1 = noBytecodeTransformer(Opcode.RESERVED_0xD1)
private val TX_0xD2 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0x2); 0x2 }
private val TX_0xD3 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0x3); 0x3 }
private val TX_0xD4 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0x4); 0x4 }
private val TX_0xD5 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0x5); 0x5 }
private val TX_0xD6 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0x6); 0x6 }
private val TX_0xD7 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0x7); 0x7 }
private val TX_0xD8 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0x8); 0x8 }
private val TX_0xD9 = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0x9); 0x9 }
private val TX_0xDA = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0xA); 0xA }
private val TX_0xDB = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0xB); 0xB }
private val TX_0xDC = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0xC); 0xC }
private val TX_0xDD = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0xD); 0xD }
private val TX_0xDE = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0xE); 0xE }
private val TX_0xDF = ToBytecodeTransformer { dest, _,  _, pos, _ -> MacroBytecodeHelper.emitStructReference(dest, pos, 0xF); 0xF }
private val TX_IVM = noBytecodeTransformer(Opcode.IVM)
private val TX_SYMBOL_VALUE_SID_U8 = ToBytecodeTransformer { dest,  _, src, pos, macTab -> MacroBytecodeHelper.emitSymbolId(dest, src.get(pos).toInt() and 0xFF); 1 }
private val TX_SYMBOL_VALUE_SID_U16 = ToBytecodeTransformer { dest, _,  src, pos, macTab -> MacroBytecodeHelper.emitSymbolId(dest, src.getShort(pos).toInt() and 0xFFFF + 256); 2 }
private val TX_SYMBOL_VALUE_SID_FLEXUINT = ToBytecodeTransformer { dest, _, src, pos, macTab ->
    val lengthOfSid = IntHelper.lengthOfFlexUIntAt(src, pos)
    val sid = 65792 + IntHelper.readFlexUIntWithLengthAt(src, pos, lengthOfSid)
    MacroBytecodeHelper.emitSymbolId(dest, sid)
    lengthOfSid
}
private val TX_ANNOTATION_1_SID = ToBytecodeTransformer { dest, _,  src, pos, _ ->
    val lengthOfSid = IntHelper.lengthOfFlexUIntAt(src, pos)
    val sid = IntHelper.readFlexUIntWithLengthAt(src, pos, lengthOfSid)
    dest.add(MacroBytecode.OP_ONE_ANNOTATION_SID.opToInstruction(sid))
    lengthOfSid
}
private val TX_ANNOTATION_2_SID = ToBytecodeTransformer { dest,  _, src, pos, _ ->
    var p = pos
    // First Annotation
    val lengthOfSid1 = IntHelper.lengthOfFlexUIntAt(src, p)
    val sid1 = IntHelper.readFlexUIntWithLengthAt(src, p, lengthOfSid1)
    dest.add(MacroBytecode.OP_ONE_ANNOTATION_SID.opToInstruction(sid1))
    p += lengthOfSid1
    // Second Annotation
    val lengthOfSid2 = IntHelper.lengthOfFlexUIntAt(src, p)
    val sid2 = IntHelper.readFlexUIntWithLengthAt(src, p, lengthOfSid2)
    dest.add(MacroBytecode.OP_ONE_ANNOTATION_SID.opToInstruction(sid2))
    p += lengthOfSid2

    p - pos
}
private val TX_ANNOTATION_N_SID = ToBytecodeTransformer { dest,  _, src, pos, _ ->
    var p = pos
    val lengthOfN = IntHelper.lengthOfFlexUIntAt(src, p)
    val n = IntHelper.readFlexUIntWithLengthAt(src, p, lengthOfN)
    p += lengthOfN
    for (i in 0 until n) {
        val lengthOfSid = IntHelper.lengthOfFlexUIntAt(src, p)
        val sid = IntHelper.readFlexUIntWithLengthAt(src, p, lengthOfSid)
        dest.add(MacroBytecode.OP_ONE_ANNOTATION_SID.opToInstruction(sid))
        p += lengthOfSid
    }
    p - pos
}
private val TX_ANNOTATION_1_FLEXSYM = ToBytecodeTransformer { dest,  _, src, pos, _ ->
    val lengthOfFlexInt = IntHelper.lengthOfFlexUIntAt(src, pos)
    val flexsym = IntHelper.readFlexIntWithLengthAt(src, pos, lengthOfFlexInt)
    val remainingLength = if (flexsym < 0) {
        val length = -flexsym
        dest.add(MacroBytecode.OP_REF_ONE_FLEXSYM_ANNOTATION.opToInstruction(length))
        dest.add(pos + lengthOfFlexInt)
        length
    } else if (flexsym == 0) {
        val sid = src.get(pos + lengthOfFlexInt).toInt()
        dest.add(MacroBytecode.OP_ANN_SYSTEM_SID.opToInstruction(sid - 0x60))
        1
    } else {
        dest.add(MacroBytecode.OP_ONE_ANNOTATION_SID.opToInstruction(flexsym))
        0
    }

    lengthOfFlexInt + remainingLength
}
// TODO: ANNOTATION_2_FLEXSYMS
// TODO: ANNOTATION_N_FLEXSYMS
private val TX_NULL_VALUE = ToBytecodeTransformer { dest,  _, src, pos, macTab -> MacroBytecodeHelper.emitNullValue(dest, IonType.NULL); 0 }
private val TX_TYPED_NULL_VALUE = ToBytecodeTransformer { dest,  _, src, pos, macTab -> MacroBytecodeHelper.emitNullValue(dest, TYPED_NULL_ION_TYPES[src.get(pos).toInt()]); 1 }
private val TX_NOP = ToBytecodeTransformer { dest,  _, src, pos, macTab -> 0 }
private val TX_NOP_WITH_LENGTH = ToBytecodeTransformer { dest,  _, src, pos, macTab -> IntHelper.getLengthPlusValueOfFlexUIntAt(src, pos) }
private val TX_SYSTEM_SYMBOL = ToBytecodeTransformer { dest,  _, src, pos, macTab -> MacroBytecodeHelper.emitSystemSymbolId(dest, src.get(pos).toInt()); 1 }


private fun TX_OPCODE_IS_MACRO_ADDR(opcode: Int) = ToBytecodeTransformer { dest,  cp,  src, pos, macTab ->
    val macro = macTab[opcode]
    val numBytes = readEExpArgsAsByteCode(src, pos, dest, cp,  macro, macTab)
    dest.add(MacroBytecode.OP_INVOKE_MACRO_ID.opToInstruction(opcode))
    numBytes
}

private fun TX_1_BYTE_MACRO_ADDR(opcode: Int) = ToBytecodeTransformer { dest,  cp,  src, pos, macTab ->
    var p = pos
    val bias = (opcode and 0xF) * 256 + 64
    val unbiasedId = src.get(p).toInt() and 0xFF
    val macroId = unbiasedId + bias
    p++
    val macro = macTab[macroId]
    p += readEExpArgsAsByteCode(src, p, dest, cp, macro, macTab)
    dest.add(MacroBytecode.OP_INVOKE_MACRO_ID.opToInstruction(macroId))
    p - pos
}

private fun TX_2_BYTE_MACRO_ADDR(opcode: Int) = ToBytecodeTransformer { dest, cp, src, pos, macTab ->
    var p = pos
    val bias = (opcode and 0xF) * 256 * 256 + 4160
    val unbiasedId = src.getShort(p).toInt() and 0xFFFF
    val macroId = unbiasedId + bias
    p += 2
    val macro = macTab[macroId]
    p += readEExpArgsAsByteCode(src, p, dest, cp, macro, macTab)
    dest.add(MacroBytecode.OP_INVOKE_MACRO_ID.opToInstruction(macroId))
    p - pos
}

private val TX_MACRO_W_FLEXUINT_ADDR = ToBytecodeTransformer { dest,  cp,  src, pos, macTab ->
    var p = pos
    val lengthOfLength = IntHelper.lengthOfFlexUIntAt(src, p)
    val macroId = IntHelper.readFlexUIntWithLengthAt(src, p, lengthOfLength)
    p += lengthOfLength
    val macro = macTab[macroId]
//    val args = IntList()
    p += readEExpArgsAsByteCode(src, p, dest, cp, macro, macTab)
    dest.add(MacroBytecode.OP_INVOKE_MACRO_ID.opToInstruction(macroId))
    p - pos
}

private val TX_SYSTEM_MACRO = ToBytecodeTransformer { dest,  cp,  src, pos, macTab ->
    var numBytes = 1
    val macroId = src.get(pos).toInt() and 0xFF
    val macro = SystemMacro[macroId]
    // val args = IntList()
    numBytes += readEExpArgsAsByteCode(src, pos, dest, cp, macro, macTab)
    dest.add(MacroBytecode.OP_INVOKE_SYS_MACRO.opToInstruction(macroId))
    numBytes
}


inline fun toPrefixedReference(crossinline emitter: (IntList, Int, Int) -> Unit) = ToBytecodeTransformer { dest, _,  src, pos, _ ->
    val lengthOfLength = IntHelper.lengthOfFlexUIntAt(src, pos)
    val length = IntHelper.readFlexUIntWithLengthAt(src, pos, lengthOfLength)
    emitter(dest, pos + lengthOfLength, length)
    length + lengthOfLength.also {
        if (it > 10000) {
            println("lengthOfLength: $lengthOfLength")
            println("length: $length")
            println("pos: $pos")
        }
    }
}

private val TX_VAR_STRING = ToBytecodeTransformer { dest,  cp,  src, pos, macTab ->
    val lengthOfLength = IntHelper.lengthOfFlexUIntAt(src, pos)
    val length = IntHelper.readFlexUIntWithLengthAt(src, pos, lengthOfLength)
    MacroBytecodeHelper.emitStringReference(dest, pos + lengthOfLength, length)
    (length + lengthOfLength).also {
        if (it > 10000) {
            println("lengthOfLength: $lengthOfLength")
            println("length: $length")
            println("pos: $pos")
        }
    }
}

private val TX_DELIMITED_LIST: ToBytecodeTransformer = ToBytecodeTransformer { dest,  cp,   src, pos, macTab ->
    var p = pos
    MacroBytecodeHelper.emitInlineList(dest) {
        var opcode = src.get(p++).toInt() and 0xFF
        while (opcode != Opcode.DELIMITED_CONTAINER_END) {
            p += VALUE_TRANSFORMERS[opcode].toBytecode(dest, cp, src, p, macTab)
            opcode = src.get(p++).toInt() and 0xFF
        }
    }
    p - pos
}

private val TX_DELIMITED_SEXP: ToBytecodeTransformer = ToBytecodeTransformer { dest,  cp,  src, pos, macTab ->
    var p = pos
    MacroBytecodeHelper.emitInlineSexp(dest) {
        var opcode = src.get(p++).toInt() and 0xFF
        while (opcode != Opcode.DELIMITED_CONTAINER_END) {
            p += VALUE_TRANSFORMERS[opcode].toBytecode(dest, cp, src, p, macTab)
            opcode = src.get(p++).toInt() and 0xFF
        }
    }
    p - pos
}

private val TX_DELIMITED_STRUCT: ToBytecodeTransformer = ToBytecodeTransformer { dest, cp, src, pos, macTab ->
    var p = pos
    MacroBytecodeHelper.emitInlineStruct(dest) {
        while (true) {
            val flexSym = IntHelper.readFlexIntAt(src, p)
            p += IntHelper.lengthOfFlexUIntAt(src, p)
            if (flexSym == 0) {
                val systemSid = src.get(p++).toInt() and 0xFF
                if (systemSid == 0xF0) {
                    break
                }
                MacroBytecodeHelper.emitFieldNameSystemSid(dest, systemSid - 0x60)
            } else if (flexSym > 0) {
                MacroBytecodeHelper.emitFieldNameSid(dest, flexSym)
            } else {
                val length = -flexSym
                MacroBytecodeHelper.emitFieldNameTextReference(dest, p, length)
                p += length
            }
            val opcode = src.get(p++).toInt() and 0xFF
            p += VALUE_TRANSFORMERS[opcode].toBytecode(dest, cp, src, p, macTab)
        }
    }
    p - pos
}

private val VALUE_TRANSFORMERS = Array<ToBytecodeTransformer>(256) { opcode ->
    when (opcode) {
        in 0x00..0x3F -> TX_OPCODE_IS_MACRO_ADDR(opcode)
        in 0x40..0x4F -> TX_1_BYTE_MACRO_ADDR(opcode)
        in 0x50..0x5F -> TX_2_BYTE_MACRO_ADDR(opcode)
        Opcode.INT_ZERO -> TX_INT_ZERO
        Opcode.INT_8 -> TX_INT_8
        Opcode.INT_16 -> TX_INT_16
        Opcode.INT_24 -> TX_INT_24
        Opcode.INT_32 -> TX_INT_32
        Opcode.INT_40 -> TX_INT_40
        Opcode.INT_48 -> TX_INT_48
        Opcode.INT_56 -> TX_INT_56
        Opcode.INT_64 -> TX_INT_64
        Opcode.RESERVED_0x69 -> TX_RESERVED_0x69
        Opcode.FLOAT_ZERO -> TX_FLOAT_ZERO
        Opcode.FLOAT_16 -> TX_FLOAT_16
        Opcode.FLOAT_32 -> TX_FLOAT_32
        Opcode.FLOAT_64 -> TX_FLOAT_64
        Opcode.BOOL_TRUE -> TX_BOOL_TRUE
        Opcode.BOOL_FALSE -> TX_BOOL_FALSE
        Opcode.DECIMAL_0 -> TX_DECIMAL_0
        0x71 -> TX_0x71
        0x72 -> TX_0x72
        0x73 -> TX_0x73
        0x74 -> TX_0x74
        0x75 -> TX_0x75
        0x76 -> TX_0x76
        0x77 -> TX_0x77
        0x78 -> TX_0x78
        0x79 -> TX_0x79
        0x7A -> TX_0x7A
        0x7B -> TX_0x7B
        0x7C -> TX_0x7C
        0x7D -> TX_0x7D
        0x7E -> TX_0x7E
        0x7F -> TX_0x7F
        Opcode.TIMESTAMP_YEAR_PRECISION -> TX_TIMESTAMP_YEAR_PRECISION
        Opcode.TIMESTAMP_MONTH_PRECISION -> TX_TIMESTAMP_MONTH_PRECISION
        Opcode.TIMESTAMP_DAY_PRECISION -> TX_TIMESTAMP_DAY_PRECISION
        Opcode.TIMESTAMP_MINUTE_PRECISION -> TX_TIMESTAMP_MINUTE_PRECISION
        Opcode.TIMESTAMP_SECOND_PRECISION -> TX_TIMESTAMP_SECOND_PRECISION
        Opcode.TIMESTAMP_MILLIS_PRECISION -> TX_TIMESTAMP_MILLIS_PRECISION
        Opcode.TIMESTAMP_MICROS_PRECISION -> TX_TIMESTAMP_MICROS_PRECISION
        Opcode.TIMESTAMP_NANOS_PRECISION -> TX_TIMESTAMP_NANOS_PRECISION
        Opcode.TIMESTAMP_MINUTE_PRECISION_WITH_OFFSET -> TX_TIMESTAMP_MINUTE_PRECISION_WITH_OFFSET
        Opcode.TIMESTAMP_SECOND_PRECISION_WITH_OFFSET -> TX_TIMESTAMP_SECOND_PRECISION_WITH_OFFSET
        Opcode.TIMESTAMP_MILLIS_PRECISION_WITH_OFFSET -> TX_TIMESTAMP_MILLIS_PRECISION_WITH_OFFSET
        Opcode.TIMESTAMP_MICROS_PRECISION_WITH_OFFSET -> TX_TIMESTAMP_MICROS_PRECISION_WITH_OFFSET
        Opcode.TIMESTAMP_NANOS_PRECISION_WITH_OFFSET -> TX_TIMESTAMP_NANOS_PRECISION_WITH_OFFSET
        Opcode.RESERVED_0x8D -> TX_RESERVED_0x8D
        Opcode.RESERVED_0x8E -> TX_RESERVED_0x8E
        Opcode.RESERVED_0x8F -> TX_RESERVED_0x8F
        Opcode.STRING_VALUE_ZERO_LENGTH -> TX_STRING_VALUE_ZERO_LENGTH
        0x91 -> TX_0x91
        0x92 -> TX_0x92
        0x93 -> TX_0x93
        0x94 -> TX_0x94
        0x95 -> TX_0x95
        0x96 -> TX_0x96
        0x97 -> TX_0x97
        0x98 -> TX_0x98
        0x99 -> TX_0x99
        0x9A -> TX_0x9A
        0x9B -> TX_0x9B
        0x9C -> TX_0x9C
        0x9D -> TX_0x9D
        0x9E -> TX_0x9E
        0x9F -> TX_0x9F
        Opcode.SYMBOL_VALUE_TEXT_ZERO_LENGTH -> TX_SYMBOL_VALUE_TEXT_ZERO_LENGTH
        0xA1 -> TX_0xA1
        0xA2 -> TX_0xA2
        0xA3 -> TX_0xA3
        0xA4 -> TX_0xA4
        0xA5 -> TX_0xA5
        0xA6 -> TX_0xA6
        0xA7 -> TX_0xA7
        0xA8 -> TX_0xA8
        0xA9 -> TX_0xA9
        0xAA -> TX_0xAA
        0xAB -> TX_0xAB
        0xAC -> TX_0xAC
        0xAD -> TX_0xAD
        0xAE -> TX_0xAE
        0xAF -> TX_0xAF
        Opcode.LIST_ZERO_LENGTH -> TX_LIST_ZERO_LENGTH
        0xB1 -> TX_0xB1
        0xB2 -> TX_0xB2
        0xB3 -> TX_0xB3
        0xB4 -> TX_0xB4
        0xB5 -> TX_0xB5
        0xB6 -> TX_0xB6
        0xB7 -> TX_0xB7
        0xB8 -> TX_0xB8
        0xB9 -> TX_0xB9
        0xBA -> TX_0xBA
        0xBB -> TX_0xBB
        0xBC -> TX_0xBC
        0xBD -> TX_0xBD
        0xBE -> TX_0xBE
        0xBF -> TX_0xBF
        Opcode.SEXP_ZERO_LENGTH -> TX_SEXP_ZERO_LENGTH
        0xC1 -> TX_0xC1
        0xC2 -> TX_0xC2
        0xC3 -> TX_0xC3
        0xC4 -> TX_0xC4
        0xC5 -> TX_0xC5
        0xC6 -> TX_0xC6
        0xC7 -> TX_0xC7
        0xC8 -> TX_0xC8
        0xC9 -> TX_0xC9
        0xCA -> TX_0xCA
        0xCB -> TX_0xCB
        0xCC -> TX_0xCC
        0xCD -> TX_0xCD
        0xCE -> TX_0xCE
        0xCF -> TX_0xCF
        Opcode.STRUCT_ZERO_LENGTH -> TX_STRUCT_ZERO_LENGTH
        Opcode.RESERVED_0xD1 -> TX_RESERVED_0xD1
        0xD2 -> TX_0xD2
        0xD3 -> TX_0xD3
        0xD4 -> TX_0xD4
        0xD5 -> TX_0xD5
        0xD6 -> TX_0xD6
        0xD7 -> TX_0xD7
        0xD8 -> TX_0xD8
        0xD9 -> TX_0xD9
        0xDA -> TX_0xDA
        0xDB -> TX_0xDB
        0xDC -> TX_0xDC
        0xDD -> TX_0xDD
        0xDE -> TX_0xDE
        0xDF -> TX_0xDF

        Opcode.IVM -> TX_IVM
        Opcode.SYMBOL_VALUE_SID_U8 -> TX_SYMBOL_VALUE_SID_U8
        Opcode.SYMBOL_VALUE_SID_U16 -> TX_SYMBOL_VALUE_SID_U16
        Opcode.SYMBOL_VALUE_SID_FLEXUINT -> TX_SYMBOL_VALUE_SID_FLEXUINT
        Opcode.ANNOTATION_1_SID -> TX_ANNOTATION_1_SID
        Opcode.ANNOTATION_2_SID -> TX_ANNOTATION_2_SID
        Opcode.ANNOTATION_N_SID -> TX_ANNOTATION_N_SID
        Opcode.ANNOTATION_1_FLEXSYM -> TX_ANNOTATION_1_FLEXSYM
        // Opcode.ANNOTATION_2_FLEXSYM -> TX_ANNOTATION_2_FLEXSYM
        // Opcode.ANNOTATION_N_FLEXSYM -> TX_ANNOTATION_N_FLEXSYM
        Opcode.NULL_VALUE -> TX_NULL_VALUE
        Opcode.TYPED_NULL_VALUE -> TX_TYPED_NULL_VALUE
        Opcode.NOP -> TX_NOP
        Opcode.NOP_WITH_LENGTH -> TX_NOP_WITH_LENGTH
        Opcode.SYSTEM_SYMBOL -> TX_SYSTEM_SYMBOL
        Opcode.SYSTEM_MACRO_EEXP -> TX_SYSTEM_MACRO


        // 0xF_
        Opcode.DELIMITED_CONTAINER_END -> noBytecodeTransformer(Opcode.DELIMITED_CONTAINER_END)
        Opcode.DELIMITED_LIST -> TX_DELIMITED_LIST
        Opcode.DELIMITED_SEXP -> TX_DELIMITED_SEXP
        Opcode.DELIMITED_STRUCT -> TX_DELIMITED_STRUCT

        Opcode.E_EXPRESSION_WITH_FLEX_UINT_ADDRESS -> TX_MACRO_W_FLEXUINT_ADDR
        Opcode.VARIABLE_LENGTH_STRING -> TX_VAR_STRING
        Opcode.VARIABLE_LENGTH_INLINE_SYMBOL -> toPrefixedReference(MacroBytecodeHelper::emitSymbolTextReference)
        Opcode.VARIABLE_LENGTH_LIST -> toPrefixedReference(MacroBytecodeHelper::emitListReference)
        Opcode.VARIABLE_LENGTH_SEXP -> toPrefixedReference(MacroBytecodeHelper::emitSexpReference)
        Opcode.VARIABLE_LENGTH_STRUCT_WITH_SIDS -> toPrefixedReference(MacroBytecodeHelper::emitStructReference)
        Opcode.VARIABLE_LENGTH_DECIMAL -> toPrefixedReference(MacroBytecodeHelper::emitDecimalReference)
        Opcode.VARIABLE_LENGTH_TIMESTAMP -> toPrefixedReference(MacroBytecodeHelper::emitLongTimestampReference)

        else -> noBytecodeTransformer(opcode)
    }
}









