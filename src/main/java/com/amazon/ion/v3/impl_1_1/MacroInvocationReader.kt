package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.template.*

// TODO: Fold this into TemplateSequenceReader
class MacroInvocationReader(
    pool: TemplateResourcePool,
    info: TemplateResourcePool.TemplateInvocationInfo,
    startInclusive: Int,
    endExclusive: Int,
    isArgumentOwner: Boolean
): TemplateReaderBase(pool, info, startInclusive, endExclusive, isArgumentOwner), SequenceReader {

    override fun returnToPool() {
        pool.invocations.add(this)
    }
}
