package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.IonException
import java.nio.ByteBuffer

/**
 * Helper class containing methods for reading FixedInts, FlexInts, FixedUInts, and FlexUInts.
 *
 * TODO: Right now, this class is hijacking the test cases for [FlexInt][com.amazon.ion.impl.bin.FlexInt].
 *       This needs to be cleaned up before any of this is merged.
 *
 *
 * The methods in this class use a clever technique to reduce the number of branches and minimize the number of calls to
 * get data from the ByteBuffer. We know that any FlexInt or FlexUint must have at least 4 bytes preceding it (because
 * the IVM is 4 bytes), so rather than reading bytes one at a time, we'll read one byte to figure out how many bytes to
 * read, and then we'll read the entire FlexUInt (plus zero or more preceding bytes) in one call to [ByteBuffer.getInt]
 * or [ByteBuffer.getLong]. This puts all the bytes we want into the _most_ significant bits of the `int` or `long`.
 * Then we can remove the extra bytes and the continuation bits by using a single right-shift operation (signed for
 * FlexInt or unsigned for FlexUInt). This technique significantly reduces the number of operations required to read a
 * Flex(U)Int as compared to reading bytes one at a time.
 *
 * A similar technique is also used for reading FixedInts and FixedUInts.
 *
 * Examples:
 * ```
 * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eeee_eee1 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
 * ```
 * - read B-E... `eeee_eee1 dddd_dddd cccc_cccc bbbb_bbbb`
 * - shift right by (8 * 3 + 1) = 25 = 4 + 7 * 3
 * - unsigned shift right for FlexUint; signed shift right for FlexInt.
 *
 * ```
 * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eeee_ee10 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
 * ```
 * - read C-F... `ffff_ffff eeee_ee10 dddd_dddd cccc_cccc`
 * - shift right by (8 * 2 + 2) = 18 = 4 + 7 * 2
 *
 * ```
 * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eeee_e100 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
 * ```
 * - read D-G... `gggg_gggg ffff_ffff eeee_e100 dddd_dddd`
 * - shift right by (8 * 1 + 3) = 11 = 4 + 7 * 1
 *
 * ```
 * aaaa_aaaa bbbb_bbbb cccc_cccc dddd_dddd eeee_1000 ffff_ffff gggg_gggg hhhh_hhhh iiii_iiii
 * ```
 * - read E-H... `hhhh_hhhh gggg_gggg ffff_ffff eeee_1000`
 * - shift right by (8 * 0 + 4) = 4
 */
object IntHelper {
    @JvmStatic
    fun readFixedInt(source: ByteBuffer, length: Int): Long {
        if (source.remaining() < length) throw IonException("Incomplete data")
        val position = source.position()
        source.position(position + length)
        if (length > 4) {
            // TODO: See if we can simplify some of the calculations
            return source.getLong(position - 8 + length) shr ((8 - length) * 8)
        } else {
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

    // TODO: Delete me?
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
        // TODO: Rewrite this as a relative get() so that we don't have to set the position
        //       again in the 1-byte case.
        val firstByte = source.get()
        val numBytes = firstByte.countTrailingZeroBits() + 1
        if (source.remaining() < (numBytes - 1)) throw IonException("Incomplete data")
        when (numBytes) {
            1 -> {
//                source.position(position + 1)
                return ((firstByte.toInt() and 0xFF) ushr 1).toLong()
            }
            2, 3, 4 -> {
                source.position(position + numBytes)
                // TODO: We could probably simplify some of these calculations.
                val backtrack = 4 - numBytes
                val data = source.getInt(position - backtrack)
                val shiftAmount = 8 * backtrack + numBytes
                return (data ushr shiftAmount).toLong()
            }
            5, 6, 7, 8 -> {
                source.position(position + numBytes)
                val backtrack = 8 - numBytes
                val data = source.getLong(position - backtrack)
                return data ushr (8 * backtrack + numBytes)
            }
            9 -> {
                // The first byte was entirely `0`. We'll assume that the least significant bit of the next byte is 1
                // which would mean that the FlexUInt is 9 bytes long. In this case, we can read a long to get the
                // remaining 8 bytes, and shift out the single bit.
                val value = source.getLong(position + 1)
                source.position(position + 9)
                // Our assumption that it is a 9 byte flex uint is incorrect.
                if (value and 0x1L == 0L) throw IonException("FlexInt value too large to find in a Long")
                return value ushr 1
            }
            else -> throw IonException("FlexInt value too large to find in a Long")
        }
    }

    @JvmStatic
    fun readFlexUInt(source: ByteBuffer): Int {
        // TODO: Consider writing a specialized implementation that is optimized for Ints.
        val value = readFlexUIntAsLong(source)
        if (value < 0 || value > Int.MAX_VALUE) {
            throw IonException("FlexUInt is too large to fit in an int")
        }
        return value.toInt()
    }

    @JvmStatic
    fun readFlexInt(source: ByteBuffer): Int {
        // TODO: Consider writing a specialized implementation that is optimized for Ints.
        val n = readFlexIntAsLong(source)
        if (n < Int.MIN_VALUE || n > Int.MAX_VALUE) {
            throw IonException("FlexInt value too large to fit in an int")
        }
        return n.toInt()
    }

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
                source.position(position + 9)
                // Our assumption that it is a 9 byte flex uint is incorrect.
                if (value and 0x1L == 0L) throw IonException("FlexInt value too large to find in a Long")
                return value shr 1
            }
            else -> throw IonException("FlexInt value too large to find in a Long")
        }
    }
}
