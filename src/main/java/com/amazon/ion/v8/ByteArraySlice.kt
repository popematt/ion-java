package com.amazon.ion.v8

/**
 * Positions are relative to `bytes`, not the underlying data stream.
 */
class ByteArraySlice(
    private val bytes: ByteArray,
    private val startInclusive: Int,
    private val endExclusive: Int
)
