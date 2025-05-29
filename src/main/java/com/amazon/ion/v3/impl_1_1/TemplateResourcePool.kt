package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.SymbolToken
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import java.io.Closeable

class TemplateResourcePool private constructor(): Closeable {

    companion object {
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
    private class TemplateInvocationInfoImpl(
        override var source: List<Expression.TemplateBodyExpression>,
        override var signature: List<Macro.Parameter>,
        override var arguments: ArgumentReader,
    ): TemplateInvocationInfo

    val invocations = ArrayList<MacroInvocationReader>(8)
    val structs = ArrayList<TemplateStructReaderImpl>(8)
    val sequences = ArrayList<TemplateSequenceReaderImpl>(8)
    val annotations = ArrayList<AnnotationIterator>(8)
    val variables = ArrayList<TemplateVariableReaderImpl>(8)
    val arguments = ArrayList<TemplateArgumentReaderImpl>()

    fun startEvaluation(macro: Macro, arguments: ArgumentReader): MacroInvocationReader {
        if (macro is TemplateMacro) {
            val reader = invocations.removeLastOrNull()
            if (reader != null) {
                reader.init(TemplateInvocationInfoImpl(macro.body, macro.signature, arguments), 0, macro.body.size)
                return reader
            } else {
                return MacroInvocationReader(this, TemplateInvocationInfoImpl(macro.body, macro.signature, arguments), 0, macro.body.size)
            }
        } else if (macro is SystemMacro) {
            if (macro.body != null) {
                val reader = invocations.removeLastOrNull()
                if (reader != null) {
                    reader.init(TemplateInvocationInfoImpl(macro.body!!, macro.signature, arguments), 0, macro.body!!.size)
                    return reader
                } else {
                    return MacroInvocationReader(this, TemplateInvocationInfoImpl(macro.body!!, macro.signature, arguments), 0, macro.body!!.size)
                }
            } else {
                // TODO:
                //  * `values` should be elided
                //  * `none` should be replaced with an empty stream of some sort

                TODO("System macros with hard coded implementations")
            }
        } else {
            TODO("unreachable")
        }
    }

    fun getSequence(info: TemplateInvocationInfo, startInclusive: Int, endExclusive: Int): TemplateSequenceReaderImpl {
        val reader = sequences.removeLastOrNull()
        if (reader != null) {
            reader.init(info, startInclusive, endExclusive)
            return reader
        } else {
            return TemplateSequenceReaderImpl(this, info, startInclusive, endExclusive)
        }
    }

    fun getStruct(info: TemplateInvocationInfo, startInclusive: Int, endExclusive: Int): TemplateStructReaderImpl {
        val reader = structs.removeLastOrNull()
        if (reader != null) {
            reader.init(info, startInclusive, endExclusive)
            return reader
        } else {
            return TemplateStructReaderImpl(this, info, startInclusive, endExclusive)
        }
    }

    fun getAnnotations(annotationSymbols: List<SymbolToken>): AnnotationIterator {
        val reader = annotations.removeLastOrNull() as TemplateAnnotationIteratorImpl?
        if (reader != null) {
            reader.init(annotationSymbols)
            return reader
        } else {
            return TemplateAnnotationIteratorImpl(annotationSymbols, this)
        }
    }

    fun getVariable(argReader: ArgumentReader, index: Int): TemplateVariableReaderImpl {
        val reader = variables.removeLastOrNull()
        if (reader != null) {
            reader.init(index, argReader)
            return reader
        } else {
            return TemplateVariableReaderImpl(this, index, argReader)
        }
    }

    override fun close() {
        returnInstance(this)
    }
}
