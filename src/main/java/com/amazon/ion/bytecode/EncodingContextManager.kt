// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.bytecode

import com.amazon.ion.IonException
import com.amazon.ion.IonType
import com.amazon.ion.SystemSymbols
import com.amazon.ion.bytecode.util.BytecodeBuffer
import com.amazon.ion.bytecode.util.ConstantPool
import com.amazon.ion.bytecode.util.StringPool
import com.amazon.ion.impl.ArrayBackedLstSnapshot
import com.amazon.ion.ion_1_1.MacroImpl
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings

/**
 * TODO:
 *   Write more documentation.
 *   Implement remaining stubbed out methods.
 *   Do we need some way to "garbage collect" from the constant pool?
 *
 * Notes:
 *
 * Terminology:
 *  - "effective" symbol/macro table
 *  - "spare" symbol/macro table -- essentially we have an object pool with size 1, allowing us to modify the inactive
 *    tables and then swap them for the active tables once the changes are complete.
 *
 * It is never safe to remove or modify any existing data in the effective tables. It is safe to append data to those
 * tables for an `add_symbols`, `add_macros`, or `use` directive (as long as the active encoding modules are just `$ion` and `_`).
 */
internal class EncodingContextManager {

    companion object {
        val SYSTEM_SYMBOLS = arrayOf(
            null,
            "\$ion",
            "\$ion_1_0",
            "\$ion_symbol_table",
            "name",
            "version",
            "imports",
            "symbols",
            "max_id",
            "\$ion_shared_symbol_table",
        )
    }

    class TableSet {
        @JvmField var macroBytecode = BytecodeBuffer()
        @JvmField var macroOffsets = BytecodeBuffer()
        @JvmField var macroNames = ConstantPool()
        @JvmField var symbols = StringPool().apply { SYSTEM_SYMBOLS.forEach { add(it) } }
        @JvmField var constants = ConstantPool()

        fun reset() {
            macroBytecode.clear()
            macroOffsets.clear()
            macroNames.clear()
            symbols.truncate(SystemSymbols.ION_1_0_MAX_ID + 1)
            constants.clear()
        }
    }

    private class Module(
        val symbols: Array<String>,
        val macros: Array<MacroImpl>,
        val macroNames: Array<String?>
    )

    // Tracks only modules _other_ than the system module and default module
    private val additionalAvailableModules = mutableMapOf<String, Module>()
    // Tracks only modules _other_ than the system module and default module
    private var additionalActiveModules = mutableListOf<Module>()

    private val effectiveTables = TableSet()
    // TODO(simplification): we might not need the spare tables for macros because
    //  macros are already evaluated before we get to this point.
    private val spareTables = TableSet()

    @SuppressFBWarnings("IE_EXPOSE_REP", justification = "array is accessible for performance")
    fun getEffectiveMacroTableBytecode(): IntArray = effectiveTables.macroBytecode.unsafeGetArray()
    @SuppressFBWarnings("IE_EXPOSE_REP", justification = "array is accessible for performance")
    fun getEffectiveMacroTableOffsets(): IntArray = effectiveTables.macroOffsets.unsafeGetArray()
    @SuppressFBWarnings("IE_EXPOSE_REP", justification = "array is accessible for performance")
    fun getEffectiveSymbolTable(): Array<String?> = effectiveTables.symbols.unsafeGetArray()
    @SuppressFBWarnings("IE_EXPOSE_REP", justification = "array is accessible for performance")
    fun getEffectiveConstantPool(): Array<Any?> = effectiveTables.constants.unsafeGetArray()

    fun getLstSnapshot() = ArrayBackedLstSnapshot(effectiveTables.symbols)

    /** Called when encountering an IVM */
    fun reset() {
        additionalActiveModules.clear()
        additionalAvailableModules.clear()
        effectiveTables.reset()
    }

