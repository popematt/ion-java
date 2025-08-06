package com.amazon.ion.v3.visitor2

import com.amazon.ion.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.*
import com.amazon.ion.v3.impl_1_0.Helpers.ionType
import com.amazon.ion.v3.impl_1_0.Helpers.tokenType
import com.amazon.ion.v3.impl_1_1.MacroV2
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.experimental.and

class VisitingIonReader10(
    private val source: ByteBuffer
) {
    init {
        source.order(ByteOrder.BIG_ENDIAN)
    }
    private val scratch = source.duplicate()

    private companion object {
        @JvmField
        val ION_1_0_SYMBOL_TABLE = arrayOf(
            null,
            SystemSymbols.ION,
            SystemSymbols.ION_1_0,
            SystemSymbols.ION_SYMBOL_TABLE,
            SystemSymbols.NAME,
            SystemSymbols.VERSION,
            SystemSymbols.IMPORTS,
            SystemSymbols.SYMBOLS,
            SystemSymbols.MAX_ID,
            SystemSymbols.ION_SHARED_SYMBOL_TABLE,
        )
    }

    private var i = 0

    private var symbolTable = ION_1_0_SYMBOL_TABLE
    // Consider tracking symbol table size so we can avoid re-allocating the symbol table all the time.

    private val symbolTableVisitor = SymbolTableVisitor()
    private val annotationIterator = AnnotationIteratorBinary10(ION_1_0_SYMBOL_TABLE, source)


    /**
     * Returns true if a (user?) value was visited.
     */
    fun next(visitor: IonVisitor): Boolean {
        val i = i
        val source = source
        if (i < source.capacity()) {
            val bytesConsumed = visitTopLevelValueAt(source, i, visitor)
            this.i += bytesConsumed
            return true
        } else {
            return false
        }
    }

    /**
     * Returns number of bytes consumed.
     */
    private fun visitTopLevelValueAt(source: ByteBuffer, position: Int, visitor: IonVisitor): Int {
        var p = position
        var annotationIterator: AnnotationIteratorBinary10? = null

        while (true) {

            val typeId = (source.get(p++).toInt() and 0xFF)
            val tokenType = tokenType(typeId)
            when (tokenType) {
                TokenTypeConst.IVM -> {
                    symbolTable = ION_1_0_SYMBOL_TABLE
                    p += 3
                }
                TokenTypeConst.NOP -> {
                    p = skipNop(typeId, source, p)
                }
                TokenTypeConst.NULL -> {
                    visitor.onNull(annotationIterator, Helpers.ionType(typeId)!!)
                    break
                }
                TokenTypeConst.BOOL -> {
                    visitor.onBool(annotationIterator, typeId == 0x11)
                    break
                }
                TokenTypeConst.INT -> {
                    // TODO: BigIntegers
                    visitor.onLong(annotationIterator, readLong(typeId, source, p))
                    p += (typeId and 0xF)
                    break
                }
                TokenTypeConst.FLOAT -> {
                    visitor.onDouble(annotationIterator, readDouble(typeId, source, p))
                    p += (typeId and 0xF)
                    break
                }
                TokenTypeConst.DECIMAL -> TODO()
                TokenTypeConst.TIMESTAMP -> {
                    p = visitTimestampValue(annotationIterator, typeId, source, p, visitor)
                    break
                }
                TokenTypeConst.SYMBOL -> {
                    p = visitSymbolAt(annotationIterator, typeId, source, p, visitor)
                    break
                }
                TokenTypeConst.STRING -> {
                    p = visitStringAt(annotationIterator, typeId, source, p, visitor)
                    break
                }
                TokenTypeConst.CLOB -> TODO()
                TokenTypeConst.BLOB -> TODO()
                TokenTypeConst.LIST -> {
                    p = visitUserListAt(annotationIterator, typeId, source, p, visitor)
                    break
                }
                TokenTypeConst.SEXP -> {
                    p = visitUserSexpAt(annotationIterator, typeId, source, p, visitor)
                    break
                }
                TokenTypeConst.STRUCT -> {
                    val anns = annotationIterator
                    if (anns != null && anns.firstSid() == SystemSymbols.ION_SYMBOL_TABLE_SID) {
                        p = readSymbolTableAt(typeId, source, p)
                    } else {
                        p = visitUserStructAt(anns, typeId, source, p, visitor)
                        break
                    }
                }
                TokenTypeConst.ANNOTATIONS -> {
                    if (typeId and 0xF == 0xE) {
                        p += VarIntHelper.readVarUIntValueAndLength(source, p).toInt() and 0xFF
                    }
                    val result = VarIntHelper.readVarUIntValueAndLength(source, p)
                    p += result.toInt() and 0xFF
                    val annLength = (result shr 8).toInt()
                    annotationIterator = this.annotationIterator
                    annotationIterator.init(p, annLength, symbolTable)
                    p += annLength
                    continue // to skip the annotation reset.
                }
                else -> TODO(TokenTypeConst(tokenType))
            }

            annotationIterator = null
        }

        return p - position
    }


    /**
     * Returns the position after the NOP
     */
    private fun skipNop(typeId: Int, source: ByteBuffer, position: Int): Int {
        return when (val l = typeId and 0xF) {
            0xE -> {
                val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, position)
                position + (valueAndLength.toInt() and 0xFF) + (valueAndLength shr 8).toInt()
            }
            else -> position + l
        }
    }

    private fun readLong(typeId: Int, source: ByteBuffer, position: Int): Long {
        val sign = (((typeId shr 4) shl 31) shr 32) or 1
        // TODO: BigIntegers
        val length = typeId and 0xF
        val offset = 8 - length
        // This case should be very rare. Only triggered for some integers less than 4 bytes that appear
        // in the first 8 bytes of the data stream.
        if (position < offset) return slowReadLong(sign, length, source, position)
        val value = source.getLong(position - offset) and ((1L shl (length * 8)) - 1L)
        return sign * value
    }

    private fun readDouble(typeId: Int, source: ByteBuffer, position: Int): Double {
        return when (typeId and 0xF) {
            0 -> 0.0
            4 -> source.getFloat(position).toDouble()
            8 -> source.getDouble(position)
            else -> throw IonException("Invalid Ion float type id: $typeId")
        }
    }

    private fun slowReadLong(sign: Int, length: Int, source: ByteBuffer, position: Int): Long {
        var result = 0L
        var p = position
        for (i in 0..< length) {
            result = (result shl 8) or (source.get(p++).toInt() and 0xFF).toLong()
        }
        return sign * result
    }


    /** Returns new position at end of struct */
    private fun visitStringAt(annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonVisitor): Int {
        var p = position
        val lengthOfString = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfString
        val end = p
        visitor.onString(annotations) {
            val s = scratch
            s.limit(end)
            s.position(start)
            StandardCharsets.UTF_8.decode(s).toString()
        }
        return p
    }
    /** Returns new position at end of struct */
    private fun visitFieldWithStringAt(fieldName: String?, annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        val lengthOfString = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfString
        val end = p
        visitor.onString(fieldName, annotations) {
            val s = scratch
            s.limit(end)
            s.position(start)
            StandardCharsets.UTF_8.decode(s).toString()
        }
        return p
    }

    private fun visitSymbolAt(annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonVisitor): Int {
        var p = position
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
        visitor.onSymbol(annotations, sid, { symbolTable[sid] })
        return p
    }

    private fun visitFieldWithSymbolAt(fieldName: String?, annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
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
        visitor.onSymbol(fieldName, annotations, sid, { symbolTable[sid] })
        return p
    }


    /** Returns new position at end of struct */
    private fun visitUserListAt(annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonVisitor): Int {
        var p = position
        val lengthOfContainer = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfContainer
        val end = p
        visitor.onList(annotations) { ionVisitor -> visitSequenceContent(source, start, end, ionVisitor) }
        return p
    }

    /** Returns new position at end of struct */
    private fun visitUserSexpAt(annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonVisitor): Int {
        var p = position
        val lengthOfContainer = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfContainer
        val end = p
        visitor.onSexp(annotations) { ionVisitor -> visitSequenceContent(source, start, end, ionVisitor) }
        return p
    }


    private fun visitSequenceContent(source: ByteBuffer, position: Int, end: Int, visitor: IonVisitor) {
        var p = position
        var annotationIterator: AnnotationIterator? = null

        while (p < end) {
            val typeId = (source.get(p++).toInt() and 0xFF)
            val tokenType = tokenType(typeId)

            when (tokenType) {
                TokenTypeConst.NOP -> p = skipNop(typeId, source, p)
                TokenTypeConst.NULL -> visitor.onNull(annotationIterator, Helpers.ionType(typeId)!!)
                TokenTypeConst.BOOL -> visitor.onBool(annotationIterator, typeId == 0x11)
                TokenTypeConst.INT -> {
                    // TODO: BigIntegers
                    visitor.onLong(annotationIterator, readLong(typeId, source, p))
                    p += (typeId and 0xF)
                }
                TokenTypeConst.FLOAT -> {
                    visitor.onDouble(annotationIterator, readDouble(typeId, source, p))
                    p += (typeId and 0xF)
                }
                TokenTypeConst.DECIMAL -> TODO()
                TokenTypeConst.TIMESTAMP -> p = visitTimestampValue(annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.SYMBOL -> p = visitSymbolAt(annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.STRING -> p = visitStringAt(annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.CLOB -> TODO()
                TokenTypeConst.BLOB -> TODO()
                TokenTypeConst.LIST -> p = visitUserListAt(annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.SEXP -> p = visitUserSexpAt(annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.STRUCT -> p = visitUserStructAt(annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.ANNOTATIONS -> {
                    if (typeId and 0xF == 0xE) {
                        p += VarIntHelper.readVarUIntValueAndLength(source, p).toInt() and 0xFF
                    }

                    val varUIntValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
                    val annLength = (varUIntValueAndLength ushr 8).toInt()
                    p += varUIntValueAndLength.toInt() and 0xFF
                    annotationIterator = this.annotationIterator
                    annotationIterator.init(p, annLength, symbolTable)
                    p += annLength
                    continue // to skip the annotation reset.
                }
                else -> TODO(TokenTypeConst(tokenType))
            }
            annotationIterator = null
        }
    }

    /** Returns the new position at the end of the symbol table. */
    private fun readSymbolTableAt(typeId: Int, source: ByteBuffer, position: Int): Int {
        var p = position
        val lengthOfContainer = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfContainer
        val end = p
        visitStructContent(source, start, end, symbolTableVisitor)

        val newSymbols = symbolTableVisitor.newSymbols.newSymbols
        val numberOfNewSymbols = symbolTableVisitor.newSymbols.size
        val currentSymbolTable = if (symbolTableVisitor.append) symbolTable else ION_1_0_SYMBOL_TABLE
        val newSymbolTable = symbolTable.copyOf(currentSymbolTable.size + numberOfNewSymbols)
        System.arraycopy(newSymbols, 0, newSymbolTable, currentSymbolTable.size, numberOfNewSymbols)
        symbolTable = newSymbolTable

        return p
    }

    /** Returns new position at end of struct */
    private fun visitUserStructAt(annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonVisitor): Int {
        var p = position
        val lengthOfContainer = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfContainer
        val end = p
        visitor.onStruct(annotations) { ionFieldVisitor -> visitStructContent(source, start, end, ionFieldVisitor) }
        return p
    }


    /** Returns new position at end of struct */
    private fun visitFieldWithListAt(fieldName: String?, annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        val lengthOfContainer = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfContainer
        val end = p
        visitor.onList(fieldName, annotations) { ionVisitor -> visitSequenceContent(source, start, end, ionVisitor) }
        return p
    }

    /** Returns new position at end of struct */
    private fun visitFieldWithSexpAt(fieldName: String?, annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        val lengthOfContainer = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfContainer
        val end = p
        visitor.onSexp(fieldName, annotations) { ionVisitor -> visitSequenceContent(source, start, end, ionVisitor) }
        return p
    }

    /** Returns new position at end of struct */
    private fun visitFieldWithStructAt(fieldName: String?, annotations: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        val lengthOfContainer = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val start = p
        p += lengthOfContainer
        val end = p
        visitor.onStruct(fieldName, annotations) { ionFieldVisitor -> visitStructContent(source, start, end, ionFieldVisitor) }
        return p
    }



    private fun visitStructContent(source: ByteBuffer, start: Int, end: Int, visitor: IonFieldVisitor) {
        var p = start
        var annotationIterator: AnnotationIterator? = null
        val symbolTable = symbolTable

        while (p < end) {
            val fieldSidValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            val fieldSid = (fieldSidValueAndLength ushr 8).toInt()
            p += fieldSidValueAndLength.toInt() and 0xFF

            val fieldName =
            try {
                symbolTable[fieldSid]
            } catch (e: Exception) {
                println(p)
                throw e
            }
            val typeId = (source.get(p++).toInt() and 0xFF)
            val tokenType = tokenType(typeId)

            when (tokenType) {
                TokenTypeConst.NOP -> p = skipNop(typeId, source, p)
                TokenTypeConst.NULL -> visitor.onNull(fieldName, annotationIterator, Helpers.ionType(typeId)!!)
                TokenTypeConst.BOOL -> visitor.onBool(fieldName, annotationIterator, typeId == 0x11)
                TokenTypeConst.INT -> {
                    // TODO: BigIntegers
                    visitor.onLong(fieldName, annotationIterator, readLong(typeId, source, p))
                    p += (typeId and 0xF)
                }
                TokenTypeConst.FLOAT -> {
                    visitor.onDouble(fieldName, annotationIterator, readDouble(typeId, source, p))
                    p += (typeId and 0xF)
                }
                TokenTypeConst.DECIMAL -> TODO()
                TokenTypeConst.TIMESTAMP -> p = visitFieldWithTimestampValue(fieldName, annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.SYMBOL -> p = visitFieldWithSymbolAt(fieldName, annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.STRING -> p = visitFieldWithStringAt(fieldName, annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.CLOB -> TODO()
                TokenTypeConst.BLOB -> TODO()
                TokenTypeConst.LIST -> p = visitFieldWithListAt(fieldName, annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.SEXP -> p = visitFieldWithSexpAt(fieldName, annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.STRUCT -> p = visitFieldWithStructAt(fieldName, annotationIterator, typeId, source, p, visitor)
                TokenTypeConst.ANNOTATIONS -> p = visitAnnotatedFieldValue(typeId, source, p, fieldName, visitor)
                else -> TODO(TokenTypeConst(tokenType))
            }
            annotationIterator = null
        }
    }

    fun visitAnnotatedFieldValue(annotationTypeId: Int, source: ByteBuffer, start: Int, fieldName: String?, visitor: IonFieldVisitor): Int {
        var p = start
        if (annotationTypeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
        }

        val varUIntValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
        val annLength = (varUIntValueAndLength ushr 8).toInt()
        p += varUIntValueAndLength.toInt() and 0xFF

        val annotationIterator = this.annotationIterator
        annotationIterator.init(p, annLength, symbolTable)
        p += annLength
        val typeId = (source.get(p++).toInt() and 0xFF)
        val tokenType = tokenType(typeId)

        when (tokenType) {
            TokenTypeConst.NOP -> p = skipNop(typeId, source, p)
            TokenTypeConst.NULL -> visitor.onNull(fieldName, annotationIterator, Helpers.ionType(typeId)!!)
            TokenTypeConst.BOOL -> visitor.onBool(fieldName, annotationIterator, typeId == 0x11)
            TokenTypeConst.INT -> {
                // TODO: BigIntegers
                visitor.onLong(fieldName, annotationIterator, readLong(typeId, source, p))
                p += (typeId and 0xF)
            }
            TokenTypeConst.FLOAT -> {
                visitor.onDouble(fieldName, annotationIterator, readDouble(typeId, source, p))
                p += (typeId and 0xF)
            }
            TokenTypeConst.DECIMAL -> TODO()
            TokenTypeConst.TIMESTAMP -> p = visitFieldWithTimestampValue(fieldName, annotationIterator, typeId, source, p, visitor)
            TokenTypeConst.SYMBOL -> p = visitFieldWithSymbolAt(fieldName, annotationIterator, typeId, source, p, visitor)
            TokenTypeConst.STRING -> p = visitFieldWithStringAt(fieldName, annotationIterator, typeId, source, p, visitor)
            TokenTypeConst.CLOB -> TODO()
            TokenTypeConst.BLOB -> TODO()
            TokenTypeConst.LIST -> p = visitFieldWithListAt(fieldName, annotationIterator, typeId, source, p, visitor)
            TokenTypeConst.SEXP -> p = visitFieldWithSexpAt(fieldName, annotationIterator, typeId, source, p, visitor)
            TokenTypeConst.STRUCT -> p = visitFieldWithStructAt(fieldName, annotationIterator, typeId, source, p, visitor)
            else -> TODO(TokenTypeConst(tokenType))
        }
        return p
    }

    fun visitFieldWithTimestampValue(fieldName: String?, ann: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        val lengthOfValue = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val end = p + lengthOfValue

        visitor.onTimestamp(fieldName, ann, { readTimestamp(source, position, end) })
        return end
    }

    fun visitTimestampValue(ann: AnnotationIterator?, typeId: Int, source: ByteBuffer, position: Int, visitor: IonVisitor): Int {
        var p = position
        val lengthOfValue = if (typeId and 0xF == 0xE) {
            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
            p += valueAndLength.toInt() and 0xFF
            (valueAndLength shr 8).toInt()
        } else {
            typeId and 0xF
        }
        val end = p + lengthOfValue

        visitor.onTimestamp(ann, { readTimestamp(source, position, end) })
        return end
    }


    private fun readTimestamp(source: ByteBuffer, start: Int, end: Int): Timestamp {
        var p = start
        val offset: Int? = if (source.get(p++).toUByte() == 0xc0u.toUByte()) {
            null
        } else {
            val offsetValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p - 1)
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
            println("Timestamp starting at $start")
            throw IonException("Illegal timestamp encoding. ", e)
        }
    }

    private fun readDecimalContent(source: ByteBuffer, start: Int, end: Int): Decimal {
        var p = start
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



    private class SymbolTableVisitor: IonFieldVisitor {
        // TODO: Maybe optimize this so that if we see that it's an append, we start writing new symbols directly into the symbol table array.
        @JvmField
        var currentSymbols: Array<String?> = ION_1_0_SYMBOL_TABLE
        @JvmField
        var newSymbols = SymbolsListVisitor()
        @JvmField
        var append = false

        override fun onNull(fieldName: String?, ann: AnnotationIterator?, type: IonType) {
        }

        override fun onBool(fieldName: String?, ann: AnnotationIterator?, value: Boolean) {
        }

        override fun onLong(fieldName: String?, ann: AnnotationIterator?, value: Long) {
        }

        override fun onBigInt(fieldName: String?, ann: AnnotationIterator?, value: () -> BigInteger) {
        }

        override fun onDouble(fieldName: String?, ann: AnnotationIterator?, value: Double) {
        }

        override fun onDecimal(fieldName: String?, ann: AnnotationIterator?, value: () -> BigDecimal) {
        }

        override fun onSymbol(fieldName: String?, ann: AnnotationIterator?, sid: Int, text: () -> String?) {
            if (fieldName == "imports" && sid == SystemSymbols.ION_SYMBOL_TABLE_SID) {
                append = true
            }
        }

        override fun onString(fieldName: String?, ann: AnnotationIterator?, text: () -> String) {
            TODO("Not yet implemented")
        }

        override fun onBlob(fieldName: String?, ann: AnnotationIterator?, bytes: ByteBuffer) {
            TODO("Not yet implemented")
        }

        override fun onClob(fieldName: String?, ann: AnnotationIterator?, bytes: ByteBuffer) {
            TODO("Not yet implemented")
        }

        override fun onList(fieldName: String?, ann: AnnotationIterator?, visitContent: (IonVisitor) -> Unit) {
            if (fieldName == "symbols") {
                newSymbols.size = 0
                visitContent(newSymbols)
            }
        }

        override fun onSexp(fieldName: String?, ann: AnnotationIterator?, visitContent: (IonVisitor) -> Unit) {
            TODO("Not yet implemented")
        }

        override fun onStruct(fieldName: String?, ann: AnnotationIterator?, visitContent: (IonFieldVisitor) -> Unit) {
            TODO("Not yet implemented")
        }

        override fun onTimestamp(fieldName: String?, ann: AnnotationIterator?, value: () -> Timestamp) {
            TODO("Not yet implemented")
        }

        override fun onMacro(
            fieldName: String?,
            macro: MacroV2,
            evaluate: (IonVisitor) -> Unit,
            visitArguments: (IonVisitor) -> Unit
        ) {
            TODO("Not yet implemented")
        }
    }

    private class SymbolsListVisitor(
    ): IonVisitor {
        @JvmField
        var newSymbols: Array<String?> = arrayOfNulls(32)
        @JvmField
        var size : Int = 0

        private fun ensureCapacity(): Array<String?> {
            val symbols = newSymbols
            val size = size
            if (size == symbols.size) {
                val largerSymbols = symbols.copyOf(size * 2)
                newSymbols = largerSymbols
                return largerSymbols
            }
            return symbols
        }

        override fun onNull(ann: AnnotationIterator?, type: IonType) {
            ensureCapacity()[size++] = null
        }

        override fun onBool(ann: AnnotationIterator?, value: Boolean) {
            TODO("Not yet implemented")
        }

        override fun onLong(ann: AnnotationIterator?, value: Long) {
            TODO("Not yet implemented")
        }

        override fun onBigInt(ann: AnnotationIterator?, value: () -> BigInteger) {
            TODO("Not yet implemented")
        }

        override fun onDouble(ann: AnnotationIterator?, value: Double) {
            TODO("Not yet implemented")
        }

        override fun onDecimal(ann: AnnotationIterator?, value: () -> BigDecimal) {
            TODO("Not yet implemented")
        }

        override fun onTimestamp(ann: AnnotationIterator?, value: () -> Timestamp) {
            TODO("Not yet implemented")
        }

        override fun onSymbol(ann: AnnotationIterator?, sid: Int, text: () -> String?) {
            TODO("Not yet implemented")
        }

        override fun onString(ann: AnnotationIterator?, text: () -> String) {
            ensureCapacity()[size++] = text()
        }

        override fun onBlob(ann: AnnotationIterator?, bytes: ByteBuffer) {
            TODO("Not yet implemented")
        }

        override fun onClob(ann: AnnotationIterator?, bytes: ByteBuffer) {
            TODO("Not yet implemented")
        }

        override fun onList(ann: AnnotationIterator?, content: (IonVisitor) -> Unit) {
            TODO("Not yet implemented")
        }

        override fun onSexp(ann: AnnotationIterator?, content: (IonVisitor) -> Unit) {
            TODO("Not yet implemented")
        }

        override fun onStruct(ann: AnnotationIterator?, content: (IonFieldVisitor) -> Unit) {
            TODO("Not yet implemented")
        }

        override fun onMacro(macro: MacroV2, evaluate: (IonVisitor) -> Unit, visitArguments: (IonVisitor) -> Unit) {
            TODO("Not reachable in Ion 1.0")
        }
    }
}



