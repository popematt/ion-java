package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*

/**
 * Reads the raw E-expression arguments
 */
class TemplateArgumentReaderImpl(pool: TemplateResourcePool): TemplateReaderBase(pool), ArgumentReader {

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
        TODO()
    }

    override fun seekToArgument(signatureIndex: Int): Int {
        seekToBeforeArgument(signatureIndex)
        return nextToken()
    }
}
