package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.SystemMacro
import com.amazon.ion.v3.impl_1_1.binary.*

/**
 * This takes ownership of the ArgumentReader and closes it when this evaluator is closed.
 */
fun startMacroEvaluation(macro: MacroV2, arguments: ArgumentBytecode, resourcePool: ResourcePool, symbolTable: Array<String?>, macroTable: Array<MacroV2>): ValueReader {
    return when (macro.systemAddress) {
//            SystemMacro.VALUES_ADDRESS -> {
//                getVariable(arguments, 0, isArgumentOwner = true)
//            }
        // All system macros that produce system values are evaluated using templates because
        // the hard-coded implementation only applies at the top level of the stream, and is
        // handled elsewhere
        SystemMacro.NONE_ADDRESS -> {
            // arguments.close()
            // TODO? Make sure that there are no args?
            NoneReader
        }
        SystemMacro.META_ADDRESS -> {
            // arguments.close()
            NoneReader
        }
//        SystemMacro.DEFAULT_ADDRESS -> invokeDefault(arguments)
//            SystemMacro.IF_NONE_ADDRESS -> invokeIfNone(arguments)
//            SystemMacro.IF_SOME_ADDRESS -> invokeIfSome(arguments)
        else -> {
            if (macro.body != null) {
                resourcePool.getSequence(arguments, macro.bytecode, 0, macro.constants, symbolTable, macroTable)
            } else {
                TODO("System macro with hard coded implementation: $macro")
            }
        }
    }
}
