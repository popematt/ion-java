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

    @JvmStatic
    fun readTimestamp(opcode: Int, source: ByteBuffer): Timestamp {
        // Often, the data for an application will use a lot of timestamps with similar precisions. For example, logging
        // might use mostly millisecond-precision timestamps with UTC offsets, or a calendar application might use
        // minute precision with an offset for representing calendar events.
        // Because of this, each case is handled in a separate method so that the JVM can inline the cases that are most
        // commonly used in any given application.
        return when (opcode) {
            0x80 -> readTimestamp0x80(source)
            0x81 -> readTimestamp0x81(source)
            0x82 -> readTimestamp0x82(source)
            0x83 -> readTimestamp0x83(source)
            0x84 -> readTimestamp0x84(source)
            0x85 -> readTimestamp0x85(source)
            0x86 -> readTimestamp0x86(source)
            0x87 -> readTimestamp0x87(source)
            0x88 -> readTimestamp0x88(source)
            0x89 -> readTimestamp0x89(source)
            0x8A -> readTimestamp0x8A(source)
            0x8B -> readTimestamp0x8B(source)
            0x8C -> readTimestamp0x8C(source)
            else -> if (opcode == 0xF8) {
                readLongTimestamp(source)
            } else {
                throw IonException("Reader not positioned on a timestamp value")
            }

        }
    }

    @JvmStatic
    private fun readTimestamp0x80(source: ByteBuffer): Timestamp {
        val data = source.get().toInt()
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        return Timestamp.forYear(year)
    }

    @JvmStatic
    private fun readTimestamp0x81(source: ByteBuffer): Timestamp {
        val data = source.getShort().toInt()
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        return Timestamp.forMonth(year, month)
    }

    @JvmStatic
    private fun readTimestamp0x82(source: ByteBuffer): Timestamp {
        val data = source.getShort().toInt()
        val year = (data and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        return Timestamp.forDay(year, month, day)
    }

    @JvmStatic
    private fun readTimestamp0x83(source: ByteBuffer): Timestamp {
        val data = source.getInt()
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
    private fun readTimestamp0x84(source: ByteBuffer): Timestamp {
        val data = readBytes(5, source)

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
    private fun readTimestamp0x85(source: ByteBuffer): Timestamp {
        val data = readBytes(6, source)

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
    private fun readTimestamp0x86(source: ByteBuffer): Timestamp {
        val data = readBytes(7, source)

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
    private fun readTimestamp0x87(source: ByteBuffer): Timestamp {
        val data = source.getLong()

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
    private fun readTimestamp0x88(source: ByteBuffer): Timestamp {
        val data = readBytes(5, source)

        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = readShortOffset(data)
        return uncheckedNewTimestamp(Precision.MINUTE, year, month, day, hour, minute, 0, null, offset)
    }

    @JvmStatic
    private fun readTimestamp0x89(source: ByteBuffer): Timestamp {
        val data = readBytes(5, source)

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
    private fun readTimestamp0x8A(source: ByteBuffer): Timestamp {
        val data = readBytes(7, source)

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
    private fun readTimestamp0x8B(source: ByteBuffer): Timestamp {
        val data = source.getLong()

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
    private fun readTimestamp0x8C(source: ByteBuffer): Timestamp {
        val data = readBytes(5, source)

        val year = (data.toInt() and S_TIMESTAMP_YEAR_MASK) + S_TIMESTAMP_YEAR_BIAS
        val month = (data.toInt() and S_TIMESTAMP_MONTH_MASK) ushr S_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and S_TIMESTAMP_DAY_MASK) ushr S_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and S_TIMESTAMP_HOUR_MASK) ushr S_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = (data.toInt() and S_TIMESTAMP_MINUTE_MASK) ushr S_TIMESTAMP_MINUTE_BIT_OFFSET
        val offset = readShortOffset(data)
        val second = ((data and S_O_TIMESTAMP_SECOND_MASK) ushr S_O_TIMESTAMP_SECOND_BIT_OFFSET).toInt()

        val unscaledValue = source.getInt() and THIRTY_BIT_MASK
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
    private fun readBytes(n: Int, source: ByteBuffer): Long {
        val position = source.position()
        source.position(position + n)
        return source.getLong(position - 8 + n) ushr (64 - n * 8)
    }

    @JvmStatic
    private fun readLongTimestamp(source: ByteBuffer): Timestamp {
        val length = IdMappings.length(0xF8, source)
        return when (length) {
            0, 1 -> throw IonException("Invalid Timestamp length")
            2 -> readLongTimestampL2(source)
            3 -> readLongTimestampL3(source)
            4, 5, -> throw IonException("Invalid Timestamp length")
            6 -> readLongTimestampL6(source)
            7 -> readLongTimestampL7(source)
            else -> readLongTimestampL8(length, source)
        }
    }

    @JvmStatic
    private fun readLongTimestampL2(source: ByteBuffer): Timestamp {
        TODO()
    }
    @JvmStatic
    private fun readLongTimestampL3(source: ByteBuffer): Timestamp {
        TODO()
    }
    @JvmStatic
    private fun readLongTimestampL6(source: ByteBuffer): Timestamp {
        TODO()
    }

    /** Seconds precision */
    @JvmStatic
    private fun readLongTimestampL7(source: ByteBuffer): Timestamp {
        TODO()
    }

    @JvmStatic
    private fun readLongTimestampL8(length: Int, source: ByteBuffer): Timestamp {
        val start = source.position()
        val data = source.getLong(start) // and 0xFF_FF_FF_FF_FF_FF_FFL

        val year = (data.toInt() and L_TIMESTAMP_YEAR_MASK)
        val month = (data.toInt() and L_TIMESTAMP_MONTH_MASK) ushr L_TIMESTAMP_MONTH_BIT_OFFSET
        val day = (data.toInt() and L_TIMESTAMP_DAY_MASK) ushr L_TIMESTAMP_DAY_BIT_OFFSET
        val hour = (data.toInt() and L_TIMESTAMP_HOUR_MASK) ushr L_TIMESTAMP_HOUR_BIT_OFFSET
        val minute = ((data and L_TIMESTAMP_MINUTE_MASK) ushr L_TIMESTAMP_MINUTE_BIT_OFFSET).toInt()
        val offset = readLongOffset(data)
        val second = ((data and L_TIMESTAMP_SECOND_MASK) ushr L_TIMESTAMP_SECOND_BIT_OFFSET).toInt()

        source.position(start + 7)

        val fractionalSecondScale = IntHelper.readFlexInt(source)
        val coefficientLength = start + length - source.position()
        val fractionalSecondCoefficient = IntHelper.readFixedUInt(source, coefficientLength)
        val fractionalSecond = BigDecimal.valueOf(fractionalSecondCoefficient.toLong(), fractionalSecondScale)

        return uncheckedNewTimestamp(Precision.SECOND, year, month, day, hour, minute, second, fractionalSecond, offset)
    }

    private fun readLongOffset(data: Long): Int? {
        val offset = ((data and L_TIMESTAMP_OFFSET_MASK) ushr L_TIMESTAMP_OFFSET_BIT_OFFSET).toInt()
        if (offset == L_TIMESTAMP_UNKNOWN_OFFSET_VALUE) {
            return null
        }
        return offset - L_TIMESTAMP_OFFSET_BIAS
    }
}
