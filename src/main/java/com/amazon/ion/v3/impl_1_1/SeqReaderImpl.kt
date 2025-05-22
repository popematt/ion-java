package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

class SeqReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
    macroTable: Array<Macro>,
): ValueReaderBase(source, pool, symbolTable, macroTable), ListReader, SexpReader {

    override fun close() {
        pool.lists.add(this)
    }
}
