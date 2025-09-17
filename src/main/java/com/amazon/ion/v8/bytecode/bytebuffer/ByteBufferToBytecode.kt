package com.amazon.ion.v8.bytecode.bytebuffer

import com.amazon.ion.IonException
import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.v8.*
import com.amazon.ion.v8.Bytecode.instructionToOp
import com.amazon.ion.v8.Bytecode.opToInstruction
import com.amazon.ion.v8.bytecode.bytebuffer.ByteBufferToBytecode.ByteArraySlice
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.min

object ByteBufferToBytecode {


    private fun ByteBuffer.readString(start: Int, length: Int): String {
        val position = this.position()
        val limit = this.limit()
        this.limit(start + length)
        this.position(start)
        val s = StandardCharsets.UTF_8.decode(this).toString()
        this.limit(limit)
        this.position(position)
        return s
    }

    fun String(byteBuffer: ByteBuffer, offset: Int, length: Int, charset: Charset): String {
        return byteBuffer.readString(offset, length)
    }

    fun ByteBuffer.get3Bytes(position: Int): Int {
        return (this[position].toInt() and 0xFF) or
                ((this[position + 1].toInt() and 0xFF) shl 8) or
                ((this[position + 2].toInt() and 0xFF) shl 16)
    }

    fun ByteBuffer.get5Bytes(position: Int): Long {
        return (this[position].toLong() and 0xFF) or
                ((this[position + 1].toLong() and 0xFF) shl 8) or
                ((this[position + 2].toLong() and 0xFF) shl 16) or
                ((this[position + 3].toLong() and 0xFF) shl 24) or
                ((this[position + 4].toLong() and 0xFF) shl 32)
    }

    fun ByteBuffer.get6Bytes(position: Int): Long {
        return (this[position].toLong() and 0xFF) or
                ((this[position + 1].toLong() and 0xFF) shl 8) or
                ((this[position + 2].toLong() and 0xFF) shl 16) or
                ((this[position + 3].toLong() and 0xFF) shl 24) or
                ((this[position + 4].toLong() and 0xFF) shl 32) or
                ((this[position + 5].toLong() and 0xFF) shl 40)
    }

    fun ByteBuffer.get7Bytes(position: Int): Long {
        return (this[position].toLong() and 0xFF) or
                ((this[position + 1].toLong() and 0xFF) shl 8) or
                ((this[position + 2].toLong() and 0xFF) shl 16) or
                ((this[position + 3].toLong() and 0xFF) shl 24) or
                ((this[position + 4].toLong() and 0xFF) shl 32) or
                ((this[position + 5].toLong() and 0xFF) shl 40) or
                ((this[position + 6].toLong() and 0xFF) shl 48)
    }


    /*
     * This file contains functions for generating our intermediate bytecode from the source data.
     */


    fun compileTopLevel(
        src: ByteBuffer,
        pos: Int,
        dest: IntList,
        cp: MutableList<Any?>,
        macSrc: IntArray,
        /**
         * A lookup table indicating the start position of each macro within the macroSource array.
         * This is always one larger than the number of macros so that `macroIndices[macroId + 1]` can be used to
         * find the end of the macro bytecode.
         */
        macIdx: IntArray,
        symTab: Array<String?>,
        limit: Int
    ): Int {
        var p = pos
        var opcode = 0
        val end = min(pos + limit, src.limit())
        while (p < end && (opcode or 0x7 != 0XE7)) {
            opcode = src[p++].toInt() and 0xFF
            val handler = OP_HANDLERS[opcode]
            p += handler.writeBytecode(src, p, opcode, dest, cp, macSrc, macIdx, symTab)

            // Possible starting point for using a refillable source buffer.
//        val checkpoint = dest.size()
//        p += handler.writeBytecode(src, p, opcode, dest, cp, macSrc, macIdx, symTab)
//        try {
//            p += handler.writeBytecode(src, p, opcode, dest, cp, macSrc, macIdx, symTab)
//        } catch (e: ArrayIndexOutOfBoundsException) {
//            // Basically, if we get an index-out-of-bounds error or something like that...
//
//            // Reset the output to the last known good position.
//            dest.truncate(checkpoint)
//            // Reset the position to the last known good position
//            p--
//
//            // And then exit normally to produce either EOF or REFILL.
//            break
//            // FIXME—this has the potential to cause an infinite loop for incomplete data.
//            //       We should try to detect whether the situation is actually refillable or not. E.g.:
//            // val finalBytecodeOperation = if (source.isRefillable) Bytecode.REFILL else Bytecode.ERROR
//            // But also, if it's not refillable, we may want to expose as much data as possible rather than
//            // truncating to the last-known, good, top-level value.
//        }
        }
        val finalBytecodeOperation = if (p >= src.limit()) Bytecode.EOF else Bytecode.REFILL
        dest.add(finalBytecodeOperation.opToInstruction())
        return p - pos
    }

    internal fun testCompileValue(
        src: ByteBuffer,
        pos: Int,
        op: Int,
        dest: IntList,
        cp: MutableList<Any?>,
        macSrc: IntArray,
        macIdx: IntArray,
        symTab: Array<String?>,
    ) = compileValue(src, pos, op, dest, cp, macSrc, macIdx, symTab)

    private fun compileValue(
        src: ByteBuffer,
        pos: Int,
        op: Int,
        dest: IntList,
        cp: MutableList<Any?>,
        macSrc: IntArray,
        /**
         * A lookup table indicating the start position of each macro within the macroSource array.
         * This is always one larger than the number of macros so that `macroIndices[macroId + 1]` can be used to
         * find the end of the macro bytecode.
         */
        macIdx: IntArray,
        symTab: Array<String?>,
    ): Int {
        var handler = OP_HANDLERS[op]
        var p = pos
        var op = op
        while (op == Ops.ANNOTATION_TEXT || op == Ops.ANNOTATION_SID) {
            p += handler.writeBytecode(src, p, op, dest, cp, macSrc, macIdx, symTab)
            op = src[p++].toInt() and 0xFF
            handler = OP_HANDLERS[op]
        }
        p += handler.writeBytecode(src, p, op, dest, cp, macSrc, macIdx, symTab)
        return p - pos
    }



    private fun interface WriteBytecode {
        fun writeBytecode(
            src: ByteBuffer,
            pos: Int,
            op: Int,
            dest: IntList,
            cp: MutableList<Any?>,
            macSrc: IntArray,
            macIdx: IntArray,
            symTab: Array<String?>,
        ): Int
    }


