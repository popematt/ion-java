package com.amazon.ion.v3.visitor

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer

class ApplicationReaderDriver(
    private val source: ByteBuffer,
    // TODO: Catalog, options?
): AutoCloseable {
    companion object {
        @JvmStatic
        private val ION_1_1_SYSTEM_MACROS: Array<Macro> = SystemMacro.entries.filter { it.id >= 0 }.sortedBy { it.id }.toTypedArray()
        @JvmStatic
        private val ION_1_1_SYSTEM_SYMBOLS = SystemSymbols_1_1.allSymbolTexts()
            .toMutableList()
            .also { it.add(0, null) }
            .toTypedArray()
        @JvmStatic
        private val ION_1_1_SYSTEM_MODULE = ModuleReader.Module("\$ion", SystemSymbols_1_1.allSymbolTexts().toMutableList(), mutableListOf())

        @JvmStatic
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

        private const val ION_1_0 = 0x0100
        private const val ION_1_1 = 0x0101
    }

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

    private var reader: ValueReader = if (source.getInt(0).toUInt() == 0xE00101EAu) {
        ion11Reader
    } else {
        ion10Reader
    }

    private var symbolTable: Array<String?> = ION_1_0_SYMBOL_TABLE
    private var macroTable: Array<Macro> = emptyArray()
    private val moduleReader = ModuleReader()
    private val availableModules = mutableMapOf<String, ModuleReader.Module>()
    private val activeModules = mutableListOf<ModuleReader.Module>()

    private fun addOrSetSymbols(argReader: EExpArgumentReader, append: Boolean) {
        println("addOrSetSymbols(append=$append)")
        val newSymbols = if (append) symbolTable.toMutableList() else mutableListOf<String?>(null)
        argReader.nextToken()
        argReader.expressionGroup().use { sl -> moduleReader.readSymbolsList(sl, newSymbols) }
        availableModules["_"]!!.symbols = newSymbols
        // TODO: Fix this to use proper encoding module sequence.
        println(newSymbols)
        symbolTable = newSymbols.toTypedArray()
    }

    private fun lookupSid(sid: Int) : String? {
        return (reader as? com.amazon.ion.v3.impl_1_0.ValueReaderBase)
            ?.let { it.symbolTable[sid] }
            ?: symbolTable[sid]
    }

    fun readAll(visitor: VisitingReaderCallback) {
        while (readTopLevelValue(reader, visitor)) {}
    }

    fun read(visitor: VisitingReaderCallback) {
        readTopLevelValue(reader, visitor)
    }

    private fun handleTopLevelIvm() {
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
        }

        if (version == ION_1_1) {
            println("Updating symbol table to Ion 1.1")
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
            symbolTable = ION_1_1_SYSTEM_SYMBOLS
        } else {
            symbolTable = ION_1_0_SYMBOL_TABLE
        }
    }

    private fun maybeVisitNull(reader: ValueReader, visitor: VisitingReaderCallback) {
        visitor.onValue(TokenType.NULL)?.onNull(reader.nullValue()) ?: reader.skip()
    }

    private fun readTopLevelValue(initialReader: ValueReader, visitor: VisitingReaderCallback): Boolean {
        var reader = initialReader
        val token = reader.nextToken()
        when (token) {
            TokenTypeConst.IVM -> {
                handleTopLevelIvm()
                return readTopLevelValue(this.reader, visitor)
            }
            TokenTypeConst.NULL -> maybeVisitNull(reader, visitor)
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
                    lookupSid(sid)
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
                    reader.listValue().use { r -> while (readValue(r, v)); }
                    v.onListEnd()
                } ?: reader.skip()
            TokenTypeConst.SEXP ->
                visitor.onValue(TokenType.SEXP)?.let { v ->
                    v.onSexpStart()
                    reader.sexpValue().use { r -> while (readValue(r, v)); }
                    v.onSexpEnd()
                } ?: reader.skip()
            TokenTypeConst.STRUCT ->
                visitor.onValue(TokenType.STRUCT)?.let { v ->
                    v.onStructStart()
                    reader.structValue().use { r -> while (readStructField(r, v)); }
                    v.onStructEnd()
                }
            TokenTypeConst.ANNOTATIONS -> reader.annotations().use { a ->
                println("Found annotated top-level value")
                val isSystemValue = handlePossibleSystemValue(a)
                if (!isSystemValue) {
                    visitor.onAnnotation(a)?.let { v -> readValue(reader, v) } ?: reader.skip()
                }
            }
            TokenTypeConst.END -> return false
            TokenTypeConst.EEXP -> {
                val macroId = reader.eexpValue()
                val macro = if (macroId < 0) {
                    if (macroId == -1) {
                        TODO("Look up textual macro addresses")
                    } else {

                        println("System Macro ID: ${macroId and Integer.MAX_VALUE}")
                        // Could be a system macro id
                        SystemMacro[macroId and Integer.MAX_VALUE]!!
                    }
                } else {

                    println("Macro ID: $macroId")
                    macroTable[macroId]
                }
                println("Macro: $macro")
                println("reader.position: ${reader.position()}")
                // TODO: Check for macros that could produce system values
                when (macro) {
                    SystemMacro.SetSymbols -> reader.eexpArgs(macro.signature).use { addOrSetSymbols(it, append = false) }
                    SystemMacro.AddSymbols -> reader.eexpArgs(macro.signature).use { addOrSetSymbols(it, append = true) }
                    SystemMacro.Use -> TODO()
                    SystemMacro.AddMacros -> TODO()
                    SystemMacro.SetMacros -> TODO()
                    else -> {
                        val macroVisitor = visitor.onEExpression(macro)
                        if (macroVisitor != null) {
                            reader.eexpArgs(macro.signature).use { r -> while (readValue(r, macroVisitor)); }
                        } else {
                            TODO("Use the macro evaluator")
                        }
                    }
                }
                println("reader.position: ${reader.position()}")
            }
            else -> TODO("Unreachable: ${TokenTypeConst(token)}")
        }
        return true
    }

    private fun readValue(reader: ValueReader, visitor: VisitingReaderCallback): Boolean {
        val token = reader.nextToken()
        // println(TokenTypeConst(token))

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
                    lookupSid(sid)
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
                    reader.listValue().use { r -> while (readValue(r, v)); }
                    v.onListEnd()
                } ?: reader.skip()

            TokenTypeConst.SEXP ->
                visitor.onValue(TokenType.SEXP)?.let { v ->
                    v.onSexpStart()
                    reader.sexpValue().use { r -> while (readValue(r, v)); }
                    v.onSexpEnd()
                } ?: reader.skip()
            TokenTypeConst.STRUCT ->
                visitor.onValue(TokenType.STRUCT)?.let { v ->
                    v.onStructStart()
                    reader.structValue().use { r -> while (readStructField(r, v)); }
                    v.onStructEnd()
                } ?: reader.skip()
            TokenTypeConst.ANNOTATIONS -> reader.annotations().use { a ->
                visitor.onAnnotation(a)?.let { v ->
                    readValue(reader, v)
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
            else -> TODO("Unreachable: ${TokenTypeConst(token)}")
        }
        return true
    }

    private fun readStructField(reader: StructReader, visitor: VisitingReaderCallback): Boolean {
        val token = reader.nextToken()
        if (token == TokenTypeConst.END) return false

        val sid = reader.fieldNameSid()
        val text = if (sid < 0) {
            reader.fieldName()
        } else {
            lookupSid(sid)
        }

        val fieldVisitor = visitor.onField(text, sid)

        if (fieldVisitor != null) {
            readValue(reader, fieldVisitor)
        } else {
            // We might need to skip annotations.
            if (reader.nextToken() == TokenTypeConst.ANNOTATIONS) reader.nextToken()
            reader.skip()
        }
        return true
    }

    private fun handlePossibleSystemValue(a: AnnotationIterator): Boolean {
        (a as PrivateAnnotationIterator).peek()
        var isSystemValue = false
        val position = reader.position()
        val sid = a.getSid()
        println("Checking for likely system value; sid=$sid, text=${symbolTable.getOrNull(sid)}")
        if (reader.getIonVersion().toInt() == ION_1_0){
            if (sid == SystemSymbols.ION_SYMBOL_TABLE_SID && reader.nextToken() == TokenTypeConst.STRUCT) {
                isSystemValue = true
                symbolTable = reader.structValue().use { readSymbolTable10(it) }
            }
        } else {
            val text = if (sid < 0) a.getText() else lookupSid(sid)
            if (text == "\$ion" && reader.nextToken() == TokenTypeConst.SEXP) {
                isSystemValue = true
                reader.sexpValue().use(this::readDirective)
            }
        }
        if (!isSystemValue) {
            reader.seekTo(position)
        }
        return isSystemValue
    }

    private fun readSymbolTable10(structReader: StructReader): Array<String?> {
        println("Reading Ion 1.0 symbol table.")
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
        val startOfNewSymbolTable = if (isLstAppend) symbolTable else ION_1_0_SYMBOL_TABLE
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
                    symbolTable = module.symbols.toTypedArray()
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
        symbolTable = emptyArray()
        macroTable = emptyArray()
    }
}
