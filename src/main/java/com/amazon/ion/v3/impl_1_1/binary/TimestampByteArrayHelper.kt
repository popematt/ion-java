// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.Timestamp._private_createFromLocalTimeFieldsUnchecked as uncheckedNewTimestamp
import com.amazon.ion.Timestamp.Precision
import com.amazon.ion.impl.bin.Ion_1_1_Constants.*
import java.math.BigDecimal
import java.nio.ByteBuffer

/**
 * Helper class containing static methods for reading timestamps from byte arrays.
 *
 * This class provides ByteArray equivalents of the methods in TimestampHelper,
 * modified to read directly from byte arrays instead of ByteBuffers.
 *
 * TODO: Update all methods to avoid auto-boxing the offset integer.
 */
object TimestampByteArrayHelper {

    @JvmField
    internal val SHORT_TS_LENGTHS = TimestampHelper.SHORT_TS_LENGTHS

    @JvmStatic
    fun lengthOfShortTimestamp(opcode: Int): Int = SHORT_TS_LENGTHS[opcode and 0xF].toInt()

    /**
     * Position should point to the first byte of the data, not the opcode.
     */
    @JvmStatic
    fun readShortTimestampAt(opcode: Int, source: ByteArray, position: Int): Timestamp {
        return when (opcode) {
            0x80 -> readTimestamp0x80(source, position)
            0x81 -> readTimestamp0x81(source, position)
            0x82 -> readTimestamp0x82(source, position)
            0x83 -> readTimestamp0x83(source, position)
            0x84 -> readTimestamp0x84(source, position)
            0x85 -> readTimestamp0x85(source, position)
            0x86 -> readTimestamp0x86(source, position)
            0x87 -> readTimestamp0x87(source, position)
            0x88 -> readTimestamp0x88(source, position)
            0x89 -> readTimestamp0x89(source, position)
            0x8A -> readTimestamp0x8A(source, position)
            0x8B -> readTimestamp0x8B(source, position)
            0x8C -> readTimestamp0x8C(source, position)
            else -> throw IonException("Reader not positioned on a short timestamp value")
        }
    }

    @JvmStatic
    private fun readTimestamp0x80(source: ByteArray, position: Int): Timestamp {
        val data = source[position].toInt()
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        return Timestamp.forYear(year)
    }

    @JvmStatic
    private fun readTimestamp0x81(source: ByteArray, position: Int): Timestamp {
        val data = (source[position].toInt() shl 8) or (source[position + 1].toInt() and 0xFF)
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        return Timestamp.forMonth(year, month)
    }

    @JvmStatic
    private fun readTimestamp0x82(source: ByteArray, position: Int): Timestamp {
        val data = (source[position].toInt() shl 8) or (source[position + 1].toInt() and 0xFF)
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        return Timestamp.forDay(year, month, day)
    }

    @JvmStatic
    private fun readTimestamp0x83(source: ByteArray, position: Int): Timestamp {
        val data = (source[position].toInt() shl 24) or
                  ((source[position + 1].toInt() and 0xFF) shl 16) or
                  ((source[position + 2].toInt() and 0xFF) shl 8) or
                  (source[position + 3].toInt() and 0xFF)
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val isOffsetKnown = (data.toInt() and S_U_TIMESTAMP_UTC_FLAG) != 0
        return uncheckedNewTimestamp(Precision.MINUTE, year, month, day, hour, minute, 0, null, 0, isOffsetKnown)
    }

    @JvmStatic
    private fun readTimestamp0x84(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(5, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val isOffsetKnown = (data.toInt() and S_U_TIMESTAMP_UTC_FLAG) != 0
        val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, null, 0, isOffsetKnown)
    }


