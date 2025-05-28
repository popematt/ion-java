package com.amazon.ion.v3.ion_reader

import com.amazon.ion.Decimal
import com.amazon.ion.IntegerSize
import com.amazon.ion.IonException
import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.SymbolTable
import com.amazon.ion.SymbolToken
import com.amazon.ion.SystemSymbols
import com.amazon.ion.Timestamp
import com.amazon.ion.impl._Private_Utils
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.StreamReader_1_0
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.ModuleReader
import com.amazon.ion.v3.visitor.ApplicationReaderDriver
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_0_SYMBOL_TABLE
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_MACROS
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_SYMBOLS
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Date

/**
 * A wrapper for [StreamReader] that implements [IonReader].
 *
 * TODO: We can move infrequently use fields, such as `_ion10reader` and `_ion11Reader` into a helper class in order to
 *       keep this class below 64 bytes (the size of a typical cache line).
 */
class StreamReaderAsIonReader(
    private val source: ByteBuffer, private val additionalMacros: List<Macro> = emptyList()
): IonReader {
    private lateinit var _ion10Reader: ValueReader
    private val ion10Reader: ValueReader
        get() {
            if (! ::_ion10Reader.isInitialized) _ion10Reader = StreamReader_1_0(source)
            return _ion10Reader
        }

    private lateinit var _ion11Reader: ValueReaderBase
    internal val ion11Reader: ValueReaderBase
        get() {
            if (!::_ion11Reader.isInitialized) _ion11Reader = StreamReaderImpl(source)
            return _ion11Reader
        }

    private lateinit var _templateReaderPool: TemplateResourcePool
    private val templateReaderPool: TemplateResourcePool
        get() {
            if (! ::_templateReaderPool.isInitialized) _templateReaderPool =
                TemplateResourcePool(ion11Reader.symbolTable, ion11Reader.macroTable)
            return _templateReaderPool
        }

    private val moduleReader = ModuleReader()

    private var type: IonType? = null
    private var fieldName: String? = null
    private var fieldNameSid: Int = -1


    private val annotationState = Annotations()

    private var symbolTable: Array<String?>

    private var reader: ValueReader = if (source.getInt(0).toUInt() == 0xE00101EAu) {
        symbolTable = ion11Reader.symbolTable
        ion11Reader
    } else {
        symbolTable = ION_1_0_SYMBOL_TABLE
        ion10Reader
    }


    private val readerManager = ReaderManager()

    init {
        readerManager.pushReader(reader)
        reset()
    }

    private fun reset() {
        type = null
        fieldName = null
        fieldNameSid = -1
        annotationState.annotationsSize = 0
    }

    override fun close() {
        readerManager.close()
    }

    override fun <T : Any?> asFacet(facetType: Class<T>?): T {
        TODO("Not yet implemented")
    }

    // TODO: Make this call `next()` and then cache the result.
    //       Add a check in `next()` for the cached result
    override fun hasNext(): Boolean = TODO("Deprecated and unsupported")

    override fun next(): IonType? {
        reset()
        if (reader.currentToken() != TokenTypeConst.UNSET) {
            reader.skip()
        }
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
                if (annotationState.annotationsSize > 0 && reader.getIonVersion().toInt() == 0x0101 && annotationState.annotations[0] == "\$ion_symbol_table") {
                    val newSymbols = reader.structValue().use { readLegacySymbolTable11(it) }
                    val r = (reader as ValueReaderBase)
                    r.initTables(newSymbols, ApplicationReaderDriver.ION_1_1_SYSTEM_MACROS)
                    this.symbolTable = newSymbols
                    r.pool.symbolTable = newSymbols
                    r.pool.macroTable = ApplicationReaderDriver.ION_1_1_SYSTEM_MACROS
                    reset()
                    null
                } else {
                    IonType.STRUCT
                }
            }
            TokenTypeConst.ANNOTATIONS -> {
                reader.annotations().use { annotationState.storeAnnotations(it, reader) }
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
            TokenTypeConst.END -> {
                if (readerManager.isTopAContainer()) {
                    return null
                } else if (readerManager.readerDepth == 1) {
                    return null
                } else {
                    reader = readerManager.popReader()!!
                    null
                }
            }
            TokenTypeConst.IVM -> {
                handleTopLevelIvm()
                null
            }
            TokenTypeConst.EEXP -> {
                val macroId = reader.eexpValue()
                val macro = ion11Reader.macroTable[macroId]
                val args = reader.eexpArgs(macro.signature)
                val eexp = templateReaderPool.startEvaluation(macro, args)
                readerManager.pushReader(eexp)
                reader = eexp
                type = null
                null
            }
            TokenTypeConst.VARIABLE_REF -> {
                val variableReader = (reader as TemplateReader).variableValue()
                readerManager.pushReader(variableReader)
                reader = variableReader
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
            readerManager.popReader()
            reader = when (version) {
                ApplicationReaderDriver.ION_1_0 -> ion10Reader
                ApplicationReaderDriver.ION_1_1 -> ion11Reader
                else -> throw IonException("Unknown Ion Version ${version ushr 8}.${version and 0xFF}")
            }
            readerManager.pushReader(reader)
            reader.seekTo(position)
        }

        if (version == 0x0101) {
            val macroTable = if (additionalMacros.isNotEmpty()) {
                Array(ION_1_1_SYSTEM_MACROS.size + additionalMacros.size) { i ->
                    if (i < ION_1_1_SYSTEM_MACROS.size) {
                        ION_1_1_SYSTEM_MACROS[i]
                    } else {
                        additionalMacros[i - ION_1_1_SYSTEM_MACROS.size]
                    }
                }
            } else {
                ION_1_1_SYSTEM_MACROS
            }
            ion11Reader.initTables(ION_1_1_SYSTEM_SYMBOLS, macroTable)
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
        readerManager.pushContainer(child)
        reader = child
        type = null
        fieldName = null
        fieldNameSid = -1
    }

    override fun stepOut() {
        reader = readerManager.popContainer()
    }

    override fun getDepth(): Int = readerManager.containerDepth

    override fun getSymbolTable(): SymbolTable {
        return when (reader.getIonVersion().toInt()) {
            0x0100 -> ArrayBackedLstSnapshot((reader as com.amazon.ion.v3.impl_1_0.ValueReaderBase).symbolTable)
            0x0101 -> ArrayBackedLstSnapshot(symbolTable)
            else -> throw IllegalStateException("Unknown Ion version ${reader.getIonVersion()}")
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

    override fun getTypeAnnotations(): Array<String?> = annotationState.getTypeAnnotations()
    override fun getTypeAnnotationSymbols(): Array<SymbolToken> = annotationState.getTypeAnnotationSymbols()
    override fun iterateTypeAnnotations(): Iterator<String?> = annotationState.iterateTypeAnnotations()

    override fun getFieldId(): Int = fieldNameSid

    override fun getFieldName(): String? = fieldName

    override fun getFieldNameSymbol(): SymbolToken = _Private_Utils.newSymbolToken(fieldName, fieldNameSid)

    override fun isNullValue(): Boolean = reader.currentToken() == TokenTypeConst.NULL

    override fun isInStruct(): Boolean {
        return readerManager.isInStruct
    }

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
