package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.impl.bin.utf8.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * In theory, we could use the resource pool to track all active child containers, and then if we need to shift data
 * over in order to refill the buffer, we can go through and update the positions of all active containers.
 *
 * Or we could try making a deep copy and swapping out the buffers in the active containers for the deep copy.
 *
 * It seems like `Channel`s could be the answer, in some way.
 */
class ResourcePool(
    private val source: ByteBuffer,
    // TODO: Correctness -- Maybe these shouldn't be here, and should be passed as function parameters instead.
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

    @JvmField
    val scratchBuffer: ByteBuffer = source.asReadOnlyBuffer()
    @JvmField
    val structs = ArrayList<StructReaderImpl>(32)
    @JvmField
    val delimitedStructs = ArrayList<DelimitedStructReaderImpl>(32)
    @JvmField
    val lists = ArrayList<SeqReaderImpl>(32)
    @JvmField
    val delimitedLists = ArrayList<DelimitedSequenceReaderImpl>(32)
    @JvmField
    val eexpArgumentReaders = ArrayList<EExpArgumentReaderImpl>(8)
    @JvmField
    val annotations = ArrayList<AnnotationIterator>(8)

    private fun newSlice(start: Int): ByteBuffer {
        val slice: ByteBuffer = source.asReadOnlyBuffer()
        slice.position(start)
        slice.order(ByteOrder.LITTLE_ENDIAN)
        return slice
    }

    private fun newSlice(start: Int, length: Int): ByteBuffer {
        val slice: ByteBuffer = source.asReadOnlyBuffer()
        slice.limit(start + length)
        slice.position(start)
        slice.order(ByteOrder.LITTLE_ENDIAN)
        return slice
    }

    fun getList(start: Int, length: Int, symbolTable: Array<String?>, macroTable: Array<Macro>): SeqReaderImpl {
        val reader = lists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length)
            reader.initTables(symbolTable, macroTable)
            return reader
        } else {
            return SeqReaderImpl(newSlice(start, length), this, symbolTable, macroTable)
        }
    }

    fun getDelimitedSequence(start: Int, parent: ValueReaderBase, symbolTable: Array<String?>, macroTable: Array<Macro>): DelimitedSequenceReaderImpl {
        val n = delimitedLists.size
        if (n > 0) {
            val reader = delimitedLists.removeAt(n - 1)
            reader.init(start, source.limit() - start)
            reader.initTables(symbolTable, macroTable)
            reader.parent = parent
            return reader
        } else {
            return DelimitedSequenceReaderImpl(newSlice(start), this, parent, symbolTable, macroTable)
        }
    }

    fun getEExpArgs(start: Int, maxLength: Int, signature: List<Macro.Parameter>, symbolTable: Array<String?>, macroTable: Array<Macro>): EExpArgumentReaderImpl {
        val reader = eexpArgumentReaders.removeLastOrNull()
            ?.apply {
                init(start, maxLength)
                initTables(symbolTable, macroTable)
            }
            ?: EExpArgumentReaderImpl(newSlice(start, maxLength), this, symbolTable, macroTable)
        reader.initArgs(signature)
        return reader
    }

    fun getAnnotations(opcode: Int, start: Int, length: Int, symbolTable: Array<String?>): AnnotationIterator {
        val reader = annotations.removeLastOrNull() as AnnotationIteratorImpl?
        if (reader != null) {
            reader.init(opcode, start, length, symbolTable)
            return reader
        } else {
            return AnnotationIteratorImpl(opcode, newSlice(start, length), this, symbolTable)
        }
    }

    fun getPrefixedSexp(start: Int, length: Int, symbolTable: Array<String?>, macroTable: Array<Macro>): SeqReaderImpl {
        val reader = lists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length)
            reader.initTables(symbolTable, macroTable)
            return reader
        } else {
            return SeqReaderImpl(newSlice(start, length), this, symbolTable, macroTable)
        }
    }

    fun getDelimitedSexp(start: Int, parent: ValueReaderBase, symbolTable: Array<String?>, macroTable: Array<Macro>): DelimitedSequenceReaderImpl {
        val reader = delimitedLists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, source.limit() - start)
            reader.initTables(symbolTable, macroTable)
            reader.parent = parent
            return reader
        } else {
            return DelimitedSequenceReaderImpl(newSlice(start), this, parent, symbolTable, macroTable)
        }
    }

    fun getStruct(start: Int, length: Int, symbolTable: Array<String?>, macroTable: Array<Macro>): StructReaderImpl {
        val reader = structs.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length)
            reader.initTables(symbolTable, macroTable)
            return reader
        } else {
            return StructReaderImpl(newSlice(start, length), this, symbolTable, macroTable)
        }
    }

    fun getDelimitedStruct(start: Int, parent: ValueReaderBase, symbolTable: Array<String?>, macroTable: Array<Macro>): DelimitedStructReaderImpl {
        val reader = delimitedStructs.removeLastOrNull()
        if (reader != null) {
            reader.init(start, source.limit() - start)
            reader.parent = parent
            reader.initTables(symbolTable, macroTable)
            return reader
        } else {
            return DelimitedStructReaderImpl(newSlice(start), this, parent, symbolTable, macroTable)
        }
    }

    override fun close() {
        annotations.clear()
        lists.clear()
        delimitedLists.clear()
        structs.clear()
        delimitedStructs.clear()
        eexpArgumentReaders.clear()
    }
}
