package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.SymbolToken
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import java.io.Closeable

class TemplateResourcePool: Closeable {

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

    fun startEvaluation(macro: Macro, arguments: ArgumentReader): MacroInvocationReader {
        if (macro is TemplateMacro) {
            val reader = invocations.removeLastOrNull()
            if (reader != null) {
                reader.init(TemplateInvocationInfoImpl(macro.body, macro.signature, arguments), 0, macro.body.size)
                return reader
            } else {
                return MacroInvocationReader(this, TemplateInvocationInfoImpl(macro.body, macro.signature, arguments), 0, macro.body.size)
            }
        } else {
            TODO("System macros")
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
        annotations.clear()
        sequences.clear()
        structs.clear()
    }
}
