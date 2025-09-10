package com.amazon.ion.v8

import com.amazon.ion.*
import java.math.BigInteger

object DecimalHelper {

    @JvmStatic
    fun readDecimal(src: ByteArray, pos: Int, length: Int): Decimal {
        if (length == 0) {
            return Decimal.ZERO
        }
        val exponentValueAndLength = IntHelper.readFlexIntValueAndLengthAt(src, pos)
        val exponentLength = exponentValueAndLength.toInt() and 0xFF
        val scale = (exponentValueAndLength.shr(8)).toInt() * -1

        val coefficientPosition = pos + exponentLength
        val coefficientLength = length - exponentLength
        val coefficient = IntHelper.readFixedIntAt(src, coefficientPosition, coefficientLength)
        if (coefficient == 0L && coefficientLength > 0) {
            return Decimal.negativeZero(scale)
        }
        return Decimal.valueOf(BigInteger.valueOf(coefficient), scale)

    }
}
