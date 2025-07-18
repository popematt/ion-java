package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.io.Closeable

interface ITemplateResourcePool {
    fun getSequence(args: ArgumentBytecode, bytecode: IntArray, start: Int, constantPool: Array<Any?>): TemplateReaderImpl
    fun returnSequence(sequence: TemplateReaderImpl)
    fun getStruct(args: ArgumentBytecode, bytecode: IntArray, start: Int, constantPool: Array<Any?>): TemplateStructReader
    fun returnStruct(struct: TemplateStructReader)
    fun getAnnotations(annotationSymbols: Array<String?>): AnnotationIterator
    fun returnAnnotations(annotations: AnnotationIterator)
}

class TemplateResourcePool private constructor(): Closeable, ITemplateResourcePool {

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
    val templateReaders = ArrayList<TemplateReaderImpl>()
    @JvmField
    val templateStructReaders = ArrayList<TemplateStructReader>()
    @JvmField
    val annotationIterators = ArrayList<AnnotationIterator>()

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
        val reader = templateReaders.removeLastOrNull() ?: TemplateReaderImpl(this)
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

        val firstArg = getSequence(arguments, arguments.getArgument(0), 0, arguments.constantPool()) as TemplateReaderImpl
        // TODO: Add a cheap way to restart the sequence
        // TODO: Check to see that the macro evaluates to something.

        if (firstArg.nextToken() != TokenTypeConst.END) {
            firstArg.rewind()
            return firstArg
        } else {
            firstArg.close()
            return getSequence(arguments, arguments.getArgument(1), 0, arguments.constantPool())
        }
    }


    override fun getSequence(args: ArgumentBytecode, bytecode: IntArray, start: Int, constantPool: Array<Any?>): TemplateReaderImpl {
        val reader = templateReaders.removeLastOrNull() ?: TemplateReaderImpl(this)
        reader.init(bytecode, constantPool, args, isArgumentOwner = false)
        reader.isStruct = false
        reader.i = start
        return reader
    }

    override fun getStruct(args: ArgumentBytecode, bytecode: IntArray, start: Int, constantPool: Array<Any?>): TemplateStructReader {
        val reader = templateStructReaders.removeLastOrNull()
            ?: TemplateStructReader(this)
        reader.init(bytecode, constantPool, args, isArgumentOwner = false)
        reader.isStruct = true
        reader.i = start
        return reader
    }

    override fun getAnnotations(annotationSymbols: Array<String?>): AnnotationIterator {
        val reader = annotationIterators.removeLastOrNull() as TemplateAnnotationIteratorImpl?
        if (reader != null) {
            reader.init(annotationSymbols)
            return reader
        } else {
            return TemplateAnnotationIteratorImpl(annotationSymbols, this)
        }
    }

    override fun returnAnnotations(annotations: AnnotationIterator) {
        annotationIterators.add(annotations)
    }

    override fun returnSequence(sequence: TemplateReaderImpl) {
        templateReaders.add(sequence)
    }
    override fun returnStruct(struct: TemplateStructReader) {
        templateStructReaders.add(struct)
    }

    override fun close() {
        returnInstance(this)
    }
}