    private fun evalMacro(
        macSrcStart: Int,
        src: ByteBuffer,
        pos: Int,
        dest: IntList,
        cp: MutableList<Any?>,
        macSrc: IntArray,
        macIdx: IntArray,
        symTab: Array<String?>,
    ): Int {
        var p = pos

        val containerPositions = IntArray(32)
        var containerStackSize = 0

        var i = macSrcStart

        while (true) {
            val instruction = macSrc[i++]
            val op = instruction ushr Bytecode.OPERATION_SHIFT_AMOUNT

            // TODO: See if this performs better using array lookups.
            when (op) {
                // Data model operations

                Bytecode.OP_REF_INT,
                Bytecode.OP_REF_DECIMAL,
                Bytecode.OP_REF_TIMESTAMP_SHORT,
                Bytecode.OP_REF_TIMESTAMP_LONG,
                Bytecode.OP_REF_STRING,
                Bytecode.OP_REF_SYMBOL_TEXT,
                Bytecode.OP_REF_CLOB,
                Bytecode.OP_REF_BLOB,
                Bytecode.OP_REF_FIELD_NAME_TEXT,
                Bytecode.OP_REF_ANNOTATION,

                Bytecode.OP_INLINE_FLOAT,
                Bytecode.OP_INLINE_INT, -> {
                    dest.add2(instruction, macSrc[i++])
                }

                Bytecode.OP_CP_BIG_INT,
                Bytecode.OP_CP_DECIMAL,
                Bytecode.OP_CP_TIMESTAMP,
                Bytecode.OP_CP_STRING,
                Bytecode.OP_CP_SYMBOL_TEXT,
                Bytecode.OP_CP_CLOB,
                Bytecode.OP_CP_BLOB,
                Bytecode.OP_CP_FIELD_NAME,
                Bytecode.OP_CP_ANNOTATION,

                Bytecode.OP_SYMBOL_SID,
                Bytecode.OP_FIELD_NAME_SID,
                Bytecode.OP_ANNOTATION_SID,

                Bytecode.OP_BOOL,
                Bytecode.OP_SYMBOL_CHAR,
                Bytecode.OP_SMALL_INT,

                Bytecode.OP_NULL_NULL,
                Bytecode.OP_NULL_BOOL,
                Bytecode.OP_NULL_INT,
                Bytecode.OP_NULL_FLOAT,
                Bytecode.OP_NULL_DECIMAL,
                Bytecode.OP_NULL_TIMESTAMP,
                Bytecode.OP_NULL_STRING,
                Bytecode.OP_NULL_SYMBOL,
                Bytecode.OP_NULL_CLOB,
                Bytecode.OP_NULL_BLOB,
                Bytecode.OP_NULL_LIST,
                Bytecode.OP_NULL_SEXP,
                Bytecode.OP_NULL_STRUCT -> {
                    dest.add(instruction)
                }

                Bytecode.OP_INLINE_DOUBLE,
                Bytecode.OP_INLINE_LONG -> {
                    dest.add3(instruction, macSrc[i++], macSrc[i++])
                }

                Bytecode.OP_LIST_START,
                Bytecode.OP_SEXP_START,
                Bytecode.OP_STRUCT_START -> {
                    containerPositions[containerStackSize++] = dest.size()
                    dest.add(instruction)
                }

                Bytecode.OP_CONTAINER_END -> {
                    val containerStartIndex = containerPositions[--containerStackSize]
                    val start = containerStartIndex + 1
                    dest.add(instruction)
                    val containerEndIndex = dest.size()
                    val startInstruction = dest[containerStartIndex]
                    dest[containerStartIndex] = startInstruction.instructionToOp().opToInstruction(containerEndIndex - start)
                }

                Bytecode.OP_PLACEHOLDER -> {
                    val destSize = dest.size()
                    val argOpcode = src[p++].toInt() and 0xFF
                    p += compileValue(src, p, argOpcode, dest, cp, macSrc, macIdx, symTab)
                    val substitutionSize = dest.size() - destSize
                    // hasValue will be all 1s if substitution size is non-zero, causing the default value to be skipped over.
                    val hasValue = ((substitutionSize * -1) shr 31)
                    i += hasValue and instruction and Bytecode.DATA_MASK
                }
                Bytecode.OP_TAGLESS_PLACEHOLDER -> {
                    // Get the opcode from the instruction.
                    val argumentOpcode = instruction and Bytecode.DATA_MASK
                    val handler = TAGLESS_OP_HANDLERS[argumentOpcode]
                    p += handler.writeBytecode(src, p, argumentOpcode, dest, cp, macSrc, macIdx, symTab)
                }
                Bytecode.EOF -> break
                Bytecode.REFILL -> TODO("Unreachable: ${Bytecode(instruction)}")
                Bytecode.ABSENT_ARGUMENT -> dest.add(instruction)
                else -> {
                    TODO("${Bytecode(instruction)} at i_macro = ${i - 1}; i_source = $pos")
                }
            }
        }
        return p - pos
    }


