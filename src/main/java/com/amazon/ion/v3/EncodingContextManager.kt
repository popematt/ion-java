package com.amazon.ion.v3

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.ModuleReader2
import com.amazon.ion.v3.impl_1_1.SystemMacro
import com.amazon.ion.v3.impl_1_1.binary.ValueReaderBase
import com.amazon.ion.v3.ion_reader.*

/**
 * General usage pattern:
 * 1. Call a method to evaluate a directive or context-affecting system macros
 * 2. Call updateFlattenedTables
 */
internal class EncodingContextManager(
    private val ionReaderShim: StreamWrappingIonReader
) {

    companion object {

        @JvmField
        internal val ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE = SystemSymbols_1_1.allSymbolTexts()
            .toMutableList()
            .also { it.add(0, null) }
            .toTypedArray()

        @JvmField
        internal val ION_1_1_SYSTEM_MACROS: Array<MacroV2> = SystemMacro.MACROS_BY_ID

        @JvmField
        internal val ION_1_1_DEFAULT_SYMBOL_TABLE = SystemSymbols_1_1.allSymbolTexts()
            .toMutableList()
            .also { it.add(0, null) }
            .toTypedArray()

        @JvmField
        internal val ION_1_1_SYSTEM_SYMBOLS = SystemSymbols_1_1.allSymbolTexts().toTypedArray()

        @JvmStatic
        private val ION_1_1_SYSTEM_MODULE = ModuleReader2.Module(
            "\$ion",
            ION_1_1_SYSTEM_SYMBOLS,
            ION_1_1_SYSTEM_MACROS.map { it.systemName!!.text to it },
        )

        @JvmField
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
    }


    private val moduleReader = ModuleReader2(ionReaderShim)
    var defaultModule = ModuleReader2.Module("_", emptyArray(), emptyList())
        private set
    private val availableModules = mutableMapOf<String, ModuleReader2.Module>()
    private val activeModules = mutableListOf<ModuleReader2.Module>()

    init {
        availableModules["\$ion"] = ION_1_1_SYSTEM_MODULE
        availableModules["_"] = defaultModule
    }

    /**
     * Reset the encoding context for Ion 1.1
     */
    fun ivm() {
        defaultModule = ModuleReader2.Module("_", ION_1_1_SYSTEM_SYMBOLS, emptyList())
        availableModules.clear()
        availableModules["\$ion"] = ION_1_1_SYSTEM_MODULE
        defaultModule.macros = ION_1_1_SYSTEM_MODULE.macros

//        TODO: use "active modules"
//        activeModules.clear()
//        activeModules.add(defaultModule)
//        activeModules.add(ION_1_1_SYSTEM_MODULE)
    }

    /**
     * Short-circuit evaluation of `add_symbols` and `set_symbols`.
     */
    fun addOrSetSymbols(argReader: ValueReader, append: Boolean) {
        val newSymbols = mutableListOf<String?>()
        // IonReaderShim will take care of closing the expression group instance.
        // ionReaderShim.init(argReader)
//        moduleReader.readSymbolsList(newSymbols)

        while (true) {
            when (val valueType = argReader.nextToken()) {
                TokenTypeConst.SYMBOL -> newSymbols.add(argReader.symbolValue())
                TokenTypeConst.STRING -> newSymbols.add(argReader.stringValue())
                TokenTypeConst.END -> break
                else -> throw IonException("Symbols list may only contain non-null, un-annotated text values; found $valueType")
            }
        }


        val newSymbolTableSize = newSymbols.size + if (append) defaultModule.symbols.size else 0

        val startOfNewSymbolTable = if (append) defaultModule.symbols else arrayOf()
        val newSymbolTable = arrayOfNulls<String>(newSymbolTableSize)
        if (append) {
            System.arraycopy(defaultModule.symbols, 0, newSymbolTable, 0, startOfNewSymbolTable.size)
        }
        System.arraycopy(newSymbols.toTypedArray(), 0, newSymbolTable, startOfNewSymbolTable.size, newSymbols.size)
        defaultModule.symbols = newSymbolTable

        // TODO: Should this be a copy-on-write instead of mutation?
        //       I don't think so, because it's not modifying any references to the existing list.
//        defaultModule.symbols = newSymbols
    }

    fun addSymbols(argReader: ValueReader) = addOrSetSymbols(argReader, append = true)
    fun setSymbols(argReader: ValueReader) = addOrSetSymbols(argReader, append = false)
    fun addMacros(argReader: ValueReader) = addOrSetMacros(argReader, append = true)
    fun setMacros(argReader: ValueReader) = addOrSetMacros(argReader, append = false)

    /**
     * Short-circuit evaluation of `add_macros` and `set_macros`.
     */
    fun addOrSetMacros(argReader: ValueReader, append: Boolean) {
        val moduleMacros = if (append) defaultModule.macros.toMutableList() else mutableListOf()
        // IonReaderShim will take care of closing the expression group instance.
        ionReaderShim.init(argReader)

        // TODO: I wonder if we can get a performance improvement by using a threadlocal cache of
        //       bytes to macro definitions. A radix tree could be a very quick lookup. We would
        //       only need to use the signature and body as the key. This makes the assumption that
        //       the same macros are usually defined the same way. However, this breaks if there's
        //       a dependency that is different. :/
        moduleReader.populateMacros(availableModules, moduleMacros)

        // TODO: Should this be a copy-on-write instead of mutation?
        //       I don't think so, because it's not modifying any references to the existing list.
        defaultModule.macros = moduleMacros
    }

    fun invokeUse(argumentReader: ArgumentReader) {
        TODO("`use` macro not implemented yet")
    }

    fun readLegacySymbolTable11(structReader: StructReader) {
        // TODO: Update to use ionReaderShim -- why? In case there's a macro invocation inside the symbol table?
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

        val startOfNewSymbolTable = if (isLstAppend) defaultModule.symbols else arrayOf<String?>(null)
        val newSymbolTable = Array<String?>(newSymbols.size + startOfNewSymbolTable.size) { null }
        System.arraycopy(startOfNewSymbolTable, 0, newSymbolTable, 0, startOfNewSymbolTable.size)
        System.arraycopy(newSymbols.toArray(), 0, newSymbolTable, startOfNewSymbolTable.size, newSymbols.size)
        defaultModule.symbols = newSymbolTable
        defaultModule.macros = emptyList()
    }

    fun readDirective(sexp: SexpReader) {
        // TODO: Handle macros that could be in the directive

        sexp.nextToken()
        val directiveKind = sexp.symbolValue()

        when (directiveKind) {
            "encoding" -> TODO("'encoding' directives not implemented yet")
            "module" -> {
                ionReaderShim.init(sexp)
                val module = moduleReader.readModule(availableModules)
                availableModules[module.name] = module
                if (module.name == "_") {
                    defaultModule = module
                }
            }
            "import" -> {
                ionReaderShim.init(sexp)
                val module = moduleReader.readImport()
                availableModules[module.name] = module
                if (module.name == "_") {
                    defaultModule = module
                }
            }
            else -> throw IonException("Unknown directive: $directiveKind")
        }
    }

    fun replaceSymbolTableAppend(valueReaderBase: ValueReaderBase) {

        // [a, b, c, d]
        val defaultModuleSymbols = defaultModule.symbols

        // [null, a, b]
        val existingSymbolTable = valueReaderBase.symbolTable
        val symbols = existingSymbolTable.copyOf(defaultModuleSymbols.size + 1)

        // 3
        val appendStart = existingSymbolTable.size
        // 4
        val appendEnd = symbols.size

        for (i in appendStart until appendEnd) {
            symbols[i] = defaultModuleSymbols[i - 1]
        }
    }

    fun updateFlattenedTables(valueReaderBase: ValueReaderBase, additionalMacros: List<MacroV2> = emptyList()) {
        // TODO: See if we can avoid rebuilding symbol tables and/or macro tables that haven't changed.
        //       This might require an API change.

        val defaultModuleSymbols = defaultModule.symbols

        val symbols = arrayOfNulls<String>(defaultModuleSymbols.size + 1)
        System.arraycopy(defaultModuleSymbols, 0, symbols, 1, defaultModuleSymbols.size)

        val macros = defaultModule.macros.map { (_, m) -> m }
            .toMutableList()
            .also { it.addAll(additionalMacros) }
            .toTypedArray()

        valueReaderBase.initTables(symbols, macros)

        // TODO: Switch to use active encoding modules. Code is below.

//        // TODO: Make this less wasteful in terms of allocations.
//        val symbols = ArrayList<String?>()
//        val macros = ArrayList<Macro>()
//        symbols.add(null)
//        for (m in activeModules) {
//            symbols.addAll(m.symbols)
//            m.macros.forEach { (_, macro) -> macros.add(macro) }
//        }
//        initTables(symbols.toTypedArray(), macros.toTypedArray())
    }
}
