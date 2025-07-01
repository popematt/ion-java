package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*

class TemplateSequenceReaderImpl(
    pool: TemplateResourcePool,
    source: Array<TemplateBodyExpressionModel>,
    args: ArgumentReader,
    isArgumentOwner: Boolean,
): ValueReader, ListReader, SexpReader, TemplateReaderBase(pool, source, args, isArgumentOwner) {
    override fun returnToPool() {
//        if (this in pool.sequences) throw IllegalStateException("Already closed: $this")
        pool.sequences.add(this)
    }
}
