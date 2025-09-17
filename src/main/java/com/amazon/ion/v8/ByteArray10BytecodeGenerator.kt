package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.bin.utf8.*
import com.amazon.ion.v8.ByteArray10BytecodeGenerator.Helpers.ionType
import com.amazon.ion.v8.Bytecode.TOKEN_TYPE_SHIFT_AMOUNT
import com.amazon.ion.v8.Bytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.math.min

class ByteArray10BytecodeGenerator(
    private val source: ByteArray
): BytecodeGenerator {
    private var i = 0

    private val decoder = Utf8StringDecoderPool.getInstance().getOrCreate()
    private val scratch = ByteBuffer.wrap(source)

    override fun refill(
        destination: IntList,
        constantPool: MutableList<Any?>,
        macroSrc: IntArray,
        macroIndices: IntArray,
        symTab: Array<String?>
    ) {

        // Fill 1 top-level value, or until the bytecode buffer is full, whichever comes first.
        val source = source
        var i = i
        i += compileTopLevel(source, i, destination, constantPool, symTab, source.size)
        this.i = i
//        Bytecode.debugString(destination.toArray(), lax = true)
    }

    override fun readBigIntegerReference(position: Int, length: Int): BigInteger {
        TODO("Not yet implemented")
    }

    override fun readDecimalReference(position: Int, length: Int): BigDecimal {
        TODO()
    }

    override fun readShortTimestampReference(position: Int, opcode: Int): Timestamp {
        TODO("Not supported for Ion 1.0")
    }

    override fun readTimestampReference(position: Int, length: Int): Timestamp {
        var p = position
        val end = position + length

        val offset: Int? = if (source.get(p).toInt() and 0xFF == 0xC0) {
            p++
            null
        } else {
            val offsetValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += offsetValueAndLength.toInt() and 0xFF
            (offsetValueAndLength shr 8).toInt()
        }
        val yearValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
        p += yearValueAndLength.toInt() and 0xFF
        val year = (yearValueAndLength shr 8).toInt()
        var month = 0
        var day = 0
        var hour = 0
        var minute = 0
        var second = 0
        var fractionalSecond: BigDecimal? = null
        var precision = Timestamp.Precision.YEAR
        if (p < end) {
            val monthValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += monthValueAndLength.toInt() and 0xFF
            month = (monthValueAndLength shr 8).toInt()
            precision = Timestamp.Precision.MONTH
            if (p < end) {
                val dayValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                p += dayValueAndLength.toInt() and 0xFF
                day = (dayValueAndLength shr 8).toInt()
                precision = Timestamp.Precision.DAY
                if (p < end) {
                    val hourValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += hourValueAndLength.toInt() and 0xFF
                    hour = (hourValueAndLength shr 8).toInt()
                    if (p >= end) {
                        throw IonException("Timestamps may not specify hour without specifying minute.")
                    }

                    val minuteValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += minuteValueAndLength.toInt() and 0xFF
                    minute = (minuteValueAndLength shr 8).toInt()
                    precision = Timestamp.Precision.MINUTE
                    if (p < end) {
                        val secondValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += secondValueAndLength.toInt() and 0xFF
                        second = (secondValueAndLength shr 8).toInt()
                        precision = Timestamp.Precision.SECOND
                        if (p < end) {
                            fractionalSecond = readDecimalContent(source, p, end)
                        }
                    }
                }
            }
        }
        try {
            return Timestamp.createFromUtcFields(
                precision,
                year,
                month,
                day,
                hour,
                minute,
                second,
                fractionalSecond,
                offset
            )
        } catch (e: IllegalArgumentException) {
            println("Timestamp starting at $position")
            throw IonException("Illegal timestamp encoding. ", e)
        }
    }

    fun readDecimalContent(source: ByteArray, position: Int, end: Int): Decimal {
        var p = position
        val exponentValueAndLength = VarIntHelper.readVarIntValueAndLength(source, p)
        p += exponentValueAndLength.toInt() and 0xFF
        val scale = -(exponentValueAndLength shr 8).toInt()

        val coefficientLength = end - p
        return if (coefficientLength > 0) {
            val bytes = ByteArray(coefficientLength)
            val s = scratch
            s.limit(end)
            s.position(p)
            s.get(bytes)
            val signum = if (bytes[0].toInt() and 0x80 == 0) 1 else -1
            bytes[0] = bytes[0] and 0x7F
            val coefficient = BigInteger(signum, bytes)
            if (coefficient == BigInteger.ZERO && signum == -1) {
                Decimal.negativeZero(scale)
            } else {
                Decimal.valueOf(BigInteger(signum, bytes), scale)
            }
        } else {
            Decimal.valueOf(BigInteger.ZERO, scale)
        }
    }



    override fun readTextReference(position: Int, length: Int): String {
        scratch.limit(position + length)
        scratch.position(position)
        return decoder.decode(scratch, length)
    }

    override fun readBytesReference(position: Int, length: Int): ByteArray {
        TODO("Not yet implemented")
    }


    fun ByteArray.getShort(position: Int): Short {
        return ((this[position + 1].toInt() and 0xFF) or ((this[position].toInt() and 0xFF) shl 8)).toShort()
    }

    fun ByteArray.getInt(position: Int): Int {
        return (this[position + 3].toInt() and 0xFF) or
                ((this[position + 2].toInt() and 0xFF) shl 8) or
                ((this[position + 1].toInt() and 0xFF) shl 16) or
                ((this[position].toInt() and 0xFF) shl 24)
    }


    private fun compileTopLevel(
        src: ByteArray,
        pos: Int,
        dest: IntList,
        cp: MutableList<Any?>,
        symTab: Array<String?>,
        limit: Int
    ): Int {

        var p = pos
        var typeId = 0
        val end = min(pos + limit, src.size)

        var firstAnnotationIndex = -1
        var firstAnnotationSid = 0

        while (p < end) {
            typeId = src[p++].toInt() and 0xFF

            when (Helpers.tokenType(typeId)) {
                TokenTypeConst.NOP -> p = skipNop(typeId, source, p)
                TokenTypeConst.IVM -> {
                    // TODO: Stop assuming that IVM is still Ion 1.0
                    dest.add(Bytecode.IVM.opToInstruction(0x0100))
                    p += 3
                    break
                }
                TokenTypeConst.ANNOTATIONS -> {
                    if (typeId and 0xF == 0xE) {
                        p += VarIntHelper.readVarUIntValueAndLength(source, p).toInt() and 0xFF
                    }
                    val result = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += result.toInt() and 0xFF
                    val annLength = (result shr 8).toInt()
                    val aStart = p
                    p += annLength
                    val aEnd = p
                    firstAnnotationIndex = dest.size()
                    firstAnnotationSid = readAnnotations(source, aStart, aEnd, dest)
                    continue
                }
                TokenTypeConst.NULL -> {
                    val tokenIonType = ionType(typeId)!!.ordinal + 1
                    val operation = (tokenIonType shl TOKEN_TYPE_SHIFT_AMOUNT) or 0b111
                    dest.add(operation.opToInstruction())
                }
                TokenTypeConst.INT -> p += compileIntValue(typeId, source, p, dest, cp)
                TokenTypeConst.FLOAT -> p += compileFloatValue(typeId, source, p, dest, cp)
                TokenTypeConst.DECIMAL -> {
                    val lengthOfValue = if (typeId and 0xF == 0xE) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += valueAndLength.toInt() and 0xFF
                        (valueAndLength shr 8).toInt()
                    } else {
                        typeId and 0xF
                    }
                    val valueEnd = p + lengthOfValue
                    dest.add2(Bytecode.OP_REF_DECIMAL.opToInstruction(lengthOfValue), p)
                    p = valueEnd
                }
                TokenTypeConst.TIMESTAMP -> {
                    val lengthOfValue = if (typeId and 0xF == 0xE) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += valueAndLength.toInt() and 0xFF
                        (valueAndLength shr 8).toInt()
                    } else {
                        typeId and 0xF
                    }
                    val tsEnd = p + lengthOfValue
                    dest.add2(Bytecode.OP_REF_TIMESTAMP_LONG.opToInstruction(lengthOfValue), p)
                    p = tsEnd
                }
                TokenTypeConst.SYMBOL -> {
                    val length = typeId and 0xF
                    val sid = when (length) {
                        0 -> 0
                        1 -> source.get(p).toInt() and 0xFF
                        2 -> source.getShort(p).toInt() and 0xFFFF
                        3 -> TODO("3-byte SIDs")
                        else -> throw IonException("SID out of supported range")
                    }
                    dest.add(Bytecode.OP_SYMBOL_SID.opToInstruction(sid))
                    p += length
                }
                TokenTypeConst.STRING -> {
                    val lengthOfValue = if (typeId and 0xF == 0xE) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += valueAndLength.toInt() and 0xFF
                        (valueAndLength shr 8).toInt()
                    } else {
                        typeId and 0xF
                    }
                    val valueEnd = p + lengthOfValue
                    dest.add2(Bytecode.OP_REF_STRING.opToInstruction(lengthOfValue), p)
                    p = valueEnd
                }
                TokenTypeConst.CLOB -> TODO()
                TokenTypeConst.BLOB -> TODO()
                TokenTypeConst.LIST -> {
                    val lengthOfValue = if (typeId and 0xF == 0xE) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += valueAndLength.toInt() and 0xFF
                        (valueAndLength shr 8).toInt()
                    } else {
                        typeId and 0xF
                    }
                    val valueEnd = p + lengthOfValue
                    BytecodeHelper.emitInlineList(dest) {
                        while (p < valueEnd) {
                            p += compileValue(source, p, dest, cp, symTab, limit)
                        }
                    }
                }
                TokenTypeConst.SEXP -> {
                    val lengthOfValue = if (typeId and 0xF == 0xE) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += valueAndLength.toInt() and 0xFF
                        (valueAndLength shr 8).toInt()
                    } else {
                        typeId and 0xF
                    }
                    val valueEnd = p + lengthOfValue
                    BytecodeHelper.emitInlineSexp(dest) {
                        while (p < valueEnd) {
                            p += compileValue(source, p, dest, cp, symTab, limit)
                        }
                    }
                }
                TokenTypeConst.STRUCT -> {

                    if (firstAnnotationSid == SystemSymbols.ION_SYMBOL_TABLE_SID) {
                        dest.truncate(firstAnnotationIndex)
                        p = readSymbolTableAt(typeId, source, p, dest, cp)
                        break
                    } else {
                        val lengthOfValue = if (typeId and 0xF == 0xE) {
                            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                            p += valueAndLength.toInt() and 0xFF
                            (valueAndLength shr 8).toInt()
                        } else {
                            typeId and 0xF
                        }
                        val valueEnd = p + lengthOfValue
                        var j = 0
                        BytecodeHelper.emitInlineStruct(dest) {
                            while (p < valueEnd) {
                                val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                                p += valueAndLength.toInt() and 0xFF
                                val sid = (valueAndLength shr 8).toInt()

                                dest.add(Bytecode.OP_FIELD_NAME_SID.opToInstruction(sid))
                                p += compileValue(source, p, dest, cp, symTab, limit)
                                j++
                            }
                        }
                        p = valueEnd
                    }
                }
            }
            firstAnnotationIndex = -1
            firstAnnotationSid = 0
        }
        val finalBytecodeOperation = if (p >= src.size) Bytecode.EOF else Bytecode.REFILL
        dest.add(finalBytecodeOperation.opToInstruction())
        return p - pos
    }


    private fun compileValue(
        src: ByteArray,
        pos: Int,
        dest: IntList,
        cp: MutableList<Any?>,
        symTab: Array<String?>,
        limit: Int
    ): Int {
        var p = pos

        val typeId = src[p++].toInt() and 0xFF

        when (Helpers.tokenType(typeId)) {
            TokenTypeConst.NOP -> p = skipNop(typeId, source, p)
            TokenTypeConst.IVM -> throw IonException("IVM is only valid at top level")
            TokenTypeConst.ANNOTATIONS -> {
                if (typeId and 0xF == 0xE) {
                    p += VarIntHelper.readVarUIntValueAndLength(source, p).toInt() and 0xFF
                }
                val result = VarIntHelper.readVarUIntValueAndLength(source, p)
                p += result.toInt() and 0xFF
                val annLength = (result shr 8).toInt()
                val aStart = p
                p += annLength
                val aEnd = p
                readAnnotations(source, aStart, aEnd, dest)
                p += compileValue(source, p, dest, cp, symTab, limit)
            }
            TokenTypeConst.NULL -> {
                val tokenIonType = ionType(typeId)!!.ordinal + 1
                val operation = (tokenIonType shl TOKEN_TYPE_SHIFT_AMOUNT) or 0b111
                dest.add(operation.opToInstruction())
            }
            TokenTypeConst.INT -> p += compileIntValue(typeId, source, p, dest, cp)
            TokenTypeConst.FLOAT -> p += compileFloatValue(typeId, source, p, dest, cp)
            TokenTypeConst.DECIMAL -> {
                val lengthOfValue = if (typeId and 0xF == 0xE) {
                    val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += valueAndLength.toInt() and 0xFF
                    (valueAndLength shr 8).toInt()
                } else {
                    typeId and 0xF
                }
                val valueEnd = p + lengthOfValue
                dest.add2(Bytecode.OP_REF_DECIMAL.opToInstruction(lengthOfValue), p)
                p = valueEnd
            }
            TokenTypeConst.TIMESTAMP -> {
                val lengthOfValue = if (typeId and 0xF == 0xE) {
                    val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += valueAndLength.toInt() and 0xFF
                    (valueAndLength shr 8).toInt()
                } else {
                    typeId and 0xF
                }
                val tsEnd = p + lengthOfValue
                dest.add2(Bytecode.OP_REF_TIMESTAMP_LONG.opToInstruction(lengthOfValue), p)
                p = tsEnd
            }
            TokenTypeConst.SYMBOL -> {
                val length = typeId and 0xF
                val sid = when (length) {
                    0 -> 0
                    1 -> source.get(p).toInt() and 0xFF
                    2 -> source.getShort(p).toInt() and 0xFFFF
                    3 -> TODO("3-byte SIDs")
                    else -> throw IonException("SID out of supported range")
                }
                dest.add(Bytecode.OP_SYMBOL_SID.opToInstruction(sid))
                p += length
            }
            TokenTypeConst.STRING -> {
                val lengthOfValue = if (typeId and 0xF == 0xE) {
                    val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += valueAndLength.toInt() and 0xFF
                    (valueAndLength shr 8).toInt()
                } else {
                    typeId and 0xF
                }
                val valueEnd = p + lengthOfValue
                dest.add2(Bytecode.OP_REF_STRING.opToInstruction(lengthOfValue), p)
                p = valueEnd
            }
            TokenTypeConst.CLOB -> TODO()
            TokenTypeConst.BLOB -> TODO()
            TokenTypeConst.LIST -> {
                val lengthOfValue = if (typeId and 0xF == 0xE) {
                    val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += valueAndLength.toInt() and 0xFF
                    (valueAndLength shr 8).toInt()
                } else {
                    typeId and 0xF
                }
                val valueEnd = p + lengthOfValue
                BytecodeHelper.emitInlineList(dest) {
                    while (p < valueEnd) {
                        p += compileValue(source, p, dest, cp, symTab, limit)
                    }
                }
                p = valueEnd
            }
            TokenTypeConst.SEXP -> {
                val lengthOfValue = if (typeId and 0xF == 0xE) {
                    val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += valueAndLength.toInt() and 0xFF
                    (valueAndLength shr 8).toInt()
                } else {
                    typeId and 0xF
                }
                val valueEnd = p + lengthOfValue
                BytecodeHelper.emitInlineSexp(dest) {
                    while (p < valueEnd) {
                        p += compileValue(source, p, dest, cp, symTab, limit)
                    }
                }
                p = valueEnd
            }
            TokenTypeConst.STRUCT -> {
                val lengthOfValue = if (typeId and 0xF == 0xE) {
                    val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += valueAndLength.toInt() and 0xFF
                    (valueAndLength shr 8).toInt()
                } else {
                    typeId and 0xF
                }
                val valueEnd = p + lengthOfValue
                BytecodeHelper.emitInlineStruct(dest) {
                    while (p < valueEnd) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += valueAndLength.toInt() and 0xFF
                        val sid = (valueAndLength shr 8).toInt()

                        if (sid > symTab.size) {
                            TODO()
                        }

                        dest.add(Bytecode.OP_FIELD_NAME_SID.opToInstruction(sid))

                        p += compileValue(source, p, dest, cp, symTab, limit)
                    }
                }
                p = valueEnd
            }
        }

        return p - pos
    }

    private fun readAnnotations(source: ByteArray, start: Int, end: Int, bytecode: IntList): Int {
        var p = start
        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
        val firstSid = (valueAndLength ushr 8).toInt()
        val length = valueAndLength.toInt() and 0xFF
        p += length
        bytecode.add(Bytecode.OP_ANNOTATION_SID.opToInstruction(firstSid))

        while (p < end) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            val sid = (valueAndLength ushr 8).toInt()
            val length = valueAndLength.toInt() and 0xFF
            p += length
            bytecode.add(Bytecode.OP_ANNOTATION_SID.opToInstruction(sid))
        }
        return firstSid
    }

    private fun readShort(typeId: Int, source: ByteArray, position: Int): Int {
        val sign = (((typeId shr 4) shl 31) shr 32) or 1
        // TODO: BigIntegers
        val length = typeId and 0xF
        val offset = 8 - length
        // This case should be very rare. Only triggered for some integers less than 4 bytes that appear
        // in the first 8 bytes of the data stream.
        val value = source.getShort(position - offset).toInt() and ((1 shl (length * 8)) - 1)
        return sign * value
    }

    private fun compileIntValue(typeId: Int, source: ByteArray, position: Int, dest: IntList, cp: MutableList<Any?>): Int {
        // TODO: BigIntegers
        var p = position
        val sign = (((typeId shr 4) shl 31) shr 32) or 1

        when (typeId and 0xF) {
            0 -> dest.add(Bytecode.OP_SMALL_INT.opToInstruction())
            1 -> dest.add(Bytecode.OP_SMALL_INT.opToInstruction(source[p++].toInt().and(0xFF).times(sign)))
            2 -> {
                val msb = source[p++].toInt().and(0xFF).shl(8)
                val lsb = source[p++].toInt() and 0xFF
                val value = (msb or lsb) * sign
                dest.add2(Bytecode.OP_INLINE_INT.opToInstruction(), value)
            }
            3 -> {
                var absoluteValue = 0
                absoluteValue = absoluteValue.shl(8) or source[p++].toInt().and(0xFF)
                absoluteValue = absoluteValue.shl(8) or source[p++].toInt().and(0xFF)
                absoluteValue = absoluteValue.shl(8) or source[p++].toInt().and(0xFF)
                dest.add2(Bytecode.OP_INLINE_INT.opToInstruction(), absoluteValue * sign)
            }
            4, 5, 6, 7 -> {
                val absoluteValue = readUInt(source, p, p + (typeId and 0xF))
                BytecodeHelper.emitInt64Value(dest, absoluteValue * sign)
                p += typeId and 0xF
            }
            else -> {
                TODO("${typeId and 0xF}")
            }
        }

        return p - position
    }

    private fun readUInt(source: ByteArray, startIndex: Int, endIndex: Int): Long {
        var result: Long = 0
        for (i in startIndex..<endIndex) {
            result = (result shl 8) or (source[i].toInt() and 0xFF).toLong()
        }
        return result
    }


    private fun compileFloatValue(typeId: Int, source: ByteArray, position: Int, dest: IntList, cp: MutableList<Any?>): Int {
        var p = position
        when (typeId and 0xF) {
            0 -> dest.add2(Bytecode.OP_INLINE_FLOAT.opToInstruction(), 0.0f.toRawBits())
            4 -> {
                var bits = 0
                bits = bits.shl(8) or source[p++].toInt().and(0xFF)
                bits = bits.shl(8) or source[p++].toInt().and(0xFF)
                bits = bits.shl(8) or source[p++].toInt().and(0xFF)
                bits = bits.shl(8) or source[p++].toInt().and(0xFF)
                dest.add2(Bytecode.OP_INLINE_FLOAT.opToInstruction(), bits)
            }
            8 -> {
                var bits = 0L
                bits = bits.shl(8) or source[p++].toLong().and(0xFF)
                bits = bits.shl(8) or source[p++].toLong().and(0xFF)
                bits = bits.shl(8) or source[p++].toLong().and(0xFF)
                bits = bits.shl(8) or source[p++].toLong().and(0xFF)
                bits = bits.shl(8) or source[p++].toLong().and(0xFF)
                bits = bits.shl(8) or source[p++].toLong().and(0xFF)
                bits = bits.shl(8) or source[p++].toLong().and(0xFF)
                bits = bits.shl(8) or source[p++].toLong().and(0xFF)
                BytecodeHelper.emitDoubleValue(dest, Double.fromBits(bits))
            }
        }

        return p - position
    }

    /**
     * Returns the position after the NOP
     */
    private fun skipNop(typeId: Int, source: ByteArray, position: Int): Int {
        return when (val l = typeId and 0xF) {
            0xE -> {
                val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, position)
                position + (valueAndLength.toInt() and 0xFF) + (valueAndLength shr 8).toInt()
            }
            else -> position + l
        }
    }

    object Helpers {
        @JvmStatic
        fun ionType(state: Int): IonType? {
            return when (state shr 4) {
                0x0 -> IonType.NULL
                0x1 -> IonType.BOOL
                0x2, 0x3 -> IonType.INT
                0x4 -> IonType.FLOAT
                0x5 -> IonType.DECIMAL
                0x6 -> IonType.TIMESTAMP
                0x7 -> IonType.SYMBOL
                0x8 -> IonType.STRING
                0x9 -> IonType.CLOB
                0xA -> IonType.BLOB
                0xB -> IonType.LIST
                0xC -> IonType.SEXP
                0xD -> IonType.STRUCT
                else -> null
            }
        }

        @JvmStatic
        fun tokenType(state: Int): Int = typeLookup[state]


        private val typeLookup = IntArray(256) {
            try {
                initType(it)
            } catch (e: IonException) {
                // TODO: Use "INVALID" here
                TokenTypeConst.UNSET
            }
        }

        private fun initType(state: Int): Int {
            return when (state) {
                // NOP
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E -> TokenTypeConst.NOP
                0x0F -> TokenTypeConst.NULL
                0x10, 0x11 -> TokenTypeConst.BOOL
                0x1F -> TokenTypeConst.NULL
                0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E -> throw IonException("Invalid input: illegal typeId: $state")
                0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E -> TokenTypeConst.INT
                0x2F -> TokenTypeConst.NULL
                0x30 -> throw IonException("Invalid input: illegal typeId: $state")
                0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E -> TokenTypeConst.INT
                0x3F -> TokenTypeConst.NULL
                0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E -> TokenTypeConst.FLOAT
                0x4F -> TokenTypeConst.NULL
                0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E -> TokenTypeConst.DECIMAL
                0x5F -> TokenTypeConst.NULL
                0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E -> TokenTypeConst.TIMESTAMP
                0x6F -> TokenTypeConst.NULL
                0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E -> TokenTypeConst.SYMBOL
                0x7F -> TokenTypeConst.NULL
                0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E -> TokenTypeConst.STRING
                0x8F -> TokenTypeConst.NULL
                0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E -> TokenTypeConst.CLOB
                0x9F -> TokenTypeConst.NULL
                0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xAB, 0xAC, 0xAD, 0xAE -> TokenTypeConst.BLOB
                0xAF -> TokenTypeConst.NULL
                0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xBB, 0xBC, 0xBD, 0xBE -> TokenTypeConst.LIST
                0xBF -> TokenTypeConst.NULL
                0xC0, 0xC1, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xCB, 0xCC, 0xCD, 0xCE -> TokenTypeConst.SEXP
                0xCF -> TokenTypeConst.NULL
                0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xDB, 0xDC, 0xDD, 0xDE -> TokenTypeConst.STRUCT
                0xDF -> TokenTypeConst.NULL
                // IVM
                0xE0 -> TokenTypeConst.IVM
                // Annotations
                0xE1, 0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xEB, 0xEC, 0xED, 0xEE -> TokenTypeConst.ANNOTATIONS
                // Reserved
                0xEF, 0xF0, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF -> throw IonException(
                    "Invalid input: illegal typeId: $state"
                )

                else -> TokenTypeConst.UNSET
            }
        }
    }

    object VarIntHelper {

        /**
         * Returns an unsigned integer up to 7 bytes, with an 1 byte integer signifying how many varuint bytes were used in its encoding.
         *
         */
        @JvmStatic
        fun readVarUIntValueAndLength(source: ByteBuffer, position: Int): Long {
            val currentByte: Int = source.get(position).toInt()
            val result = (currentByte and 0b01111111).toLong()
            return if (currentByte < 0) {
                (result shl 8) or 1L
            } else {
                readVarUIntValueAndLength2(source, position + 1, result)
            }
        }

        @JvmStatic
        private fun readVarUIntValueAndLength2(source: ByteBuffer, position: Int, partialResult: Long): Long {
            var currentByte: Int = source.get(position).toInt()
            var result = (partialResult shl 7) or (currentByte and 0b01111111).toLong()
            if (currentByte < 0) {
                return (result shl 8) or 2
            }

            var p = position
            var length = 2
            do {
                length++
                currentByte = source.get(++p).toInt()
                result = (result shl 7) or (currentByte and 0b01111111).toLong()
            } while (currentByte >= 0)

            return (result shl 8) or length.toLong()
        }

        /**
         * Returns a signed integer up to 7 bytes, with an 1 byte integer signifying how many varuint bytes were used in its encoding.
         *
         */
        @JvmStatic
        fun readVarIntValueAndLength(source: ByteBuffer, position: Int): Long {
            var p = position

            var length = 1
            var currentByte = source.get(p++)
            var result = (currentByte.toInt() and 0x3F).toLong()
            // 1. Shift the sign-bit leftwards to the two's-complement sign-bit of a java integer
            // 2. Signed shift right so that we have either -1 or 0
            // 3. "or" with 1 so that we have -1 or 1

            // TODO: This is suspect.
            val sign = (((currentByte.toInt() and 0b01000000) shl 25) shr 32) or 1

            while (currentByte >= 0) {
                length++
                currentByte = source.get(p++)
                result = (result shl 7) or (currentByte.toInt() and 0b01111111).toLong()
            }

            return ((sign * result) shl 8) or length.toLong()
        }



        /**
         * Returns an unsigned integer up to 7 bytes, with an 1 byte integer signifying how many varuint bytes were used in its encoding.
         *
         */
        @JvmStatic
        fun readVarUIntValueAndLength(source: ByteArray, position: Int): Long {
            val currentByte: Int = source[position].toInt()
            val result = (currentByte and 0b01111111).toLong()
            return if (currentByte < 0) {
                (result shl 8) or 1L
            } else {
                readVarUIntValueAndLength2(source, position + 1, result)
            }
        }

        @JvmStatic
        private fun readVarUIntValueAndLength2(source: ByteArray, position: Int, partialResult: Long): Long {
            val currentByte: Int = source.get(position).toInt()
            val result = (partialResult shl 7) or (currentByte and 0b01111111).toLong()
            if (currentByte < 0) {
                return (result shl 8) or 2
            } else {
                return readVarUIntValueAndLength3Plus(source, position + 1, result)
            }
        }

        @JvmStatic
        private fun readVarUIntValueAndLength3Plus(source: ByteArray, position: Int, partialResult: Long): Long {
            var currentByte: Int
            var result = partialResult
            var p = position
            var length = 2
            do {
                length++
                currentByte = source.get(p++).toInt()
                result = (result shl 7) or (currentByte and 0b01111111).toLong()
            } while (currentByte >= 0)

            return (result shl 8) or length.toLong()
        }

        /**
         * Returns a signed integer up to 7 bytes, with an 1 byte integer signifying how many varuint bytes were used in its encoding.
         *
         */
        @JvmStatic
        fun readVarIntValueAndLength(source: ByteArray, position: Int): Long {
            // 0b11000011
            var p = position

            var length = 1
            try {
                var currentByte = source[p++].toInt()
                var result = (currentByte and 0b00111111).toLong()
                // 1. Shift the sign-bit leftwards to the two's-complement sign-bit of a java integer
                // 2. Signed shift right so that we have either -1 or 0
                // 3. "or" with 1 so that we have -1 or 1
                val sign = if (currentByte and 0b01000000 == 0) 1 else -1
                while (currentByte >= 0) {
                    length++
                    currentByte = source[p++].toInt()
                    result = (result shl 7) or (currentByte and 0b01111111).toLong()
                }

                return ((sign * result) shl 8) or length.toLong()
            } catch (e: ArrayIndexOutOfBoundsException) {
                println(source.joinToString { (it.toInt() and 0xFF).toString(16) })
                println("position=$position, length=$length, p=$p")
                throw e
            }
        }
    }

    private fun readSymbolTableAt(structTypeId: Int, source: ByteArray, position: Int, dest: IntList, cp: MutableList<Any?>): Int {
        val directiveIndex = dest.reserve()
        var isAppend = false


        var p = position
        val lengthOfContainer = if (structTypeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            structTypeId and 0xF
        }
        val end = p + lengthOfContainer

        while (p < end) {
            val fieldSidValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            val fieldSid = (fieldSidValueAndLength ushr 8).toInt()
            p += fieldSidValueAndLength.toInt() and 0xFF

            var typeId = (source.get(p++).toInt() and 0xFF)
            var tokenType = Helpers.tokenType(typeId)

            if (tokenType == TokenTypeConst.ANNOTATIONS) {
                if (typeId and 0xF == 0xE) {
                    p += VarIntHelper.readVarUIntValueAndLength(source, p).toInt() and 0xFF
                }
                val result = VarIntHelper.readVarUIntValueAndLength(source, p)
                p += (result.toInt() and 0xFF) + (result shr 8).toInt()
                typeId = (source.get(p++).toInt() and 0xFF)
                tokenType = Helpers.tokenType(typeId)
            }

            when (tokenType) {
                TokenTypeConst.NULL,
                TokenTypeConst.BOOL,
                TokenTypeConst.NOP,
                TokenTypeConst.INT,
                TokenTypeConst.FLOAT,
                TokenTypeConst.DECIMAL,
                TokenTypeConst.TIMESTAMP,
                TokenTypeConst.STRING,
                TokenTypeConst.CLOB,
                TokenTypeConst.BLOB,
                TokenTypeConst.SEXP,
                TokenTypeConst.STRUCT -> {
                    if (typeId and 0xF == 0xE) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += (valueAndLength.toInt() and 0xFF) + (valueAndLength shr 8).toInt()
                    } else {
                        p += typeId and 0xF
                    }
                }

                TokenTypeConst.LIST -> {
                    // TODO: imports list
                    if (fieldSid == SystemSymbols.SYMBOLS_SID) {
                        p = readSymbolsList(typeId, source, p, dest, cp)
                    }
                }
                TokenTypeConst.SYMBOL -> {
                    val length = typeId and 0xF
                    val sid = when (length) {
                        0 -> 0
                        1 -> source.get(p).toInt() and 0xFF
                        2 -> source.getShort(p).toInt() and 0xFFFF
                        3 -> source.getInt(p - 1) and 0xFFFFFF
                        4 -> source.getInt(p)
                        else -> throw IonException("SID out of supported range")
                    }
                    p += length
                    isAppend = (fieldSid == SystemSymbols.IMPORTS_SID && sid == SystemSymbols.ION_SYMBOL_TABLE_SID) || isAppend
                }
                TokenTypeConst.ANNOTATIONS -> TODO("Unreachable")
                else -> TODO(TokenTypeConst(tokenType))
            }
        }

        val directiveOperation = if (isAppend) Bytecode.DIRECTIVE_ADD_SYMBOLS else Bytecode.DIRECTIVE_SET_SYMBOLS
        dest[directiveIndex] = directiveOperation.opToInstruction()
        dest.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        return end
    }

    private fun readSymbolsList(listTypeId: Int, source: ByteArray, position: Int, dest: IntList, cp: MutableList<Any?>): Int {
        var p = position
        val lengthOfContainer = if (listTypeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            listTypeId and 0xF
        }
        val end = p + lengthOfContainer
        while (p < end) {

            val typeId = (source.get(p++).toInt() and 0xFF)
            val tokenType = Helpers.tokenType(typeId)

            when (tokenType) {
                TokenTypeConst.NOP -> p = skipNop(typeId, source, p)
                TokenTypeConst.NULL,
                TokenTypeConst.BOOL,
                TokenTypeConst.INT,
                TokenTypeConst.FLOAT,
                TokenTypeConst.DECIMAL,
                TokenTypeConst.TIMESTAMP,
                TokenTypeConst.SYMBOL,
                TokenTypeConst.CLOB,
                TokenTypeConst.BLOB,
                TokenTypeConst.LIST,
                TokenTypeConst.SEXP,
                TokenTypeConst.STRUCT -> {
                    if (typeId and 0xF == 0xE) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += (valueAndLength.toInt() and 0xFF) + (valueAndLength shr 8).toInt()
                    } else {
                        p += typeId and 0xF
                    }
                    dest.add(Bytecode.OP_NULL_NULL.opToInstruction())
                }
                TokenTypeConst.STRING -> {
                    val lengthOfValue = if (typeId and 0xF == 0xE) {
                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                        p += valueAndLength.toInt() and 0xFF
                        (valueAndLength shr 8).toInt()
                    } else {
                        typeId and 0xF
                    }
                    val s = readTextReference(p, lengthOfValue)
                    val cpIndex = cp.size
                    cp.add(s)
                    dest.add(Bytecode.OP_CP_STRING.opToInstruction(cpIndex))
                    p += lengthOfValue
                }
                TokenTypeConst.ANNOTATIONS -> {
                    if (typeId and 0xF == 0xE) {
                        p += VarIntHelper.readVarUIntValueAndLength(source, p).toInt() and 0xFF
                    }
                    val result = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += (result.toInt() and 0xFF) + (result shr 8).toInt()
                }
                else -> TODO(TokenTypeConst(tokenType))
            }
        }
        return end
    }
}
