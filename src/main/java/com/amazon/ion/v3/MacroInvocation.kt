package com.amazon.ion.v3

import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.*
import com.amazon.ion.v3.impl_1_1.template.*

/**
 * Goal:
 *   This holds the macro definition, a way to iterate over the arguments, and a way to evaluate the macro.
 *
 * When given to user code, the user code must choose exactly one of iterate over the arguments or evaluate the macro (and potentially skip the macro).
 *
 * Ideally, reading the arguments is done lazily so that there isn't going to be double work done.
 */
class MacroInvocation(
    val macro: MacroV2,
    private val arguments: ArgumentBytecode,
    private val pool: ResourcePool,
    private val symbolTable: Array<String?>,
    private val macroTable: Array<MacroV2>,
) {
    fun evaluate(): ValueReader { return startMacroEvaluation(macro, arguments, pool, symbolTable, macroTable) }

    fun iterateArguments(): Iterator<ValueReader> = object : Iterator<ValueReader> {
            private var i = 0
            private val n = arguments.signature.size

            override fun hasNext(): Boolean = i < n

            override fun next(): ValueReader {
                val arguments = arguments
                val argSlice = arguments.getArgumentSlice(i++)
                // TODO: Make this read a slice of the args rather than needing to perform an array copy.
                return pool.getSequence(ArgumentBytecode.NO_ARGS, argSlice.bytecode, argSlice.startIndex, arguments.constantPool(), symbolTable, macroTable)
            }
        }
}
