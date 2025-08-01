package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer

/**
 * TODO: Combine this with [SeqReaderImpl]
 */
class DelimitedSequenceReaderImpl(
    source: ByteBuffer,
    pool: ResourcePool,
    var parent: ValueReaderBase,
    symbolTable: Array<String?>,
    macroTable: Array<MacroV2>,
): ValueReaderBase(source, pool, symbolTable, macroTable), ListReader, SexpReader {

    // TODO: What if we could make it so that it scans its own length when it is opened?
    //       In doing so, it could also cache any nested delimited containers that it must create.

    override fun nextToken(): Int {
        val token = super.nextToken()
        if (token == TokenTypeConst.END) {
            limit = i
        }
        return token
    }


    private var e: String = "unknown"

    override fun close() {
        if (this in pool.delimitedLists) {
            System.err.println("Previously closed at: $e")
            throw IllegalStateException("Already closed: $this")
        }
        while (nextToken() != TokenTypeConst.END) {
            skip()
        }
        parent.i = this.i

//        e = Exception().stackTraceToString()

        pool.delimitedLists.add(this)
    }
}
