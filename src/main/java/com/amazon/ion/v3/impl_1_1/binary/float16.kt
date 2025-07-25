package com.amazon.ion.v3.impl_1_1.binary



fun toFloat(input: Short): Float {
    // TODO: Make sure we're getting this right.
    val i = input.toUShort()

    var t1: UInt = (i and 0x7fffu).toUInt()     // Non-sign bits
    var t2: UInt = (i and 0x8000u).toUInt()     // Sign bit
    val t3: UInt = (i and 0x7c00u).toUInt()     // Exponent

    t1 = t1 shl 13                              // Align mantissa on MSB
    t2 = t2 shl 16                              // Shift sign bit into position

    t1 += 0x38000000u                           // Adjust bias

    t1 = when (t3) {
        0u -> 0u                                // Denormals-as-zero
        0x7c00u -> {
            // Infinity or NaN
            0b01111111_10000000_00000000_00000000u or t1
        }
        else -> t1
    }
    t1 = t1 or t2                               // Re-insert sign bit

    return Float.fromBits(t1.toInt())
}

/**
 * Based on https://gist.github.com/zhuker/b4bd1fb306c7b04975b712c37c4c4075
 *
 * Fast half-precision to single-precision floating point conversion
 *  - Supports signed zero and denormals-as-zero (DAZ)
 *  - Does not support infinities or NaN
 *  - Few, partially pipelinable, non-branching instructions,
 *  - Core operations ~6 clock cycles on modern x86-64
 */
private fun toFloatReals(input: Short): Float {
    // TODO: Make sure we're getting this right.
    val i = input.toUShort()

    var t1: UInt = (i and 0x7fffu).toUInt()     // Non-sign bits
    var t2: UInt = (i and 0x8000u).toUInt()     // Sign bit
    val t3: UInt = (i and 0x7c00u).toUInt()     // Exponent

    t1 = t1 shl 13                              // Align mantissa on MSB
    t2 = t2 shl 16                              // Shift sign bit into position

    t1 += 0x38000000u                           // Adjust bias

    t1 = if (t3 == 0u) 0u else t1               // Denormals-as-zero
    t1 = t1 or t2                               // Re-insert sign bit

    return Float.fromBits(t1.toInt())
}
