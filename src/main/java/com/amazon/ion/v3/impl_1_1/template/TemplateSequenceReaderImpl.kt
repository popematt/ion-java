package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.ListReader
import com.amazon.ion.v3.SexpReader
import com.amazon.ion.v3.ValueReader

class TemplateSequenceReaderImpl(
    pool: TemplateResourcePool,
    info: TemplateResourcePool.TemplateInvocationInfo,
    startInclusive: Int,
    endExclusive: Int,
    isArgumentOwner: Boolean,
): ValueReader, ListReader, SexpReader, TemplateReaderBase(pool, info, startInclusive, endExclusive, isArgumentOwner) {
    override fun returnToPool() {
        pool.sequences.add(this)
    }
}
