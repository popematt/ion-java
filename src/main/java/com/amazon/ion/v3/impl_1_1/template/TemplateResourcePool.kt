package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.SymbolToken
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.impl.macro.SystemMacro
import com.amazon.ion.impl.macro.TemplateMacro
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.ArgumentReader
import com.amazon.ion.v3.TokenTypeConst
import com.amazon.ion.v3.ValueReader
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

    interface TemplateInvocationInfo {
        val source: List<Expression.TemplateBodyExpression>
        val signature: List<Macro.Parameter>
        val arguments: ArgumentReader
    }
    internal class TemplateInvocationInfoImpl(
        override var source: List<Expression.TemplateBodyExpression>,
        override var signature: List<Macro.Parameter>,
        override var arguments: ArgumentReader,
    ): TemplateInvocationInfo

    val structs = ArrayList<TemplateStructReaderImpl>()
    val sequences = ArrayList<TemplateSequenceReaderImpl>()
    val annotations = ArrayList<AnnotationIterator>()
    val variables = ArrayList<TemplateVariableReaderImpl>()
    val arguments = ArrayList<TemplateArgumentReaderImpl>()

    /**
     * This takes ownership of the ArgumentReader and closes it when this evaluator is closed.
     */
    fun startEvaluation(macro: Macro, arguments: ArgumentReader): ValueReader {
        return when (macro) {
            is TemplateMacro -> return invokeTemplate(macro, arguments)
            is SystemMacro -> when (macro) {
                // All system macros that produce system values are evaluated using templates because
                // the hard-coded implementation only applies at the top level of the stream, and is
                // handled elsewhere
                SystemMacro.Values -> getVariable(arguments, 0, isArgumentOwner = true)
                SystemMacro.None -> {
                    arguments.close()
                    // TODO? Make sure that there are no args?
                    NoneReader
                }
                SystemMacro.Meta -> {
                    arguments.close()
                    NoneReader
                }
                SystemMacro.Default -> invokeDefault(arguments)
                SystemMacro.IfNone -> invokeIfNone(arguments)
                SystemMacro.IfSome -> invokeIfSome(arguments)

                // TODO: Remove null check once all hard coded implementations are complete
                else -> if (macro.body != null) {
                    invokeTemplate(macro, arguments)
                } else {
                    // TODO:
                    //  * `values` should be elided
                    //  * `none` should be replaced with an empty stream of some sort
                    TODO("System macros with hard coded implementations: $macro")
                }
            }
        }
    }

    private fun invokeTemplate(macro: Macro, arguments: ArgumentReader): ValueReader {
        val reader = sequences.removeLastOrNull()
        if (reader != null) {
            reader.init(
                TemplateInvocationInfoImpl(macro.body!!, macro.signature, arguments),
                0,
                macro.body!!.size,
                isArgumentOwner = true
            )
            return reader
        } else {
            return TemplateSequenceReaderImpl(
                this,
                TemplateInvocationInfoImpl(macro.body!!, macro.signature, arguments),
                0,
                macro.body!!.size,
                isArgumentOwner = true
            )
        }
    }

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


    fun getSequence(info: TemplateInvocationInfo, startInclusive: Int, endExclusive: Int): TemplateSequenceReaderImpl {
        val reader = sequences.removeLastOrNull()
        if (reader != null) {
            reader.init(info, startInclusive, endExclusive, isArgumentOwner = false)
            return reader
        } else {
            return TemplateSequenceReaderImpl(this, info, startInclusive, endExclusive, isArgumentOwner = false)
        }
    }

    fun getStruct(info: TemplateInvocationInfo, startInclusive: Int, endExclusive: Int): TemplateStructReaderImpl {
        val reader = structs.removeLastOrNull()
        if (reader != null) {
            reader.init(info, startInclusive, endExclusive, isArgumentOwner = false)
            return reader
        } else {
            return TemplateStructReaderImpl(this, info, startInclusive, endExclusive, isArgumentOwner = false, n++)
        }
    }

    private var n = 0

    fun getAnnotations(annotationSymbols: List<SymbolToken>): AnnotationIterator {
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

    fun getArguments(info: TemplateInvocationInfo, signature: List<Macro.Parameter>, startInclusive: Int, endExclusive: Int): ArgumentReader {
        val argReader = arguments.removeLastOrNull()
            ?.apply { init(info, startInclusive, endExclusive, isArgumentOwner = false) }
            ?: TemplateArgumentReaderImpl(this, info, startInclusive, endExclusive, isArgumentOwner = false)
        argReader.initArgs(signature)
        return argReader
    }

    override fun close() {
        returnInstance(this)
    }
}