    private val OPCODE_IS_MAC_ID = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        evalMacro(macIdx[op], src, pos, dest, cp, macSrc, macIdx, symTab)
    }

    private val TWELVE_BIT_MAC_ID = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val bias = (op and 0xF) * 256 + 64
        val unbiasedId = src.get(pos).toInt() and 0xFF
        val macAddr = unbiasedId + bias
        1 + evalMacro(macIdx[macAddr], src, pos + 1, dest, cp, macSrc, macIdx, symTab)
    }

    private val TWENTY_BIT_MAC_ID = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val bias = (op and 0xF) * 65536 + 4160
        val unbiasedId = src.getShort(pos).toInt() and 0xFFFF
        val macAddr = unbiasedId + bias
        2 + evalMacro(macIdx[macAddr], src, pos + 2, dest, cp, macSrc, macIdx, symTab)
    }

    private val TX_INT_ZERO = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt16Value(
        dest,
        0
    ); 0}
    private val TX_INT_8 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt16Value(
        dest,
        src.get(pos).toShort()
    ); 1 }
    private val TX_INT_16 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt16Value(
        dest,
        src.getShort(pos)
    ); 2 }
    private val TX_INT_24 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt32Value(
        dest,
        src.get3Bytes(pos)
    ); 3 }
    private val TX_INT_32 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt32Value(
        dest,
        src.getInt(pos)
    ); 4 }
    private val TX_INT_40 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt64Value(
        dest,
        src.get5Bytes(pos)
    ); 5 }
    private val TX_INT_48 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt64Value(
        dest,
        src.get6Bytes(pos)
    ); 6 }
    private val TX_INT_56 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt64Value(
        dest,
        src.get7Bytes(pos)
    ); 7 }
    private val TX_INT_64 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitInt64Value(
        dest,
        src.getLong(pos)
    ); 8 }
    private val TX_FLOAT_ZERO = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitFloatValue(
        dest,
        0.0f
    ); 0 }
    private val TX_FLOAT_16 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> TODO("Read a half-precision float and emit it as single-precision float in bytecode"); 2 }
    private val TX_FLOAT_32 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitFloatValue(
        dest,
        src.getFloat(pos)
    ); 4 }
    private val TX_FLOAT_64 = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitDoubleValue(
        dest,
        src.getDouble(pos)
    ); 8 }
    private val TX_BOOL_TRUE = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitBooleanValue(
        dest,
        true
    ); 0 }
    private val TX_BOOL_FALSE = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitBooleanValue(
        dest,
        false
    ); 0 }
    private val DECIMAL_REF = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitDecimalReference(
        dest,
        pos,
        op and 0xF
    ); op and 0xF }
    private val SHORT_TIMESTAMPS = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitShortTimestampReference(
        dest,
        op,
        pos
    ); TimestampHelper.lengthOfShortTimestamp(op)
    }
    private val VAR_TIMESTAMP_REF = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        BytecodeHelper.emitLongTimestampReference(dest, pos + lengthOfLength, lengthOfValue)
        lengthOfLength + lengthOfValue

    }
    private val STRING_REF = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitStringReference(
        dest,
        pos,
        op and 0xF
    ); op and 0xF }
    private val VAR_STRING_REF = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        BytecodeHelper.emitStringReference(dest, pos + lengthOfLength, lengthOfValue)
        lengthOfValue + lengthOfLength
    }
    private val SYMBOL_REF = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> BytecodeHelper.emitSymbolTextReference(
        dest,
        pos,
        op and 0xF
    ); op and 0xF }
    private val VAR_SYMBOL_REF = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        BytecodeHelper.emitSymbolTextReference(dest, pos + lengthOfLength, lengthOfValue)
        lengthOfValue + lengthOfLength
    }

    private val SYMBOL_SID = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val length = valueAndLength.toInt() and 0xFF
        val sid = (valueAndLength ushr 8).toInt()
        dest.add(Bytecode.OP_SYMBOL_SID.opToInstruction(sid))
        length
    }

    private val SYMBOL_SID_OR_TEXT = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexIntValueAndLengthAt(src, pos)
        val flexIntLength = valueAndLength.toInt() and 0xFF
        val flexIntValue = (valueAndLength ushr 8).toInt()
        if (flexIntValue >= 0) {
            dest.add(Bytecode.OP_SYMBOL_SID.opToInstruction(flexIntValue))
            flexIntLength
        } else {
            val textLength = -1 - flexIntValue
            BytecodeHelper.emitSymbolTextReference(dest, pos + flexIntLength, textLength)
            textLength + flexIntLength
        }
    }



    private val SHORT_PREFIXED_LIST = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val length = op and 0xF
        val end = pos + length
        BytecodeHelper.emitInlineList(dest) {
            while (p < end) {
                val childOp = src[p++].toInt() and 0xFF
                p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        length
    }

    private val VAR_PREFIXED_LIST = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        var p = pos + lengthOfLength
        val end = p + lengthOfValue
        BytecodeHelper.emitInlineList(dest) {
            while (p < end) {
                val childOp = src[p++].toInt() and 0xFF
                p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        lengthOfValue + lengthOfLength
    }

    private val DELIMITED_LIST = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val containerStartIndex = dest.reserve()
        val start = containerStartIndex + 1
        while (true) {
            val childOp = src[p++].toInt() and 0xFF
            if (childOp == 0xF0) break
            p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
        }
        dest.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        val end = dest.size()
        dest[containerStartIndex] = Bytecode.OP_LIST_START.opToInstruction(end - start)
        p - pos
    }


    private val SHORT_PREFIXED_SEXP = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val length = op and 0xF
        val end = pos + length
        BytecodeHelper.emitInlineSexp(dest) {
            while (p < end) {
                val childOp = src[p++].toInt() and 0xFF
                p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        length
    }

    private val VAR_PREFIXED_SEXP = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        var p = pos + lengthOfLength
        val end = p + lengthOfValue
        BytecodeHelper.emitInlineSexp(dest) {
            while (p < end) {
                val childOp = src[p++].toInt() and 0xFF
                p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        lengthOfValue + lengthOfLength
    }

    private val DELIMITED_SEXP = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        BytecodeHelper.emitInlineSexp(dest) {
            while (true) {
                val childOp = src[p++].toInt() and 0xFF
                if (childOp == 0xF0) break
                p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        p - pos
    }

    private val SHORT_PREFIXED_STRUCT = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val length = op and 0xF
        val end = pos + length
        BytecodeHelper.emitInlineStruct(dest) {
            while (p < end) {
                val fieldNameLengthAndValue = IntHelper.readFlexIntValueAndLengthAt(src, p)
                val fieldNameLength = fieldNameLengthAndValue.toInt() and 0xFF
                val fieldNameValue = (fieldNameLengthAndValue ushr 8).toInt()
                p += fieldNameLength

                if (fieldNameValue < 0) {
                    val textLength = -1 - fieldNameValue
                    dest.add(Bytecode.OP_REF_FIELD_NAME_TEXT.opToInstruction(textLength))
                    dest.add(p)
                    p += textLength
                } else {
                    dest.add(Bytecode.OP_FIELD_NAME_SID.opToInstruction(fieldNameValue))
                }

                val childOp = src[p++].toInt() and 0xFF
                p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        length
    }


    private val VAR_PREFIXED_STRUCT = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        var p = pos + lengthOfLength
        val end = p + lengthOfValue
        BytecodeHelper.emitInlineStruct(dest) {
            while (p < end) {
                val fieldNameLengthAndValue = IntHelper.readFlexIntValueAndLengthAt(src, p)
                val fieldNameLength = fieldNameLengthAndValue.toInt() and 0xFF
                val fieldNameValue = (fieldNameLengthAndValue ushr 8).toInt()
                p += fieldNameLength

                if (fieldNameValue < 0) {
                    val textLength = -1 - fieldNameValue
                    dest.add(Bytecode.OP_REF_FIELD_NAME_TEXT.opToInstruction(textLength))
                    dest.add(p)
                    p += textLength
                } else {
                    dest.add(Bytecode.OP_FIELD_NAME_SID.opToInstruction(fieldNameValue))
                }

                val childOp = src[p++].toInt() and 0xFF
                p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        lengthOfValue + lengthOfLength
    }

    private val DELIMITED_STRUCT = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val containerStartIndex = dest.reserve()
        val start = containerStartIndex + 1
        while (true) {
            val fieldNameLengthAndValue = IntHelper.readFlexIntValueAndLengthAt(src, p)
            val fieldNameLength = fieldNameLengthAndValue.toInt() and 0xFF
            val fieldNameValue = (fieldNameLengthAndValue ushr 8).toInt()
            p += fieldNameLength

            if (fieldNameValue < 0) {
                val textLength = -1 - fieldNameValue
                dest.add(Bytecode.OP_REF_FIELD_NAME_TEXT.opToInstruction(textLength))
                dest.add(p)
                p += textLength
            } else {
                dest.add(Bytecode.OP_FIELD_NAME_SID.opToInstruction(fieldNameValue))
            }

            val childOp = src[p++].toInt() and 0xFF
            if (childOp == 0xF0) break
            p += compileValue(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
        }
        dest.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        val end = dest.size()
        dest[containerStartIndex] = Bytecode.OP_STRUCT_START.opToInstruction(end - start)
        p - pos
    }

    private val VAR_BLOB_REF = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        dest.add2(Bytecode.OP_REF_BLOB.opToInstruction(lengthOfValue), pos + lengthOfLength)
        lengthOfValue + lengthOfLength
    }

    private val VAR_CLOB_REF = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        dest.add2(Bytecode.OP_REF_CLOB.opToInstruction(lengthOfValue), pos + lengthOfLength)
        lengthOfValue + lengthOfLength
    }

    private val TAGGED_PARAM = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        dest.add2(Bytecode.OP_PLACEHOLDER.opToInstruction(1), Bytecode.ABSENT_ARGUMENT.opToInstruction()); 0
    }

    private val TAGGED_PARAM_WITH_DEFAULT = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val pIndex = dest.reserve()
        val start = pIndex + 1
        // Compile the default value.
        val defaultValueOp = src[p++].toInt() and 0xFF
        p += compileValueWithFullPooling(src, p, defaultValueOp, dest, cp, macSrc, macIdx, symTab)
        val end = dest.size()
        dest[pIndex] = Bytecode.OP_PLACEHOLDER.opToInstruction(end - start)
        p - pos
    }


    private val TAGLESS_PARAM = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val typeOp = src[pos].toInt() and 0xFF
        when (typeOp) {
            in 0x60..0x68,
            in 0x6B..0x6D,
            in 0xE0..0xE8,
            in 0x82..0x87,
            0xA0, 0xEA,
            0xF8, 0xF9, 0xFA -> dest.add(Bytecode.OP_TAGLESS_PLACEHOLDER.opToInstruction(typeOp))
            else -> throw IonException("Not a valid tagless placeholder type: $typeOp")
        }
        1
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val TAGLESS_ELEMENT_SEQUENCE = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val typeOp = src[p++].toInt() and 0xFF

        val macId = when (typeOp shr 4) {
            0x0, 0x1, 0x2, 0x3 -> typeOp
            0x4 -> {
                val bias = (typeOp and 0xF) * 256 + 64
                val unbiasedId = src.get(p++).toInt() and 0xFF
                unbiasedId + bias
            }
            0x5 -> {
                val bias = (typeOp and 0xF) * 65536 + 4160
                val unbiasedId = src.getShort(p).toInt() and 0xFFFF
                p += 2
                unbiasedId + bias
            }
            0xF -> {
                when (typeOp) {
                    Ops.E_EXPRESSION_WITH_FLEX_UINT_ADDRESS -> {
                        TODO()
                    }
                    Ops.LENGTH_PREFIXED_MACRO_INVOCATION -> {
                        TODO()
                    }
                    else -> -1
                }
            }
            else -> -1
        }

        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, p)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val numberOfChildValues = (valueAndLength ushr 8).toInt()
        p += lengthOfLength

        val containerStartIndex = dest.reserve()
        val start = containerStartIndex + 1
        if (macId < 0) {
            val handler = TAGLESS_OP_HANDLERS[typeOp]
            for (i in 0 until numberOfChildValues) {
                p += handler.writeBytecode(src, p, typeOp, dest, cp, macSrc, macIdx, symTab)
            }
        } else {
            val macSrcStart = macIdx[macId]
            for (i in 0 until numberOfChildValues) {
                p += evalMacro(macSrcStart, src, p, dest, cp, macSrc, macIdx, symTab)
            }
        }
        dest.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        val end = dest.size()
        // Branchless-ly translate from the sequence opcode to the Bytecode instruction.
        val startBytecodeOp = (op - Ops.TAGLESS_ELEMENT_LIST + TokenTypeConst.LIST) shl Bytecode.TOKEN_TYPE_SHIFT_AMOUNT
        dest[containerStartIndex] = startBytecodeOp.opToInstruction(end - start)
        p - pos
    }

    // TODO: Confirm that we don't ever need to add a `Bytecode.NOTHING` here.
