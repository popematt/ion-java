package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.v3.*
import java.nio.ByteBuffer

class SeqReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
): ValueReaderBase(source, pool), ListReader, SexpReader {

    override fun close() {
        pool.lists.add(this)
    }

}
