package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.IonException
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.Expression.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

/**
 * Reads the raw E-expression arguments
 */
class TemplateArgumentReaderImpl(
    pool: TemplateResourcePool,
    info: TemplateResourcePool.TemplateInvocationInfo,
    startInclusive: Int,
    endExclusive: Int,
): TemplateReaderBase(pool, info, startInclusive, endExclusive), ArgumentReader {

    private var signature: List<Macro.Parameter> = emptyList()

    private var presence = IntArray(8)
    // The position of a "not present" argument is always the first byte after the last byte of the previous argument
    private var argumentIndices = IntArray(8)

    // The position in the _signature_
    private var currentParameterIndex = 0

    override fun close() {
        pool.arguments.add(this)
    }

    fun initArgs(signature: List<Macro.Parameter>) {
        this.signature = signature
        this.currentParameterIndex = 0
        // Make sure we have enough space in our arrays
        if (signature.size > argumentIndices.size) {
            argumentIndices = IntArray(signature.size)
            presence = IntArray(signature.size)
        }
        // TODO: Make this lazy or precompute this
        var position = startInclusive
        for (i in signature.indices) {
            if (i < endExclusive) {
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
                    } else {
                        presence[i] = 1
                    }
                    position = argEndExclusive
                }
            } else {
                presence[i] = 0
                argumentIndices[i] = endExclusive
            }
        }
    }

    override fun seekToArgument(signatureIndex: Int): Int {
        this.i = argumentIndices[signatureIndex]
        val tokenType = when (presence[signatureIndex]) {
            2 -> {
                currentExpression = info.source[i]
                currentParameterIndex = signatureIndex + 1
                TokenTypeConst.EXPRESSION_GROUP
            }
            1 -> {
                super.nextToken()
            }
            0 -> {
                currentExpression = info.source[i]
                currentParameterIndex = signatureIndex + 1
                TokenTypeConst.EMPTY_ARGUMENT
            }
            else -> throw IonException("Invalid presence bits for parameter $signatureIndex; was ${presence[signatureIndex]}")
        }
        return tokenType
    }

    override fun nextToken(): Int {
        val cpIndex = currentParameterIndex
        currentParameterIndex++
        if (cpIndex >= signature.size) {
            return TokenTypeConst.END
        }
        return seekToArgument(cpIndex)
    }

    override fun expressionGroup(): SequenceReader {
        val expr = takeCurrentExpression<ExpressionGroup>()
        return pool.getSequence(info, expr.startInclusive, expr.endExclusive)
    }

    override fun skip() {
        // Nothing to do?
    }
}
