package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.v2.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class ValueReaderBase(
    internal var source: ByteBuffer,
    internal var pool: ResourcePool,
    protected var symbolTable: Array<String?>,
    ): ValueReader {

    init {
        source.order(ByteOrder.LITTLE_ENDIAN)
    }

    companion object {
        const val TID_NONE: Short = -1 // change to UNSET
        const val NEEDS_DATA: Short = -2
        const val INVALID_DATA: Short = -3
        const val TID_AFTER_ANNOTATION: Short = -4
        const val TID_AFTER_FIELD_NAME: Short = -5
        const val TID_START: Short = -6
        const val TID_END: Short = -7
    }

    /**
     * Either the current opcode/typeId (if positive) or some other indicator, if negative.
     */
    internal var opcode: Short = TID_NONE

    internal fun init(
        start: Int,
        length: Int,
        symbolTable: Array<String?>,
    ) {
        source.limit(start + length)
        source.position(start)
        this.symbolTable = symbolTable
        opcode = TID_NONE
    }

    override fun nextToken(): Int {
        var type: Int
        do {
            if (!source.hasRemaining()) return TokenTypeConst.END

            // Check for the next token...
            val b = source.get()
            opcode = (b.toInt() and 0xFF).toShort()
            type = type(opcode.toInt())
            when (opcode) {
                TID_NONE -> continue
                TID_END -> return TokenTypeConst.END
                else -> break
            }
        } while (true)

        return type
    }

    //  Returns the TokenType constant.
    private fun type(state: Int): Int {
        if (state < 0) {
            return if (state == TID_END.toInt())
                TokenTypeConst.END
            else
                TokenTypeConst.UNSET
        }
        return IdMappings.TOKEN_TYPE_FOR_OPCODE[state]
    }

    override fun currentToken(): Int = type(opcode.toInt())

    override fun ionType(): IonType? {
        TODO("Not yet implemented")
    }

    override fun valueSize(): Int {
        TODO("Not yet implemented")
    }

    override fun skip() {
        val opcode = opcode.toInt()
        val length = IdMappings.length(opcode, source)
        if (length >= 0) {
            source.position(source.position() + length)
        } else {
            when (val token = type(opcode)) {
                // TODO: make this better.
                TokenTypeConst.LIST -> listValue().use {  }
                TokenTypeConst.SEXP -> sexpValue().use {  }
                TokenTypeConst.STRUCT -> structValue().use {  }
                TokenTypeConst.ANNOTATIONS -> annotations().use {  }
                else -> TODO("Skipping a ${TokenTypeConst(token)} [opcode: 0x${opcode.toString(16)}]")
            }
        }
    }

    override fun nullValue(): IonType {
        return if (opcode.toInt() == 0xEA) {
            IonType.NULL
        } else if (opcode.toInt() == 0xEB) {
            val type = source.get()
            when (type.toInt()) {
                0x00 -> IonType.BOOL
                0x01 -> IonType.INT
                0x02 -> IonType.FLOAT
                0x03 -> IonType.DECIMAL
                0x04 -> IonType.TIMESTAMP
                0x05 -> IonType.STRING
                0x06 -> IonType.SYMBOL
                0x07 -> IonType.BLOB
                0x08 -> IonType.CLOB
                0x09 -> IonType.LIST
                0x0A -> IonType.SEXP
                0x0B -> IonType.STRUCT
                else -> throw IonException("Not a valid null value")
            }
        } else {
            throw IonException("Not positioned on a null")
        }
    }

    override fun booleanValue(): Boolean {
        val bool = when (opcode.toInt()) {
            0x6E -> true
            0x6F -> false
            else -> throw IonException("Not positioned on a boolean")
        }
        opcode = TID_NONE
        return bool
    }

    override fun longValue(): Long {
        return when (val typeId = this.opcode.toInt()) {
            0x60 -> 0
            0x61 -> source.get().toLong()
            0x62 -> source.getShort().toLong()
            0x63 -> IntHelper.readFixedInt(source, 3)
            0x64 -> source.getInt().toLong()
            0x65 -> IntHelper.readFixedInt(source, 5)
            0x66 -> IntHelper.readFixedInt(source, 6)
            0x67 -> IntHelper.readFixedInt(source, 7)
            0x68 -> source.getLong()
            else -> if (typeId == 0xF6) {
                // Not really supported for Longs anyway.
                TODO("Variable length integers")
            } else {
                throw IonException("Not positioned on an int")
            }
        }.also { opcode = TID_NONE }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun stringValue(): String {
        val opcode = opcode.toInt()
        val length = if (opcode shr 4 == 9) {
            opcode and 0xF
        } else if (opcode == 0xF9) {
            IntHelper.readFlexUInt(source)
        } else {
            throw IonException("Not positioned on a string")
        }
        val position = source.position()
        val scratchBuffer = pool.scratchBuffer
        scratchBuffer.limit(position + length)
        scratchBuffer.position(position)
        source.position(position + length)
        this.opcode = TID_NONE
        return pool.utf8Decoder.decode(scratchBuffer, length)
    }

    override fun symbolValue(): String? {
        val opcode = opcode.toInt()
        val length = IdMappings.length(opcode, source)
        val position = source.position()
        this.opcode = TID_NONE
        if (opcode == 0xF9 || opcode and 0xF0 == 0xA0) {
            // Inline text
            val scratchBuffer = pool.scratchBuffer
            scratchBuffer.limit(position + length)
            scratchBuffer.position(position)
            source.position(position + length)
            this.opcode = TID_NONE
            return pool.utf8Decoder.decode(scratchBuffer, length)
        } else if (opcode == 0xEE) {
            // System SID
            val sid = IntHelper.readFixedUInt(source, 1)
            return SystemSymbols_1_1[sid]?.text
        } else if (opcode == 0xE1) {
            // 1 byte SID
            val sid = IntHelper.readFixedUInt(source, 1)
            return symbolTable[sid]
        } else if (opcode == 0xE2) {
            // 2 byte SID with bias
            val sid = IntHelper.readFixedUInt(source, 2) + 256
            return symbolTable[sid]
        } else if (opcode == 0xE3) {
            // FlexUInt SID with bias
            val sid = IntHelper.readFlexUInt(source) + 65792
            return symbolTable[sid]
        } else {
            throw IonException("Not positioned on a symbol")
        }
    }

    override fun symbolValueSid(): Int {
        val opcode = opcode.toInt()
        if (opcode == 0xF9 || opcode and 0xF0 == 0xA0) {
            // Inline text
            return -1
        } else if (opcode == 0xEE) {
            // System SID
            return -1
        } else if (opcode == 0xE1) {
            // 1 byte SID
            this.opcode = TID_NONE
            val sid = IntHelper.readFixedUInt(source, 1)
            return sid
        } else if (opcode == 0xE2) {
            // 2 byte SID with bias
            this.opcode = TID_NONE
            val sid = IntHelper.readFixedUInt(source, 2) + 256
            return sid
        } else if (opcode == 0xE3) {
            // FlexUInt SID with bias
            this.opcode = TID_NONE
            val sid = IntHelper.readFlexUInt(source) + 65792
            return sid
        } else {
            throw IonException("Not positioned on a symbol")
        }
    }

    override fun clobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun blobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun lookupSid(sid: Int): String? {
        return symbolTable[sid]
    }

    override fun listValue(): ListReader {
        val opcode = opcode.toInt()
        this.opcode = TID_NONE
        val length = if (opcode shr 4 == 0xB) {
            opcode and 0xF
        } else if (opcode == 0xFB) {
            IntHelper.readFlexUInt(source)
        } else if (opcode == 0xF1) {
            val start = source.position()
            return pool.getDelimitedList(start, source.limit(), symbolTable, this)
        } else {
            throw IonException("Not positioned on a list")
        }
        val start = source.position()
        source.position(start + length)
        return pool.getList(start, length, symbolTable)
    }

    override fun sexpValue(): SexpReader {
        val length = IdMappings.length(opcode.toInt(), source)
        if (length < 0) {
            // Delimited container
            val start = source.position()
            return pool.getDelimitedSexp(start, this, symbolTable)
        } else {
            // Length prefixed container
            val start = source.position()
            source.position(start + length)
            opcode = TID_NONE
            return pool.getPrefixedSexp(start, length, symbolTable)
        }
    }

    override fun structValue(): StructReader {
        val opcode = opcode.toInt()
        val length = IdMappings.length(opcode, source)
        if (length < 0) {
            // Delimited container
            val start = source.position()
            TODO("getDelimitedStruct")
        } else {
            // Length prefixed container
            val start = source.position()
            source.position(start + length)
            this.opcode = TID_NONE
            return pool.getStruct(start, length, symbolTable)
        }
    }

    override fun annotations(): AnnotationIterator {
        val opcode = opcode.toInt()
        this.opcode = TID_AFTER_ANNOTATION
        val start = source.position()
        var length = IdMappings.length(opcode, source)
        if (length < 0) {
            length = source.limit() - start
            // And we need to make sure that the position of _this_ reader is updated correctly...
            // TODO: Fix this.
            val ann = pool.getAnnotations(opcode, start, length, symbolTable)
            while (ann.hasNext()) ann.next()
            source.position((ann as AnnotationIteratorImpl).source.position())
            ann.close()
        } else {
            source.position(start + length)
        }
        return pool.getAnnotations(opcode, start, length, symbolTable)
    }

    override fun timestampValue(): Timestamp {
        val opcode = opcode.toInt()
        this.opcode = TID_NONE
        return TimestampHelper.readTimestamp(opcode, source)
    }

    override fun doubleValue(): Double {
        return when (opcode.toInt()) {
            0x6A -> 0.0
            0x6B -> TODO()
            0x6C -> source.getFloat().toDouble()
            0x6D -> source.getDouble()
            else -> throw IonException("Not positioned on a float")
        }.also { opcode = TID_NONE }
    }

    override fun decimalValue(): Decimal {
        TODO("Not yet implemented")
    }

    override fun eexpValue(): EexpReader {
        val opcode = opcode.toInt()
        TODO("$opcode")
    }
}

