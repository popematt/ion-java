package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.MacroCompiler
import com.amazon.ion.impl.macro.ReaderAdapterIonReader
import com.amazon.ion.v3.*
import com.amazon.ion.v3.ion_reader.*

/**
 * Work in progress class that can read module definitions.
 *
 * This does not support e-expressions in the module definition.
 */
internal class ModuleReader(
    private val readerAdapter: ReaderAdapter,
    private val macroCompiler: MacroCompiler,
) {
    var symbolTable: (Int) -> String? = { null }

    // TODO: Add a proper `Module` abstraction. For now, we'll use this.
    internal data class Module(val name: String, var symbols: List<String?>, var macros: List<Pair<String?, Macro>>)

    fun readModule(sexp: ValueReader, availableBindings: Map<String, Module>): Module {
        sexp.nextToken()
        val moduleName = sexp.symbolValue() ?: throw IonException("Module name must have known text")
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
        while (sexp.nextToken() != TokenTypeConst.END) {
            if (sexp.currentToken() != TokenTypeConst.SEXP) throw IonException("Invalid module definition; expected SEXP found ${TokenTypeConst(sexp.currentToken())}")
            sexp.sexpValue().use { clause ->
                state = readModuleDeclarationClause(clause, localAvailableBindings, moduleSymbols, moduleMacros, state)
            }
        }
        return Module(moduleName, moduleSymbols, moduleMacros)
    }

    private fun readModuleDeclarationClause(clause: ValueReader, localAvailableBindings: MutableMap<String, Module>, moduleSymbols: MutableList<String?>, moduleMacros: MutableList<Pair<String?, Macro>>, state: Int): Int {
        if (clause.nextToken() != TokenTypeConst.SYMBOL) throw IonException("Invalid module definition; expected SYMBOL found ${TokenTypeConst(clause.currentToken())}")
        val clauseType = clause.symbolValue()

        when (clauseType) {
            "import" -> {
                if (state > 1) throw IonException("'import' clause must appear before any other clauses")
                val import = readImport(clause)
                localAvailableBindings[import.name] = import
                return 1
            }
            "module" -> {
                if (state > 2) throw IonException("'module' clause must appear before any 'macros' or 'symbols' clauses")
                val module = readModule(clause, localAvailableBindings)
                localAvailableBindings[module.name] = module
                return 2
            }
            "macros" -> {
                if (state >= 3) throw IonException("'macros' clause must appear before 'symbols' clause and may only occur once")
                // Read macro table
                populateMacros(clause, localAvailableBindings, moduleMacros)
                return 3
            }
            "symbols" -> {
                if (state >= 4) throw IonException("'symbols' clause may only occur once")
                populateSymbols(clause, localAvailableBindings, moduleSymbols)
                return 4
            }
            else -> throw IonException("Unrecognized clause '$clauseType' in module definition")
        }
    }

    fun populateMacros(clause: ValueReader, localAvailableBindings: Map<String, Module>, moduleMacros: MutableList<Pair<String?, Macro>>) {
        while (true) {
            when (clause.nextToken()) {
                TokenTypeConst.SEXP -> {

                }
                TokenTypeConst.END -> return
            }
        }


    }

    private fun populateSymbols(clause: ValueReader, localAvailableBindings: Map<String, Module>, moduleSymbols: MutableList<String?>) {
        while (true) {
            when (clause.nextToken()) {
                TokenTypeConst.SYMBOL -> {
                    val moduleName = clause.symbolValue() ?: throw IonException("Module name must have known text")
                    val module = localAvailableBindings[moduleName] ?: throw IonException("No module named $moduleName is available")
                    moduleSymbols.addAll(module.symbols)
                }
                TokenTypeConst.LIST -> {
                    clause.listValue().use { symbolsList -> readSymbolsList(symbolsList, moduleSymbols) }
                }
                TokenTypeConst.END -> return
                else -> throw IonException("Invalid symbol table declaration")
            }
        }
    }

    fun readSymbolsList(list: ValueReader, into: MutableList<String?>) {
        while (true) {
            when (val token = list.nextToken()) {
                TokenTypeConst.SYMBOL -> into.add(list.symbolValue())
                TokenTypeConst.STRING -> into.add(list.stringValue())
                TokenTypeConst.END -> return
                else -> throw IonException("Symbols list may only contain non-null, un-annotated text values; found ${TokenTypeConst(token)}")
            }
        }
    }

    // Reader should already be positioned on the first symbol
    fun readImport(sexp: ValueReader): Module {
        TODO("imports not implemented yet")
    }

    private fun getMacro(macroRef: MacroRef): Macro? {
        TODO()
    }
}
