package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.IonException
import com.amazon.ion.SystemSymbols
import com.amazon.ion.impl.SystemSymbols_1_1
import com.amazon.ion.impl.macro.SystemMacro
import com.amazon.ion.v2.SexpReader
import com.amazon.ion.v2.StreamReader
import com.amazon.ion.v2.TokenTypeConst
import com.amazon.ion.v2.ValueReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val ION_1_1_SYMBOL_TABLE = SystemSymbols_1_1.allSymbolTexts().toTypedArray()

class SystemStreamReaderImpl internal constructor(
    source: ByteBuffer,
): ValueReaderBase(source, ResourcePool(source.asReadOnlyBuffer()), ION_1_1_SYMBOL_TABLE), StreamReader {

    companion object {
        fun systemReader(source: ByteBuffer): ValueReader {
            source.order(ByteOrder.LITTLE_ENDIAN)
            return SeqReaderImpl(source, ResourcePool(source.asReadOnlyBuffer()), ION_1_1_SYMBOL_TABLE)
        }
    }

    override tailrec fun nextToken(): Int {
        val token = super.nextToken()

        if (token != TokenTypeConst.ANNOTATIONS) return token

        // Now, try again to get a token for an application value
        return nextToken()
    }

    override fun ivm(): StreamReader {
        if (opcode.toInt() != 0xE0) throw IonException("Not positioned on an IVM")
        opcode = TID_NONE
        // TODO: Check the last byte of the IVM to make sure it is well formed.
        val version = source.getShort().toInt()
        source.get()
        return when (version) {
            // TODO: Get a system reader instead of the application reader?
            0x0100 -> pool.getIon10Reader(source.position())
            // TODO: Update the API so that we only switch readers when there's an actual version change.
            0x0101 -> this
            else -> throw IonException("Unrecognized Ion version")
        }
    }

    override fun close() {
        pool.close()
    }
}
