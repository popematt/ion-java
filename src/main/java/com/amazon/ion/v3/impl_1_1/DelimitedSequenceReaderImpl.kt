package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.v3.*
import java.nio.ByteBuffer

class DelimitedSequenceReaderImpl(
    source: ByteBuffer,
    pool: ResourcePool,
    var parent: ValueReaderBase,
): ValueReaderBase(source, pool), ListReader, SexpReader {

    override fun nextToken(): Int {
        val token = super.nextToken()
        if (token == TokenTypeConst.END) {
            source.limit(source.position())
        }
        return token
    }

    override fun close() {
        while (nextToken() != TokenTypeConst.END) {
            skip()
        }
        parent.source.position(this.source.position())
        pool.delimitedLists.add(this)
    }
}