    /**
     * The [BytecodeIonReader] should be positioned in the directive, but not on the first value yet.
     * When this method returns, the [BytecodeIonReader] will be positioned at the end of the directive, but not stepped out.
     */
    fun readSetSymbolsDirective(reader: BytecodeIonReader) {
        if (additionalActiveModules.isNotEmpty()) {
            readSetSymbolsWithActiveModules(reader)
            return
        }

        val symbols = spareTables.symbols
        symbols.truncate(SystemSymbols.ION_1_0_MAX_ID + 1)
        while (true) {
            val s = when (reader.next()) {
                IonType.SYMBOL,
                IonType.STRING -> reader.stringValue()
                null -> break
                else -> throw IonException("Expected text; found ${reader.type}")
            }
            symbols.add(s)
        }

        // Swap the effective and spare symbol tables
        spareTables.symbols = effectiveTables.symbols
        effectiveTables.symbols = symbols
    }

    private fun readSetSymbolsWithActiveModules(reader: BytecodeIonReader) {
        // rebuildEffectiveSymbolTable(updateReaderSymbolTable)
        TODO()
    }

    /**
     * The [BytecodeIonReader] should be positioned in the directive, but not on the first value yet.
     * When this method returns, the [BytecodeIonReader] will be positioned at the end of the directive, but not stepped out.
     */
    fun readAddSymbols(reader: BytecodeIonReader) {
        if (additionalActiveModules.isNotEmpty()) {
            readAddSymbolsWithActiveModules(reader)
            return
        }

        val symbols = effectiveTables.symbols
        while (true) {
            val s = when (reader.next()) {
                IonType.SYMBOL,
                IonType.STRING -> reader.stringValue()
                null -> break
                else -> throw IonException("Expected text; found ${reader.type}")
            }
            symbols.add(s)
        }
        // This unchecked cast is safe because we have only ever put strings into this array.
        val symbolsArray = symbols.unsafeGetArray() as Array<*>
    }

    private fun readAddSymbolsWithActiveModules(reader: BytecodeIonReader) {
        // rebuildEffectiveSymbolTable(updateReaderSymbolTable)
        TODO()
    }

    /**
     * The [BytecodeIonReader] should be positioned in the directive, but not on the first value yet.
     * When this method returns, the [BytecodeIonReader] will be positioned at the end of the directive, but not stepped out.
     */
    fun readSetMacrosDirective(reader: BytecodeIonReader) {
        if (additionalActiveModules.isNotEmpty()) {
            readSetMacrosWithActiveModules(reader)
            return
        }

        val spare = spareTables
        spare.reset()
        val newMacroNames = spare.macroNames
        val newMacroBytecode = spare.macroBytecode
        val newMacroOffsets = spare.macroOffsets
        newMacroOffsets.add(0)

        // TODO: We need some way to "garbage collect" from the constant pool.
        while (true) {
            when (reader.next()) {
                IonType.SEXP -> {
                    val name = readMacroDeclaration(reader, newMacroBytecode)
                    newMacroNames.add(name)
                    newMacroOffsets.add(newMacroBytecode.size())
                }
                null -> break
                else -> throw IonException("Expected macro declaration (s-expression); found ${reader.type}")
            }
        }

        val effective = effectiveTables

        spare.macroBytecode = effective.macroBytecode
        spare.macroNames = effective.macroNames
        spare.macroOffsets = effective.macroOffsets
        effective.macroBytecode = newMacroBytecode
        effective.macroNames = newMacroNames
        effective.macroOffsets = newMacroOffsets
    }

    private fun readSetMacrosWithActiveModules(reader: BytecodeIonReader) {
        // rebuildEffectiveMacroTable(updateReaderMacroTable)
        TODO()
    }

