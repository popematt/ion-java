package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_0.*
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_SYMBOLS
import java.nio.ByteBuffer

class StreamReaderImpl internal constructor(
    source: ByteBuffer,
): ValueReaderBase(source, ResourcePool(source.asReadOnlyBuffer(), ION_1_1_SYSTEM_SYMBOLS, emptyArray()), ION_1_1_SYSTEM_SYMBOLS, emptyArray()), StreamReader {
    override fun close() {
        pool.close()
    }
}
