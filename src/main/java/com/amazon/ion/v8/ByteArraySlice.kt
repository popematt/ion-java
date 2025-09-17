package com.amazon.ion.v8

interface ByteSlice {
    fun newByteArray(): ByteArray

    // TODO: a method to iterate over the bytes? Maybe in chunks?
}

/**
 * Light-weight representation of a slice of a [ByteArray].
 *
 * Positions are relative to `bytes`, not the underlying data stream.
 */
class ByteArraySlice(
    val bytes: ByteArray,
    val startInclusive: Int,
    val endExclusive: Int
): ByteSlice {
    val length = endExclusive - startInclusive

    override fun newByteArray() = bytes.copyOfRange(startInclusive, endExclusive)
}
