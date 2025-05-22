package com.amazon.ion.v3

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.v3.impl_1_0.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.ValueReaderBase
import com.amazon.ion.v3.visitor.*
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.*
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_0
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_MACROS
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Date

/**
 * A wrapper for [StreamReader] that implements [IonReader].
 *
 * TODO: This class has an object layout of exactly 64 bytes, which is a typical size for a cache line.
 *       If we want to add any more fields, we should consider creating an `AnnotationsHelper` class
 *       that can encapsulate all of the state needed for annotations. (Currently it is 16 bytes, or
 *       25% of the total object size.)
 */
class StreamReaderAsIonReader(private val source: ByteBuffer): IonReader {

    private lateinit var _ion10Reader: ValueReader
    private val ion10Reader: ValueReader
        get() {
            if (! ::_ion10Reader.isInitialized) _ion10Reader = StreamReader_1_0(source)
            return _ion10Reader
        }

    private lateinit var _ion11Reader: ValueReader
    private val ion11Reader: ValueReader
        get() {
            if (!::_ion11Reader.isInitialized) _ion11Reader = StreamReaderImpl(source)
            return _ion11Reader
        }

    private inline fun <reified T> Array<T?>.grow(): Array<T?> {
        val newSize = this.size * 2
        val newArray = arrayOfNulls<T>(newSize)
        this.copyInto(newArray)
        return newArray
    }

    private fun IntArray.grow(): IntArray {
        val newSize = this.size * 2
        val newArray = IntArray(newSize)
        this.copyInto(newArray)
        return newArray
    }

    private fun pushContainer(reader: ValueReader) {
        if (stackSize >= stack.size) {
            stack = stack.grow()
        }
        stack[stackSize++] = reader
    }

    private var stack = arrayOfNulls<ValueReader>(32)
    private var stackSize: Int = 0

    private var type: IonType? = null
    private var fieldName: String? = null
    private var fieldNameSid: Int = -1

    private var annotations: Array<String?> = arrayOfNulls(8)
    private var annotationsSids = IntArray(8) { -1 }
    private var annotationsSize = 0


    private var reader: ValueReader = if (source.getInt(0).toUInt() == 0xE00101EAu) {
        ion11Reader
    } else {
        ion10Reader
    }

    init {
        stackSize = 0
        reset()
    }

    private fun storeAnnotations() {
        val annotationsIterator = reader.annotations()
        var annotationCount = 0
        while (annotationsIterator.hasNext()) {
            if (annotationCount >= annotations.size) {
                annotations = annotations.grow()
                annotationsSids = annotationsSids.grow()
            }
            annotationsIterator.next()
            val sid = annotationsIterator.getSid()
            annotationsSids[annotationCount] = sid
            if (sid < 0) {
                annotations[annotationCount] = annotationsIterator.getText()
            } else {
                annotations[annotationCount] = reader.lookupSid(sid)
            }
            annotationCount++
        }
        annotationsSize = annotationCount
    }

    private fun reset() {
        type = null
        fieldName = null
        fieldNameSid = -1
        annotationsSize = 0
    }

    override fun close() {
        reader.close()
        stack.iterator().forEach { it?.close() }
        stackSize = 0
    }

    override fun <T : Any?> asFacet(facetType: Class<T>?): T {
        TODO("Not yet implemented")
    }

    // TODO: Make this call `next()` and then cache the result.
    //       Add a check in `next()` for the cached result
    override fun hasNext(): Boolean = TODO("Deprecated and unsupported")

    override fun next(): IonType? {
        reset()
        return _next()
    }


    private tailrec fun _next(): IonType? {
        val token = reader.nextToken()

        type = when (token) {
            TokenTypeConst.NULL -> reader.ionType()
            TokenTypeConst.BOOL -> IonType.BOOL
            TokenTypeConst.INT -> IonType.INT
            TokenTypeConst.FLOAT -> IonType.FLOAT
            TokenTypeConst.DECIMAL -> IonType.DECIMAL
            TokenTypeConst.TIMESTAMP -> IonType.TIMESTAMP
            TokenTypeConst.STRING -> IonType.STRING
            TokenTypeConst.SYMBOL -> IonType.SYMBOL
            TokenTypeConst.CLOB -> IonType.CLOB
            TokenTypeConst.BLOB -> IonType.BLOB
            TokenTypeConst.LIST -> IonType.LIST
            TokenTypeConst.SEXP -> IonType.SEXP
            TokenTypeConst.STRUCT -> {
                if (annotationsSize > 0 && reader.getIonVersion().toInt() == 0x0101 && annotations[0] == "\$ion_symbol_table") {
                    val newSymbols = reader.structValue().use { readLegacySymbolTable11(it) }
                    val r = (reader as ValueReaderBase)
                    r.initTables(newSymbols, ION_1_1_SYSTEM_MACROS)
                    r.pool.symbolTable = newSymbols
                    r.pool.macroTable = ION_1_1_SYSTEM_MACROS
                    reset()
                    null
                } else {
                    IonType.STRUCT
                }
            }
            TokenTypeConst.ANNOTATIONS -> {
                storeAnnotations()
                null
            }
            TokenTypeConst.FIELD_NAME -> {
                fieldNameSid = (reader as StructReader).fieldNameSid()
                if (fieldNameSid < 0) {
                    fieldName = (reader as StructReader).fieldName()
                } else {
                    fieldName = reader.lookupSid(fieldNameSid)
                }
                null
            }
            TokenTypeConst.END -> return null
            TokenTypeConst.IVM -> {
                handleTopLevelIvm()
                null
            }
            else -> TODO("Unreachable: ${TokenTypeConst(token)}")
        }
        if (type != null) {
            return type
        }
        return _next()
    }