    @JvmStatic
    private fun readTimestamp0x85(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(6, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val isOffsetKnown = (data.toInt() and S_U_TIMESTAMP_UTC_FLAG) != 0
        val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        val unscaledValue = (data and S_U_TIMESTAMP_MILLISECOND_MASK) ushr S_U_TIMESTAMP_FRACTION_BIT_OFFSET
        if (unscaledValue > MAX_MILLISECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue, MILLISECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, 0, isOffsetKnown)
    }

    @JvmStatic
    private fun readTimestamp0x86(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(7, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val isOffsetKnown = (data.toInt() and S_U_TIMESTAMP_UTC_FLAG) != 0
        val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        val unscaledValue = (data and S_U_TIMESTAMP_MICROSECOND_MASK) ushr S_U_TIMESTAMP_FRACTION_BIT_OFFSET
        if (unscaledValue > MAX_MICROSECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue, MICROSECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, 0, isOffsetKnown)
    }

    @JvmStatic
    private fun readTimestamp0x87(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(8, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val isOffsetKnown = (data.toInt() and S_U_TIMESTAMP_UTC_FLAG) != 0
        val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        val unscaledValue = (data and S_U_TIMESTAMP_NANOSECOND_MASK) ushr S_U_TIMESTAMP_FRACTION_BIT_OFFSET
        if (unscaledValue > MAX_NANOSECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue, NANOSECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, 0, isOffsetKnown)
    }

    @JvmStatic
    private fun readTimestamp0x88(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(5, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = readShortOffset(data)
        return uncheckedNewTimestamp(Precision.MINUTE, year, month, day, hour, minute, 0, null, offset)
    }

    @JvmStatic
    private fun readTimestamp0x89(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(5, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = readShortOffset(data)
        val second = ((data and S_O_TIMESTAMP_SECOND_MASK) ushr S_O_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, null, offset)
    }

    @JvmStatic
    private fun readTimestamp0x8A(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(7, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = readShortOffset(data)
        val second = ((data and S_O_TIMESTAMP_SECOND_MASK) ushr S_O_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        val unscaledValue = (data and S_O_TIMESTAMP_MILLISECOND_MASK) ushr S_O_TIMESTAMP_FRACTION_BIT_OFFSET
        if (unscaledValue > MAX_MILLISECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue, MILLISECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset)
    }

    @JvmStatic
    private fun readTimestamp0x8B(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(8, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = readShortOffset(data)
        val second = ((data and S_O_TIMESTAMP_SECOND_MASK) ushr S_O_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        val unscaledValue = (data and S_U_TIMESTAMP_MICROSECOND_MASK) ushr S_O_TIMESTAMP_FRACTION_BIT_OFFSET
        if (unscaledValue > MAX_MICROSECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue, MICROSECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset)
    }

    @JvmStatic
    private fun readTimestamp0x8C(source: ByteArray, position: Int): Timestamp {
        val data = readBytesAt(5, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = readShortOffset(data)
        val second = ((data and S_O_TIMESTAMP_SECOND_MASK) ushr S_O_TIMESTAMP_SECOND_BIT_OFFSET).toInt()

        val unscaledValue = readFixedUIntAt(source, position + 5, 4) and THIRTY_BIT_MASK
        if (unscaledValue > MAX_NANOSECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue.toLong(), NANOSECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset)
    }

    @JvmStatic
    private fun readShortOffset(data: Long): Int? {
        val offsetBits = ((data and S_O_TIMESTAMP_OFFSET_MASK) ushr S_O_TIMESTAMP_OFFSET_BIT_OFFSET).toInt()
        if (offsetBits == SEVEN_BIT_MASK) {
            return null
        }
        return (offsetBits - S_O_TIMESTAMP_OFFSET_BIAS) * S_O_TIMESTAMP_OFFSET_INCREMENT
    }

    /**
     * Reads `n` bytes as the least significant bytes of a Long.
     */
    @JvmStatic
    private fun readBytesAt(n: Int, source: ByteArray, position: Int): Long {
        var result = 0L
        for (i in n - 1 downTo 0) {
            result = (result shl 8) or (source[position + i].toLong() and 0xFF)
        }
        return result
    }

    /**
     * Reads a fixed unsigned integer from a byte array.
     */
    @JvmStatic
    private fun readFixedUIntAt(source: ByteArray, position: Int, length: Int): Int {
        var result = 0
        for (i in 0 until length) {
            result = (result shl 8) or (source[position + i].toInt() and 0xFF)
        }
        return result
    }

    @JvmStatic
    fun readLongTimestampAt(source: ByteArray, start: Int, length: Int): Timestamp {
        return when (length) {
            0, 1 -> throw IonException("Invalid Timestamp length")
            2 -> readLongTimestampL2(source, start)
            3 -> readLongTimestampL3(source, start)
            4, 5, -> throw IonException("Invalid Timestamp length")
            6 -> readLongTimestampL6(source, start)
            7 -> readLongTimestampL7(source, start)
            else -> readLongTimestampL8(source, start, length)
        }
    }

    @JvmStatic
    private fun readLongTimestampL2(source: ByteArray, start: Int): Timestamp {
        TODO()
    }

    @JvmStatic
    private fun readLongTimestampL3(source: ByteArray, start: Int): Timestamp {
        TODO()
    }

    @JvmStatic
    private fun readLongTimestampL6(source: ByteArray, start: Int): Timestamp {
        TODO()
    }

    /** Seconds precision */
    @JvmStatic
    private fun readLongTimestampL7(source: ByteArray, start: Int): Timestamp {
        TODO()
    }

    @JvmStatic
    private fun readLongTimestampL8(source: ByteArray, start: Int, length: Int): Timestamp {
        val data = readBytesAt(8, source, start)

        val year = (data.toInt() and L_TIMESTAMP_YEAR_MASK)
        val month = (data.toInt() and L_TIMESTAMP_MONTH_MASK) ushr L_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and L_TIMESTAMP_DAY_MASK) ushr L_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and L_TIMESTAMP_HOUR_MASK) ushr L_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = ((data and L_TIMESTAMP_MINUTE_MASK) ushr L_TIMESTAMP_MINUTE_BIT_OFFSET).toInt()
        var offset = ((data and L_TIMESTAMP_OFFSET_MASK) ushr L_TIMESTAMP_OFFSET_BIT_OFFSET).toInt()
        val isOffsetKnown = offset != L_TIMESTAMP_UNKNOWN_OFFSET_VALUE
        offset -= L_TIMESTAMP_OFFSET_BIAS
        val second = ((data and L_TIMESTAMP_SECOND_MASK) ushr L_TIMESTAMP_SECOND_BIT_OFFSET).toInt()

        val lengthOfScale = IntHelper.lengthOfFlexUIntAt(ByteBuffer.wrap(source), start + 7)
        val fractionalSecondScale = IntHelper.readFlexIntAt(ByteBuffer.wrap(source), start + 7)
        val coefficientLength = length - 7 - lengthOfScale
        val fractionalSecondCoefficient = readFixedUIntAt(source, start + 7 + lengthOfScale, coefficientLength)
        val fractionalSecond = BigDecimal.valueOf(fractionalSecondCoefficient.toLong(), fractionalSecondScale)

        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset, isOffsetKnown)
    }
}
