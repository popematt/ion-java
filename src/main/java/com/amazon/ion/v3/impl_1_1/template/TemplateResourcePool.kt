package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.SymbolToken
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.impl.macro.TemplateMacro
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.ArgumentReader
import com.amazon.ion.v3.TokenTypeConst
import com.amazon.ion.v3.ValueReader
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
    fun startEvaluation(macro: MacroV2, arguments: ArgumentReader): ValueReader {
        return when (macro.systemAddress) {
            SystemMacro.VALUES_ADDRESS -> {
                getVariable(arguments, 0, isArgumentOwner = true)
            }
            // All system macros that produce system values are evaluated using templates because
            // the hard-coded implementation only applies at the top level of the stream, and is
            // handled elsewhere
            SystemMacro.NONE_ADDRESS -> {
                arguments.close()
                // TODO? Make sure that there are no args?
                NoneReader
            }
            SystemMacro.META_ADDRESS -> {
                arguments.close()
                NoneReader
            }
            SystemMacro.DEFAULT_ADDRESS -> invokeDefault(arguments)
            SystemMacro.IF_NONE_ADDRESS -> invokeIfNone(arguments)
            SystemMacro.IF_SOME_ADDRESS -> invokeIfSome(arguments)
            else -> {
                if (macro.body != null) {
                    invokeTemplate(macro, arguments)
                } else {
                    TODO("System macro with hard coded implementation: $macro")
                }
            }
        }
    }

    private fun invokeTemplate(macro: MacroV2, arguments: ArgumentReader): ValueReader {
        val reader = sequences.removeLastOrNull()
        if (reader != null) {
            reader.init(
                macro.body!!,
                arguments,
                isArgumentOwner = true
            )
            return reader
        } else {
            return TemplateSequenceReaderImpl(
                this,
                macro.body!!,
                arguments,
                isArgumentOwner = true
            )
        }
    }

    // Default could be cheaper if it was default values in the signature, I think.

    private fun invokeDefault(arguments: ArgumentReader): ValueReader {
//        println("Invoking Default with arguments: $arguments")
        arguments.seekToBeforeArgument(0)
        val firstArg = arguments.nextToken()
        // TODO: This doesn't properly handle the case where there's an empty expression group in binary
        //       Same thing for all of the `if` macros

//        println("<><><><><><><><><><><><><> Checking first arg for 'default': ${TokenTypeConst(arguments.currentToken())}")

        // TODO: Actually evaluate the first argument until we've found the first value token or else END
        if (firstArg == TokenTypeConst.ABSENT_ARGUMENT) {
//            println("<><><><><><><><><><><><><> Returning the second argument for 'default'")
//            println(arguments)
            return getVariable(arguments, 1, isArgumentOwner = true)
        } else {
            return getVariable(arguments, 0, isArgumentOwner = true)
        }
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


    fun getSequence(args: ArgumentReader, source: Array<TemplateBodyExpressionModel>): TemplateSequenceReaderImpl {
        val reader = sequences.removeLastOrNull()
        if (reader != null) {
            reader.init(source, args, isArgumentOwner = false)
            return reader
        } else {
            return TemplateSequenceReaderImpl(this, source, args, isArgumentOwner = false)
        }
    }

    fun getStruct(arguments: ArgumentReader, source: Array<TemplateBodyExpressionModel>): TemplateStructReaderImpl {
        val reader = structs.removeLastOrNull()
        if (reader != null) {
            reader.init(source, arguments, isArgumentOwner = false)
            return reader
        } else {
            return TemplateStructReaderImpl(this, source, arguments, isArgumentOwner = false)
        }
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

    fun getArguments(args: ArgumentReader, signature: Array<Macro.Parameter>, source: Array<TemplateBodyExpressionModel>): ArgumentReader {
        val argReader = arguments.removeLastOrNull()
            ?.apply { init(source, args, isArgumentOwner = false) }
            ?: TemplateArgumentReaderImpl(this, source, args, isArgumentOwner = false)
        argReader.initArgs(signature)
        return argReader
    }

    override fun close() {
        returnInstance(this)
    }
}
