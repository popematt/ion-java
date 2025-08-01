package com.amazon.ion.v3.impl_1_1.binary

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

fun readText(position: Int, length: Int, scratchBuffer: ByteBuffer): String {
    scratchBuffer.limit(position + length)
    scratchBuffer.position(position)
    return StandardCharsets.UTF_8.decode(scratchBuffer).toString()
}
