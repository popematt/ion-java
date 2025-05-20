package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v2.*
import com.amazon.ion.v2.impl_1_0.*
import java.nio.ByteBuffer

private val ION_1_1_SYMBOL_TABLE = SystemSymbols_1_1.allSymbolTexts().toTypedArray()

class StreamReaderImpl internal constructor(
    source: ByteBuffer,
    ion10Reader: StreamReader_1_0? = null
): ValueReaderBase(source, ResourcePool(source.asReadOnlyBuffer(), ion10Reader), ION_1_1_SYMBOL_TABLE), StreamReader {

    override fun ivm(): StreamReader {
        if (opcode.toInt() != 0xE0) throw IonException("Not positioned on an IVM")
        symbolTable = ION_1_1_SYMBOL_TABLE
        opcode = TID_NONE
        val version = source.getShort().toInt()
        // TODO: Check the last byte of the IVM to make sure it is well formed.
        source.get()
        return when (version) {
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
