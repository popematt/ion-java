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

    private lateinit var _templateReaderPool: TemplateResourcePool
    private val templateReaderPool: TemplateResourcePool
        get() {
            if (! ::_templateReaderPool.isInitialized) _templateReaderPool = TemplateResourcePool.getInstance()
            return _templateReaderPool
        }

    private val encodingContextManager = EncodingContextManager(StreamWrappingIonReader())
//
//    private val moduleReader = ModuleReader2(ReaderAdapterIonReader(ionReaderShim))
//    private val availableModules = mutableMapOf<String, ModuleReader2.Module>()
//    private val activeModules = mutableListOf<ModuleReader2.Module>()

    private var type: IonType? = null
    private var fieldName: String? = null
    private var fieldNameSid: Int = -1


    private val annotationState = Annotations()

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
        if (reader.isTokenSet()) {
            reader.skip()
        }
        return _next()
    }


    private tailrec fun _next(): IonType? {
        val reader = this.reader
        val token = reader.nextToken()

        // TODO: Assign a local variable here.
        val proposedType = when (token) {
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
                    reader.structValue().use {
                        encodingContextManager.readLegacySymbolTable11(it)
                        encodingContextManager.updateFlattenedTables(ion11Reader, additionalMacros)
                    }
                    reset()
                    null
                } else {
                    IonType.STRUCT
                }
            }
            TokenTypeConst.ANNOTATIONS -> {
                val a = reader.annotations()
                annotationState.storeAnnotations(a)
                a.close()
                null
            }
            TokenTypeConst.FIELD_NAME -> {
                val r = (reader as StructReader)
                fieldNameSid = r.fieldNameSid()
                if (fieldNameSid < 0) {
                    fieldName = r.fieldName()
                    if (fieldName == null) {
                        fieldNameSid = 0
                    }
                } else {
                    fieldName = r.lookupSid(fieldNameSid)
                }
                null
            }
            TokenTypeConst.END -> {
                // Something is wrong with how we're stepping out of things.
                if (readerManager.isTopAContainer()) {
                    return null
                } else if (readerManager.readerDepth == 1) {
                    return null
                } else {
                    this.reader = readerManager.popReader()
                    null
                }
            }
            TokenTypeConst.IVM -> {
                handleTopLevelIvm()
                null
            }
            TokenTypeConst.MACRO_INVOCATION -> {
                handleMacro()
                null
            }
            TokenTypeConst.ABSENT_ARGUMENT,
            TokenTypeConst.EXPRESSION_GROUP -> {
                val expressionGroup = reader.expressionGroup()
                readerManager.pushReader(expressionGroup)
                this.reader = expressionGroup
                null
            }
            else -> {
                TODO("Unreachable: ${TokenTypeConst(token)}")
            }
        }
        if (proposedType != null) {
            type = proposedType
            return proposedType
        }
        return _next()
    }

    // FIXME: 38% of All
    private fun handleMacro() {
        val macroInvocation = reader.macroInvocation()
        val macro = macroInvocation.macro
//        println("Evaluating macro $macro")

        val isShortCircuitEvaluation = readerManager.containerDepth == 0 && when (macro.systemAddress) {
            SystemMacro.ADD_SYMBOLS_ADDRESS -> {
                val symbolsList = templateReaderPool.getSequence(macroInvocation.arguments, macroInvocation.arguments.getArgument(0), 0, macroInvocation.arguments.constantPool())
                // FIXME: 6% of All
                encodingContextManager.addOrSetSymbols(symbolsList, append = true)
                // FIXME: 5% of All
                encodingContextManager.updateFlattenedTables(ion11Reader, additionalMacros)
//                        println("Add symbols: ${ion11Reader.symbolTable.contentToString()}")
                true
            }
            SystemMacro.SET_SYMBOLS_ADDRESS -> {
                val symbolsList = templateReaderPool.getSequence(macroInvocation.arguments, macroInvocation.arguments.getArgument(0), 0, macroInvocation.arguments.constantPool())
                encodingContextManager.addOrSetSymbols(symbolsList, append = false)
                encodingContextManager.updateFlattenedTables(ion11Reader, additionalMacros)
                true
            }
            SystemMacro.ADD_MACROS_ADDRESS -> {
                val macroList = templateReaderPool.getSequence(macroInvocation.arguments, macroInvocation.arguments.getArgument(0), 0, macroInvocation.arguments.constantPool())
                encodingContextManager.addOrSetMacros(macroList, append = true)
                encodingContextManager.updateFlattenedTables(ion11Reader, additionalMacros)
                true
            }
            SystemMacro.SET_MACROS_ADDRESS -> {
                val macroList = templateReaderPool.getSequence(macroInvocation.arguments, macroInvocation.arguments.getArgument(0), 0, macroInvocation.arguments.constantPool())
                encodingContextManager.addOrSetMacros(macroList, append = false)
                encodingContextManager.updateFlattenedTables(ion11Reader, additionalMacros)
                true
            }
            SystemMacro.USE_ADDRESS -> TODO("Use")
            else -> false
        }

        if (!isShortCircuitEvaluation) {
            val eexp = macroInvocation.evaluate(templateReaderPool)
            readerManager.pushReader(eexp)
            reader = eexp
            type = null
        }
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
            encodingContextManager.ivm()
            encodingContextManager.updateFlattenedTables(ion11Reader, additionalMacros)
//            println("IVM: ${ion11Reader.symbolTable.contentToString()}")
        }
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
            0x0101 -> ArrayBackedLstSnapshot(ion11Reader.symbolTable)
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
