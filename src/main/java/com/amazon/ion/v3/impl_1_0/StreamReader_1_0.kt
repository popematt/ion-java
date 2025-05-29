package com.amazon.ion.v3.impl_1_0

import com.amazon.ion.Decimal
import com.amazon.ion.IonException
import com.amazon.ion.IonType
import com.amazon.ion.SystemSymbols
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.bin.utf8.Utf8StringDecoder
import com.amazon.ion.impl.bin.utf8.Utf8StringDecoderPool
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.StaticFunctions.Companion.length
import com.amazon.ion.v3.impl_1_0.StaticFunctions.Companion.readVarInt
import com.amazon.ion.v3.impl_1_0.StaticFunctions.Companion.readVarUInt
import com.amazon.ion.v3.impl_1_0.StaticFunctions.Companion.type
import com.amazon.ion.v3.impl_1_0.StaticFunctions.Companion.ionType
import com.amazon.ion.v3.impl_1_0.StaticFunctions.Companion.skip
import java.io.Closeable
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

// TODO:
// interface SequenceReader
// interface StreamReader: SequenceReader
// interface ListReader: SequenceReader
// interface SexpReader: SequenceReader
// interface StructReader
// internal abstract class ValueReaderBase: ValueReader
// internal class ListReaderImpl: ValueReaderBase(), ListReader
// etc.

private val ION_1_0_SYMBOL_TABLE = arrayOf(
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

// Specifically for Ion 1.0
internal class ReaderPool_1_0(
    private val source: ByteBuffer,
): Closeable {
    val scratch: Array<ByteArray> = Array(16) { n -> ByteArray(n) }

    private lateinit var _utf8Decoder: Utf8StringDecoder

    val utf8Decoder: Utf8StringDecoder
        get() {
            if (!::_utf8Decoder.isInitialized) {
                _utf8Decoder = Utf8StringDecoderPool.getInstance().getOrCreate()
            }
            return _utf8Decoder
        }


    val scratchBuffer = source.asReadOnlyBuffer()
    val structs = ArrayList<StructReader_1_0>(64)
    val lists = ArrayList<ListReader_1_0>(64)
    val sexps = ArrayList<SexpReader_1_0>(64)
    val annotations = ArrayList<AnnotationIterator>(16)

    fun getList(start: Int, length: Int, symbolTable: Array<String?>): ListReader_1_0 {
        val reader = lists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + length)
            slice.position(start)
            return ListReader_1_0(slice, this, symbolTable)
        }
    }

    fun getAnnotations(start: Int, length: Int, symbolTable: Array<String?>): AnnotationIterator {
        val reader = annotations.removeLastOrNull() as AnnotationIteratorImpl_1_0?
        if (reader != null) {
            reader.init(start, length, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + length)
            slice.position(start)
            return AnnotationIteratorImpl_1_0(slice, symbolTable, this)
        }
    }

    fun getSexp(start: Int, length: Int, symbolTable: Array<String?>): SexpReader_1_0 {
        val reader = sexps.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + length)
            slice.position(start)
            return SexpReader_1_0(slice, this, symbolTable)
        }
    }

    fun getStruct(start: Int, length: Int, symbolTable: Array<String?>): StructReader_1_0 {
        val reader = structs.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + length)
            slice.position(start)
            return StructReader_1_0(slice, this, symbolTable)
        }
    }

    override fun close() {
        annotations.clear()
        structs.clear()
        lists.clear()
        sexps.clear()
    }
}

internal class StaticFunctions {