    /**
     * The [BytecodeIonReader] should be positioned in the directive, but not on the first value yet.
     * When this method returns, the [BytecodeIonReader] will be positioned at the end of the directive, but not stepped out.
     */
    fun readAddMacrosDirective(reader: BytecodeIonReader) {
        if (additionalActiveModules.isNotEmpty()) {
            readAddMacrosWithActiveModules(reader)
            return
        }

        val effective = effectiveTables
        val newMacroNames = effective.macroNames
        val newMacroBytecode = effective.macroBytecode
        val newMacroOffsets = effective.macroOffsets

        while (true) {
            when (reader.next()) {
                IonType.SEXP -> {
                    val name = readMacroDeclaration(reader, newMacroBytecode)
                    newMacroNames.add(name) // TODO: Check for name conflicts
                    newMacroOffsets.add(newMacroBytecode.size())
                }
                null -> break
                else -> throw IonException("Expected macro declaration (s-expression); found ${reader.type}")
            }
        }
    }

    private fun readAddMacrosWithActiveModules(reader: BytecodeIonReader) {
        // rebuildEffectiveMacroTable(updateReaderMacroTable)
        TODO()
    }

    /**
     * Must be positioned on, but not stepped into, the macro declaration s-expression.
     */
    private fun readMacroDeclaration(reader: BytecodeIonReader, dest: BytecodeBuffer): String? {
        // Step into the S-Expression
        reader.stepIn()
        val macroNameIonType = reader.next()
        if (macroNameIonType != IonType.SYMBOL && macroNameIonType != IonType.NULL) throw IonException("Not a valid macro name.")
        val macroName = if (reader.isNullValue) null else reader.stringValue()
        // TODO: Validate the macro name
        reader.copyMacroDefinitionBytecode(dest)
        return macroName
    }

    /**
     * The [BytecodeIonReader] should be positioned in the directive, but not on the first value yet.
     * When this method returns, the [BytecodeIonReader] will be positioned at the end of the directive, but not stepped out.
     */
    fun readUseDirective(reader: BytecodeIonReader) {
        TODO("Shared symbol tables and shared modules not supported yet.")
    }

    /**
     * The [BytecodeIonReader] should be positioned in the directive, but not on the first value yet.
     * When this method returns, the [BytecodeIonReader] will be positioned at the end of the directive, but not stepped out.
     */
    fun readModuleDirective(reader: BytecodeIonReader) {
        TODO("Module definitions not supported yet.")
    }

    /**
     * The [BytecodeIonReader] should be positioned in the directive, but not on the first value yet.
     * When this method returns, the [BytecodeIonReader] will be positioned at the end of the directive, but not stepped out.
     */
    fun readImportDirective(reader: BytecodeIonReader) {
        TODO("Shared symbol tables and shared modules not supported yet.")
    }

    /**
     * The [BytecodeIonReader] should be positioned in the directive, but not on the first value yet.
     * When this method returns, the [BytecodeIonReader] will be positioned at the end of the directive, but not stepped out.
     *
     * Content should be a list of module names, as symbols.
     */
    fun readEncodingDirective(reader: BytecodeIonReader) {
        val newEncodingSequence = mutableListOf<Module>()
        while (true) {
            when (reader.next()) {
                IonType.SYMBOL -> {
                    val moduleName = reader.stringValue()!!
                    val module = additionalAvailableModules[moduleName] ?: throw IonException("No module binding for name $moduleName")
                    newEncodingSequence.add(module)
                }
                null -> break
                else -> throw IonException("Expected a module name (symbol), but found ${reader.type}")
            }
        }
        if (newEncodingSequence != additionalActiveModules) {
            additionalActiveModules = newEncodingSequence
            rebuildEffectiveSymbolTable()
            rebuildEffectiveMacroTable()
        }
    }

    /**
     * Rebuilds the effective symbol table using all the active encoding modules
     */
    private fun rebuildEffectiveSymbolTable() {
        val newEffectiveSymbolTable = spareTables.symbols
        newEffectiveSymbolTable.truncate(SystemSymbols.ION_1_0_MAX_ID + 1)
        // TODO: Make this more efficient with an array copy operation.
        additionalActiveModules.forEach { m -> m.symbols.forEach { newEffectiveSymbolTable.add(it) } }

        spareTables.symbols = effectiveTables.symbols
        effectiveTables.symbols = newEffectiveSymbolTable
    }

    private fun rebuildEffectiveMacroTable() {
        TODO()
    }
}
