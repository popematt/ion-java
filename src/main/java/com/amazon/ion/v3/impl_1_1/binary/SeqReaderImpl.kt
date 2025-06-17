package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer

class SeqReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
    macroTable: Array<MacroV2>,
): ValueReaderBase(source, pool, symbolTable, macroTable), ListReader, SexpReader {

    override fun close() {
        if (this in pool.lists) throw IllegalStateException("Already closed: $this")
        pool.lists.add(this)
    }
}
