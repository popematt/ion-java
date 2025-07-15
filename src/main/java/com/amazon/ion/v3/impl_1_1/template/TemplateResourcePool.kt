package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.SymbolToken
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.impl.macro.TemplateMacro
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.io.Closeable

class TemplateResourcePool private constructor(): Closeable {

    companion object {
        // TODO: See if it's cheaper to just allocate a new one.

        @JvmStatic
        private val cached: ThreadLocal<TemplateResourcePool?> = ThreadLocal.withInitial { null }

        @JvmStatic
        fun getInstance(): TemplateResourcePool {
            val instance = cached.get() ?: TemplateResourcePool()
            cached.set(null)
            return instance
        }

        @JvmStatic
        private fun returnInstance(instance: TemplateResourcePool) {
            if (cached.get() == null) {
                cached.set(instance)
            }
        }
    }

    @JvmField
    val structs = ArrayList<TemplateStructReaderImpl>()
    @JvmField
    val sequences = ArrayList<TemplateSequenceReaderImpl>()
    @JvmField
    val annotations = ArrayList<AnnotationIterator>()
    @JvmField
    val variables = ArrayList<TemplateVariableReaderImpl>()
    @JvmField
    val arguments = ArrayList<TemplateArgumentReaderImpl>()

    /**
     * This takes ownership of the ArgumentReader and closes it when this evaluator is closed.
     */
    fun startEvaluation(macro: MacroV2, arguments: ArgumentBytecode): ValueReader {
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
            SystemMacro.DEFAULT_ADDRESS -> invokeDefault(arguments)
//            SystemMacro.IF_NONE_ADDRESS -> invokeIfNone(arguments)
//            SystemMacro.IF_SOME_ADDRESS -> invokeIfSome(arguments)
            else -> {
                if (macro.body != null) {
                    invokeTemplate(macro, arguments)
                } else {
                    TODO("System macro with hard coded implementation: $macro")
                }
            }
        }
    }

    private fun invokeTemplate(macro: MacroV2, arguments: ArgumentBytecode): ValueReader {
        val reader = sequences.removeLastOrNull() ?: TemplateSequenceReaderImpl(this)
        reader.init(
            macro.bytecode,
            macro.constants,
            arguments,
            isArgumentOwner = true
        )
        return reader
    }

    // Default could be cheaper if it was default values in the signature, I think.

    private fun invokeDefault(arguments: ArgumentBytecode): ValueReader {
//        println("Invoking Default with arguments: $arguments")
        val firstArg = arguments.getArgument(0)
        // TODO: This doesn't properly handle the case where there's an empty expression group in binary
        //       Same thing for all of the `if` macros

        // TODO: Actually evaluate the first argument until we've found the first value token or else END
//        if (firstArg.size > 1) {
//            return getVariable(arguments, 0, isArgumentOwner = true)
//        } else {
//            return getVariable(arguments, 1, isArgumentOwner = true)
//        }
        TODO()
    }

    private fun invokeIfNone(arguments: ArgumentReader): ValueReader {
        arguments.seekToBeforeArgument(0)
        val firstArg = arguments.nextToken()
//        println("Checking first arg for 'if_none': ${TokenTypeConst(arguments.currentToken())}")
        return if (firstArg == TokenTypeConst.ABSENT_ARGUMENT) {
            getVariable(arguments, 1, isArgumentOwner = true)
        } else {
            getVariable(arguments, 2, isArgumentOwner = true)
        }
    }

    fun invokeIfSome(arguments: ArgumentReader): ValueReader {
        arguments.seekToBeforeArgument(0)
        val firstArg = arguments.nextToken()
//        println("Checking first arg for 'if_some': ${TokenTypeConst(arguments.currentToken())}")
        return if (firstArg != TokenTypeConst.ABSENT_ARGUMENT) {
            getVariable(arguments, 1, isArgumentOwner = true)
        } else {
            getVariable(arguments, 2, isArgumentOwner = true)
        }
    }


    fun getSequence(args: ArgumentBytecode, bytecode: IntArray, start: Int, constantPool: Array<Any?>): TemplateSequenceReaderImpl {
        val reader = sequences.removeLastOrNull() ?: TemplateSequenceReaderImpl(this)
        reader.init(bytecode, constantPool, args, isArgumentOwner = false)
        reader.i = start
        return reader
    }

    fun getStruct(arguments: ArgumentBytecode, bytecode: IntArray, constantPool: Array<Any?>, start: Int): TemplateStructReaderImpl {
        val reader = structs.removeLastOrNull()
            ?: TemplateStructReaderImpl(this)
        reader.init(bytecode, constantPool, arguments, isArgumentOwner = false)
        reader.i = start
        return reader
    }

    fun getAnnotations(annotationSymbols: Array<String?>): AnnotationIterator {
        val reader = annotations.removeLastOrNull() as TemplateAnnotationIteratorImpl?
        if (reader != null) {
            reader.init(annotationSymbols)
            return reader
        } else {
            return TemplateAnnotationIteratorImpl(annotationSymbols, this)
        }
    }

    fun getVariable(argReader: ArgumentReader, index: Int, isArgumentOwner: Boolean): TemplateVariableReaderImpl {
//        println("Getting variable: $index")
        val reader = variables.removeLastOrNull()
        if (reader != null) {
            reader.init(index, argReader, isArgumentOwner)
            return reader
        } else {
            return TemplateVariableReaderImpl(this, index, argReader, isArgumentOwner)
        }
    }

    fun getArguments(args: ArgumentReader, signature: Array<Macro.Parameter>, source: Array<TemplateBodyExpressionModel>, tokens: IntArray): ArgumentReader {
        if (signature.isEmpty()) {
            return NoneReader
        }
//        val argReader = arguments.removeLastOrNull()
//            ?.apply { init(source, tokens, args, isArgumentOwner = false) }
//            ?: TemplateArgumentReaderImpl(this, source, tokens, args, isArgumentOwner = false)
//        argReader.initArgs(signature)
        TODO()
    }

    override fun close() {
        returnInstance(this)
    }
}
