package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.IonException
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.TemplateBodyExpressionModel

/**
 * Reads the raw E-expression arguments
 */
class TemplateArgumentReaderImpl(
    pool: TemplateResourcePool,
    source: Array<TemplateBodyExpressionModel>,
    argumentReader: ArgumentReader,
    isArgumentOwner: Boolean,
): TemplateReaderBase(pool, source, argumentReader, isArgumentOwner), ArgumentReader {

    override fun toString(): String {
        return "TemplateArgumentReaderImpl(signature=${signature.contentToString()}, source=${source.contentToString()})"
    }

    override var signature: Array<Macro.Parameter> = emptyArray()
        private set

    override fun returnToPool() {
        pool.arguments.add(this)
    }

    fun initArgs(signature: Array<Macro.Parameter>) {
        this.signature = signature
    }

    override fun seekToBeforeArgument(signatureIndex: Int) {
        if (signatureIndex >= signature.size) throw IllegalStateException("Not in the signature")
        i = signatureIndex
        currentExpression = null
    }

    override fun seekToArgument(signatureIndex: Int): Int {
        seekToBeforeArgument(signatureIndex)
        return nextToken()
    }

    override fun nextToken(): Int {
        if (i >= signature.size) {
            return TokenTypeConst.END
        }
        // TODO: Consider modifying the template source code so that we always have an expression for every parameter,
        //       And we can eliminate this check (and this method override) entirely.
        if (i >= source.size) {
            return TokenTypeConst.ABSENT_ARGUMENT
        }
        return super.nextToken()
    }
}