//       Seems like we might need to in order to make sure that annotations can be reset.
//       But if we do write it, then it messes up the case when we want to use the default value.
//       Instead, we'll have a default value of "nothing".
    private val NOTHING_ARG = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> 0 }

    private val IVM = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> dest.add(Bytecode.IVM.opToInstruction(src.getShort(pos).toInt() and 0xFFFF)); 3 }

    private val TYPED_NULL_INSTRUCTIONS = intArrayOf(
        Bytecode.OP_NULL_BOOL.opToInstruction(),
        Bytecode.OP_NULL_INT.opToInstruction(),
        Bytecode.OP_NULL_FLOAT.opToInstruction(),
        Bytecode.OP_NULL_DECIMAL.opToInstruction(),
        Bytecode.OP_NULL_TIMESTAMP.opToInstruction(),
        Bytecode.OP_NULL_STRING.opToInstruction(),
        Bytecode.OP_NULL_SYMBOL.opToInstruction(),
        Bytecode.OP_NULL_BLOB.opToInstruction(),
        Bytecode.OP_NULL_CLOB.opToInstruction(),
        Bytecode.OP_NULL_LIST.opToInstruction(),
        Bytecode.OP_NULL_SEXP.opToInstruction(),
        Bytecode.OP_NULL_STRUCT.opToInstruction(),
    )


    @OptIn(ExperimentalStdlibApi::class)
    private val OP_TODO = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> TODO("${op.toHexString()} at $pos}") }

    @OptIn(ExperimentalStdlibApi::class)
    private val OP_INVALID = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> throw IonException("Encountered invalid opcode: ${op.toHexString()}") }

    @OptIn(ExperimentalStdlibApi::class)
    private val OP_INVALID_TAGLESS = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> throw IonException(
        "Encountered invalid opcode for tagless type: ${op.toHexString()}"
    )
    }


    private val DIRECTIVE_OPCODE_TO_BYTECODE_OP = intArrayOf(
        0,
        Bytecode.DIRECTIVE_SET_SYMBOLS,
        Bytecode.DIRECTIVE_ADD_SYMBOLS,
        Bytecode.DIRECTIVE_SET_MACROS,
        Bytecode.DIRECTIVE_ADD_MACROS,
        Bytecode.DIRECTIVE_USE,
        Bytecode.DIRECTIVE_MODULE,
        Bytecode.DIRECTIVE_ENCODING,
    )
    private val DIRECTIVE = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val bytecodeOp = DIRECTIVE_OPCODE_TO_BYTECODE_OP[op and 0xF]
        dest.add(bytecodeOp.opToInstruction())
        while (true) {
            val childOp = src[p++].toInt() and 0xFF
            if (childOp == 0xF0) break
            p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
        }
        dest.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        p - pos
    }

    private val FLEX_INT_HANDLER_INT = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexIntValueAndLengthAt(src, pos)
        val length = valueAndLength.toInt() and 0xFF
        val value = (valueAndLength ushr 8).toInt()
        dest.add2(Bytecode.OP_INLINE_INT.opToInstruction(), value)
        length
    }
    private val FLEX_INT_HANDLER = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val flexIntLength = IntHelper.lengthOfFlexUIntAt(src, pos)
        when (flexIntLength) {
            1, 2, 3, 4 -> {
                val valueAndLength = IntHelper.readFlexIntValueAndLengthAt(src, pos)
                val length = valueAndLength.toInt() and 0xFF
                val value = (valueAndLength ushr 8).toInt()
                dest.add2(Bytecode.OP_INLINE_INT.opToInstruction(), value)
            }
            5, 6, 7, 8, 9 -> {
                val longValue = IntHelper.readFlexIntLongValue(src, pos)
                BytecodeHelper.emitInt64Value(dest, longValue)
            }
            else -> {
                val bigInt = IntHelper.readFlexIntBigIntegerValue(src, pos)
                val cpIndex = cp.size
                cp.add(bigInt)
                dest.add(Bytecode.OP_CP_BIG_INT.opToInstruction(cpIndex))
            }
        }
        flexIntLength
    }

    private val FLEX_UINT_HANDLER = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexIntValueAndLengthAt(src, pos)
        val length = valueAndLength.toInt() and 0xFF
        val value = (valueAndLength ushr 8).toInt()
        dest.add2(Bytecode.OP_INLINE_INT.opToInstruction(), value)
        length
    }
    private val FIXED_UINT_HANDLER = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val length = op - 0xE0
        val value = IntHelper.readFixedUIntAt(src, pos, op - 0xE0)
        // TODO see if using value.countLeadingZeroBits() is more efficient here.
        //      If nothing else, it should result in slightly denser bytecode.
        when (length) {
            1 -> dest.add(Bytecode.OP_SMALL_INT.opToInstruction(value.toShort().toInt()))
            2, 3 -> BytecodeHelper.emitInt32Value(dest, value.toInt())
            4, 5, 6, 7 -> BytecodeHelper.emitInt64Value(dest, value)
            8 -> {
                if (value > 0) {
                    BytecodeHelper.emitInt64Value(dest, value)
                } else {
                    TODO("Expose this as a BigInteger")
                }
            }
        }
        length
    }


    private val OP_HANDLERS = Array<WriteBytecode>(256) { op ->
        when (op) {
            in 0x00..0x3F -> OPCODE_IS_MAC_ID
            in 0x40..0x4F -> TWELVE_BIT_MAC_ID
            in 0x50..0x5F -> TWENTY_BIT_MAC_ID
            0x60 -> TX_INT_ZERO
            0x61 -> TX_INT_8
            0x62 -> TX_INT_16
            0x63 -> TX_INT_24
            0x64 -> TX_INT_32
            0x65 -> TX_INT_40
            0x66 -> TX_INT_48
            0x67 -> TX_INT_56
            0x69 -> OP_INVALID
            0x68 -> TX_INT_64
            0x6A -> TX_FLOAT_ZERO
            0x6B -> TX_FLOAT_16
            0x6C -> TX_FLOAT_32
            0x6D -> TX_FLOAT_64
            0x6E -> TX_BOOL_TRUE
            0x6F -> TX_BOOL_FALSE

            in 0x70..0x7F -> DECIMAL_REF

            in 0x80..0x8C -> SHORT_TIMESTAMPS
            0x8D -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
                val sidAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
                val length = sidAndLength.toInt() and 0xFF
                val sid = (sidAndLength ushr 8).toInt()
                dest.add(Bytecode.OP_ANNOTATION_SID.opToInstruction(sid))
                length
            }
            0x8E -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
                val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
                val lengthOfLength = valueAndLength.toInt() and 0xFF
                val lengthOfText = (valueAndLength ushr 8).toInt()
                dest.add2(Bytecode.OP_REF_ANNOTATION.opToInstruction(lengthOfText), pos + lengthOfLength)
                lengthOfLength + lengthOfText
            }


            // Nulls
            0x8F -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> dest.add(Bytecode.OP_NULL_NULL.opToInstruction()); 0}
            0x90 -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> dest.add(TYPED_NULL_INSTRUCTIONS[src[pos].toInt()]); 1 }


            in 0x91..0x9F -> STRING_REF

            0xA0 -> SYMBOL_SID
            in 0xA1..0xAF -> SYMBOL_REF

            in 0xB0 .. 0xBF -> SHORT_PREFIXED_LIST

            in 0xC0 .. 0xCF -> SHORT_PREFIXED_SEXP

            0xD1 -> OP_INVALID
            in 0xD0 .. 0xDF -> SHORT_PREFIXED_STRUCT

            0xE0 -> IVM
            in 0xE1 .. 0xE7 -> DIRECTIVE
            0xE8 -> NOTHING_ARG

            // These should be unreachable.