    companion object {
        @JvmStatic
        fun readVarUInt(source: ByteBuffer): Int {
            val firstByte = source.get()
            return if (firstByte < 0) {
                firstByte.toInt() and 0x7F
            } else {
                readVarUInt(source, firstByte).toInt()
            }
        }

        @JvmStatic
        fun readVarUInt(source: ByteBuffer, firstByte: Byte): Long {
            var result = firstByte.toLong()
            do {
                val currentByte = source.get()
                result = (result shl 7) or (currentByte.toInt() and 0b01111111).toLong()
            } while (currentByte >= 0)
            if (result > Int.MAX_VALUE) throw IonException("Found a VarUInt that was too large to fit in a `int` -- $result")
            return result
        }

        @JvmStatic
        fun readVarInt(source: ByteBuffer): Long {
            var result: Long
            var currentByte = source.get()
            val sign = if ((currentByte.toInt() and 0b01000000) == 0) 1 else -1
            result = (currentByte.toInt() and 0x3F).toLong()
            while (currentByte >= 0) {
                currentByte = source.get()
                result = (result shl 7) or (currentByte.toInt() and 0b01111111).toLong()
                if (result > Int.MAX_VALUE) throw IonException("Found a VarInt that was too large to fit in a `Int` -- $result")
            }
            return result * sign
        }

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
        fun type(state: Int): Int {
            if (state < 0) {
                if (state == ValueReaderBase.TID_END.toInt()) TokenTypeConst.END
                return TokenTypeConst.UNSET
            }
            return typeLookup[state]
        }

        @JvmStatic
        val typeLookup = IntArray(256) {
            try {
                initType(it)
            } catch (e: IonException) {
                // TODO: Use "INVALID" here
                TokenTypeConst.UNSET
            }
        }

        @JvmStatic
        fun initType(state: Int): Int {
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

        /**
         * Returns the next byte after the token being skipped.
         */
        @JvmStatic
        @OptIn(ExperimentalStdlibApi::class)
        fun skip(typeId: Int, buffer: ByteBuffer) {
            when (typeId) {
                0x00, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80, 0x90, 0xA0, 0xB0, 0xC0, 0xD0 -> { /* Zero-length */
                }

                0x01, 0x11, 0x21, 0x31, 0x41, 0x51, 0x61, 0x71, 0x81, 0x91, 0xA1, 0xB1, 0xC1, 0xD1, 0xE1 -> {
                    buffer.position(buffer.position() + 1)
                }

                0x02, 0x12, 0x22, 0x32, 0x42, 0x52, 0x62, 0x72, 0x82, 0x92, 0xA2, 0xB2, 0xC2, 0xD2, 0xE2 -> {
                    buffer.position(buffer.position() + 2)
                }

                0x03, 0x13, 0x23, 0x33, 0x43, 0x53, 0x63, 0x73, 0x83, 0x93, 0xA3, 0xB3, 0xC3, 0xD3, 0xE3 -> {
                    buffer.position(buffer.position() + 3)
                }

                0x04, 0x14, 0x24, 0x34, 0x44, 0x54, 0x64, 0x74, 0x84, 0x94, 0xA4, 0xB4, 0xC4, 0xD4, 0xE4 -> {
                    buffer.position(buffer.position() + 4)
                }

                0x05, 0x15, 0x25, 0x35, 0x45, 0x55, 0x65, 0x75, 0x85, 0x95, 0xA5, 0xB5, 0xC5, 0xD5, 0xE5 -> {
                    buffer.position(buffer.position() + 5)
                }

                0x06, 0x16, 0x26, 0x36, 0x46, 0x56, 0x66, 0x76, 0x86, 0x96, 0xA6, 0xB6, 0xC6, 0xD6, 0xE6 -> {
                    buffer.position(buffer.position() + 6)
                }

                0x07, 0x17, 0x27, 0x37, 0x47, 0x57, 0x67, 0x77, 0x87, 0x97, 0xA7, 0xB7, 0xC7, 0xD7, 0xE7 -> {
                    buffer.position(buffer.position() + 7)
                }

                0x08, 0x18, 0x28, 0x38, 0x48, 0x58, 0x68, 0x78, 0x88, 0x98, 0xA8, 0xB8, 0xC8, 0xD8, 0xE8 -> {
                    buffer.position(buffer.position() + 8)
                }

                0x09, 0x19, 0x29, 0x39, 0x49, 0x59, 0x69, 0x79, 0x89, 0x99, 0xA9, 0xB9, 0xC9, 0xD9, 0xE9 -> {
                    buffer.position(buffer.position() + 9)
                }

                0x0A, 0x1A, 0x2A, 0x3A, 0x4A, 0x5A, 0x6A, 0x7A, 0x8A, 0x9A, 0xAA, 0xBA, 0xCA, 0xDA, 0xEA -> {
                    buffer.position(buffer.position() + 10)
                }

                0x0B, 0x1B, 0x2B, 0x3B, 0x4B, 0x5B, 0x6B, 0x7B, 0x8B, 0x9B, 0xAB, 0xBB, 0xCB, 0xDB, 0xEB -> {
                    buffer.position(buffer.position() + 11)
                }

                0x0C, 0x1C, 0x2C, 0x3C, 0x4C, 0x5C, 0x6C, 0x7C, 0x8C, 0x9C, 0xAC, 0xBC, 0xCC, 0xDC, 0xEC -> {
                    buffer.position(buffer.position() + 12)
                }

                0x0D, 0x1D, 0x2D, 0x3D, 0x4D, 0x5D, 0x6D, 0x7D, 0x8D, 0x9D, 0xAD, 0xBD, 0xCD, 0xDD, 0xED -> {
                    buffer.position(buffer.position() + 13)
                }

                0x0E, 0x1E, 0x2E, 0x3E, 0x4E, 0x5E, 0x6E, 0x7E, 0x8E, 0x9E, 0xAE, 0xBE, 0xCE, 0xDE, 0xEE -> {
                    val length = readVarUInt(buffer).toInt()
                    buffer.position(buffer.position() + length)
                }

                0x0F, 0x1F, 0x2F, 0x3F, 0x4F, 0x5F, 0x6F, 0x7F, 0x8F, 0x9F, 0xAF, 0xBF, 0xCF, 0xDF -> { /* Nulls */
                }
                // IVM
                0xE0 -> throw IonException("Cannot skip IVM")
                // Reserved
                0xEF, 0xF0, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF -> throw IonException(
                    "Invalid input: illegal typeId: $typeId"
                )

                else -> TODO("This should be unreachable: ${typeId.toHexString()}")
            }
        }

        @JvmStatic
        fun length(typeId: Int, buffer: ByteBuffer): Int {
            return when (val it = lengthAmounts[typeId]) {
                -2 -> throw IonException("Invalid input: illegal typeId: $typeId")
                -1 -> readVarUInt(buffer).toInt()
                else -> it
            }
        }

        @JvmStatic
        val lengthAmounts = IntArray(256) { lengthInit(it) }

        @JvmStatic
        @OptIn(ExperimentalStdlibApi::class)
        fun lengthInit(typeId: Int): Int {
            return when (typeId) {
                0x00, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80, 0x90, 0xA0, 0xB0, 0xC0, 0xD0 -> 0
                0x01, 0x11, 0x21, 0x31, 0x41, 0x51, 0x61, 0x71, 0x81, 0x91, 0xA1, 0xB1, 0xC1, 0xD1, 0xE1 -> 1
                0x02, 0x12, 0x22, 0x32, 0x42, 0x52, 0x62, 0x72, 0x82, 0x92, 0xA2, 0xB2, 0xC2, 0xD2, 0xE2 -> 2
                0x03, 0x13, 0x23, 0x33, 0x43, 0x53, 0x63, 0x73, 0x83, 0x93, 0xA3, 0xB3, 0xC3, 0xD3, 0xE3 -> 3
                0x04, 0x14, 0x24, 0x34, 0x44, 0x54, 0x64, 0x74, 0x84, 0x94, 0xA4, 0xB4, 0xC4, 0xD4, 0xE4 -> 4
                0x05, 0x15, 0x25, 0x35, 0x45, 0x55, 0x65, 0x75, 0x85, 0x95, 0xA5, 0xB5, 0xC5, 0xD5, 0xE5 -> 5
                0x06, 0x16, 0x26, 0x36, 0x46, 0x56, 0x66, 0x76, 0x86, 0x96, 0xA6, 0xB6, 0xC6, 0xD6, 0xE6 -> 6
                0x07, 0x17, 0x27, 0x37, 0x47, 0x57, 0x67, 0x77, 0x87, 0x97, 0xA7, 0xB7, 0xC7, 0xD7, 0xE7 -> 7
                0x08, 0x18, 0x28, 0x38, 0x48, 0x58, 0x68, 0x78, 0x88, 0x98, 0xA8, 0xB8, 0xC8, 0xD8, 0xE8 -> 8
                0x09, 0x19, 0x29, 0x39, 0x49, 0x59, 0x69, 0x79, 0x89, 0x99, 0xA9, 0xB9, 0xC9, 0xD9, 0xE9 -> 9
                0x0A, 0x1A, 0x2A, 0x3A, 0x4A, 0x5A, 0x6A, 0x7A, 0x8A, 0x9A, 0xAA, 0xBA, 0xCA, 0xDA, 0xEA -> 10
                0x0B, 0x1B, 0x2B, 0x3B, 0x4B, 0x5B, 0x6B, 0x7B, 0x8B, 0x9B, 0xAB, 0xBB, 0xCB, 0xDB, 0xEB -> 11
                0x0C, 0x1C, 0x2C, 0x3C, 0x4C, 0x5C, 0x6C, 0x7C, 0x8C, 0x9C, 0xAC, 0xBC, 0xCC, 0xDC, 0xEC -> 12
                0x0D, 0x1D, 0x2D, 0x3D, 0x4D, 0x5D, 0x6D, 0x7D, 0x8D, 0x9D, 0xAD, 0xBD, 0xCD, 0xDD, 0xED -> 13
                0x0E, 0x1E, 0x2E, 0x3E, 0x4E, 0x5E, 0x6E, 0x7E, 0x8E, 0x9E, 0xAE, 0xBE, 0xCE, 0xDE, 0xEE -> -1
                // Nulls
                0x0F, 0x1F, 0x2F, 0x3F, 0x4F, 0x5F, 0x6F, 0x7F, 0x8F, 0x9F, 0xAF, 0xBF, 0xCF, 0xDF -> 0
                // IVM
                0xE0 -> 3 // ...3 more than the typeId byte.
                // Reserved
                0xEF, 0xF0, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF -> -2

                else -> TODO("This should be unreachable: ${typeId.toHexString()}")
            }
        }
    }
}

abstract class ValueReaderBase internal constructor(
    @JvmField
    internal var source: ByteBuffer,
    @JvmField
    internal var pool: ReaderPool_1_0,
    @JvmField
    internal var symbolTable: Array<String?>,
): ValueReader {

    companion object {
        const val NEEDS_DATA: Short = -99
        const val INVALID_DATA: Short = -98
        const val TID_AFTER_ANNOTATION: Short = -97
        const val TID_FIELD_NAME: Short = -96
        const val TID_START: Short = -95
        const val TID_END: Short = -94
        const val TID_NONE: Short = -93
    }

    init {
        source.order(ByteOrder.BIG_ENDIAN)
    }

    /**
     * Either the current opcode/typeId (if positive) or some other indicator, if negative.
     */
    @JvmField
    internal var typeId: Short = TID_NONE

    internal fun init(
        start: Int,
        length: Int,
        symbolTable: Array<String?>,
    ) {
        source.limit(start + length)
        source.position(start)
        this.symbolTable = symbolTable
        typeId = TID_NONE
        debugInit()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun debugInit() {
//        println("""
//            ${this::class.simpleName}
//              source.position: ${source.position()}
//              source.limit: ${source.limit()}
//              typeId: ${typeId.toHexString()}
//        """.trimIndent())
    }

    protected abstract fun release()

    override fun close() {
        // Free any resources; fields that are non-nullable are replaced with singleton placeholder values
        // source = EMPTY_BYTE_BUFFER
        // And return to the pool
        release()
    }

    override fun nextToken(): Int {
        var type: Int
        do {
            if (!source.hasRemaining()) return TokenTypeConst.END

            // Check for the next token...
            val b = source.get()
            typeId = (b.toInt() and 0xFF).toShort()
            type = type(typeId.toInt())
            when (typeId) {
                TID_NONE -> continue
                TID_END -> return TokenTypeConst.END
                else -> break
            }
        } while (true)

        return type
    }

    // TODO: Consider caching `currentToken` value.
    override fun currentToken(): Int = type(typeId.toInt())

    override fun isTokenSet(): Boolean = typeId != TID_NONE

    override fun ionType(): IonType? = ionType(typeId.toInt())

    override fun skip() {
        skip(typeId.toInt(), source)
        typeId = TID_NONE
    }

    override fun annotations(): AnnotationIterator {
        length(typeId.toInt(), source)
        val lengthOfAnnotationSids = readVarUInt(source)
        val start = source.position()
        source.position(start + lengthOfAnnotationSids)
        typeId = TID_AFTER_ANNOTATION
        return pool.getAnnotations(start, lengthOfAnnotationSids, symbolTable)
    }

    override fun valueSize(): Int {
        return when (val l = typeId.toInt() and 0xF) {
            14 -> -1
            15 -> 0
            else -> l
        }
    }

    override fun nullValue(): IonType {
        if (typeId.toInt() and 0xF != 0xF) {
            throw IonException("Not positioned on a null value")
        }
        return when (typeId.toInt() ushr 4) {
            0x0 -> IonType.NULL
            0x1 -> IonType.BOOL
            0x2,
            0x3 -> IonType.INT
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
            else -> throw IonException("Not positioned on a null value")
        }.also { typeId = TID_NONE }
    }

    /**
     * Returns the current value as a boolean.
     * This is only valid when [.getType] returns [IonType.BOOL].
     */
    override fun booleanValue(): Boolean {
        return when (typeId.toInt()) {
            0x10 -> false
            0x11 -> true
            else -> throw IonException("Not positioned on a boolean value")
        }.also { typeId = TID_NONE }
    }

    /**
     * Returns the current Int value as a long.
     */
    override fun longValue(): Long = IntReader.readLong(typeId, source).also { typeId = TID_NONE }


    override fun doubleValue(): Double {
        val length = length(typeId.toInt(), source)
        typeId = TID_NONE
        return when (length) {
            0 -> 0.0
            4 -> source.getFloat().toDouble()
            8 -> source.getDouble()
            else -> throw IonException("Not a valid Ion Float")
        }
    }

    override fun decimalValue(): Decimal {
        val length = length(typeId.toInt(), source)
        typeId = TID_NONE
        val end = source.position() + length
        return readDecimalContent(end)
    }

    private fun readDecimalContent(end: Int): Decimal {
        val scale = -readVarInt(source).toInt()
        val coefficientLength = end - source.position()
        return if (coefficientLength > 0) {
            val scratch = pool.scratch.getOrElse(coefficientLength) { ByteArray(coefficientLength) }
            source.get(scratch)
            val signum = if (scratch[0].toInt() and 0x80 == 0) 1 else -1
            scratch[0] = scratch[0] and 0x7F
            val coefficient = BigInteger(signum, scratch)
            if (coefficient == BigInteger.ZERO && signum == -1) {
                Decimal.negativeZero(scale)
            } else {
                Decimal.valueOf(BigInteger(signum, scratch), scale)
            }
        } else {
            Decimal.valueOf(BigInteger.ZERO, scale)
        }
    }

    override fun timestampValue(): Timestamp {
        val len = length(typeId.toInt(), source)
        typeId = TID_NONE

        val end = source.position() + len

        val offset: Int? = if (source.get(source.position()).toUByte() == 0xc0u.toUByte()) {
            null
        } else {
            readVarInt(source).toInt()
        }
        val year = readVarUInt(source).toInt()
        var month = 0
        var day = 0
        var hour = 0
        var minute = 0
        var second = 0
        var fractionalSecond: BigDecimal? = null
        var precision = Timestamp.Precision.YEAR
        if (source.position() < end) {
            month = readVarUInt(source).toInt()
            precision = Timestamp.Precision.MONTH
            if (source.position() < end) {
                day = readVarUInt(source).toInt()
                precision = Timestamp.Precision.DAY
                if (source.position() < end) {
                    hour = readVarUInt(source).toInt()
                    if (source.position() >= end) {
                        throw IonException("Timestamps may not specify hour without specifying minute.")
                    }
                    minute = readVarUInt(source).toInt()
                    precision = Timestamp.Precision.MINUTE
                    if (source.position() < end) {
                        second = readVarUInt(source).toInt()
                        precision = Timestamp.Precision.SECOND
                        if (source.position() < end) {
                            fractionalSecond = readDecimalContent(end)
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
            throw IonException("Illegal timestamp encoding. ", e)
        }
    }

    override fun stringValue(): String {
        var length = typeId.toInt() and 0xF
        if (length == 0xE) {
            length = readVarUInt(source).toInt()
        }
        val start = source.position()
        pool.scratchBuffer.limit(start + length)
        pool.scratchBuffer.position(start)
        val position = source.position()
        val text = pool.utf8Decoder.decode(pool.scratchBuffer, length)
        source.position(position + length)
        typeId = TID_NONE
        return text
    }

    override fun symbolValue(): String? {
        return symbolTable[symbolValueSid()]
    }

    override fun lookupSid(sid: Int): String? = symbolTable[sid]

    override fun symbolValueSid(): Int {
        var length = typeId.toInt() and 0xF
        if (length == 0xE) {
            length = readVarUInt(source)
        }
        val sid = when (length) {
            0 -> 0
            1 -> source.get().toInt() and 0xFF
            2 -> source.getShort().toInt() and 0xFFFF
            3 -> IntReader.readUIntAsInt(source, 3)
            4 -> source.getInt()
            else -> throw IonException("SIDs larger than 4 bytes are not supported; found $length")
        }
        typeId = TID_NONE
        return sid
    }

    override fun blobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun clobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun listValue(): ListReader_1_0 {
        val length = length(typeId.toInt(), source)
        val start = source.position()
        source.position(start + length)
        typeId = TID_NONE
        return pool.getList(start, length, symbolTable)
    }
    override fun sexpValue(): SexpReader_1_0 {
        val length = length(typeId.toInt(), source)
        val start = source.position()
        source.position(start + length)
        typeId = TID_NONE
        return pool.getSexp(start, length, symbolTable)
    }
    override fun structValue(): StructReader_1_0 {
        val length = length(typeId.toInt(), source)
        val start = source.position()
        source.position(start + length)
        typeId = TID_NONE
        return pool.getStruct(start, length, symbolTable)
    }

    // Delimited containers would need to have a reference to the parent, perhaps, so that when you close the delimited
    // container, the parent position can be updated to the appropriate index.

    override fun getIonVersion(): Short = 0x0100

    override fun ivm(): Short {
        if (typeId.toInt() != 0xE0) throw IonException("Not positioned on an IVM")
        symbolTable = ION_1_0_SYMBOL_TABLE
        typeId = TID_NONE
        val version = source.getShort()
        // TODO: Check the last byte of the IVM to make sure it is well formed.
        source.get()
        return version
    }

    override fun seekTo(position: Int) {
        source.position(position)
    }

    override fun position(): Int = source.position()
}

class StreamReader_1_0(source: ByteBuffer): ValueReaderBase(source, ReaderPool_1_0(source.asReadOnlyBuffer()), ION_1_0_SYMBOL_TABLE), StreamReader {

    override fun release() {
        pool.close()
    }

    // When we see a top-level annotated value, first place a marker, then check if the annotation is "$ion_symbol_table"
    // Then see if it's a struct. If so, then use the symbol table reader.


    override tailrec fun nextToken(): Int {
        val token = super.nextToken()

        if (token != TokenTypeConst.ANNOTATIONS) return token

        source.mark()
        val savedTypeId = typeId
        // Ignore the combined length
        length(typeId.toInt(), source)

        val annotationsLength = readVarUInt(source)
        if (annotationsLength > 1) {
            source.reset()
            typeId = savedTypeId
            return TokenTypeConst.ANNOTATIONS
        }
        val firstAnnotationSid = readVarUInt(source)
        if (firstAnnotationSid != SystemSymbols.ION_SYMBOL_TABLE_SID) {
            source.reset()
            typeId = savedTypeId
            return TokenTypeConst.ANNOTATIONS
        }
        val maybeStruct = super.nextToken()
        if (maybeStruct != TokenTypeConst.STRUCT) {
            source.reset()
            typeId = savedTypeId
            return TokenTypeConst.ANNOTATIONS
        }

        // Finally, we know we are on a struct annotated with $ion_symbol_table
        symbolTable = structValue().use { structReader -> readSymbolTable(structReader) }

        // Now, try again to get a token for an application value
        return nextToken()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun readSymbolTable(structReader: StructReader_1_0): Array<String?> {
        val newSymbols = ArrayList<String?>()
        var isLstAppend = false
        while (true) {
            val token = structReader.nextToken()
            if (token == TokenTypeConst.END) {
                break
            }
            val fieldName = structReader.fieldNameSid()
            when (fieldName) {
                SystemSymbols.IMPORTS_SID -> {

                    val importsToken = structReader.nextToken()
                    when (importsToken) {
                        TokenTypeConst.SYMBOL -> {
                            if (structReader.symbolValueSid() == SystemSymbols.ION_SYMBOL_TABLE_SID) {
                                isLstAppend = true
                            }
                        }
                        TokenTypeConst.LIST -> {
                            TODO("Imports not supported yet.")
                        }
                        else -> {}
                    }
                }
                SystemSymbols.SYMBOLS_SID -> {
                    structReader.nextToken()
                    structReader.listValue().use { listReader ->
                        while (true) {
                            when (val t = listReader.nextToken()) {
                                TokenTypeConst.END -> break
                                TokenTypeConst.STRING -> newSymbols.add(listReader.stringValue())
                                TokenTypeConst.NULL -> newSymbols.add(null)
                                else -> throw IonException("Unexpected token type in symbols list: $t")
                            }
                        }
                    }
                }
                else -> {
                    if (fieldName >= symbolTable.size) throw IonException("SID $fieldName is not in the symbol table (position: ${structReader.source.position()})\n${structReader.source}")
                }
            }
        }
        val startOfNewSymbolTable = if (isLstAppend) symbolTable else ION_1_0_SYMBOL_TABLE
        val newSymbolTable = Array<String?>(newSymbols.size + startOfNewSymbolTable.size) { null }
        System.arraycopy(startOfNewSymbolTable, 0, newSymbolTable, 0, startOfNewSymbolTable.size)
        System.arraycopy(newSymbols.toArray(), 0, newSymbolTable, startOfNewSymbolTable.size, newSymbols.size)
        return newSymbolTable
    }
}



class ListReader_1_0 internal constructor(
    source: ByteBuffer,
    pool: ReaderPool_1_0,
    symbolTable: Array<String?>,
) : ValueReaderBase(source, pool, symbolTable), ListReader  {
    override fun release() { pool.lists.add(this) }
}

class SexpReader_1_0 internal constructor(
    source: ByteBuffer,
    pool: ReaderPool_1_0,
    symbolTable: Array<String?>,
) : ValueReaderBase(source, pool, symbolTable), SexpReader  {
    override fun release() { pool.sexps.add(this) }
}

class StructReader_1_0 internal constructor(
    source: ByteBuffer,
    pool: ReaderPool_1_0,
    symbolTable: Array<String?>,
) : ValueReaderBase(source, pool, symbolTable), StructReader {
    override fun release() { pool.structs.add(this) }

    override fun nextToken(): Int {
        if (!source.hasRemaining()) return TokenTypeConst.END
        if (typeId == TID_NONE) return TokenTypeConst.FIELD_NAME

        return super.nextToken()
    }

    override fun fieldNameSid() : Int {
        val fieldSid = readVarUInt(source)
        typeId = TID_FIELD_NAME
        return fieldSid
    }

    override fun fieldName() : String? {
        val fieldSid = readVarUInt(source)
        typeId = TID_FIELD_NAME
        return symbolTable[fieldSid]
    }
}

object IntReader {
    @JvmStatic
    fun readLong(typeId: Short, source: ByteBuffer): Long {

        return when (typeId.toInt()) {
            0x20 -> 0
            0x21 -> (source.get().toInt() and 0xFF).toLong()
            0x22 -> readUInt(source, 2)
            0x23 -> readUInt(source, 3)
            0x24 -> readUInt(source, 4)
            0x25 -> readUInt(source, 5)
            0x26 -> readUInt(source, 6)
            0x27 -> readUInt(source, 7)
            0x28 -> readUInt(source, 8)
            0x29,
            0x2A,
            0x2B,
            0x2C,
            0x2D,
            0x2E -> TODO("This probably won't fit in a long")
            0x31 -> (source.get().toInt() or 0xFFFFFF00.toInt()).toLong()
            0x32 -> -readUInt(source, 2)
            0x33 -> -readUInt(source, 3)
            0x34 -> -readUInt(source, 4)
            0x35 -> -readUInt(source, 5)
            0x36 -> -readUInt(source, 6)
            0x37 -> -readUInt(source, 7)
            0x38 -> -readUInt(source, 8)
            0x39,
            0x3a,
            0x3b,
            0x3c,
            0x3d,
            0x3e -> TODO("This probably won't fit in a long")
            else -> throw IonException("Not positioned on a non-null integer value")
        }
    }

    @JvmStatic
    private fun readUInt(source: ByteBuffer, length: Int): Long {
        var result: Long = 0
        for (i in 0..< length) {
            result = (result shl 8) or (source.get().toInt() and 0xFF).toLong()
        }
        return result
    }

    @JvmStatic
    fun readUIntAsInt(source: ByteBuffer, length: Int): Int {
        var result = 0
        for (i in 0..< length) {
            result = (result shl 8) or (source.get().toInt() and 0xFF)
        }
        return result
    }
}

