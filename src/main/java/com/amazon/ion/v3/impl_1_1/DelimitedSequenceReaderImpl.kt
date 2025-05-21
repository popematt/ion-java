package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

/**
 * TODO: Combine this with [SeqReaderImpl]
 */
class DelimitedSequenceReaderImpl(
    source: ByteBuffer,
    pool: ResourcePool,
    var parent: ValueReaderBase,
    symbolTable: Array<String?>,
    macroTable: Array<Macro>,
): ValueReaderBase(source, pool, symbolTable, macroTable), ListReader, SexpReader {

    // TODO: What if we could make it so that it scans its own length when it is opened?
    //       In doing so, it could also cache any nested delimited containers that it must create.

    fun findEnd() {
        source.mark()
        while (nextToken() != TokenTypeConst.END) {
            // TODO: See if we should cache any child delimited containers.
            skip()
        }
        source.limit(source.position())
        source.reset()
    }

    override fun nextToken(): Int {
        val token = super.nextToken()
        if (token == TokenTypeConst.END) {
            source.limit(source.position())
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
        parent.source.position(this.source.position())

//        e = Exception().stackTraceToString()

        pool.delimitedLists.add(this)
    }
}
