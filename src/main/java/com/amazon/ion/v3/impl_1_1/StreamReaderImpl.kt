package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.*
import java.nio.ByteBuffer

private val ION_1_1_SYMBOL_TABLE = SystemSymbols_1_1.allSymbolTexts().toTypedArray()

class StreamReaderImpl internal constructor(
    source: ByteBuffer,
): ValueReaderBase(source, ResourcePool(source.asReadOnlyBuffer(), ION_1_1_SYMBOL_TABLE, emptyArray()), ION_1_1_SYMBOL_TABLE, emptyArray()), StreamReader {
    override fun close() {
        pool.close()
    }
}
