package com.amazon.ion.v3.visitor

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.ModuleReader

// TODO: This will probably be better as a reader-driver rather than as a visitor.
// Actually, the way function overrides work, it won't work as a base class, unless we make `onAnnotation` final.
abstract class ApplicationReaderVisitor: VisitingReaderCallback {

    companion object {
        @JvmStatic
        private val ION_1_1_SYSTEM_SYMBOLS = SystemSymbols_1_1.allSymbolTexts()
            .also { it.add(0, null) }
            .toTypedArray()
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
    }

    private var depth = 0
    private var symbolTable = emptyArray<String?>()

    override fun onIVM(major: Int, minor: Int) {
        symbolTable = if (minor == 0) {
            ION_1_0_SYMBOL_TABLE
        } else {
            ION_1_1_SYSTEM_SYMBOLS
        }
        // TODO:
        //  * reset macro table
        //  * clear available modules
    }


    override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback? {
        if (depth == 0) {
            // TODO: See if we can peek() so that we don't have to clone() here
            val ann = annotations.clone()
            ann.next()
            val sid = ann.getSid()
            val annotation = if (sid < 0) ann.getText() else symbolTable[sid]
            if (annotation == "\$ion") {
                TODO()
            }
        }
        return this
    }

    private val moduleReader = ModuleReader()
    private val availableModules = mutableMapOf<String, ModuleReader.Module>()
    init {
        // TODO: also do this when encountering an IVM.
        // TODO: Support macros.
        availableModules.clear()
        availableModules["_"] =
            ModuleReader.Module("_", SystemSymbols_1_1.allSymbolTexts().toMutableList(), mutableListOf())
        availableModules["\$ion"] =
            ModuleReader.Module("\$ion", SystemSymbols_1_1.allSymbolTexts().toMutableList(), mutableListOf())
    }
}
