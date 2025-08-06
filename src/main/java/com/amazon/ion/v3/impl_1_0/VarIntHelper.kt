package com.amazon.ion.v3.impl_1_0

import com.amazon.ion.*
import java.nio.ByteBuffer

class VarIntHelper {

    companion object {
        /**
         * Returns an unsigned integer up to 7 bytes, with an 1 byte integer signifying how many varuint bytes were used in its encoding.
         *
         */
        @JvmStatic
        fun readVarUIntValueAndLength0(source: ByteBuffer, position: Int): Long {
            var p = position
            var result = 0L
            var length = 0
            var currentByte: Int
            do {
                length++
                currentByte = source.get(p++).toInt()
                result = (result shl 7) or (currentByte and 0b01111111).toLong()
            } while (currentByte >= 0)

            return (result shl 8) or length.toLong()
        }

        @JvmStatic
        fun readVarUIntValueAndLength(source: ByteBuffer, position: Int): Long {
            val currentByte: Int = source.get(position).toInt()
            val result = (currentByte and 0b01111111).toLong()
            if (currentByte < 0) {
                return (result shl 8) or 1L
            } else {
                return readVarUIntValueAndLength2(source, position + 1, result)
            }
        }

        @JvmStatic
        fun readVarUIntValueAndLength2(source: ByteBuffer, position: Int, partialResult: Long): Long {
            var currentByte: Int = source.get(position).toInt()
            var result = (partialResult shl 7) or (currentByte and 0b01111111).toLong()
            if (currentByte < 0) {
                return (result shl 8) or 2
            }

            var p = position
            var length = 2
            do {
                length++
                currentByte = source.get(++p).toInt()
                result = (result shl 7) or (currentByte and 0b01111111).toLong()
            } while (currentByte >= 0)

            return (result shl 8) or length.toLong()

        }

        /**
         * Returns a signed integer up to 7 bytes, with an 1 byte integer signifying how many varuint bytes were used in its encoding.
         *
         */
        @JvmStatic
        fun readVarIntValueAndLength(source: ByteBuffer, position: Int): Long {
            var p = position

            var length = 1
            var currentByte = source.get(p++)
            var result = (currentByte.toInt() and 0x3F).toLong()
            // 1. Shift the sign-bit leftwards to the two's-complement sign-bit of a java integer
            // 2. Signed shift right so that we have either -1 or 0
            // 3. "or" with 1 so that we have -1 or 1
            val sign = (((currentByte.toInt() and 0b01000000) shl 25) shr 32) or 1

            while (currentByte >= 0) {
                length++
                currentByte = source.get(p++)
                result = (result shl 7) or (currentByte.toInt() and 0b01111111).toLong()
            }

            return ((sign * result) shl 8) or length.toLong()
        }
    }



    class ValueAndLength(
        @JvmField
        var value: Long,
        @JvmField
        var length: Int,
    )

    @JvmField
    val returnValue = ValueAndLength(0, -1)

    fun readVarUIntAt(source: ByteBuffer, position: Int): ValueAndLength {
        val firstByte = source.get(position)
        if (firstByte < 0) {

            val r = returnValue
            r.value = firstByte.toLong() and 0x7F
            r.length = 1
            return r
        } else {
            return readVarUIntAt(source, position + 1, firstByte)
        }
    }

    fun readVarUIntAt(source: ByteBuffer, position: Int, firstByte: Byte): ValueAndLength {
        var p = position
        var result = firstByte.toLong()
        do {
            val currentByte = source.get(p++)
            result = (result shl 7) or (currentByte.toInt() and 0b01111111).toLong()
        } while (currentByte >= 0)
        if (result > Int.MAX_VALUE) throw IonException("Found a VarUInt that was too large to fit in a `int` -- $result")
        val r = returnValue
        r.value = result
        r.length = (p - position + 1)
        return r
    }

    fun readVarIntAt(source: ByteBuffer, position: Int): ValueAndLength {
        var result: Long
        var p = position
        var currentByte = source.get(p++)
        // 1. Shift the sign-bit leftwards to the two's-complement sign-bit of a java integer
        // 2. Signed shift right so that we have either -1 or 0
        // 3. "or" with 1 so that we have -1 or 1
        val sign = (((currentByte.toInt() and 0b01000000) shl 25) shr 32) or 1
        result = (currentByte.toInt() and 0x3F).toLong()
        while (currentByte >= 0) {
            currentByte = source.get(p++)
            result = (result shl 7) or (currentByte.toInt() and 0b01111111).toLong()
            if (result > Int.MAX_VALUE) throw IonException("Found a VarInt that was too large to fit in a `Int` -- $result")
        }
        val r = returnValue
        r.value = result * sign
        r.length = p - position
        return r
    }
}
