package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.binary.DelimitedSequenceReaderImpl
import com.amazon.ion.v3.impl_1_1.binary.ResourcePool
import com.amazon.ion.v3.impl_1_1.binary.ValueReaderBase
import com.amazon.ion.v3.ion_reader.*
import java.nio.ByteBuffer

class StreamReaderWithEncodingContextManagement(source: ByteBuffer): ValueReader, StreamReader {

    private val encodingContextManager: EncodingContextManager = EncodingContextManager(StreamWrappingIonReader())
    private val pool = ResourcePool(source)
    private var rootReader: ValueReaderBase = pool.getPrefixedSexp(0, source.remaining(), EncodingContextManager.ION_1_1_DEFAULT_SYMBOL_TABLE, EncodingContextManager.ION_1_1_SYSTEM_MACROS)
    private var delegate: ValueReader = rootReader
    private val stack = ArrayList<ValueReader>()

    override tailrec fun nextToken(): Int {
        val delegate = delegate
        when (val token = delegate.nextToken()) {
            TokenTypeConst.END -> {
                if (stack.isEmpty()) return TokenTypeConst.END
                this.delegate = stack.removeLast()
            }
            TokenTypeConst.IVM -> {
                val checkpoint = delegate.position()
                val version = delegate.ivm().toInt()
                // If the version is the same, then we know it's a valid version, and we can skip validation
                if (version != 0x0101) {
                    delegate.seekTo(checkpoint)
                    // TODO: Reset opcode?
                    return TokenTypeConst.IVM
                }
                encodingContextManager.ivm()
                (delegate as DelimitedSequenceReaderImpl)
                encodingContextManager.updateFlattenedTables(delegate, emptyList())
            }
            TokenTypeConst.VARIABLE_REF -> {
                // stack.add(delegate)
                // this.delegate = (delegate as TemplateReader).deprecated_variableValue()
            }
            TokenTypeConst.MACRO_INVOCATION -> {

                TODO()
            }
            TokenTypeConst.EXPRESSION_GROUP -> {
                stack.add(delegate)
                this.delegate = (delegate as ArgumentReader).expressionGroup()
            }
            TokenTypeConst.ABSENT_ARGUMENT -> {
                this.delegate = stack.removeLast()
            }
            TokenTypeConst.ANNOTATIONS -> {
                val backtrackPosition = delegate.position() - 1
                val isSystemValue = handlePossibleSystemValue(delegate)
                if (isSystemValue) {
                    TODO()
                } else {
                    delegate.seekTo(backtrackPosition)
                    return delegate.nextToken()
                }
            }
            else -> return token
        }
        return nextToken()
    }


    private fun handlePossibleSystemValue(reader: ValueReader): Boolean {
        val resetToPosition = reader.position() - 1
        val a = reader.annotations()
        (a as PrivateAnnotationIterator).peek()
        var isSystemValue = false
        val sid = a.getSid()
        // Is this an Ion 1.0 symbol table?

        val text = if (sid < 0) a.getText() else reader.lookupSid(sid)
        val nt = reader.nextToken()
        // Is it a `$ion::(..)`
        if (text == "\$ion" && nt == TokenTypeConst.SEXP) {
            isSystemValue = true
            reader.sexpValue().use(encodingContextManager::readDirective)
            encodingContextManager.updateFlattenedTables(rootReader, emptyList())
        } else if (text == "\$ion_symbol_table" && nt == TokenTypeConst.STRUCT) {
            // Is it a legacy symbol table?
            isSystemValue = true
            reader.structValue().use {
                encodingContextManager.readLegacySymbolTable11(it)
                encodingContextManager.updateFlattenedTables(rootReader, emptyList())
            }
        }
        a.close()
        return isSystemValue
    }






    override fun close() {
        encodingContextManager.ivm()
        delegate.close()
        pool.close()
    }

    override fun ivm(): Short = delegate.ivm()
    override fun getIonVersion(): Short = 0x0101
    override fun annotations(): AnnotationIterator = delegate.annotations()
    override fun booleanValue(): Boolean = delegate.booleanValue()
    override fun nullValue(): IonType = delegate.nullValue()
    override fun longValue(): Long = delegate.longValue()
    override fun doubleValue(): Double = delegate.doubleValue()
    override fun stringValue(): String = delegate.stringValue()
    override fun symbolValue(): String? = delegate.symbolValue()
    override fun symbolValueSid(): Int = delegate.symbolValueSid()
    override fun blobValue(): ByteBuffer = delegate.blobValue()
    override fun clobValue(): ByteBuffer = delegate.clobValue()
    override fun decimalValue(): Decimal = delegate.decimalValue()
    override fun timestampValue(): Timestamp = delegate.timestampValue()
    override fun listValue(): ListReader = delegate.listValue()
    override fun sexpValue(): SexpReader = delegate.sexpValue()
    override fun structValue(): StructReader = delegate.structValue()
    override fun lookupSid(sid: Int): String? = delegate.lookupSid(sid)
    override fun skip() = delegate.skip()
    override fun valueSize(): Int = delegate.valueSize()
    override fun currentToken(): Int = delegate.currentToken()
    override fun isTokenSet(): Boolean = delegate.isTokenSet()
    override fun ionType(): IonType? = delegate.ionType()

    override fun macroInvocation(): MacroInvocation = delegate.macroInvocation()

    override fun position(): Int = delegate.position()
    override fun seekTo(position: Int) = delegate.seekTo(position)

    override fun expressionGroup(): SequenceReader = TODO()
}
