package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.MacroCompiler

/**
 * Work in progress class that can read module definitions.
 *
 * This does not support e-expressions in the module definition.
 */
internal class ModuleReader2(
    private val readerAdapter: ReaderAdapter,
) {
    private val macroCompiler: MacroCompiler = MacroCompiler(this::getMacro, readerAdapter)

    // var symbolTable: (Int) -> String? = { null }
    private var localAvailableBindings: Map<String, Module> = emptyMap()
    private var moduleMacros: List<Pair<String?, Macro>> = emptyList()

    // TODO: Add a proper `Module` abstraction. For now, we'll use this.
    internal data class Module(val name: String, var symbols: List<String?>, var macros: List<Pair<String?, Macro>>) {
        fun getMacro(id: Int): Macro = macros[id].second
        fun getMacro(name: String): Macro? = macros.firstOrNull { it.first == name }?.second
    }

    /**
     * Must be positioned in module sexp, after module keyword, but before the module name
     */
    fun readModule(availableBindings: Map<String, Module>): Module {
        readerAdapter.nextValue()
        val moduleName = readerAdapter.symbolValue().assumeText()
        val localAvailableBindings = mutableMapOf<String, Module>()
        localAvailableBindings.putAll(availableBindings)
        val moduleSymbols = ArrayList<String?>()
        val moduleMacros = ArrayList<Pair<String?, Macro>>()

        // state:
        // 0 -> before imports
        // 1 -> seen an import
        // 2 -> seen a nested module
        // 3 -> seen macro table
        // 4 -> seen symbol table
        var state = 0
        var t = readerAdapter.nextEncodingType()
        while (t != null) {
            if (readerAdapter.encodingType() != IonType.SEXP) {
                throw IonException("Invalid module definition; expected SEXP found $t")
            }
            readerAdapter.stepIntoContainer()
            state = readModuleDeclarationClause(localAvailableBindings, moduleSymbols, moduleMacros, state)
            readerAdapter.stepOutOfContainer()
            t = readerAdapter.nextEncodingType()
        }
        return Module(moduleName, moduleSymbols, moduleMacros)
    }

    private fun readModuleDeclarationClause(localAvailableBindings: MutableMap<String, Module>, moduleSymbols: MutableList<String?>, moduleMacros: MutableList<Pair<String?, Macro>>, state: Int): Int {
        if (readerAdapter.nextEncodingType() != IonType.SYMBOL) {
            throw IonException("Invalid module definition; expected SYMBOL found ${readerAdapter.encodingType()}")
        }
        val clauseType = readerAdapter.symbolValue().assumeText()

        when (clauseType) {
            "import" -> {
                if (state > 1) throw IonException("'import' clause must appear before any other clauses")
                val import = readImport()
                localAvailableBindings[import.name] = import
                return 1
            }
            "module" -> {
                if (state > 2) throw IonException("'module' clause must appear before any 'macros' or 'symbols' clauses")
                val module = readModule(localAvailableBindings)
                localAvailableBindings[module.name] = module
                return 2
            }
            "macros" -> {
                if (state >= 3) throw IonException("'macros' clause must appear before 'symbols' clause and may only occur once")
                // Read macro table
                populateMacros(localAvailableBindings, moduleMacros)
                return 3
            }
            "symbols" -> {
                if (state >= 4) throw IonException("'symbols' clause may only occur once")
                populateSymbols(localAvailableBindings, moduleSymbols)
                return 4
            }
            else -> throw IonException("Unrecognized clause '$clauseType' in module definition")
        }
    }

    fun populateMacros(localAvailableBindings: Map<String, Module>, moduleMacros: MutableList<Pair<String?, Macro>>) {
        this.localAvailableBindings = localAvailableBindings
        while (true) {
            when (val valueType = readerAdapter.nextEncodingType()) {
                IonType.SEXP -> {
                    // TODO: `export` clause
                    val macro = macroCompiler.compileMacro()
                    val name = macroCompiler.macroName
                    moduleMacros.add(Pair(name, macro))
                }
                IonType.SYMBOL -> {
                    TODO("re-exporting all macros from a module")
                }
                null -> return
                else -> throw IonException("Invalid macro table declaration; found unexpected $valueType")
            }
        }
    }

    private fun populateSymbols(localAvailableBindings: Map<String, Module>, moduleSymbols: MutableList<String?>) {
        while (true) {
            when (readerAdapter.nextEncodingType()) {
                IonType.SYMBOL -> {
                    val moduleName = readerAdapter.symbolValue().text ?: throw IonException("Module name must have known text")
                    val module = localAvailableBindings[moduleName] ?: throw IonException("No module named $moduleName is available")
                    moduleSymbols.addAll(module.symbols)
                }
                IonType.LIST -> {
                    readerAdapter.stepIntoContainer()
                    readSymbolsList(moduleSymbols)
                    readerAdapter.stepOutOfContainer()
                }
                null -> return
                else -> throw IonException("Invalid symbol table declaration")
            }
        }
    }

    fun readSymbolsList(into: MutableList<String?>) {
        while (true) {
            when (val valueType = readerAdapter.nextEncodingType()) {
                IonType.SYMBOL -> into.add(readerAdapter.symbolValue().text)
                IonType.STRING -> into.add(readerAdapter.stringValue())
                null -> return
                else -> throw IonException("Symbols list may only contain non-null, un-annotated text values; found $valueType")
            }
        }
    }

    // Reader should already be positioned on the first symbol
    fun readImport(): Module {
        TODO("imports not implemented yet")
    }

    private fun getMacro(macroRef: MacroRef): Macro? {
        val moduleName = macroRef.module
        val macroName = macroRef.name
        return if (moduleName != null) {
            val module = localAvailableBindings[moduleName] ?: throw IonException("No module named $moduleName")
            if (macroName == null) {
                module.getMacro(macroRef.id)
            } else {
                module.getMacro(macroName)
            }
        } else if (macroName != null) {
            moduleMacros.firstOrNull { (name, _) -> name == macroName }?.second
                ?: localAvailableBindings["_"]!!.getMacro(macroName)
                ?: SystemMacro.getMacroOrSpecialForm(macroRef)
        } else {
            moduleMacros[macroRef.id].second
        }
    }
}
