package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.IonException
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.ArgumentReader
import com.amazon.ion.v3.TokenTypeConst

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
        return "TemplateArgumentReaderImpl(signature=$signature, argumentIndices=${argumentIndices.contentToString()}, source=${info.source})"
    }

    override var signature: List<Macro.Parameter> = emptyList()
        private set

    private var presence = IntArray(8)
    // The position of a "not present" argument is always the first byte after the last byte of the previous argument
    private var argumentIndices = IntArray(8)

    // The position in the _signature_
    private var nextParameterIndex = 0

    override fun returnToPool() {
        pool.arguments.add(this)
    }

    fun initArgs(signature: List<Macro.Parameter>) {
        this.signature = signature
        this.nextParameterIndex = 0
        // Make sure we have enough space in our arrays
        if (signature.size > argumentIndices.size) {
            argumentIndices = IntArray(signature.size)
            presence = IntArray(signature.size)
        }
        // TODO: Make this lazy or precompute this
        var position = startInclusive
        for (i in signature.indices) {
            if (position < endExclusive) {
                argumentIndices[i] = position
                val currentExpression = info.source[position]
                position++
                if (currentExpression is Expression.HasStartAndEnd) {
                    val argEndExclusive = currentExpression.endExclusive
                    if (currentExpression is Expression.ExpressionGroup) {
                        if (argEndExclusive == position) {
                            presence[i] = 0
                        } else {
                            presence[i] = 2
                        }
                        position = argEndExclusive
                    } else {
                        presence[i] = 1
                    }
                } else {
                    presence[i] = 1
                }
            } else {
                presence[i] = 0
                argumentIndices[i] = endExclusive
            }
        }
    }

    override fun seekToBeforeArgument(signatureIndex: Int) {
        if (signatureIndex >= signature.size) throw IllegalStateException("Not in the signature")
//        println("[$id] Seeking to before template arg $signatureIndex of $signature")
        // println("${signature[signatureIndex]}($signatureIndex)")
        nextParameterIndex = signatureIndex
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
//        println("[$id] Reading Template arg ${signature[currentParameterIndex]}($currentParameterIndex)")

        nextParameterIndex++

        this.i = argumentIndices[currentParameterIndex]
        val tokenType = when (presence[currentParameterIndex]) {
            2 -> {
                currentExpression = info.source[i]
                TokenTypeConst.EXPRESSION_GROUP
            }
            1 -> {
                super.nextToken()
            }
            0 -> {
                currentExpression = null
                TokenTypeConst.NOP_EMPTY_ARGUMENT
            }
            else -> throw IonException("Invalid presence bits for parameter $currentParameterIndex; was ${presence[currentParameterIndex]}")
        }
        return tokenType
    }

//    override fun skip() {
//        // Nothing to do?
//    }
}
