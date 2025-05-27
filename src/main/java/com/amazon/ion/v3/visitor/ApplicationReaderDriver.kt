package com.amazon.ion.v3.visitor

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.ValueReaderBase
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 *
 * TODO: I think we can abstract some of the common functionality, such as managing multiple readers,
 *       into a common base class.
 */
class ApplicationReaderDriver @JvmOverloads constructor(
    private val source: ByteBuffer,
    private val additionalMacros: List<Macro> = emptyList()
    // TODO: Catalog, options?
): AutoCloseable {
    constructor(outputStream: ByteArrayOutputStream): this(ByteBuffer.wrap(outputStream.toByteArray()))

    companion object {
        @JvmStatic
        internal val ION_1_1_SYSTEM_MACROS: Array<Macro> = SystemMacro.entries.filter { it.id >= 0 }.sortedBy { it.id }.toTypedArray()
        @JvmStatic
        internal val ION_1_1_SYSTEM_SYMBOLS = SystemSymbols_1_1.allSymbolTexts()
            .toMutableList()
            .also { it.add(0, null) }
            .toTypedArray()
        @JvmStatic
        private val ION_1_1_SYSTEM_MODULE = ModuleReader.Module("\$ion", SystemSymbols_1_1.allSymbolTexts().toMutableList(), mutableListOf())

        @JvmStatic
        internal val ION_1_0_SYMBOL_TABLE = arrayOf(
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

        const val ION_1_0 = 0x0100
        internal const val ION_1_1 = 0x0101
    }

    private lateinit var _templateReaderPool: TemplateResourcePool
    private val templateReaderPool: TemplateResourcePool
        get() {
            if (! ::_templateReaderPool.isInitialized) _templateReaderPool = TemplateResourcePool(ion11Reader.symbolTable, macroTable)
            return _templateReaderPool
        }

    private lateinit var _ion10Reader: StreamReader_1_0
    private val ion10Reader: StreamReader_1_0
        get() {
            if (! ::_ion10Reader.isInitialized) _ion10Reader = StreamReader_1_0(source)
            return _ion10Reader
        }

    private lateinit var _ion11Reader: ValueReaderBase
    private val ion11Reader: ValueReaderBase
        get() {
            if (!::_ion11Reader.isInitialized) _ion11Reader = StreamReaderImpl(source).also {
                it.initTables(ION_1_1_SYSTEM_SYMBOLS, ION_1_1_SYSTEM_MACROS)
            }

            return _ion11Reader
        }

    private var topLevelReader: ValueReader = if (source.getInt(0).toUInt() == 0xE00101EAu) {
        ion11Reader
    } else {
        ion10Reader
    }

    // TODO: Remove the macro table?
    private var macroTable: Array<Macro> = emptyArray()

    private val moduleReader = ModuleReader()
    private val availableModules = mutableMapOf<String, ModuleReader.Module>()
    private val activeModules = mutableListOf<ModuleReader.Module>()

    private fun addOrSetSymbols(argReader: ArgumentReader, append: Boolean) {
        val r = ion11Reader
        val newSymbols = if (append) r.symbolTable.toMutableList() else mutableListOf<String?>(null)
        argReader.nextToken()
        argReader.expressionGroup().use { sl -> moduleReader.readSymbolsList(sl, newSymbols) }
        availableModules["_"]!!.symbols = newSymbols
        // TODO: Fix this to use proper encoding module sequence.
        val symbolTable = newSymbols.toTypedArray()
        // TODO: Clean this up:
        r.initTables(symbolTable, macroTable)
        r.pool.symbolTable = symbolTable
        r.pool.macroTable = macroTable
    }

    fun readAll(visitor: VisitingReaderCallback) {
        readTopLevelValues(topLevelReader, visitor)
    }

    fun readN(n: Int, visitor: VisitingReaderCallback) {
        readTopLevelValues(topLevelReader, visitor, n)
    }

    fun read(visitor: VisitingReaderCallback) {
        readTopLevelValues(topLevelReader, visitor, 1)
    }

    private fun maybeVisitNull(reader: ValueReader, visitor: VisitingReaderCallback) {
        visitor.onValue(TokenType.NULL)?.onNull(reader.nullValue()) ?: reader.skip()
    }

    private fun maybeVisitBool(reader: ValueReader, visitor: VisitingReaderCallback) {
        val v = visitor.onValue(TokenType.BOOL)
        if (v != null) {
            v.onBoolean(reader.booleanValue())
        } else {
            reader.skip()
        }
    }

    private fun maybeVisitString(reader: ValueReader, visitor: VisitingReaderCallback) {
        val v = visitor.onValue(TokenType.STRING)
        if (v != null) {
            v.onString(reader.stringValue())
        } else {
            reader.skip()
        }
    }

    /**
     * Returns the number of application values that were read.
     *
     * TODO: This is the way to go to avoid making a method call in a tight loop, However, this seems to have some
     *       trouble with IVMs.
     */
    private fun readTopLevelValues(originalReader: ValueReader, originalVisitor: VisitingReaderCallback, max: Int = Int.MAX_VALUE): Int {
        var reader = originalReader
        var annotatedValueVisitor: VisitingReaderCallback? = null
        var i = 0
        while (i++ < max) {

            val visitor = annotatedValueVisitor ?: originalVisitor
            val token = reader.nextToken()

            when (token) {
                TokenTypeConst.IVM -> {
                    i-- // Since an IVM doesn't count as a value.

                    val currentVersion = reader.getIonVersion().toInt()
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
                        topLevelReader = reader
                    }

                    if (version == ION_1_1) {
                        // FIXME: Update this so that the default module is empty, and the active modules contains
                        //        the default module followed by the system module.
                        availableModules.clear()
                        availableModules["_"] =
                            ModuleReader.Module("_", SystemSymbols_1_1.allSymbolTexts().toMutableList(), mutableListOf())

                        // TODO: Add macros to the system module
                        availableModules["\$ion"] = ION_1_1_SYSTEM_MODULE
                        activeModules.clear()
                        activeModules.add(availableModules["_"]!!)
                        macroTable = ION_1_1_SYSTEM_MACROS
                        if (additionalMacros.isNotEmpty()) {
                            macroTable = Array(ION_1_1_SYSTEM_MACROS.size + additionalMacros.size) { i ->
                                if (i < ION_1_1_SYSTEM_MACROS.size) {
                                    ION_1_1_SYSTEM_MACROS[i]
                                } else {
                                    additionalMacros[i - ION_1_1_SYSTEM_MACROS.size]
                                }
                            }
                        }
                        // ion11Reader.symbolTable = ION_1_1_SYSTEM_SYMBOLS
                        ion11Reader.initTables(ION_1_1_SYSTEM_SYMBOLS, macroTable)
                    } else {
                        ion10Reader.symbolTable = ION_1_0_SYMBOL_TABLE
                    }
                    continue
                }
                TokenTypeConst.NULL -> visitor.onValue(TokenType.NULL)?.onNull(reader.nullValue()) ?: reader.skip()
                TokenTypeConst.BOOL -> visitor.onValue(TokenType.BOOL)?.onBoolean(reader.booleanValue()) ?: reader.skip()
                TokenTypeConst.INT -> visitor.onValue(TokenType.INT)?.onLongInt(reader.longValue()) ?: reader.skip()
                TokenTypeConst.FLOAT -> visitor.onValue(TokenType.FLOAT)?.onFloat(reader.doubleValue()) ?: reader.skip()
                TokenTypeConst.DECIMAL -> visitor.onValue(TokenType.DECIMAL)?.onDecimal(reader.decimalValue()) ?: reader.skip()
                TokenTypeConst.TIMESTAMP -> visitor.onValue(TokenType.TIMESTAMP)?.onTimestamp(reader.timestampValue()) ?: reader.skip()
                TokenTypeConst.STRING -> {
                    val v = visitor.onValue(TokenType.STRING)
                    if (v != null) {
                        v.onString(reader.stringValue())
                    } else {
                        reader.skip()
                    }
                }
                TokenTypeConst.SYMBOL -> visitor.onValue(TokenType.SYMBOL)?.let {
                    val sid = reader.symbolValueSid()
                    val text = if (sid < 0) {
                        reader.symbolValue()
                    } else {
                        reader.lookupSid(sid)
                    }
                    it.onSymbol(text, sid)
                } ?: reader.skip()
                TokenTypeConst.CLOB -> visitor.onValue(TokenType.CLOB)?.onClob(reader.clobValue()) ?: reader.skip()
                TokenTypeConst.BLOB -> visitor.onValue(TokenType.BLOB)?.onBlob(reader.blobValue()) ?: reader.skip()
                TokenTypeConst.LIST ->{
                    val v = visitor.onValue(TokenType.LIST)
                    if (v != null) {
                        v.onListStart()
                        reader.listValue().use { r -> readAllValues(r, v) }
                        v.onListEnd()
                    } else {
                        reader.skip()
                    }
                }
                TokenTypeConst.SEXP -> {
                    val v = visitor.onValue(TokenType.SEXP)
                    if (v != null) {
                        v.onSexpStart()
                        reader.sexpValue().use { r -> readAllValues(r, v) }
                        v.onSexpEnd()
                    } else {
                        reader.skip()
                    }
                }
                TokenTypeConst.STRUCT -> {
                    val v = visitor.onValue(TokenType.STRUCT)
                    if (v != null) {
                        v.onStructStart()
                        reader.structValue().use { r -> readAllStructFields(r, v) }
                        v.onStructEnd()
                    } else {
                        reader.skip()
                    }
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
                TokenTypeConst.EEXP -> {
                    val macroId = reader.eexpValue()
                    val macro = if (macroId < 0) {
                        if (macroId == -1) {
                            TODO("Look up textual macro addresses")
                        } else {
                            // Could be a system macro id
                            SystemMacro[macroId and Integer.MAX_VALUE]!!
                        }
                    } else {
                        macroTable[macroId]
                    }
                    // TODO: Check for macros that could produce system values
                    // TODO: When there's a system value, decrement i
                    when (macro) {
                        SystemMacro.SetSymbols -> reader.eexpArgs(macro.signature).use { addOrSetSymbols(it, append = false) }
                        SystemMacro.AddSymbols -> reader.eexpArgs(macro.signature).use { addOrSetSymbols(it, append = true) }
                        SystemMacro.Use -> TODO()
                        SystemMacro.AddMacros -> TODO()
                        SystemMacro.SetMacros -> TODO()
                        else -> {
                            val macroVisitor = visitor.onEExpression(macro)
                            if (macroVisitor != null) {
                                reader.eexpArgs(macro.signature).use { r -> readAllValues(r, macroVisitor) }
                            } else {
                                // TODO: Since we're at the top level, we need to read as top-level values in case a directive
                                //       is produced.
                                // Start a macro evaluation session
                                reader.eexpArgs(macro.signature).use { args ->
                                    templateReaderPool
                                        .startEvaluation(macro, args)
                                        .use { macroInvocation ->
                                            println("Starting macro: \n${macro.body!!.joinToString("\n")}\n")
                                            i += readTopLevelValues(macroInvocation, visitor)
                                        }
                                }
                            }
                        }
                    }
                }
                TokenTypeConst.END -> return i
                TokenTypeConst.VARIABLE_REF -> {
                    (reader as MacroInvocationReader).variableValue().use { variable ->
                        readTopLevelValues(variable, visitor)
                    }
                }
                else -> TODO("Unreachable: ${TokenTypeConst(token)}")
            }
        }
        return i
    }

    private fun readValue(reader: ValueReader, visitor: VisitingReaderCallback): Boolean {
        val token = reader.nextToken()

        when (token) {
            TokenTypeConst.NULL -> visitor.onValue(TokenType.NULL)?.onNull(reader.nullValue()) ?: reader.skip()
            TokenTypeConst.BOOL -> visitor.onValue(TokenType.BOOL)?.onBoolean(reader.booleanValue()) ?: reader.skip()
            TokenTypeConst.INT -> visitor.onValue(TokenType.INT)?.onLongInt(reader.longValue()) ?: reader.skip()
            TokenTypeConst.FLOAT -> visitor.onValue(TokenType.FLOAT)?.onFloat(reader.doubleValue()) ?: reader.skip()
            TokenTypeConst.DECIMAL -> visitor.onValue(TokenType.DECIMAL)?.onDecimal(reader.decimalValue()) ?: reader.skip()
            TokenTypeConst.TIMESTAMP -> visitor.onValue(TokenType.TIMESTAMP)?.onTimestamp(reader.timestampValue()) ?: reader.skip()
            TokenTypeConst.STRING -> visitor.onValue(TokenType.STRING)?.onString(reader.stringValue()) ?: reader.skip()
            TokenTypeConst.SYMBOL -> visitor.onValue(TokenType.SYMBOL)?.let {
                val sid = reader.symbolValueSid()
                val text = if (sid < 0) {
                    reader.symbolValue()
                } else {
                    reader.lookupSid(sid)
                }
                it.onSymbol(text, sid)
            } ?: reader.skip()
            TokenTypeConst.CLOB -> visitor.onValue(TokenType.CLOB)?.onClob(reader.clobValue()) ?: reader.skip()
            TokenTypeConst.BLOB -> visitor.onValue(TokenType.BLOB)?.onBlob(reader.blobValue()) ?: reader.skip()
            // IMPLEMENTATION NOTE:
            //     For containers, we're going to step in, and then immediately step out if we need to skip.
            //     This might be slightly less efficient, but it works for both delimited and prefixed containers.
            TokenTypeConst.LIST ->
                visitor.onValue(TokenType.LIST)?.let { v ->
                    v.onListStart()
                    // TODO: Switch to a read all method so that we aren't calling `readValue` in a tight loop
                    reader
                        .listValue()
                        .use { r ->
                            readAllValues(r, v)
                        }
                    v.onListEnd()
                } ?: reader.skip()

            TokenTypeConst.SEXP ->
                visitor.onValue(TokenType.SEXP)?.let { v ->
                    v.onSexpStart()
                    reader.sexpValue().use { r -> readAllValues(r, v) }
                    v.onSexpEnd()
                } ?: reader.skip()
            TokenTypeConst.STRUCT -> {
                val v = visitor.onValue(TokenType.STRUCT)
                if (v != null) {
                    v.onStructStart()
                    reader.structValue().use { r -> readAllStructFields(r, v) }
                    v.onStructEnd()
                } else {
                    reader.skip()
                }
            }
            TokenTypeConst.ANNOTATIONS -> reader.annotations().use { a ->
                val v = visitor.onAnnotation(a)
                if (v != null) {
                    readValue(reader, v)
                } else {
                    reader.nextToken()
                    reader.skip()
                }
            }
            TokenTypeConst.EEXP -> {
                val macroId = reader.eexpValue()
                val macro = if (macroId < 0) {
                    TODO("Look up textual macro addresses")
                } else {
                    macroTable[macroId]
                }
                val macroVisitor = visitor.onEExpression(macro)
                if (macroVisitor != null) {
                    reader.eexpArgs(macro.signature).use { r ->
                        while (readValue(r, macroVisitor));
                    }
                } else {
                    TODO("Use the macro evaluator")
                }
            }
            TokenTypeConst.END -> return false
            TokenTypeConst.VARIABLE_REF -> {
                (reader as MacroInvocationReader).variableValue().use { variable ->
                    readValue(variable, visitor)
                }
            }
            else -> TODO("Unreachable: ${TokenTypeConst(token)}")
        }
        return true
    }

    // TODO: Use a stack in this function for nested sequences
    //       Use a stack in readAllStructs for nested structs
    private fun readAllValues(reader: ValueReader, visitor: VisitingReaderCallback) {
        var annotatedValueVisitor: VisitingReaderCallback? = null

        while(true) {

            val visitor = annotatedValueVisitor ?: visitor
            val token = reader.nextToken()

            when (token) {
                TokenTypeConst.NULL -> visitor.onValue(TokenType.NULL)?.onNull(reader.nullValue()) ?: reader.skip()
                TokenTypeConst.BOOL -> visitor.onValue(TokenType.BOOL)?.onBoolean(reader.booleanValue()) ?: reader.skip()
                TokenTypeConst.INT -> visitor.onValue(TokenType.INT)?.onLongInt(reader.longValue()) ?: reader.skip()
                TokenTypeConst.FLOAT -> visitor.onValue(TokenType.FLOAT)?.onFloat(reader.doubleValue()) ?: reader.skip()
                TokenTypeConst.DECIMAL -> visitor.onValue(TokenType.DECIMAL)?.onDecimal(reader.decimalValue()) ?: reader.skip()
                TokenTypeConst.TIMESTAMP -> visitor.onValue(TokenType.TIMESTAMP)?.onTimestamp(reader.timestampValue()) ?: reader.skip()
                TokenTypeConst.STRING -> {
                    val v = visitor.onValue(TokenType.STRING)
                    if (v != null) {
                        v.onString(reader.stringValue())
                    } else {
                        reader.skip()
                    }
                }
                TokenTypeConst.SYMBOL -> visitor.onValue(TokenType.SYMBOL)?.let {
                    val sid = reader.symbolValueSid()
                    val text = if (sid < 0) {
                        reader.symbolValue()
                    } else {
                        reader.lookupSid(sid)
                    }
                    it.onSymbol(text, sid)
                } ?: reader.skip()
                TokenTypeConst.CLOB -> visitor.onValue(TokenType.CLOB)?.onClob(reader.clobValue()) ?: reader.skip()
                TokenTypeConst.BLOB -> visitor.onValue(TokenType.BLOB)?.onBlob(reader.blobValue()) ?: reader.skip()
                // IMPLEMENTATION NOTE:
                //     For containers, we're going to step in, and then immediately step out if we need to skip.
                //     This might be slightly less efficient, but it works for both delimited and prefixed containers.
                TokenTypeConst.LIST ->{
                    val v = visitor.onValue(TokenType.LIST)
                    if (v != null) {
                        v.onListStart()
                        reader.listValue().use { r -> readAllValues(r, v) }
                        v.onListEnd()
                    } else {
                        reader.skip()
                    }
                }
                TokenTypeConst.SEXP -> {
                    val v = visitor.onValue(TokenType.SEXP)
                    if (v != null) {
                        v.onSexpStart()
                        reader.sexpValue().use { r -> readAllValues(r, v) }
                        v.onSexpEnd()
                    } else {
                        reader.skip()
                    }
                }
                TokenTypeConst.STRUCT -> {
                    val v = visitor.onValue(TokenType.STRUCT)
                    if (v != null) {
                        v.onStructStart()
                        reader.structValue().use { r -> readAllStructFields(r, v) }
                        v.onStructEnd()
                    } else {
                        reader.skip()
                    }
                }
                TokenTypeConst.ANNOTATIONS -> {
                    val a = reader.annotations()
                    val v = visitor.onAnnotation(a)
                    a.close()
                    if (v != null) {
                        annotatedValueVisitor = v
                        continue
                    } else {
                        reader.nextToken()
                        reader.skip()
                    }
                }
                TokenTypeConst.EEXP -> {
                    val macroId = reader.eexpValue()
                    val macro = if (macroId < 0) {
                        TODO("Look up textual macro addresses")
                    } else {
                        macroTable[macroId]
                    }
                    val macroVisitor = visitor.onEExpression(macro)
                    if (macroVisitor != null) {
                        reader.eexpArgs(macro.signature).use { r -> readAllValues(r, macroVisitor) }
                    } else {
                        reader.eexpArgs(macro.signature).use { args ->
                            templateReaderPool.startEvaluation(macro, args).use { macroInvocation ->
                                readAllValues(macroInvocation, visitor)
                            }
                        }
                    }
                }
                TokenTypeConst.END -> return
                TokenTypeConst.VARIABLE_REF -> {
                    (reader as MacroInvocationReader).variableValue().use { variable ->
                        readAllValues(variable, visitor)
                    }
                }
                else -> TODO("Unreachable: ${TokenTypeConst(token)}")
            }
        }
    }

    private fun readAllStructFields(reader: StructReader, visitor: VisitingReaderCallback) {
        while (true) {
            // Field name
            val token = reader.nextToken()
            if (token == TokenTypeConst.END) return
            val sid = reader.fieldNameSid()
            val text = if (sid < 0) {
                reader.fieldName()
            } else {
                reader.lookupSid(sid)
            }

            val fieldVisitor = visitor.onField(text, sid)

            if (fieldVisitor != null) {
                readValue(reader, fieldVisitor)
            } else {
                // We might need to skip annotations.
                if (reader.nextToken() == TokenTypeConst.ANNOTATIONS) reader.nextToken()
                reader.skip()
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
                reader.sexpValue().use(this::readDirective)
            } else if (text == "\$ion_symbol_table" && nt == TokenTypeConst.STRUCT) {
                // Is it a legacy symbol table?
                isSystemValue = true
                val symbolTable = reader.structValue().use { readLegacySymbolTable11(it) }
                // TODO: Clean this up
                val r = ion11Reader
                r.initTables(symbolTable, ION_1_1_SYSTEM_MACROS)
                r.pool.symbolTable = symbolTable
                r.pool.macroTable = ION_1_1_SYSTEM_MACROS
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

    private fun readLegacySymbolTable11(structReader: StructReader): Array<String?> {
        val newSymbols = ArrayList<String?>()
        var isLstAppend = false
        while (true) {
            val token = structReader.nextToken()
            if (token == TokenTypeConst.END) {
                break
            } else if (token != TokenTypeConst.FIELD_NAME) {
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
        val r = ion11Reader
        val startOfNewSymbolTable = if (isLstAppend) r.symbolTable else arrayOf<String?>(null)
        val newSymbolTable = Array<String?>(newSymbols.size + startOfNewSymbolTable.size) { null }
        System.arraycopy(startOfNewSymbolTable, 0, newSymbolTable, 0, startOfNewSymbolTable.size)
        System.arraycopy(newSymbols.toArray(), 0, newSymbolTable, startOfNewSymbolTable.size, newSymbols.size)
        return newSymbolTable
    }

    private fun readDirective(sexp: SexpReader) {
        // TODO: Handle macros that could be in the directive

        sexp.nextToken()
        val directiveKind = sexp.symbolValue()

        when (directiveKind) {
            "encoding" -> TODO("'encoding' directives not implemented yet")
            "module" -> {
                val module = moduleReader.readModule(sexp, availableModules)
                availableModules[module.name] = module
                if (module.name == "_") {
                    ion11Reader.symbolTable = module.symbols.toTypedArray()
                }
            }
            "import" -> {
                val module = moduleReader.readImport(sexp)
                availableModules[module.name] = module
            }
            else -> throw IonException("Unknown directive: $directiveKind")
        }
    }

    override fun close() {
        if (::_ion10Reader.isInitialized) _ion10Reader.close()
        if (::_ion11Reader.isInitialized) _ion11Reader.close()
        macroTable = emptyArray()
    }
}