    fun handleTopLevelIvm() {
        val currentVersion = reader.getIonVersion().toInt()
        // Calling IVM also resets the symbol table, etc.
        val version = reader.ivm().toInt()
        // If the version is the same, then we know it's a valid version, and we can skip validation
        if (version != currentVersion) {
            val position = reader.position()
            reader = when (version) {
                ION_1_0 -> ion10Reader
                ION_1_1 -> ion11Reader
                else -> throw IonException("Unknown Ion Version ${version ushr 8}.${version and 0xFF}")
            }
            reader.seekTo(position)
        }
    }

    private fun readLegacySymbolTable11(structReader: StructReader): Array<String?> {
        val newSymbols = ArrayList<String?>()
        var isLstAppend = false
        while (true) {
            val token = structReader.nextToken()
            if (token == TokenTypeConst.END) {
                break
            } else if (token != TokenTypeConst.FIELD_NAME) {
                val sr = (structReader as ValueReaderBase)
                println(sr.source)
                throw IllegalStateException("Unexpected token type: ${TokenTypeConst(token)}")
            }
            val fieldNameSid = structReader.fieldNameSid()
            val fieldName = if (fieldNameSid < 0) {
                structReader.fieldName()
            } else {
                structReader.lookupSid(fieldNameSid)
            }
            when (fieldName) {
                SystemSymbols.IMPORTS -> {
                    val importsToken = structReader.nextToken()
                    when (importsToken) {
                        TokenTypeConst.SYMBOL -> {
                            val sid = structReader.symbolValueSid()
                            val text = if (sid < 0) {
                                structReader.symbolValue()
                            } else {
                                structReader.lookupSid(sid)
                            }
                            if (text == SystemSymbols.ION_SYMBOL_TABLE) {
                                isLstAppend = true
                            }
                        }
                        TokenTypeConst.LIST -> {
                            TODO("Imports not supported yet.")
                        }
                        else -> {
                            // TODO: Should this be an error?
                        }
                    }
                }
                SystemSymbols.SYMBOLS -> {
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
                else -> {}
            }
        }
        val startOfNewSymbolTable = if (isLstAppend) (reader as ValueReaderBase).symbolTable else arrayOf<String?>(null)
        val newSymbolTable = Array<String?>(newSymbols.size + startOfNewSymbolTable.size) { null }
        System.arraycopy(startOfNewSymbolTable, 0, newSymbolTable, 0, startOfNewSymbolTable.size)
        System.arraycopy(newSymbols.toArray(), 0, newSymbolTable, startOfNewSymbolTable.size, newSymbols.size)
        return newSymbolTable
    }


    override fun stepIn() {
        val child = when (reader.currentToken()) {
            TokenTypeConst.NULL -> throw IonException("Cannot step into a null value")
            TokenTypeConst.LIST -> reader.listValue()
            TokenTypeConst.SEXP -> reader.sexpValue()
            TokenTypeConst.STRUCT -> reader.structValue()
            else -> throw IonException("Cannot step in unless positioned on a container")
        }
        pushContainer(reader)
        reader = child
        type = null
        fieldName = null
        fieldNameSid = -1
    }

    override fun stepOut() {
        if (stackSize == 0) {
            throw IonException("Nothing to step out of.")
        }
        val parent = stack[--stackSize]!!
        reader.close()
        reader = parent
    }

    override fun getDepth(): Int = stack.size

    override fun getSymbolTable(): SymbolTable {
        return when (reader.getIonVersion().toInt()) {
            0x0100 -> LstSnapshot((reader as com.amazon.ion.v3.impl_1_0.ValueReaderBase).symbolTable)
            0x0101 -> LstSnapshot((reader as ValueReaderBase).symbolTable)
            else -> throw IllegalStateException("Unknown Ion version ${reader.getIonVersion()}")
        }
    }

    private class LstSnapshot(private val symbolText: Array<String?>): _Private_LocalSymbolTable {
        override fun getName(): String {
            TODO("Not yet implemented")
        }

        override fun getVersion(): Int {
            TODO("Not yet implemented")
        }

        override fun isLocalTable(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isSharedTable(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isSubstitute(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isSystemTable(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isReadOnly(): Boolean {
            TODO("Not yet implemented")
        }

        override fun makeReadOnly() {
            TODO("Not yet implemented")
        }

        override fun getSystemSymbolTable(): SymbolTable {
            TODO("Not yet implemented")
        }

        override fun getIonVersionId(): String {
            TODO("Not yet implemented")
        }

        override fun getImportedTables(): Array<SymbolTable> {
            TODO("Not yet implemented")
        }

        override fun getImportedMaxId(): Int {
            TODO("Not yet implemented")
        }

        override fun getMaxId(): Int {
            TODO("Not yet implemented")
        }

        override fun intern(text: String?): SymbolToken {
            TODO("Not yet implemented")
        }

        override fun find(text: String?): SymbolToken {
            TODO("Not yet implemented")
        }

        override fun findSymbol(name: String?): Int {
            TODO("Not yet implemented")
        }

        override fun findKnownSymbol(id: Int): String {
            TODO("Not yet implemented")
        }

        override fun iterateDeclaredSymbolNames(): MutableIterator<String> {
            TODO("Not yet implemented")
        }

        override fun writeTo(writer: IonWriter?) {
            TODO("Not yet implemented")
        }

        override fun makeCopy(): _Private_LocalSymbolTable {
            TODO("Not yet implemented")
        }

        override fun getImportedTablesNoCopy(): Array<SymbolTable> {
            TODO("Not yet implemented")
        }

    }

    override fun getType(): IonType? = type

    override fun getIntegerSize(): IntegerSize {
        val size = reader.valueSize()
        return when {
            size < 0 -> IntegerSize.BIG_INTEGER
            size <= 4 -> IntegerSize.INT
            size <= 8 -> IntegerSize.LONG
            else -> IntegerSize.BIG_INTEGER
        }
    }

    override fun getTypeAnnotations(): Array<String?> = annotations.copyOf(annotationsSize)

    override fun getTypeAnnotationSymbols(): Array<SymbolToken> {
        return Array(annotationsSize) { i ->
            _Private_Utils.newSymbolToken(annotations[i], annotationsSids[i]) as SymbolToken
        }
    }

    override fun iterateTypeAnnotations(): Iterator<String?> = annotationIterator.also { it.reset() }

    private val annotationIterator: TypeAnnotationsIterator = TypeAnnotationsIterator(this)

    class TypeAnnotationsIterator(val reader: StreamReaderAsIonReader) : Iterator<String?> {
        fun reset() {
            i = 0
        }

        var i = 0
        override fun hasNext(): Boolean = i < reader.annotationsSize

        override fun next(): String? {
            if (!hasNext()) throw NoSuchElementException()
            return reader.annotations[i++]
        }
    }


    override fun getFieldId(): Int = fieldNameSid

    override fun getFieldName(): String? = fieldName

    override fun getFieldNameSymbol(): SymbolToken = _Private_Utils.newSymbolToken(fieldName, fieldNameSid)

    override fun isNullValue(): Boolean = reader.currentToken() == TokenTypeConst.NULL

    override fun isInStruct(): Boolean = reader is StructReader

    override fun booleanValue(): Boolean = reader.booleanValue()
    override fun intValue(): Int = reader.longValue().toInt()
    override fun longValue(): Long = reader.longValue()
    // TODO: Support real big integers
    override fun bigIntegerValue(): BigInteger = reader.longValue().toBigInteger()
    override fun doubleValue(): Double = reader.doubleValue()
    override fun bigDecimalValue(): BigDecimal = reader.decimalValue()
    override fun decimalValue(): Decimal = reader.decimalValue()
    override fun dateValue(): Date = timestampValue().dateValue()
    override fun timestampValue(): Timestamp = reader.timestampValue()
    override fun stringValue(): String? {
        return when (reader.currentToken()) {
            TokenTypeConst.NULL -> null
            TokenTypeConst.STRING -> reader.stringValue()
            TokenTypeConst.SYMBOL -> reader.symbolValue()
            else -> throw IonException("Reader is not positioned on a string")
        }
    }
    override fun symbolValue(): SymbolToken {
        val sid = reader.symbolValueSid()
        val text = if (sid < 0) {
            reader.symbolValue()
        } else {
            reader.lookupSid(sid)
        }
        return _Private_Utils.newSymbolToken(text, sid)
    }

    override fun byteSize(): Int = TODO("lobs not implemented")
    override fun newBytes(): ByteArray = TODO("lobs not implemented")
    override fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int  = TODO("lobs not implemented")
}
