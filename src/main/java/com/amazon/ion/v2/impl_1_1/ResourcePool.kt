package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.impl.bin.utf8.*
import com.amazon.ion.v2.*
import com.amazon.ion.v2.impl_1_0.*
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResourcePool(
    private val source: ByteBuffer,
    private var ion10Reader: StreamReader_1_0? = null
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

    val scratchBuffer: ByteBuffer = source.asReadOnlyBuffer()
    val structs = ArrayList<StructReaderImpl>(64)
    // TODO: Combine lists and s-expressions into one class and one pool
    val lists = ArrayList<SeqReaderImpl>(64)
    val delimitedLists = ArrayList<DelimitedSequenceReaderImpl>(64)
    val annotations = ArrayList<AnnotationIterator>(8)

    fun getList(start: Int, length: Int, symbolTable: Array<String?>): SeqReaderImpl {
        val reader = lists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + length)
            slice.position(start)
            slice.order(ByteOrder.LITTLE_ENDIAN)
            return SeqReaderImpl(slice, this, symbolTable)
        }
    }

    fun getDelimitedList(start: Int, maxLength: Int, symbolTable: Array<String?>, parent: ValueReaderBase): DelimitedSequenceReaderImpl {
        val reader = delimitedLists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, maxLength, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + maxLength)
            slice.position(start)
            slice.order(ByteOrder.LITTLE_ENDIAN)
            return DelimitedSequenceReaderImpl(slice, this, symbolTable, parent)
        }
    }

    fun getAnnotations(opcode: Int, start: Int, length: Int, symbolTable: Array<String?>): AnnotationIterator {
        val reader = annotations.removeLastOrNull() as AnnotationIteratorImpl?
        if (reader != null) {
            reader.init(opcode, start, length, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + length)
            slice.position(start)
            slice.order(ByteOrder.LITTLE_ENDIAN)
            return AnnotationIteratorImpl(opcode, slice, symbolTable, this)
        }
    }

    fun getPrefixedSexp(start: Int, length: Int, symbolTable: Array<String?>): SeqReaderImpl {
        val reader = lists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + length)
            slice.position(start)
            slice.order(ByteOrder.LITTLE_ENDIAN)
            return SeqReaderImpl(slice, this, symbolTable)
        }
    }

    fun getDelimitedSexp(start: Int, parent: ValueReaderBase, symbolTable: Array<String?>): DelimitedSequenceReaderImpl {
        val reader = delimitedLists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, source.limit() - start, symbolTable)
            reader.parent = parent
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.position(start)
            slice.order(ByteOrder.LITTLE_ENDIAN)
            return DelimitedSequenceReaderImpl(slice, this, symbolTable, parent)
        }
    }

    fun getStruct(start: Int, length: Int, symbolTable: Array<String?>): StructReaderImpl {
        val reader = structs.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length, symbolTable)
            return reader
        } else {
            val slice: ByteBuffer = source.asReadOnlyBuffer()
            slice.limit(start + length)
            slice.position(start)
            slice.order(ByteOrder.LITTLE_ENDIAN)
            return StructReaderImpl(slice, this, symbolTable)
        }
    }

    fun getIon10Reader(start: Int): StreamReader_1_0 {
        if (ion10Reader == null) {
            ion10Reader = StreamReader_1_0(source.duplicate())
        }
        ion10Reader!!.source.position(start)
        return ion10Reader!!
    }

    override fun close() {
        structs.clear()
        lists.clear()
        annotations.clear()
        delimitedLists.clear()
        ion10Reader = null
    }
}
