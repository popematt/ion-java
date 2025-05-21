package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.v3.*
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_MACROS
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_SYMBOLS
import java.nio.ByteBuffer

class StreamReaderImpl internal constructor(
    source: ByteBuffer,
): ValueReaderBase(source, ResourcePool(source.asReadOnlyBuffer()), ION_1_1_SYSTEM_SYMBOLS, ION_1_1_SYSTEM_MACROS), StreamReader {
    override fun close() {
        pool.close()
    }
}
