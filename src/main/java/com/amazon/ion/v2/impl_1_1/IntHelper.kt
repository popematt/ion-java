package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.IonException
import java.nio.ByteBuffer

object IntHelper {
    @JvmStatic
    fun readFixedInt(source: ByteBuffer, length: Int): Long {
        if (source.remaining() < length) throw IonException("Incomplete data")
        val position = source.position()
        source.position(position + length)
        if (length > 4) {
            // TODO: See if we can simplify some of the calculations
            // val mask = -1L ushr (64 - length * 8)
            return source.getLong(position - 8 + length) shr ((8 - length) * 8)
        } else {
            // val mask = -1 ushr (32 - length * 8)
            return (source.getInt(position - 4 + length) shr ((4 - length) * 8)).toLong()
        }
    }

    @JvmStatic
    fun readFixedUIntAsLong(source: ByteBuffer, length: Int): Long {
        val value = readFixedInt(source, length)
        // Mask to remove any sign extension that occurred
        val mask = -1L ushr (64 - length * 8)
        val result = value and mask
        if (result < 0) throw IonException("FixedUInt to large to fit in a long")
        return result
    }

    @JvmStatic
    fun readFixedUInt(source: ByteBuffer, length: Int): Int {
        val value = readFixedInt(source, length).toInt()
        // Mask to remove any sign extension that occurred
        val mask = -1 ushr (32 - length * 8)
        val result = value and mask
        if (result < 0) throw IonException("FixedUInt to large to fit in an int")
        return result
    }

    @JvmStatic
    fun slowReadFixedIntAsLong(source: ByteBuffer, length: Int): Long {
        return when (length) {
            0 -> return 0
            1 -> source.get().toLong()
            2 -> source.getShort().toLong()
            3 -> ((source.get().toInt() and 0xFF) or (source.getShort().toInt() shl 8)).toLong()
            4 -> source.getInt().toLong()
            5 -> ((source.get().toLong() and 0xFF) or (source.getInt().toLong() shl 8))
            6 -> ((source.getShort().toLong() and 0xFFFF) or (source.getInt().toLong() shl 16))
            7 -> ((source.get().toLong() and 0xFF) or ((source.getShort().toLong() and 0xFFFF) shl 8) or (source.getInt().toLong() shl 24))
            8 -> source.getLong()
            else -> throw IonException("FixedInt is too large to fit in a long")
        }
    }

    @JvmStatic
    fun readFlexUIntAsLong(source: ByteBuffer): Long {
        val position = source.position()
        val firstByte = source.get()
        val numBytes = firstByte.countTrailingZeroBits() + 1
        if (source.remaining() < numBytes) throw IonException("Incomplete data")
        source.position(position + numBytes)
        when (numBytes) {
            1, 2, 3, 4 -> {
                // TODO: We could probably simplify some of these calculations.
                val backtrack = 4 - numBytes
                val data = source.getInt(position - backtrack)
                val shiftAmount = 8 * backtrack + numBytes
                return (data ushr shiftAmount).toLong()
            }
            5, 6, 7, 8 -> {
                val backtrack = 8 - numBytes
                val data = source.getLong(position - backtrack)
                return data ushr (8 * backtrack + numBytes)
            }
            9 -> {
                // The first byte was entirely `0`. We'll assume that the least significant bit of the next byte is 1
                // which would mean that the FlexUInt is 9 bytes long. In this case, we can read a long to get the
                // remaining 8 bytes, and shift out the single bit.
                val value = source.getLong(position + 1)
                // Our assumption that it is a 9 byte flex uint is incorrect.
                if (value and 0x1L == 0L) throw IonException("FlexInt value too large to find in a Long")
                return value ushr 1
            }
            else -> throw IonException("FlexInt value too large to find in a Long")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    fun readFlexUInt(source: ByteBuffer): Int {
        val position = source.position()
        val value = readFlexUIntAsLong(source)
        if (value < 0 || value > Int.MAX_VALUE) {
            throw IonException("FlexUInt is too large to fit in an int: $value (${(0..5).joinToString { source.get(position + it).toHexString()}})")
        }
        return value.toInt()
    }

    @JvmStatic
    fun readFlexInt(source: ByteBuffer): Int {
        // TODO: Consider writing a specialized implementation that is optimized for Ints.
        val n = readFlexIntAsLong(source)
        if (n < Int.MIN_VALUE || n > Int.MAX_VALUE) {
            return n.toInt()
        } else {
            throw IonException("FlexInt value too large to fit in an int")
        }
    }

    /**
     *
     * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eeee_eee1 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
     * - read B-E... eeee_eee1 dddd_dddd cccc_cccc bbbb_bbbb
     * - shift right, preserving sign, by (8 * 3 + 1) = 25 = 4 + 7 * 3
     *
     * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eeee_ee10 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
     * - read C-F... ffff_ffff eeee_ee10 dddd_dddd cccc_cccc
     * - shift right, preserving sign, by (8 * 2 + 2) = 18 = 4 + 7 * 2
     *
     * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eeee_e100 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
     * - read D-G... gggg_gggg ffff_ffff eeee_e100 dddd_dddd
     * - shift right, preserving sign, by (8 * 1 + 3) = 11 = 4 + 7 * 1
     *
     * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eeee_1000 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
     * - read E-H... hhhh_hhhh gggg_gggg ffff_ffff eeee_1000
     * - shift right, preserving sign, by (8 * 0 + 4) = 4
     *
     * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eee1_0000 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
     */
    @JvmStatic
    fun readFlexIntAsLong(source: ByteBuffer): Long {
        val position = source.position()
        val firstByte = source.get()
        val numBytes = firstByte.countTrailingZeroBits() + 1
        if (source.remaining() < numBytes) throw IonException("Incomplete data")
        source.position(position + numBytes)
        when (numBytes) {
            1, 2, 3, 4 -> {
                // TODO: We could probably simplify some of these calculations.
                val backtrack = 4 - numBytes
                val data = source.getInt(position - backtrack)
                val shiftAmount = 8 * backtrack + numBytes
                return (data shr shiftAmount).toLong()
            }
            5, 6, 7, 8 -> {
                val backtrack = 8 - numBytes
                val data = source.getLong(position - backtrack)
                return data shr (8 * backtrack + numBytes)
            }
            9 -> {
                // The first byte was entirely `0`. We'll assume that the least significant bit of the next byte is 1
                // which would mean that the FlexUInt is 9 bytes long. In this case, we can read a long to get the
                // remaining 8 bytes, and shift out the single bit.
                val value = source.getLong(position + 1)
                // Our assumption that it is a 9 byte flex uint is incorrect.
                if (value and 0x1L == 0L) throw IonException("FlexInt value too large to find in a Long")
                return value shr 1
            }
            else -> throw IonException("FlexInt value too large to find in a Long")
        }
    }
}
