package com.amazon.ion.v3

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.impl_1_1.ModuleReader2
import com.amazon.ion.v3.ion_reader.*

/**
 * General usage pattern:
 * 1. Call a method to evaluate a directive or context-affecting system macros
 * 2. Call updateFlattenedTables
 */
class EncodingContextManager(
    private val ionReaderShim: StreamWrappingIonReader
) {

    companion object {
        @JvmStatic
        internal val ION_1_1_SYSTEM_MACROS: Array<SystemMacro> = SystemMacro.entries.filter { it.id >= 0 }.sortedBy { it.id }.toTypedArray()

        @JvmStatic
        internal val ION_1_1_DEFAULT_SYMBOL_TABLE = SystemSymbols_1_1.allSymbolTexts()
            .toMutableList()
            .also { it.add(0, null) }
            .toTypedArray()

        @JvmStatic
        internal val ION_1_1_SYSTEM_SYMBOLS = SystemSymbols_1_1.allSymbolTexts().toTypedArray()

        @JvmStatic
        private val ION_1_1_SYSTEM_MODULE = ModuleReader2.Module(
            "\$ion",
            SystemSymbols_1_1.allSymbolTexts().toMutableList(),
            ION_1_1_SYSTEM_MACROS.map { it.systemSymbol.text to it },
        )

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
    }


    private val moduleReader = ModuleReader2(ReaderAdapterIonReader(ionReaderShim))
    private var defaultModule = ModuleReader2.Module("_", emptyList(), emptyList())
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
        defaultModule = ModuleReader2.Module("_", emptyList(), emptyList())
        availableModules.clear()
        availableModules["\$ion"] = ION_1_1_SYSTEM_MODULE
        defaultModule.symbols = ION_1_1_SYSTEM_SYMBOLS.toList()
        defaultModule.macros = ION_1_1_SYSTEM_MODULE.macros

//        TODO: use "active modules"
//        activeModules.clear()
//        activeModules.add(defaultModule)
//        activeModules.add(ION_1_1_SYSTEM_MODULE)
    }

    /**
     * Short-circuit evaluation of `add_symbols` and `set_symbols`.
     */
    fun addOrSetSymbols(argReader: ArgumentReader, append: Boolean) {
        val newSymbols = if (append) defaultModule.symbols.toMutableList() else mutableListOf()
        argReader.nextToken()
        argReader.expressionGroup().use { sl ->
            ionReaderShim.init(sl)
            moduleReader.readSymbolsList(newSymbols)
        }
        // TODO: Should this be a copy-on-write instead of mutation?
        //       I don't think so, because it's not modifying any references to the existing list.
        defaultModule.symbols = newSymbols
    }

    /**
     * Short-circuit evaluation of `add_macros` and `set_macros`.
     */
    fun addOrSetMacros(argReader: ArgumentReader, append: Boolean) {
        val moduleMacros = if (append) defaultModule.macros.toMutableList() else mutableListOf()
        argReader.nextToken()
        argReader.expressionGroup().use { sl ->
            ionReaderShim.init(sl)
            moduleReader.populateMacros(availableModules, moduleMacros)
        }
        // TODO: Should this be a copy-on-write instead of mutation?
        //       I don't think so, because it's not modifying any references to the existing list.
        defaultModule.macros = moduleMacros
    }

    fun invokeUse(argumentReader: ArgumentReader) {
        TODO("`use` macro not implemented yet")
    }

    // TODO: Do we even need this?
    private fun readSymbolTable10(structReader: StructReader): Array<String?> {
        // NOTE: no need to use ionReaderShim since there will be no macros.
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

        val startOfNewSymbolTable = if (isLstAppend) defaultModule.symbols.toTypedArray() else ION_1_0_SYMBOL_TABLE
        val newSymbolTable = Array<String?>(newSymbols.size + startOfNewSymbolTable.size) { null }
        System.arraycopy(startOfNewSymbolTable, 0, newSymbolTable, 0, startOfNewSymbolTable.size)
        System.arraycopy(newSymbols.toArray(), 0, newSymbolTable, startOfNewSymbolTable.size, newSymbols.size)
        return newSymbolTable
    }

    fun readLegacySymbolTable11(structReader: StructReader) {
        // TODO: Update to use ionReaderShim
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

        val startOfNewSymbolTable = if (isLstAppend) defaultModule.symbols.toTypedArray() else arrayOf<String?>(null)
        val newSymbolTable = Array<String?>(newSymbols.size + startOfNewSymbolTable.size) { null }
        System.arraycopy(startOfNewSymbolTable, 0, newSymbolTable, 0, startOfNewSymbolTable.size)
        System.arraycopy(newSymbols.toArray(), 0, newSymbolTable, startOfNewSymbolTable.size, newSymbols.size)
        defaultModule.symbols = newSymbolTable.toList()
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

    fun updateFlattenedTables(initTables: (Array<String?>, Array<Macro>) -> Unit, additionalMacros: List<Macro>) {
        // TODO: Switch to use active encoding modules. Code is below.
        val symbols = ArrayList<String?>()
        symbols.add(null)
        symbols.addAll(defaultModule.symbols)
        initTables(symbols.toTypedArray(), defaultModule.macros.map { (_, m) -> m }.toMutableList().also { it.addAll(additionalMacros) }.toTypedArray())

        // Active encoding modules code
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