//        0xE9 -> TAGGED_PARAM
//        0xEA -> TAGGED_PARAM_WITH_DEFAULT
//        0xEB -> TAGLESS_PARAM

            // List and Struct
            0xEC -> TAGLESS_ELEMENT_SEQUENCE
            0xED -> TAGLESS_ELEMENT_SEQUENCE

            // NOPs
            0xEE -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> 0 }
            0xEF -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
                IntHelper.getLengthPlusValueOfFlexUIntAt(
                    src,
                    pos
                )
            }

            0xF0 -> OP_TODO
            0xF1 -> DELIMITED_LIST
            0xF2 -> DELIMITED_SEXP
            0xF3 -> DELIMITED_STRUCT

            0xF8 -> VAR_TIMESTAMP_REF

            0xF9 -> VAR_STRING_REF
            0xFA -> VAR_SYMBOL_REF
            0xFB -> VAR_PREFIXED_LIST
            0xFC -> VAR_PREFIXED_SEXP
            0xFD -> VAR_PREFIXED_STRUCT
            0xFE -> VAR_BLOB_REF
            0xFF -> VAR_CLOB_REF

            in 0..255 -> OP_TODO
            else -> TODO("unreachable")
        }
    }

    private val TAGLESS_OP_HANDLERS = Array<WriteBytecode>(256) { op ->
        when (op) {
            0x60 -> FLEX_INT_HANDLER
            0x61 -> TX_INT_8
            0x62 -> TX_INT_16
            0x63 -> TX_INT_24
            0x64 -> TX_INT_32
            0x65 -> TX_INT_40
            0x66 -> TX_INT_48
            0x67 -> TX_INT_56
            0x68 -> TX_INT_64
            0x6B -> TX_FLOAT_16
            0x6C -> TX_FLOAT_32
            0x6D -> TX_FLOAT_64
            // in 0x80..0x8C -> SHORT_TIMESTAMPS
            0xA0 -> SYMBOL_SID
            0xE0 -> FLEX_UINT_HANDLER
            in 0xE1 .. 0xE8 -> FIXED_UINT_HANDLER
            0xEA -> SYMBOL_SID_OR_TEXT // symbol sid or text
            0xF8 -> VAR_TIMESTAMP_REF
            0xF9 -> VAR_STRING_REF
            0xFA -> VAR_SYMBOL_REF
            else -> OP_INVALID_TAGLESS
        }
    }


    fun compileValueWithFullPooling(
        src: ByteBuffer,
        pos: Int,
        op: Int,
        dest: IntList,
        cp: MutableList<Any?>,
        macSrc: IntArray,
        /**
         * A lookup table indicating the start position of each macro within the macroSource array.
         * This is always one larger than the number of macros so that `macroIndices[macroId + 1]` can be used to
         * find the end of the macro bytecode.
         */
        macIdx: IntArray,
        symTab: Array<String?>,
    ): Int {
        var handler = OP_HANDLERS_WITH_FULL_POOLING[op]
        var p = pos
        var op = op
        while (op == Ops.ANNOTATION_TEXT || op == Ops.ANNOTATION_SID) {
            p += handler.writeBytecode(src, p, op, dest, cp, macSrc, macIdx, symTab)
            op = src[p++].toInt() and 0xFF
            handler = OP_HANDLERS_WITH_FULL_POOLING[op]
        }
        p += handler.writeBytecode(src, p, op, dest, cp, macSrc, macIdx, symTab)
        return p - pos
    }

    private fun evalMacroWithFullPooling(
        macSrcStart: Int,
        src: ByteBuffer,
        pos: Int,
        dest: IntList,
        cp: MutableList<Any?>,
        macSrc: IntArray,
        macIdx: IntArray,
        symTab: Array<String?>,
    ): Int {
        var p = pos

        val containerPositions = IntArray(32)
        var containerStackSize = 0

        var i = macSrcStart

        while (true) {
            val instruction = macSrc[i++]
            val op = instruction ushr Bytecode.OPERATION_SHIFT_AMOUNT

            // TODO: See if this performs better using array lookups.
            when (op) {
                // Data model operations

                Bytecode.OP_REF_INT,
                Bytecode.OP_REF_DECIMAL,
                Bytecode.OP_REF_TIMESTAMP_SHORT,
                Bytecode.OP_REF_TIMESTAMP_LONG,
                Bytecode.OP_REF_STRING,
                Bytecode.OP_REF_SYMBOL_TEXT,
                Bytecode.OP_REF_CLOB,
                Bytecode.OP_REF_BLOB,
                Bytecode.OP_REF_FIELD_NAME_TEXT,
                Bytecode.OP_REF_ANNOTATION -> dest.add2(instruction, macSrc[i++])

                Bytecode.OP_CP_BIG_INT,
                Bytecode.OP_CP_DECIMAL,
                Bytecode.OP_CP_TIMESTAMP,
                Bytecode.OP_CP_STRING,
                Bytecode.OP_CP_SYMBOL_TEXT,
                Bytecode.OP_CP_CLOB,
                Bytecode.OP_CP_BLOB,
                Bytecode.OP_CP_FIELD_NAME,
                Bytecode.OP_CP_ANNOTATION -> dest.add(instruction)

                Bytecode.OP_INLINE_FLOAT,
                Bytecode.OP_INLINE_INT -> dest.add2(instruction, macSrc[i++])

                Bytecode.OP_INLINE_DOUBLE,
                Bytecode.OP_INLINE_LONG -> dest.add3(instruction, macSrc[i++], macSrc[i++])

                Bytecode.OP_SYMBOL_SID,
                Bytecode.OP_FIELD_NAME_SID,
                Bytecode.OP_ANNOTATION_SID -> dest.add(instruction)

                Bytecode.OP_BOOL,
                Bytecode.OP_SYMBOL_CHAR,
                Bytecode.OP_SMALL_INT -> dest.add(instruction)

                Bytecode.OP_NULL_NULL,
                Bytecode.OP_NULL_BOOL,
                Bytecode.OP_NULL_INT,
                Bytecode.OP_NULL_FLOAT,
                Bytecode.OP_NULL_DECIMAL,
                Bytecode.OP_NULL_TIMESTAMP,
                Bytecode.OP_NULL_STRING,
                Bytecode.OP_NULL_SYMBOL,
                Bytecode.OP_NULL_CLOB,
                Bytecode.OP_NULL_BLOB,
                Bytecode.OP_NULL_LIST,
                Bytecode.OP_NULL_SEXP,
                Bytecode.OP_NULL_STRUCT -> dest.add(instruction)

                Bytecode.OP_LIST_START,
                Bytecode.OP_SEXP_START,
                Bytecode.OP_STRUCT_START -> {
                    containerPositions[containerStackSize++] = dest.size()
                    dest.add(instruction)
                }

                Bytecode.OP_CONTAINER_END -> {
                    val containerStartIndex = containerPositions[--containerStackSize]
                    val start = containerStartIndex + 1
                    dest.add(instruction)
                    val containerEndIndex = dest.size()
                    val startInstruction = dest[containerStartIndex]
                    dest[containerStartIndex] = startInstruction.instructionToOp().opToInstruction(containerEndIndex - start)
                }

                Bytecode.OP_PLACEHOLDER -> {
                    val destSize = dest.size()
                    val argOpcode = src[p++].toInt() and 0xFF
                    p += compileValueWithFullPooling(src, p, argOpcode, dest, cp, macSrc, macIdx, symTab)
                    val substitutionSize = dest.size() - destSize
                    // hasValue will be all 1s if substitution size is non-zero, causing the default value to be skipped over.
                    val hasValue = ((substitutionSize * -1) shr 31)
                    i += hasValue and instruction and Bytecode.DATA_MASK
                }
                Bytecode.OP_TAGLESS_PLACEHOLDER -> {
                    val argOpcode = instruction and Bytecode.DATA_MASK
                    val handler = TAGLESS_OP_HANDLERS_WITH_FULL_POOLING[argOpcode]
                    p += handler.writeBytecode(src, p, argOpcode, dest, cp, macSrc, macIdx, symTab)
                }
//            Bytecode.OP_MACRO_SHAPED_PARAMETER -> {
//                val macroId = instruction and Bytecode.DATA_MASK
//                p += evalMacroWithFullPooling(macIdx[macroId], src, p, dest, cp, macSrc, macIdx, symTab)
//            }
                Bytecode.EOF -> break
                Bytecode.REFILL -> TODO("Unreachable: ${Bytecode(instruction)}")
                else -> TODO("${Bytecode(instruction)} at ${i - 1}")
            }
        }
        return p - pos
    }


    private val OPCODE_IS_MAC_ID_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        evalMacroWithFullPooling(macIdx[op], src, pos, dest, cp, macSrc, macIdx, symTab)
    }

    private val TWELVE_BIT_MAC_ID_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val bias = (op and 0xF) * 256 + 64
        val unbiasedId = src.get(pos).toInt() and 0xFF
        val macAddr = unbiasedId + bias
        1 + evalMacroWithFullPooling(macIdx[macAddr], src, pos + 1, dest, cp, macSrc, macIdx, symTab)
    }

    private val TWENTY_BIT_MAC_ID_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val bias = (op and 0xF) * 65536 + 4160
        val unbiasedId = src.getShort(pos).toInt() and 0xFFFF
        val macAddr = unbiasedId + bias
        2 + evalMacroWithFullPooling(macIdx[macAddr], src, pos + 2, dest, cp, macSrc, macIdx, symTab)
    }

    private val DECIMAL_REF_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val cpIndex = cp.size
        cp.add(DecimalHelper.readDecimal(src, pos, op and 0xF))
        dest.add(Bytecode.OP_CP_DECIMAL.opToInstruction(cpIndex))
        op and 0xF
    }
    private val SHORT_TIMESTAMPS_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val cpIndex = cp.size
        cp.add(TimestampHelper.readShortTimestampAt(op, src, pos))
        dest.add(Bytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
        TimestampHelper.lengthOfShortTimestamp(op)
    }
    private val STRING_REF_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val length = op and 0xF
        val s = src.readString(pos, length)
        val cpIndex = cp.size
        cp.add(s)
        dest.add(Bytecode.OP_CP_STRING.opToInstruction(cpIndex))
        length
    }
    private val VAR_STRING_REF_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        val s = src.readString(pos + lengthOfLength, lengthOfValue)
        val cpIndex = cp.size
        cp.add(s)
        dest.add(Bytecode.OP_CP_STRING.opToInstruction(cpIndex))
        lengthOfValue + lengthOfLength
    }
    private val SYMBOL_REF_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val s = src.readString(pos, op and 0xF)
        val cpIndex = cp.size
        cp.add(s)
        dest.add(Bytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
        op and 0xF
    }
    private val VAR_SYMBOL_REF_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        val s = src.readString(pos + lengthOfLength, lengthOfValue)
        val cpIndex = cp.size
        cp.add(s)
        dest.add(Bytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
        lengthOfValue + lengthOfLength
    }
    private val SYMBOL_SID_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val length = valueAndLength.toInt() and 0xFF
        val sid = (valueAndLength ushr 8).toInt()
        val cpIndex = cp.size
        cp.add(symTab[sid])
        dest.add(Bytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
        length
    }

    private val SYMBOL_SID_OR_TEXT_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexIntValueAndLengthAt(src, pos)
        val flexIntLength = valueAndLength.toInt() and 0xFF
        val flexIntValue = (valueAndLength ushr 8).toInt()
        if (flexIntValue >= 0) {
            val cpIndex = cp.size
            cp.add(symTab[flexIntValue])
            dest.add(Bytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
            flexIntLength
        } else {
            val textLength = -1 - flexIntValue
            val s = src.readString(pos + flexIntLength, textLength)
            val cpIndex = cp.size
            cp.add(s)
            dest.add(Bytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
            textLength + flexIntLength
        }
    }

    private val SHORT_PREFIXED_LIST_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val length = op and 0xF
        val end = pos + length
        BytecodeHelper.emitInlineList(dest) {
            while (p < end) {
                val childOp = src[p++].toInt() and 0xFF
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        length
    }

    private val VAR_PREFIXED_LIST_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        var p = pos + lengthOfLength
        val end = p + lengthOfValue
        BytecodeHelper.emitInlineList(dest) {
            while (p < end) {
                val childOp = src[p++].toInt() and 0xFF
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        lengthOfValue + lengthOfLength
    }

    private val DELIMITED_LIST_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        BytecodeHelper.emitInlineList(dest) {
            while (true) {
                val childOp = src[p++].toInt() and 0xFF
                if (childOp == 0xF0) break
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        p - pos
    }


    private val SHORT_PREFIXED_SEXP_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val length = op and 0xF
        val end = pos + length
        BytecodeHelper.emitInlineSexp(dest) {
            while (p < end) {
                val childOp = src[p++].toInt() and 0xFF
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        length
    }

    private val VAR_PREFIXED_SEXP_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        var p = pos + lengthOfLength
        val end = p + lengthOfValue
        BytecodeHelper.emitInlineSexp(dest) {
            while (p < end) {
                val childOp = src[p++].toInt() and 0xFF
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        lengthOfValue + lengthOfLength
    }

    private val DELIMITED_SEXP_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        BytecodeHelper.emitInlineSexp(dest) {
            while (true) {
                val childOp = src[p++].toInt() and 0xFF
                if (childOp == 0xF0) break
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        p - pos
    }

    private val SHORT_PREFIXED_STRUCT_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val length = op and 0xF
        val end = pos + length
        BytecodeHelper.emitInlineStruct(dest) {
            while (p < end) {
                val fieldNameLengthAndValue = IntHelper.readFlexIntValueAndLengthAt(src, p)
                val fieldNameLength = fieldNameLengthAndValue.toInt() and 0xFF
                val fieldNameValue = (fieldNameLengthAndValue ushr 8).toInt()
                p += fieldNameLength

                val text = if (fieldNameValue < 0) {
                    // TODO: try the decoder pool, as in IonContinuableCoreBinary
                    val textLength = -1 - fieldNameValue
                    val s = src.readString(p, textLength)
                    p += textLength
                    s
                } else {
                    symTab[fieldNameValue]
                }
                val cpIndex = cp.size
                cp.add(text)
                dest.add(Bytecode.OP_CP_FIELD_NAME.opToInstruction(cpIndex))


                val childOp = src[p++].toInt() and 0xFF
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        length
    }


    private val VAR_PREFIXED_STRUCT_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        var p = pos + lengthOfLength
        val end = p + lengthOfValue
        BytecodeHelper.emitInlineStruct(dest) {
            while (p < end) {
                val fieldNameLengthAndValue = IntHelper.readFlexIntValueAndLengthAt(src, p)
                val fieldNameLength = fieldNameLengthAndValue.toInt() and 0xFF
                val fieldNameValue = (fieldNameLengthAndValue ushr 8).toInt()
                p += fieldNameLength

                val text = if (fieldNameValue < 0) {
                    // TODO: try the decoder pool, as in IonContinuableCoreBinary
                    val textLength = -1 - fieldNameValue
                    val s = String(src, p, textLength, StandardCharsets.UTF_8)
                    p += textLength
                    s
                } else {
                    symTab[fieldNameValue]
                }
                val cpIndex = cp.size
                cp.add(text)
                dest.add(Bytecode.OP_CP_FIELD_NAME.opToInstruction(cpIndex))


                val childOp = src[p++].toInt() and 0xFF
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        lengthOfValue + lengthOfLength
    }

    private val DELIMITED_STRUCT_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        BytecodeHelper.emitInlineStruct(dest) {
            while (true) {
                val fieldNameLengthAndValue = IntHelper.readFlexIntValueAndLengthAt(src, p)
                val fieldNameLength = fieldNameLengthAndValue.toInt() and 0xFF
                val fieldNameValue = (fieldNameLengthAndValue ushr 8).toInt()
                p += fieldNameLength

                val text = if (fieldNameValue < 0) {
                    // TODO: try the decoder pool, as in IonContinuableCoreBinary
                    val textLength = -1 - fieldNameValue
                    val s = String(src, p, textLength, StandardCharsets.UTF_8)
                    p += textLength
                    s
                } else {
                    symTab[fieldNameValue]
                }
//            println(text)
                val cpIndex = cp.size
                cp.add(text)
                dest.add(Bytecode.OP_CP_FIELD_NAME.opToInstruction(cpIndex))

                val childOp = src[p++].toInt() and 0xFF
                if (childOp == 0xF0) {
                    // TODO: We could trim the last field name.
                    break
                }
                p += compileValueWithFullPooling(src, p, childOp, dest, cp, macSrc, macIdx, symTab)
            }
        }
        p - pos
    }

    fun ByteArraySlice(byteBuffer: ByteBuffer, startInclusive: Int, endInclusive: Int): ByteArraySlice {
        val p = byteBuffer.position()
        val l = byteBuffer.limit()
        byteBuffer.limit(endInclusive)
        byteBuffer.position(startInclusive)
        val byteArray = ByteArray(endInclusive - startInclusive)
        byteBuffer.get(byteArray)
        byteBuffer.limit(l)
        byteBuffer.position(p)
        return ByteArraySlice(byteArray, 0, byteArray.size)
    }

    private val VAR_BLOB_REF_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        val slice = ByteArraySlice(src, pos + lengthOfLength, pos + lengthOfLength + lengthOfValue)
        val cpIndex = cp.size
        cp.add(slice)
        dest.add(Bytecode.OP_CP_BLOB.opToInstruction(cpIndex))
        lengthOfValue + lengthOfLength
    }

    private val VAR_CLOB_REF_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val lengthOfValue = (valueAndLength ushr 8).toInt()
        val array = ByteArray(lengthOfValue)
        src.get(array)
        val slice = ByteArraySlice(src, pos + lengthOfLength, pos + lengthOfLength + lengthOfValue)
        val cpIndex = cp.size
        cp.add(slice)
        dest.add(Bytecode.OP_CP_CLOB.opToInstruction(cpIndex))
        lengthOfValue + lengthOfLength
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val TAGLESS_ELEMENT_SEQUENCE_WITH_FULL_POOLING = WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
        var p = pos
        val typeOp = src[p++].toInt() and 0xFF

        // println("typeOp: ${typeOp.toHexString()}")

        val macId = when (typeOp shr 4) {
            0x0, 0x1, 0x2, 0x3 -> typeOp
            0x4 -> {
                val bias = (typeOp and 0xF) * 256 + 64
                val unbiasedId = src.get(p++).toInt() and 0xFF
                unbiasedId + bias
            }
            0x5 -> {
                val bias = (typeOp and 0xF) * 65536 + 4160
                val unbiasedId = src.getShort(p).toInt() and 0xFFFF
                p += 2
                unbiasedId + bias
            }
            0xF -> {
                when (typeOp) {
                    Ops.E_EXPRESSION_WITH_FLEX_UINT_ADDRESS -> {
                        TODO()
                    }
                    Ops.LENGTH_PREFIXED_MACRO_INVOCATION -> {
                        TODO()
                    }
                    else -> -1
                }
            }
            else -> -1
        }

        val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, p)
        val lengthOfLength = valueAndLength.toInt() and 0xFF
        val numberOfChildValues = (valueAndLength ushr 8).toInt()
        p += lengthOfLength

        val containerStartIndex = dest.reserve()
        val start = containerStartIndex + 1
        if (macId < 0) {
            val handler = TAGLESS_OP_HANDLERS_WITH_FULL_POOLING[typeOp]
            for (i in 0 until numberOfChildValues) {
                p += handler.writeBytecode(src, p, typeOp, dest, cp, macSrc, macIdx, symTab)
            }
        } else {
            val macSrcStart = macIdx[macId]
            for (i in 0 until numberOfChildValues) {
                p += evalMacroWithFullPooling(macSrcStart, src, p, dest, cp, macSrc, macIdx, symTab)
            }
        }
        dest.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        val end = dest.size()
        // Branchless-ly translate from the sequence opcode to the Bytecode instruction.
        val startBytecodeOp = (op - Ops.TAGLESS_ELEMENT_LIST + TokenTypeConst.LIST) shl Bytecode.TOKEN_TYPE_SHIFT_AMOUNT
        dest[containerStartIndex] = startBytecodeOp.opToInstruction(end - start)
        p - pos
    }


    private val TAGLESS_OP_HANDLERS_WITH_FULL_POOLING = Array<WriteBytecode>(256) { op ->
        when (op) {
            0x60 -> FLEX_INT_HANDLER
            0x61 -> TX_INT_8
            0x62 -> TX_INT_16
            0x63 -> TX_INT_24
            0x64 -> TX_INT_32
            0x65 -> TX_INT_40
            0x66 -> TX_INT_48
            0x67 -> TX_INT_56
            0x68 -> TX_INT_64
            0x6B -> TX_FLOAT_16
            0x6C -> TX_FLOAT_32
            0x6D -> TX_FLOAT_64
            // in 0x80..0x8C -> SHORT_TIMESTAMPS
            0xA0 -> SYMBOL_SID
            0xE0 -> FLEX_UINT_HANDLER
            in 0xE1 .. 0xE8 -> FIXED_UINT_HANDLER
            0xEA -> SYMBOL_SID_OR_TEXT_WITH_FULL_POOLING // symbol sid or text
            0xF8 -> OP_TODO // Long Timestamp
            0xF9 -> VAR_STRING_REF_WITH_FULL_POOLING
            0xFA -> VAR_SYMBOL_REF_WITH_FULL_POOLING
            else -> OP_INVALID_TAGLESS
        }
    }


    private val OP_HANDLERS_WITH_FULL_POOLING = Array<WriteBytecode>(256) { op ->
        when (op) {
            in 0x00..0x3F -> OPCODE_IS_MAC_ID_WITH_FULL_POOLING
            in 0x40..0x4F -> TWELVE_BIT_MAC_ID_WITH_FULL_POOLING
            in 0x50..0x5F -> TWENTY_BIT_MAC_ID_WITH_FULL_POOLING
            0x60 -> TX_INT_ZERO
            0x61 -> TX_INT_8
            0x62 -> TX_INT_16
            0x63 -> TX_INT_24
            0x64 -> TX_INT_32
            0x65 -> TX_INT_40
            0x66 -> TX_INT_48
            0x67 -> TX_INT_56
            0x69 -> OP_INVALID
            0x68 -> TX_INT_64
            0x6A -> TX_FLOAT_ZERO
            0x6B -> TX_FLOAT_16
            0x6C -> TX_FLOAT_32
            0x6D -> TX_FLOAT_64
            0x6E -> TX_BOOL_TRUE
            0x6F -> TX_BOOL_FALSE

            in 0x70..0x7F -> DECIMAL_REF_WITH_FULL_POOLING

            in 0x80..0x8C -> SHORT_TIMESTAMPS_WITH_FULL_POOLING

            0x8D -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
                val sidAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
                val length = sidAndLength.toInt() and 0xFF
                val sid = (sidAndLength ushr 8).toInt()
                val cpIndex = cp.size
                cp.add(symTab[sid])
                dest.add(Bytecode.OP_CP_ANNOTATION.opToInstruction(cpIndex))
                length
            }
            0x8E -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
                val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(src, pos)
                val lengthOfLength = valueAndLength.toInt() and 0xFF
                val lengthOfText = (valueAndLength ushr 8).toInt()
                val s = String(src, pos + lengthOfLength, lengthOfText, StandardCharsets.UTF_8)
                val cpIndex = cp.size
                cp.add(s)
                dest.add(Bytecode.OP_CP_ANNOTATION.opToInstruction(cpIndex))
                lengthOfLength + lengthOfText
            }

            // Nulls
            0x8F -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> dest.add(Bytecode.OP_NULL_NULL.opToInstruction()); 0}
            0x90 -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> dest.add(TYPED_NULL_INSTRUCTIONS[src[pos].toInt()]); 1 }

            in 0x91..0x9F -> STRING_REF_WITH_FULL_POOLING
            0xA0 -> SYMBOL_SID_WITH_FULL_POOLING
            in 0xA1..0xAF -> SYMBOL_REF_WITH_FULL_POOLING
            in 0xB0 .. 0xBF -> SHORT_PREFIXED_LIST_WITH_FULL_POOLING
            in 0xC0 .. 0xCF -> SHORT_PREFIXED_SEXP_WITH_FULL_POOLING
            0xD1 -> OP_INVALID
            in 0xD0 .. 0xDF -> SHORT_PREFIXED_STRUCT_WITH_FULL_POOLING

            0xE0 -> IVM
            in 0xE1 .. 0xE7 -> DIRECTIVE
            0xE8 -> NOTHING_ARG
            0xE9 -> TAGGED_PARAM
            0xEA -> TAGGED_PARAM_WITH_DEFAULT
            0xEB -> TAGLESS_PARAM

            // List and Struct
            0xEC -> TAGLESS_ELEMENT_SEQUENCE_WITH_FULL_POOLING
            0xED -> TAGLESS_ELEMENT_SEQUENCE_WITH_FULL_POOLING

            0xEE -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab -> 0 }
            0xEF -> WriteBytecode { src, pos, op, dest, cp, macSrc, macIdx, symTab ->
                IntHelper.getLengthPlusValueOfFlexUIntAt(
                    src,
                    pos
                )
            }

            0xF0 -> OP_TODO
            0xF1 -> DELIMITED_LIST_WITH_FULL_POOLING
            0xF2 -> DELIMITED_SEXP_WITH_FULL_POOLING
            0xF3 -> DELIMITED_STRUCT_WITH_FULL_POOLING
            // Maybe TODO:
            //  0xF4 -> Macro with flex uint address
            //  0xF5 -> Macro with flex uint address and flexuint length prefix
            // TODO:
            //  0xF6 -> BigInteger
            //  0xF7 -> Var Decimal
            //  0xF8 -> Long Timestamp
            0xF9 -> VAR_STRING_REF_WITH_FULL_POOLING
            0xFA -> VAR_SYMBOL_REF_WITH_FULL_POOLING
            0xFB -> VAR_PREFIXED_LIST_WITH_FULL_POOLING
            0xFC -> VAR_PREFIXED_SEXP_WITH_FULL_POOLING
            0xFD -> VAR_PREFIXED_STRUCT_WITH_FULL_POOLING
            0xFE -> VAR_BLOB_REF_WITH_FULL_POOLING
            0xFF -> VAR_CLOB_REF_WITH_FULL_POOLING

            in 0..255 -> OP_TODO
            else -> TODO("unreachable")
        }
    }








}
