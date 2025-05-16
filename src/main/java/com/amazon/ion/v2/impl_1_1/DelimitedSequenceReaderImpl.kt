package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.v2.*
import java.nio.ByteBuffer

class DelimitedSequenceReaderImpl(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
    var parent: ValueReaderBase,
): ValueReaderBase(source, pool, symbolTable), ListReader, SexpReader {

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
