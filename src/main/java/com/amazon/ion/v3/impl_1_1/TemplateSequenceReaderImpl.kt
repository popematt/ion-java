package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.v3.*

class TemplateSequenceReaderImpl(
    pool: TemplateResourcePool,
    info: TemplateResourcePool.TemplateInvocationInfo,
    startInclusive: Int,
    endExclusive: Int,
): ValueReader, ListReader, SexpReader, TemplateReaderBase(pool, info, startInclusive, endExclusive) {
    override fun close() {
        pool.sequences.add(this)
    }
}
