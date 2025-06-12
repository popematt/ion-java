package com.amazon.ion.v3.visitor

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.*
import com.amazon.ion.v3.impl_1_1.binary.*
import com.amazon.ion.v3.impl_1_1.binary.ValueReaderBase
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.ion_reader.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 *
 * This guides the visitors around.
 */
class ApplicationTourGuide @JvmOverloads constructor(
    private val source: ByteBuffer,
): AutoCloseable {
    constructor(outputStream: ByteArrayOutputStream): this(ByteBuffer.wrap(outputStream.toByteArray()))

    companion object {
        const val ION_1_0 = 0x0100
        const val ION_1_1 = 0x0101

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
    }

    private lateinit var _templateReaderPool: TemplateResourcePool
    private val templateReaderPool: TemplateResourcePool
        get() {
            if (! ::_templateReaderPool.isInitialized) _templateReaderPool = TemplateResourcePool.getInstance()
            return _templateReaderPool
        }

    private lateinit var ion10Reader: StreamReader_1_0

    private lateinit var ion11Reader: ValueReaderBase

    private val readerManager = ReaderManager()

    private fun initIon10Reader() {
        ion10Reader = StreamReader_1_0(source.asReadOnlyBuffer())
    }
    private fun initIon11Reader() {
        ion11Reader = StreamReaderImpl(source.asReadOnlyBuffer())
    }

    private var topLevelReader: ValueReader = if (source.getInt(0).toUInt() == 0xE00101EAu) {
        initIon11Reader()
        ion11Reader
    } else {
        initIon10Reader()
        ion10Reader
    }

    private val encodingContextManager = EncodingContextManager(StreamWrappingIonReader())

    fun readAll(visitor: VisitingReaderCallback) {
        // TODO: check the version and initialize the top level reader here.
    }

    // TODO: readN function.

    /**
     * Internal only entry-point to start using the visitor API at any depth.
     */
    internal fun readValues(reader: ValueReader, visitor: ReaderVisitor) {
        readTopLevelValues(reader, visitor)
    }

    private fun visitSymbol(reader: ValueReader, visitor: ReaderVisitor) {
        val sid = reader.symbolValueSid()
        val text = if (sid < 0) {
            reader.symbolValue()
        } else {
            reader.lookupSid(sid)
        }
        visitor.onSymbol(text, sid)
    }

    /**
     * Returns the number of application values that were read.
     *
     * TODO: This is the way to go to avoid making a method call in a tight loop, However, this seems to have some
     *       trouble with IVMs.
     */
    private fun readTopLevelValues(originalReader: ValueReader, originalVisitor: ReaderVisitor, max: Int = Int.MAX_VALUE): Int {
        var reader = originalReader
        var annotatedValueVisitor: ReaderVisitor? = null
        var i = 0

        // TODO? originalVisitor.onStart()

        while (i++ < max) {
            val visitor = annotatedValueVisitor ?: originalVisitor
            when (val token = reader.nextToken()) {
                TokenTypeConst.IVM -> { reader = privateOnIvm(reader, visitor) }
                TokenTypeConst.NULL -> visitor.onNull(reader.nullValue())
                TokenTypeConst.BOOL -> visitor.onBoolean(reader.booleanValue())
                TokenTypeConst.INT -> visitor.onLongInt(reader.longValue())
                TokenTypeConst.FLOAT -> visitor.onFloat(reader.doubleValue())
                TokenTypeConst.DECIMAL -> visitor.onDecimal(reader.decimalValue())
                TokenTypeConst.TIMESTAMP -> visitor.onTimestamp(reader.timestampValue())
                TokenTypeConst.STRING -> visitor.onString(reader.stringValue())
                TokenTypeConst.SYMBOL -> {
                    val sid = reader.symbolValueSid()
                    val text = if (sid < 0) {
                        reader.symbolValue()
                    } else {
                        reader.lookupSid(sid)
                    }
                    visitor.onSymbol(text, sid)
                }
                TokenTypeConst.CLOB -> visitor.onClob(reader.clobValue())
                TokenTypeConst.BLOB -> visitor.onBlob(reader.blobValue())
                TokenTypeConst.LIST -> {
                    val listVisitor = visitor.onList()
                    listVisitor.onStart()
                    val listReader = reader.listValue()
                    readAllTheThings(listReader, listVisitor)
                    listReader.close()
                    listVisitor.onEnd()
                }
                TokenTypeConst.SEXP -> {
                    val sexpVisitor = visitor.onSexp()
                    sexpVisitor.onStart()
                    val sexpReader = reader.sexpValue()
                    readAllTheThings(sexpReader, sexpVisitor)
                    sexpReader.close()
                    sexpVisitor.onEnd()
                }
                TokenTypeConst.STRUCT -> {
                    val v = visitor.onStruct()
                    val r = reader.structValue()
                    v.onStart()
                    readAllTheThings(r, v)
                    v.onEnd()
                    r.close()
                }
                TokenTypeConst.ANNOTATIONS -> {
                    val annotationIterator = handlePossibleSystemValue(reader)
                    if (annotationIterator != null) {
                        val v = visitor.onAnnotation(annotationIterator)
                        if (v != null) {
                            // Annotations do not count towards the total
                            i--
                            annotatedValueVisitor = v
                            continue
                        } else {
                            // But if they are skipping the value, we'll count it anyway, so we won't decrement.
                            reader.skip()
                        }
                    } else {
                        // System values should not be counted.
                        i--
                    }
                }
                TokenTypeConst.MACRO_INVOCATION -> {
                    val macro = reader.macroValue()
                    // TODO: Check for macros that could produce system values
                    // TODO: When there's a system value, decrement i
                    when (macro) {
                        SystemMacro.SetSymbols -> reader.macroArguments(macro.signature).use {
                            encodingContextManager.addOrSetSymbols(it, append = false)
                            encodingContextManager.updateFlattenedTables(ion11Reader, emptyList())
                        }
                        SystemMacro.AddSymbols -> reader.macroArguments(macro.signature).use {
                            encodingContextManager.addOrSetSymbols(it, append = true)
                            encodingContextManager.updateFlattenedTables(ion11Reader, emptyList())
                        }
                        SystemMacro.SetMacros -> reader.macroArguments(macro.signature).use {
                            encodingContextManager.addOrSetMacros(it, append = false)
                            encodingContextManager.updateFlattenedTables(ion11Reader, emptyList())
                        }
                        SystemMacro.AddMacros -> reader.macroArguments(macro.signature).use {
                            encodingContextManager.addOrSetMacros(it, append = true)
                            encodingContextManager.updateFlattenedTables(ion11Reader, emptyList())
                        }
                        SystemMacro.Use -> reader.macroArguments(macro.signature).use {
                            encodingContextManager.invokeUse(it)
                            encodingContextManager.updateFlattenedTables(ion11Reader, emptyList())
                        }
                        else -> {
                            val macroVisitor = visitor.onMacro(macro)
                            if (macroVisitor != null) {
                                reader.macroArguments(macro.signature).use { r ->
                                    readAllTheThings(r, macroVisitor)
                                }
                            } else {
                                // TODO: Since we're at the top level, we need to read as top-level values in case a directive
                                //       is produced.
                                // Start a macro evaluation session
                                readTopLevelValues(reader.macroArguments(macro.signature), visitor)
                            }
                        }
                    }
                }
                TokenTypeConst.END -> {
                    return i
                }
                TokenTypeConst.VARIABLE_REF -> {
                }
                else -> TODO("Unreachable: ${TokenTypeConst(token)}")
            }
        }
        return i
    }

    private fun privateOnIvm(reader: ValueReader, visitor: ReaderVisitor): ValueReader {
        var nextReader = reader
        val currentVersion = reader.getIonVersion().toInt()
        val version = reader.ivm().toInt()
        // If the version is the same, then we know it's a valid version, and we can skip validation
        if (version != currentVersion) {
            val position = reader.position()
            nextReader = when (version) {
                ION_1_0 -> {
                    if (!this::ion10Reader.isInitialized) { initIon10Reader() }
                    ion10Reader
                }
                ION_1_1 -> {
                    if (!this::ion11Reader.isInitialized) { initIon11Reader() }
                    ion11Reader
                }
                else -> throw IonException("Unknown Ion Version ${version ushr 8}.${version and 0xFF}")
            }
            nextReader.seekTo(position)
            topLevelReader = reader
        }

        if (version == ION_1_1) {
            encodingContextManager.ivm()
            encodingContextManager.updateFlattenedTables(ion11Reader, emptyList())
        }
        visitor.onIVM(version shr 8, version and 0xFF)
        return nextReader
    }

    private fun readAllTheThings(
        initialReader: ValueReader,
        visitor: ReaderVisitor,
    ) {
        var stack = Array<ValueReader?>(0) { null }
        var stackSize = 0
        var reader: ValueReader = initialReader

        fun pushReaderToStack() {
            if (stack.size == stackSize) {
                val oldStack = stack
                stack = arrayOfNulls(stackSize + 10)
                System.arraycopy(oldStack, 0, stack, 0, stackSize)
            }
            stack[stackSize] = reader
            stackSize++
        }

        while (true) {
            val token = reader.nextToken()
            when (token) {
                TokenTypeConst.END -> {
                    reader.close()
                    if (stackSize > 0) {
                        reader = stack[--stackSize]!!
                    } else {
                        break
                    }
                }
                TokenTypeConst.NULL -> visitor.onNull(reader.nullValue())
                TokenTypeConst.BOOL -> visitor.onBoolean(reader.booleanValue())
                TokenTypeConst.INT -> visitor.onLongInt(reader.longValue())
                TokenTypeConst.FLOAT -> visitor.onFloat(reader.doubleValue())
                TokenTypeConst.DECIMAL -> visitor.onDecimal(reader.decimalValue())
                TokenTypeConst.TIMESTAMP -> visitor.onTimestamp(reader.timestampValue())
                TokenTypeConst.STRING -> visitor.onString(reader.stringValue())
                TokenTypeConst.SYMBOL -> visitSymbol(reader, visitor)
                TokenTypeConst.CLOB -> visitor.onClob(reader.clobValue())
                TokenTypeConst.BLOB -> visitor.onBlob(reader.blobValue())
                TokenTypeConst.LIST -> {
                    val v = visitor.onList()
                    val r = reader.listValue()
                    v.onStart()
                    readAllTheThings(r, v)
                    v.onEnd()
                    r.close()
                }
                TokenTypeConst.SEXP -> {
                    val v = visitor.onSexp()
                    val r = reader.sexpValue()
                    v.onStart()
                    readAllTheThings(r, v)
                    v.onEnd()
                    r.close()
                }
                TokenTypeConst.STRUCT -> {
                    val v = visitor.onStruct()
                    val r = reader.structValue()
                    v.onStart()
                    readAllTheThings(r, v)
                    v.onEnd()
                    r.close()
                }
                TokenTypeConst.ABSENT_ARGUMENT -> {
                    // Do nothing
                }
                TokenTypeConst.EXPRESSION_GROUP -> {
                    pushReaderToStack()
                    (reader as ArgumentReader)
                    reader = reader.expressionGroup()
                }
                TokenTypeConst.MACRO_INVOCATION -> {
                    val macro = reader.macroValue()
                    val macroVisitor = visitor.onMacro(macro)
                    val args = reader.macroArguments(macro.signature)
                    if (macroVisitor != null) {
                        readAllTheThings(args, macroVisitor)
                        args.close()
                    } else {
                        val invocation = templateReaderPool.startEvaluation(macro, args)
                        pushReaderToStack()
                        reader = invocation
                    }
                }
                TokenTypeConst.VARIABLE_REF -> {
                }
                else -> TODO("Unreachable: ${TokenTypeConst(token)}")
            }
        }
    }

    private fun readAllTheFields(
        initialReader: ValueReader,
        visitor: ReaderVisitor,
        initialFieldName: String? = null,
        initialFieldNameSid: Int = -1,
    ) {
        var fieldName: String? = initialFieldName
        var fieldNameSid = initialFieldNameSid
        var stack = Array<ValueReader?>(0) { null }
        var stackSize = 0
        var reader: ValueReader = initialReader
        var isInStruct = fieldName != null || fieldNameSid >= 0

        fun pushReaderToStack() {
            if (stack.size == stackSize) {
                val oldStack = stack
                stack = arrayOfNulls(stackSize + 10)
                System.arraycopy(oldStack, 0, stack, 0, stackSize)
            }
            stack[stackSize] = reader
            stackSize++
        }

        while (true) {
            val token = reader.nextToken()
            when (token) {
                TokenTypeConst.END -> {
                    reader.close()
                    if (stackSize > 0) {
                        reader = stack[--stackSize]!!
                    } else {
                        break
                    }
                }
                TokenTypeConst.NULL -> {
                    if (isInStruct) {
                        val fieldVisitor = visitor.onField(fieldName, fieldNameSid)
                        if (fieldVisitor != null) {
                            fieldVisitor.onNull(reader.nullValue())
                        } else {
                            reader.skip()
                        }
                    } else {
                        visitor.onNull(reader.nullValue())
                    }
                }
                TokenTypeConst.BOOL -> visitor.onBoolean(reader.booleanValue())
                TokenTypeConst.INT -> visitor.onLongInt(reader.longValue())
                TokenTypeConst.FLOAT -> visitor.onFloat(reader.doubleValue())
                TokenTypeConst.DECIMAL -> visitor.onDecimal(reader.decimalValue())
                TokenTypeConst.TIMESTAMP -> visitor.onTimestamp(reader.timestampValue())
                TokenTypeConst.STRING -> visitor.onString(reader.stringValue())
                TokenTypeConst.SYMBOL -> visitSymbol(reader, visitor)
                TokenTypeConst.CLOB -> visitor.onClob(reader.clobValue())
                TokenTypeConst.BLOB -> visitor.onBlob(reader.blobValue())
                TokenTypeConst.LIST -> {
                    val v = visitor.onList()
                    val r = reader.listValue()
                    v.onStart()
                    readAllTheThings(r, v)
                    v.onEnd()
                    r.close()
                }
                TokenTypeConst.SEXP -> {
                    val v = visitor.onSexp()
                    val r = reader.sexpValue()
                    v.onStart()
                    readAllTheThings(r, v)
                    v.onEnd()
                    r.close()
                }
                TokenTypeConst.STRUCT -> {
                    val v = visitor.onStruct()
                    val r = reader.structValue()
                    v.onStart()
                    readAllTheThings(r, v)
                    v.onEnd()
                    r.close()
                }
                TokenTypeConst.FIELD_NAME -> {
                    (reader as StructReader)
                    isInStruct = true
                    fieldNameSid = reader.fieldNameSid()
                    fieldName = if (fieldNameSid < 0) {
                        reader.fieldName()
                    } else {
                        reader.lookupSid(fieldNameSid)
                    }
                }
                TokenTypeConst.ABSENT_ARGUMENT -> {
                    // Do nothing
                }
                TokenTypeConst.EXPRESSION_GROUP -> {
                    pushReaderToStack()
                    (reader as ArgumentReader)
                    reader = reader.expressionGroup()
                }
                TokenTypeConst.MACRO_INVOCATION -> {
                    val macro = reader.macroValue()
                    val macroVisitor = visitor.onMacro(macro)
                    val args = reader.macroArguments(macro.signature)
                    if (macroVisitor != null) {
                        readAllTheThings(args, macroVisitor)
                    } else {
                        val invocation = templateReaderPool.startEvaluation(macro, args)
                        pushReaderToStack()
                        // Can't push onto the stack because the args need to be closed at the right time.
                        readAllTheThings(invocation, visitor)
                        invocation.close()
                    }
                    args.close()
                }
                TokenTypeConst.VARIABLE_REF -> {
                }
                else -> TODO("Unreachable: ${TokenTypeConst(token)}")
            }
        }
    }

    /**
     * Reader should be positioned on a top-level annotations token.
     *
     * Returns the annotation iterator iff this is not a system value.
     * Reader will be ready for `nextToken()` when this function returns.
     */
    private fun handlePossibleSystemValue(reader: ValueReader): AnnotationIterator? {
        val a = reader.annotations()
        (a as PrivateAnnotationIterator).peek()
        var isSystemValue = false
        val position = reader.position()
        val sid = a.getSid()
        // Is this an Ion 1.0 symbol table?
        if (reader.getIonVersion().toInt() == ION_1_0) {
            if (sid == SystemSymbols.ION_SYMBOL_TABLE_SID && reader.nextToken() == TokenTypeConst.STRUCT) {
                isSystemValue = true
                ion10Reader.symbolTable = reader.structValue().use { readSymbolTable10(it) }
            }
        } else {
            val text = if (sid < 0) a.getText() else reader.lookupSid(sid)
            val nt = reader.nextToken()
            // Is it a `$ion::(..)`
            if (text == "\$ion" && nt == TokenTypeConst.SEXP) {
                isSystemValue = true
                reader.sexpValue().use(encodingContextManager::readDirective)
                encodingContextManager.updateFlattenedTables(ion11Reader, emptyList())
            } else if (text == "\$ion_symbol_table" && nt == TokenTypeConst.STRUCT) {
                // Is it a legacy symbol table?
                isSystemValue = true
                reader.structValue().use {
                    encodingContextManager.readLegacySymbolTable11(it)
                    encodingContextManager.updateFlattenedTables(ion11Reader, emptyList())
                }
            } else if (text == "\$ion_literal") {
                reader.seekTo(position)
                // advance the annotations iterator to skip the `$ion_literal` annotation.
                a.next()
                return a
            }
        }
        return if (!isSystemValue) {
            reader.seekTo(position)
            a
        } else {
            a.close()
            null
        }
    }


    private fun readSymbolTable10(structReader: StructReader): Array<String?> {
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
                        else -> {
                            // TODO: Should this be an error?
                        }
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
                else -> {}
            }
        }

        val r = ion10Reader
        val startOfNewSymbolTable = if (isLstAppend) r.symbolTable else ION_1_0_SYMBOL_TABLE
        val newSymbolTable = Array<String?>(newSymbols.size + startOfNewSymbolTable.size) { null }
        System.arraycopy(startOfNewSymbolTable, 0, newSymbolTable, 0, startOfNewSymbolTable.size)
        System.arraycopy(newSymbols.toArray(), 0, newSymbolTable, startOfNewSymbolTable.size, newSymbols.size)
        return newSymbolTable
    }

    override fun close() {
        if (this::ion10Reader.isInitialized) {
            ion10Reader.close()
        }
        if (this::ion11Reader.isInitialized) {
            ion11Reader.close()
        }
    }
}
