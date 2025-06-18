package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.IonException
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*

/**
 * Reads the raw E-expression arguments
 */
class TemplateArgumentReaderImpl(
    pool: TemplateResourcePool,
    info: TemplateResourcePool.TemplateInvocationInfo,
    startInclusive: Int,
    endExclusive: Int,
    isArgumentOwner: Boolean,
): TemplateReaderBase(pool, info, startInclusive, endExclusive, isArgumentOwner), ArgumentReader {

    override fun toString(): String {
        return "TemplateArgumentReaderImpl(signature=${signature.contentToString()}, argumentIndices=${argumentIndices.contentToString()}, source=${info.source.contentToString()})"
    }

    override var signature: Array<Macro.Parameter> = emptyArray()
        private set

    // position of -1 indicates not present.
    private var argumentIndices = IntArray(signature.size)

    // The position in the _signature_
    private var nextParameterIndex: Short = 0


    override fun returnToPool() {
        pool.arguments.add(this)
    }

    fun initArgs(signature: Array<Macro.Parameter>) {
        this.signature = signature
        this.nextParameterIndex = 0
        val signatureSize = signature.size
        // Make sure we have enough space in our arrays
        if (signatureSize > argumentIndices.size) {
            argumentIndices = IntArray(signature.size)
        }

        // Local references
        val argumentIndices = argumentIndices
        val info = info
        val source = info.source
        val endExclusive = endExclusive

        // TODO: Make this lazy or precompute this
        //       Actually, this will be rendered mostly obsolete with nested template invocation flattening.
        //       But it might be useful to make it lazy for system macro invocations.
        var position = startInclusive
        for (i in 0 until signatureSize) {
            if (position < endExclusive) {
                argumentIndices[i] = position
                val currentExpression = source[position++]
                if (currentExpression.expressionKind == TokenTypeConst.EXPRESSION_GROUP && currentExpression.length == 0) {
                    argumentIndices[i] = -1
                }
                position += currentExpression.length
            } else {
                argumentIndices[i] = -1
            }
        }
    }

    override fun seekToBeforeArgument(signatureIndex: Int) {
        if (signatureIndex >= signature.size) throw IllegalStateException("Not in the signature")
        nextParameterIndex = signatureIndex.toShort()
        currentExpression = null
    }

    override fun seekToArgument(signatureIndex: Int): Int {
        seekToBeforeArgument(signatureIndex)
        return nextToken()
    }

    override fun nextToken(): Int {
        val currentParameterIndex = nextParameterIndex
        if (currentParameterIndex >= signature.size) {
            return TokenTypeConst.END
        }
        nextParameterIndex++

        val i = argumentIndices[currentParameterIndex.toInt()]
        if (i < 0) {
            currentExpression = null
            return TokenTypeConst.ABSENT_ARGUMENT
        }
        this.i = i
        return super.nextToken()
    }
}
