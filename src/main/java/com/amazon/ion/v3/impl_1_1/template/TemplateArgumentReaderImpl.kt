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
        return "TemplateArgumentReaderImpl(signature=$signature, argumentIndices=${argumentIndices.contentToString()}, source=${info.source})"
    }

    override fun reinitState() {
        // TODO: See if we can eliminate this.
        signature = emptyList()
        nextParameterIndex = 0
//        presence = ByteArray(8)
//        presence = 0
//        argumentIndices = IntArray(8)
    }

    override var signature: List<Macro.Parameter> = emptyList()
        private set

    // private var presence = ByteArray(8)
//    private var presence = 0
    // The position of a "not present" argument is always the first byte after the last byte of the previous argument
    // position of -1 indicates not present.
    private var argumentIndices = IntArray(signature.size)

    // The position in the _signature_
    private var nextParameterIndex: Short = 0


//    private var e: String = "unknown"
    override fun returnToPool() {
//        if (this in pool.arguments) {
//            System.err.println("Previously closed at: $e")
//            throw IllegalStateException("Already closed: $this")
//        }
        // e = Exception().stackTraceToString()
        pool.arguments.add(this)
    }

    fun initArgs(signature: List<Macro.Parameter>) {
        this.signature = signature
        this.nextParameterIndex = 0
        val signatureSize = signature.size
        // Make sure we have enough space in our arrays
        if (signatureSize > argumentIndices.size) {
            argumentIndices = IntArray(signature.size)
            // Max signature size is 32? That might be okay. We could have another implementation
            // for handling larger signature sizes that uses a byte array even if that is a little slower.
//            presence = 0 // ByteArray(signature.size)
        }

        // Local references
        // var presence = presence
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
                val currentExpression = source[position]
                if (currentExpression is Expression.HasStartAndEnd) {
                    val argEndExclusive = currentExpression.endExclusive
                    if (currentExpression is Expression.ExpressionGroup) {
                        if (argEndExclusive == position + 1) {
                            // Set the presence bits to 0
                            // It's actually a no-op
                            // presence = presence or (0L shl (i * 2))
                            argumentIndices[i] = -1
                        }
                    }
                    position = argEndExclusive
                } else {
                    position++
                }
            } else {
                // Presence = 0
                // presence = presence or (0L shl (i * 2))
                argumentIndices[i] = -1
            }
        }

        // Using a byteArray instead of a long for presence:
//        for (i in 0 until signatureSize) {
//            if (position < endExclusive) {
//                argumentIndices[i] = position
//                val currentExpression = source[position]
//                position++
//                if (currentExpression is Expression.HasStartAndEnd) {
//                    val argEndExclusive = currentExpression.endExclusive
//                    if (currentExpression is Expression.ExpressionGroup) {
//                        if (argEndExclusive == position) {
//                            presence[i] = 0
//                        } else {
//                            presence[i] = 2
//                        }
//                    } else {
//                        presence[i] = 1
//                    }
//                    position = argEndExclusive
//                } else {
//                    presence[i] = 1
//                }
//            } else {
//                presence[i] = 0
//                argumentIndices[i] = endExclusive
//            }
//        }


//        println("Setting up template arg reader... $id")
//        println(signature)
//        println(presence.contentToString())
//        println(argumentIndices.contentToString())
//        println(info.source.mapIndexed(::Pair).joinToString("\n") { (i, it) -> "    $i = $it" })

    }

    override fun seekToBeforeArgument(signatureIndex: Int) {
        if (signatureIndex >= signature.size) throw IllegalStateException("Not in the signature")
//        println("[$id] Seeking to before template arg $signatureIndex of $signature")
//         println("${signature[signatureIndex]}($signatureIndex)")
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
