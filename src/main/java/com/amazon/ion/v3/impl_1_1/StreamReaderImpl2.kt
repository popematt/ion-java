package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.IonException
import com.amazon.ion.SystemSymbols
import com.amazon.ion.impl.SystemSymbols_1_1
import com.amazon.ion.impl.macro.SystemMacro
import com.amazon.ion.v3.SexpReader
import com.amazon.ion.v3.StreamReader
import com.amazon.ion.v3.TokenTypeConst
import com.amazon.ion.v3.impl_1_0.StreamReader_1_0
import java.nio.ByteBuffer

private val ION_1_1_SYMBOL_TABLE = SystemSymbols_1_1.allSymbolTexts().toTypedArray()

class StreamReaderImpl2 internal constructor(
    source: ByteBuffer,
): ValueReaderBase(source, ResourcePool(source.asReadOnlyBuffer())), StreamReader {

    // private var symbolTable = ION_1_1_SYMBOL_TABLE

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

    private fun updateSymbols(newSymbols: MutableList<String?>) {
        val presence = source.getShort().toInt() ushr 8
        when (presence) {
            0 -> {}
            1 -> TODO("Just use an expression group, please!")
            2 -> {
                val length = IntHelper.readFlexUInt(source)
                val start = source.position()
                val symbolsList = if (length == 0) {
                    pool.getDelimitedList(start, source.limit() - start, this)
                } else {
                    source.position(start + length)
                    pool.getList(start, length)
                }
                symbolsList.use { sl -> moduleReader.readSymbolsList(sl, newSymbols) }
            }
            else -> throw IonException("Invalid presence bits for set_symbols macro")
        }
        availableModules["_"]!!.symbols = newSymbols
        // TODO: Fix this to use proper encoding module sequence.
        symbolTable = newSymbols.toTypedArray()
    }

    override tailrec fun nextToken(): Int {
        val token = super.nextToken()

        if (token == TokenTypeConst.EEXP) {
            // TODO: Support other opcodes for invoking these macros
            if (opcode.toInt() == 0xEF) {
                val position = source.position()
                val macroId = source.get(position)
                when (macroId) {
                    SystemMacro.SetSymbols.id -> updateSymbols(ArrayList<String?>().also { it.add(null) })
                    SystemMacro.AddSymbols.id -> updateSymbols(ArrayList<String?>().also { it.addAll(symbolTable) })
                    SystemMacro.SetMacros.id -> TODO()
                    SystemMacro.AddMacros.id -> TODO()
                    SystemMacro.Use.id -> TODO()
                    else -> return token
                }
                return nextToken()
            }
        }
        if (token != TokenTypeConst.ANNOTATIONS) return token

        source.mark()
        val savedTypeId = opcode
        val annotations = annotations()
        annotations.next()
        if (annotations.getText() != SystemSymbols.ION) {
            annotations.close()
            source.reset()
            opcode = savedTypeId
            return TokenTypeConst.ANNOTATIONS
        }
        val maybeSexp = super.nextToken()
        if (maybeSexp != TokenTypeConst.SEXP) {
            annotations.close()
            source.reset()
            opcode = savedTypeId
            return TokenTypeConst.ANNOTATIONS
        }
        // TODO: Also check for `$ion_symbol_table::{ ... }`
        // TODO: Also check for macros that can produce system values.

        // Finally, we know we are on a directive
        sexpValue().use { readDirective(it) }

        // Now, try again to get a token for an application value
        return nextToken()
    }

    private fun readDirective(sexp: SexpReader) {
        // TODO: Handle macros that could be in the directive

        sexp.nextToken()
        val directiveKind = sexp.symbolValue()

        when (directiveKind) {
            "encoding" -> TODO("'encoding' directives not implemented yet")
            "export",
            "module" -> {
                val module = moduleReader.readModule(sexp, availableModules)
                availableModules[module.name] = module
                if (module.name == "_") {
                    println("Updating symbol table. Now contains ${module.symbols.size} symbols")
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
        pool.close()
    }
}
