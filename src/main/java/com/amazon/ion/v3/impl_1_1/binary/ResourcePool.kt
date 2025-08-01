package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.impl.bin.utf8.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*
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
    source: ByteBuffer,
): Closeable {

    private val source = source.apply { order(ByteOrder.LITTLE_ENDIAN) }

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
    val scratchBuffer: ByteBuffer = source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)

    @JvmField
    val structs = ArrayList<StructReaderImpl>(32)
    @JvmField
    val delimitedStructs = ArrayList<DelimitedStructReaderImpl>(32)
    @JvmField
    val lists = ArrayList<SeqReaderImpl>(32)
    @JvmField
    val delimitedLists = ArrayList<DelimitedSequenceReaderImpl>(32)
//    @JvmField
//    val eexpArgumentReaders = ArrayList<EExpArgumentReaderImpl>(8)
    @JvmField
    val annotations = ArrayList<AnnotationIterator>(8)


    @JvmField
    val templateReaders = ArrayList<TemplateReaderImpl>(8)
    @JvmField
    val templateStructReaders = ArrayList<TemplateStructReader>(8)
    @JvmField
    internal val annotationIterators = ArrayList<AnnotationIterator>(8)

    internal fun newSlice(start: Int): ByteBuffer {
        val slice: ByteBuffer = source.asReadOnlyBuffer()
        slice.position(start)
        slice.order(ByteOrder.LITTLE_ENDIAN)
        return slice
    }

//    private fun newSlice(start: Int, length: Int): ByteBuffer {
//        val slice: ByteBuffer = source.asReadOnlyBuffer()
//        slice.limit(start + length)
//        slice.position(start)
//        slice.order(ByteOrder.LITTLE_ENDIAN)
//        return slice
//    }

    fun getList(start: Int, length: Int, symbolTable: Array<String?>, macroTable: Array<MacroV2>): SeqReaderImpl {
        val reader = lists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length)
            reader.initTables(symbolTable, macroTable)
            return reader
        } else {
            return SeqReaderImpl(source, this, symbolTable, macroTable).also {
                it.init(start, length)
            }
        }
    }

    fun getDelimitedSequence(start: Int, parent: ValueReaderBase, symbolTable: Array<String?>, macroTable: Array<MacroV2>): DelimitedSequenceReaderImpl {
        val n = delimitedLists.size
        if (n > 0) {
            val reader = delimitedLists.removeAt(n - 1)
            reader.init(start, source.capacity() - start)
            reader.initTables(symbolTable, macroTable)
            reader.parent = parent
            return reader
        } else {
            return DelimitedSequenceReaderImpl(newSlice(start), this, parent, symbolTable, macroTable).also {
                it.init(start, source.capacity() - start)
            }
        }
    }

    fun getAnnotations(opcode: Int, start: Int, length: Int, symbolTable: Array<String?>): AnnotationIterator {
        val reader = annotations.removeLastOrNull() as AnnotationIteratorImpl?
        if (reader != null) {
            reader.init(opcode, start, length, symbolTable)
            return reader
        } else {
            return AnnotationIteratorImpl(opcode, source, this, symbolTable).also {
                it.init(opcode, start, length, symbolTable)
            }
        }
    }

    fun getPrefixedSexp(start: Int, length: Int, symbolTable: Array<String?>, macroTable: Array<MacroV2>): SeqReaderImpl {
        val reader = lists.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length)
            reader.initTables(symbolTable, macroTable)
            return reader
        } else {
            return SeqReaderImpl(source, this, symbolTable, macroTable).also {
                it.init(start, length)
            }
        }
    }

    fun getStruct(start: Int, length: Int, symbolTable: Array<String?>, macroTable: Array<MacroV2>): StructReaderImpl {
        val reader = structs.removeLastOrNull()
        if (reader != null) {
            reader.init(start, length)
            reader.initTables(symbolTable, macroTable)
            return reader
        } else {
            return StructReaderImpl(source, this, symbolTable, macroTable).also {
                it.init(start, length)
            }
        }
    }

    fun getDelimitedStruct(start: Int, parent: ValueReaderBase, symbolTable: Array<String?>, macroTable: Array<MacroV2>): DelimitedStructReaderImpl {
        val reader = delimitedStructs.removeLastOrNull()
        if (reader != null) {
            reader.init(start, source.capacity() - start)
            reader.parent = parent
            reader.initTables(symbolTable, macroTable)
            return reader
        } else {
            return DelimitedStructReaderImpl(source, this, parent, symbolTable, macroTable).also {
                it.init(start, source.capacity() - start)
            }
        }
    }


    fun getSequence(args: ArgumentBytecode, bytecode: IntArray, start: Int, constantPool: Array<Any?>, symbolTable: Array<String?>, macroTable: Array<MacroV2>): TemplateReaderImpl {
        val reader = templateReaders.removeLastOrNull() ?: TemplateReaderImpl(this)
        // TODO: move source argument into the constructor.
        reader.init(source, bytecode, constantPool, args)
        reader.initTables(symbolTable, macroTable)
        reader.isStruct = false
        reader.i = start
        return reader
    }

    fun getStruct(args: ArgumentBytecode, bytecode: IntArray, start: Int, constantPool: Array<Any?>, symbolTable: Array<String?>, macroTable: Array<MacroV2>): TemplateStructReader {
        val reader = templateStructReaders.removeLastOrNull()
            ?: TemplateStructReader(this)
        // TODO: move source argument into the constructor.
        reader.init(source, bytecode, constantPool, args)
        reader.initTables(symbolTable, macroTable)
        reader.isStruct = true
        reader.i = start
        return reader
    }

    fun getAnnotations(annotationSymbols: Array<String?>): AnnotationIterator {
        val reader = annotationIterators.removeLastOrNull() as TemplateAnnotationIteratorImpl?
        if (reader != null) {
            reader.init(annotationSymbols)
            return reader
        } else {
            return TemplateAnnotationIteratorImpl(annotationSymbols, this)
        }
    }

    fun returnAnnotations(annotations: AnnotationIterator) {
        annotationIterators.add(annotations)
    }

    fun returnSequence(sequence: TemplateReaderImpl) {
        templateReaders.add(sequence)
    }
    fun returnStruct(struct: TemplateStructReader) {
        templateStructReaders.add(struct)
    }



    override fun close() {
        annotations.clear()
        lists.clear()
        delimitedLists.clear()
        structs.clear()
        delimitedStructs.clear()
//        eexpArgumentReaders.clear()
    }
}
