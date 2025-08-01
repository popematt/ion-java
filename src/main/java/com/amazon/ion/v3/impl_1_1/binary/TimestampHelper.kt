package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.Timestamp._private_createFromLocalTimeFieldsUnchecked as uncheckedNewTimestamp
import com.amazon.ion.Timestamp.Precision
import com.amazon.ion.impl.bin.Ion_1_1_Constants.*
import java.math.BigDecimal
import java.nio.ByteBuffer

/**
 * Helper class containing static methods for reading timestamps.
 *
 * TODO: See if there's any performance benefit to using the second option. There might be a benefit
 *       to shifting and converting so that the remaining operations can be int ops instead of long ops.
 *       Because ints require only one slot in the JVM operand stack whereas longs require two slots in
 *       the operand stack. This may not be as relevant for 64-bit architectures, but it is not completely
 *       irrelevant because Java bytecodes still treat longs as double-wide values.
 * ```
 * val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
 * ```
 * vs
 * ```
 * val second = (data ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt() and SIX_BIT_MASK
 * ```
 */
object TimestampHelper {

    @JvmField
    internal val SHORT_TS_LENGTHS = byteArrayOf(1, 2, 2, 4, 5, 6, 7, 8, 9, 5, 5, 7, 8, 9)

    @JvmStatic
    fun lengthOfShortTimestamp(opcode: Int): Int = SHORT_TS_LENGTHS[opcode and 0xF].toInt()

    @JvmStatic
    fun readShortTimestampAt(opcode: Int, source: ByteBuffer, position: Int): Timestamp {
        // Often, the data for an application will use a lot of timestamps with similar precisions. For example, logging
        // might use mostly millisecond-precision timestamps with UTC offsets, or a calendar application might use
        // minute precision with an offset for representing calendar events.
        // Because of this, each case is handled in a separate method so that the JVM can inline the cases that are most
        // commonly used in any given application.
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
    private fun readTimestamp0x80(source: ByteBuffer, position: Int): Timestamp {
        val data = source.get(position).toInt()
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        return Timestamp.forYear(year)
    }

    @JvmStatic
    private fun readTimestamp0x81(source: ByteBuffer, position: Int): Timestamp {
        val data = source.getShort(position).toInt()
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        return Timestamp.forMonth(year, month)
    }

    @JvmStatic
    private fun readTimestamp0x82(source: ByteBuffer, position: Int): Timestamp {
        val data = source.getShort(position).toInt()
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        return Timestamp.forDay(year, month, day)
    }

    @JvmStatic
    private fun readTimestamp0x83(source: ByteBuffer, position: Int): Timestamp {
        val data = source.getInt(position)
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        // 0 -> Unknown, 1 -> Z
        val offset  = if ((data and S_U_TIMESTAMP_UTC_FLAG) == 0) null else 0
        return uncheckedNewTimestamp(Precision.MINUTE, year, month, day, hour, minute, 0, null, offset)
    }

    @JvmStatic
    private fun readTimestamp0x84(source: ByteBuffer, position: Int): Timestamp {
        val data = readBytesAt(5, source, position)
        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = if ((data.toInt() and S_U_TIMESTAMP_UTC_FLAG) == 0) null else 0
        val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, null, offset)
    }

    @JvmStatic
    private fun readTimestamp0x85(source: ByteBuffer, position: Int): Timestamp {
        val data = readBytesAt(6, source, position)

        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = if ((data.toInt() and S_U_TIMESTAMP_UTC_FLAG) == 0) null else 0
        val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        val unscaledValue = (data and S_U_TIMESTAMP_MILLISECOND_MASK) ushr S_U_TIMESTAMP_FRACTION_BIT_OFFSET
        if (unscaledValue > MAX_MILLISECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue, MILLISECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset)
    }

    @JvmStatic
    private fun readTimestamp0x86(source: ByteBuffer, position: Int): Timestamp {
        val data = readBytesAt(7, source, position)

        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = if ((data.toInt() and S_U_TIMESTAMP_UTC_FLAG) == 0) null else 0
        val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        val unscaledValue = (data and S_U_TIMESTAMP_MICROSECOND_MASK) ushr S_U_TIMESTAMP_FRACTION_BIT_OFFSET
        if (unscaledValue > MAX_MICROSECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue, MICROSECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset)
    }

