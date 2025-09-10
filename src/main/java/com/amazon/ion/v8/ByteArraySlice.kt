package com.amazon.ion.v8

/**
 * Light-weight representation of a slice of a [ByteArray].
 *
 * Positions are relative to `bytes`, not the underlying data stream.
 */
class ByteArraySlice(
    val bytes: ByteArray,
    val startInclusive: Int,
    val endExclusive: Int
) {
    val length = endExclusive - startInclusive

    fun newByteArray() = bytes.copyOfRange(startInclusive, endExclusive)
}
