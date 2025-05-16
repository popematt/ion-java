package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.v2.*
import java.nio.ByteBuffer

class SeqReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
): ValueReaderBase(source, pool, symbolTable), ListReader, SexpReader {

    override fun close() {
        pool.lists.add(this)
    }

}
