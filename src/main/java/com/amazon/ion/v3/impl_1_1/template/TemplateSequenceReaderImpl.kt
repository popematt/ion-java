package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.*

class TemplateSequenceReaderImpl(
    pool: TemplateResourcePool,
): ValueReader, ListReader, SexpReader, TemplateReaderBase(pool) {
    override fun returnToPool() {
//        if (this in pool.sequences) throw IllegalStateException("Already closed: $this")
        pool.sequences.add(this)
    }
}