    @JvmStatic
    private fun readTimestamp0x87(source: ByteBuffer, position: Int): Timestamp {
        val data = source.getLong(position)

        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = if ((data.toInt() and S_U_TIMESTAMP_UTC_FLAG) == 0) null else 0
        val second = ((data and S_U_TIMESTAMP_SECOND_MASK) ushr S_U_TIMESTAMP_SECOND_BIT_OFFSET).toInt()
        val unscaledValue = (data and S_U_TIMESTAMP_NANOSECOND_MASK) ushr S_U_TIMESTAMP_FRACTION_BIT_OFFSET
        if (unscaledValue > MAX_NANOSECONDS) {
            throw IonException("Timestamp fraction must be between 0 and 1.")
        }
        val fractionalSecond = BigDecimal.valueOf(unscaledValue, NANOSECOND_SCALE)
        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset)
    }

    @JvmStatic
    private fun readTimestamp0x88(source: ByteBuffer, position: Int): Timestamp {
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
    private fun readTimestamp0x89(source: ByteBuffer, position: Int): Timestamp {
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
    private fun readTimestamp0x8A(source: ByteBuffer, position: Int): Timestamp {
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
    private fun readTimestamp0x8B(source: ByteBuffer, position: Int): Timestamp {
        val data = source.getLong(position)

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
    private fun readTimestamp0x8C(source: ByteBuffer, position: Int): Timestamp {
        val data = readBytesAt(5, source, position)

        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = readShortOffset(data)
        val second = ((data and S_O_TIMESTAMP_SECOND_MASK) ushr S_O_TIMESTAMP_SECOND_BIT_OFFSET).toInt()

        val unscaledValue = source.getInt(position + 5) and THIRTY_BIT_MASK
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
    private fun readBytesAt(n: Int, source: ByteBuffer, position: Int): Long {
        return source.getLong(position - 8 + n) ushr (64 - n * 8)
    }

    @JvmStatic
    fun readLongTimestampAt(source: ByteBuffer, start: Int, length: Int): Timestamp {
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
    private fun readLongTimestampL2(source: ByteBuffer, start: Int): Timestamp {
        TODO()
    }
    @JvmStatic
    private fun readLongTimestampL3(source: ByteBuffer, start: Int): Timestamp {
        TODO()
    }
    @JvmStatic
    private fun readLongTimestampL6(source: ByteBuffer, start: Int): Timestamp {
        TODO()
    }

    /** Seconds precision */
    @JvmStatic
    private fun readLongTimestampL7(source: ByteBuffer, start: Int): Timestamp {
        TODO()
    }

    @JvmStatic
    private fun readLongTimestampL8(source: ByteBuffer, start: Int, length: Int): Timestamp {
        val data = source.getLong(start) // and 0xFF_FF_FF_FF_FF_FF_FFL

        val year = (data.toInt() and L_TIMESTAMP_YEAR_MASK)
        val month = (data.toInt() and L_TIMESTAMP_MONTH_MASK) ushr L_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and L_TIMESTAMP_DAY_MASK) ushr L_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and L_TIMESTAMP_HOUR_MASK) ushr L_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = ((data and L_TIMESTAMP_MINUTE_MASK) ushr L_TIMESTAMP_MINUTE_BIT_OFFSET).toInt()
        val offset = readLongOffset(data)
        val second = ((data and L_TIMESTAMP_SECOND_MASK) ushr L_TIMESTAMP_SECOND_BIT_OFFSET).toInt()

        val lengthOfScale = IntHelper.lengthOfFlexUIntAt(source, start + 7)
        val fractionalSecondScale = IntHelper.readFlexIntAt(source, start + 7)
        // TODO: Simplify the arithmetic
        val coefficientLength = length - 7 - lengthOfScale
        val fractionalSecondCoefficient = IntHelper.readFixedUIntAt(source, start + 7 + lengthOfScale, coefficientLength)
        val fractionalSecond = BigDecimal.valueOf(fractionalSecondCoefficient.toLong(), fractionalSecondScale)

        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset)
    }

    @JvmStatic
    private fun readLongOffset(data: Long): Int? {
        val offset = ((data and L_TIMESTAMP_OFFSET_MASK) ushr L_TIMESTAMP_OFFSET_BIT_OFFSET).toInt()
        if (offset == L_TIMESTAMP_UNKNOWN_OFFSET_VALUE) {
            return null
        }
        return offset - L_TIMESTAMP_OFFSET_BIAS
    }
}
