package com.amazon.ion.v3.ion_reader

import com.amazon.ion.Decimal
import com.amazon.ion.IntegerSize
import com.amazon.ion.IonException
import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.SymbolTable
import com.amazon.ion.SymbolToken
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.StreamReader_1_0
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.SystemMacro
import com.amazon.ion.v3.impl_1_1.binary.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.visitor.ApplicationReaderDriver
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
class StreamReaderAsIonReader @JvmOverloads constructor(
    private val source: ByteBuffer, private val additionalMacros: List<MacroV2> = emptyList()
): IonReader {
    private lateinit var _ion10Reader: ValueReader
    private val ion10Reader: ValueReader
        get() {
            if (! ::_ion10Reader.isInitialized) _ion10Reader = StreamReader_1_0(source.asReadOnlyBuffer())
            return _ion10Reader
        }

    @JvmField
    internal val ion11Reader: ValueReaderBase = StreamReaderImpl(source.asReadOnlyBuffer())

    private val encodingContextManager = EncodingContextManager(StreamWrappingIonReader())

    private var type: Byte = 0
    private var fieldName: String? = null
    private var fieldNameSid: Int = -1

    private var isNull = false

    private val annotationState = Annotations()
    private var annotationCount = 0

    private var reader: ValueReader = if (source.getInt(0).toUInt() == 0xE00101EAu) {
        ion11Reader
    } else {
        ion10Reader
    }

    private val readerManager = ReaderManager()

    init {
        readerManager.pushReader(reader)
        reset()
    }

    private fun reset() {
        type = 0
        fieldName = null
        fieldNameSid = -1
        isNull = false
        annotationCount = 0
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
        // TODO: See if we can stop calling this method.
        if (reader.isTokenSet()) {
            reader.skip()
        }
        return _next()
    }

    private companion object {
        @JvmField
        val TOKEN_TYPE_TO_ION_TYPE = Array(32) {
            when (it) {
                TokenTypeConst.NULL -> IonType.NULL
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
                TokenTypeConst.STRUCT -> IonType.STRUCT
                else -> null
            }
        }

        @JvmStatic
        fun nullHandler(_this: StreamReaderAsIonReader, reader: ValueReader, token: Int): Int {
            _this.isNull = true
            return reader.nullValue().ordinal + 1
        }

        @JvmStatic
        fun sexpHandler(_this: StreamReaderAsIonReader, reader: ValueReader, token: Int): Int {
            return if (_this.readerManager.containerDepth == 0 && _this.annotationCount != 0 && reader.getIonVersion().toInt() == 0x0101 && _this.annotationState.annotations[0] == "\$ion") {
                val directive = reader.sexpValue()
                _this.encodingContextManager.readDirective(directive)
                _this.encodingContextManager.updateFlattenedTables(_this.ion11Reader, _this.additionalMacros)
                _this.reset()
                TokenTypeConst.UNSET
            } else {
                TokenTypeConst.SEXP
            }
        }

        @JvmStatic
        fun structHandler(_this: StreamReaderAsIonReader, reader: ValueReader, token: Int): Int {
            return if (_this.readerManager.containerDepth == 0 && _this.annotationCount > 0 && reader.getIonVersion().toInt() == 0x0101 && _this.annotationState.annotations[0] == "\$ion_symbol_table") {
                val encodingContextManager = _this.encodingContextManager
                reader.structValue().use {
                    encodingContextManager.readLegacySymbolTable11(it)
                    encodingContextManager.updateFlattenedTables(_this.ion11Reader, _this.additionalMacros)
                }
                _this.reset()
                TokenTypeConst.UNSET
            } else {
                TokenTypeConst.STRUCT
            }
        }

        @JvmStatic
        fun endHandler(_this: StreamReaderAsIonReader, reader: ValueReader, token: Int): Int {
            val readerManager = _this.readerManager
            // Something is wrong with how we're stepping out of things.
            return if (readerManager.isTopAContainer()) {
                TokenTypeConst.END
            } else if (readerManager.readerDepth == 1) {
                TokenTypeConst.END
            } else {
                _this.reader = readerManager.popReader()
                TokenTypeConst.UNSET
            }
        }

        @JvmStatic
        fun annotationHandler(_this: StreamReaderAsIonReader, reader: ValueReader, token: Int): Int {
            val a = reader.annotations()
            _this.annotationCount = _this.annotationState.storeAnnotations(a)
            a.close()
            return TokenTypeConst.UNSET
        }

        @JvmStatic
        fun fieldHandler(_this: StreamReaderAsIonReader, reader: ValueReader, token: Int): Int {
            val r = (reader as StructReader)
            var fnSid: Int
            val fnText: String?
            fnSid = r.fieldNameSid()
            if (fnSid < 0) {
                fnText = r.fieldName()
                if (fnText == null) {
                    fnSid = 0
                }
            } else {
                fnText = r.lookupSid(fnSid)
            }
            _this.fieldNameSid = fnSid
            _this.fieldName = fnText
            return TokenTypeConst.UNSET
        }

        @JvmStatic
        fun handleTopLevelIvm(_this: StreamReaderAsIonReader, _unused: ValueReader, token: Int): Int {
            val currentVersion = _this.reader.getIonVersion().toInt()
            // Calling IVM also resets the symbol table, etc.
            val version = _this.reader.ivm().toInt()
            // If the version is the same, then we know it's a valid version, and we can skip validation
            if (version != currentVersion) {
                val position = _this.reader.position()
                _this.readerManager.popReader()
                _this.reader = when (version) {
                    ApplicationReaderDriver.ION_1_0 -> _this.ion10Reader
                    ApplicationReaderDriver.ION_1_1 -> _this.ion11Reader
                    else -> throw IonException("Unknown Ion Version ${version ushr 8}.${version and 0xFF}")
                }
                _this.readerManager.pushReader(_this.reader)
                _this.reader.seekTo(position)
            }

            if (version == 0x0101) {
                _this.encodingContextManager.ivm()
                _this.encodingContextManager.updateFlattenedTables(_this.ion11Reader, _this.additionalMacros)
//            println("IVM: ${ion11Reader.symbolTable.contentToString()}")
            }
            return TokenTypeConst.UNSET
        }

        @JvmStatic
        fun expressionGroupHandler(_this: StreamReaderAsIonReader, reader: ValueReader, token: Int): Int {
            val expressionGroup = reader.expressionGroup()
            _this.readerManager.pushReader(expressionGroup)
            _this.reader = expressionGroup
            return TokenTypeConst.UNSET
        }

        @JvmStatic
        private fun handleMacro(_this: StreamReaderAsIonReader, reader: ValueReader, token: Int): Int {
            val macroInvocation = reader.macroInvocation()
            val macro = macroInvocation.macro

            if (_this.readerManager.containerDepth == 0) {
                when (macro.systemAddress) {
                    SystemMacro.ADD_SYMBOLS_ADDRESS -> shortCircuitUpdateTables(_this, macroInvocation, EncodingContextManager::addSymbols)
                    SystemMacro.SET_SYMBOLS_ADDRESS -> shortCircuitUpdateTables(_this, macroInvocation, EncodingContextManager::setSymbols)
                    SystemMacro.ADD_MACROS_ADDRESS -> shortCircuitUpdateTables(_this, macroInvocation, EncodingContextManager::addMacros)
                    SystemMacro.SET_MACROS_ADDRESS -> shortCircuitUpdateTables(_this, macroInvocation, EncodingContextManager::setMacros)
                    SystemMacro.USE_ADDRESS -> TODO("Use")
                    else -> handleRegularMacroEvaluation(_this, macroInvocation)
                }
            } else {
                handleRegularMacroEvaluation(_this, macroInvocation)
            }


            return TokenTypeConst.UNSET
        }

        private fun handleRegularMacroEvaluation(_this: StreamReaderAsIonReader, macroInvocation: MacroInvocation) {
            val eexp = macroInvocation.evaluate()
            _this.readerManager.pushReader(eexp)
            _this.reader = eexp
            _this.type = TokenTypeConst.UNSET.toByte()
        }

        private fun shortCircuitUpdateTables(_this: StreamReaderAsIonReader, macroInvocation: MacroInvocation, method: (EncodingContextManager, ValueReader) -> Unit) {
            val argIterator = macroInvocation.iterateArguments()
            val firstArg = argIterator.next()
            method(_this.encodingContextManager, firstArg)
            _this.encodingContextManager.updateFlattenedTables(_this.ion11Reader, _this.additionalMacros)
        }

    }

    private tailrec fun _next(): IonType? {
        val reader = this.reader
        val token = reader.nextToken()

        val proposedType: Int = when (token) {
            TokenTypeConst.NULL -> nullHandler(this, reader, token)
            TokenTypeConst.BOOL,
            TokenTypeConst.INT,
            TokenTypeConst.FLOAT,
            TokenTypeConst.DECIMAL,
            TokenTypeConst.TIMESTAMP,
            TokenTypeConst.STRING,
            TokenTypeConst.SYMBOL,
            TokenTypeConst.CLOB,
            TokenTypeConst.BLOB,
            TokenTypeConst.LIST -> token
            TokenTypeConst.SEXP -> sexpHandler(this, reader, token)
            TokenTypeConst.STRUCT -> structHandler(this, reader, token)
            TokenTypeConst.ANNOTATIONS -> annotationHandler(this, reader, token)
            TokenTypeConst.FIELD_NAME -> fieldHandler(this, reader, token)
            TokenTypeConst.END -> endHandler(this, reader, token)
            TokenTypeConst.IVM -> handleTopLevelIvm(this, reader, token)
            TokenTypeConst.MACRO_INVOCATION -> handleMacro(this, reader, token)
            TokenTypeConst.ABSENT_ARGUMENT,
            TokenTypeConst.EXPRESSION_GROUP -> expressionGroupHandler(this, reader, token)
            TokenTypeConst.UNSET -> {
                // println("Got 'UNSET'. This is weird.")
                TokenTypeConst.UNSET
            }
            else -> {
                TODO("Unreachable: ${TokenTypeConst(token)}")
            }
        }

        if (proposedType > 0) {
            type = proposedType.toByte()
            return TOKEN_TYPE_TO_ION_TYPE[proposedType]
        }
        return _next()
    }

    override fun stepIn() {
        val token = reader.currentToken()
        val child = when (token) {
            TokenTypeConst.NULL -> throw IonException("Cannot step into a null value")
            TokenTypeConst.LIST -> reader.listValue()
            TokenTypeConst.SEXP -> reader.sexpValue()
            TokenTypeConst.STRUCT -> reader.structValue()
            else -> throw IonException("Cannot step in unless positioned on a container; currently token is ${TokenTypeConst(token)}")
        }
        readerManager.pushContainer(child)
        reader = child
        type = TokenTypeConst.UNSET.toByte()
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
            0x0101 -> ArrayBackedLstSnapshot(ion11Reader.symbolTable)
            else -> throw IllegalStateException("Unknown Ion version ${reader.getIonVersion()}")
        }
    }

    override fun getType(): IonType? = TOKEN_TYPE_TO_ION_TYPE[type.toInt()]

    override fun getIntegerSize(): IntegerSize {
        val size = reader.valueSize()
        return when {
            size < 0 -> IntegerSize.BIG_INTEGER
            size <= 4 -> IntegerSize.INT
            size <= 8 -> IntegerSize.LONG
            else -> IntegerSize.BIG_INTEGER
        }
    }

    override fun getTypeAnnotations(): Array<String?> = annotationState.let {
        it.annotationsSize = annotationCount
        it.getTypeAnnotations()
    }
    override fun getTypeAnnotationSymbols(): Array<SymbolToken> = annotationState.let {
        it.annotationsSize = annotationCount
        it.getTypeAnnotationSymbols()
    }
    override fun iterateTypeAnnotations(): Iterator<String?> = annotationState.let {
        it.annotationsSize = annotationCount
        it.iterateTypeAnnotations()
    }

    override fun getFieldId(): Int = fieldNameSid

    override fun getFieldName(): String? = fieldName

    override fun getFieldNameSymbol(): SymbolToken = _Private_Utils.newSymbolToken(fieldName, fieldNameSid)

    override fun isNullValue(): Boolean = isNull

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
            try {
                reader.lookupSid(sid)
            } catch (e: IndexOutOfBoundsException) {
                println(reader)
                println(readerManager)
                throw e
            }
        }
        return _Private_Utils.newSymbolToken(text, sid)
    }

    override fun byteSize(): Int = TODO("lobs not implemented")
    override fun newBytes(): ByteArray = TODO("lobs not implemented")
    override fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int  = TODO("lobs not implemented")
}
