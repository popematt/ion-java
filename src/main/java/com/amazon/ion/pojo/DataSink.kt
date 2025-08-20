package com.amazon.ion.pojo

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import java.math.BigDecimal
import java.math.BigInteger

interface DataSink {

    fun writeByteRaw(value: Byte)
    fun writeByteAt(value: Byte, offset: Int)
    fun reserve(n: Int): Int


    fun writeBoolean(value: Boolean)

    fun writeInteger(value: Byte)
    fun writeInteger(value: Short)
    fun writeInteger(value: Int)
    fun writeInteger(value: Long)
    fun writeInteger(value: BigInteger)

    fun writeFloat(value: Float)
    fun writeFloat(value: Double)

    fun writeDecimal(value: BigDecimal)
    fun writeDecimal(value: Decimal)

    fun writeTimestamp(value: Timestamp)

    fun writeString(value: String)

    fun writeSymbol(value: String)

    fun writeMacroAddress(macro: Macro)

    fun writeTaglessFlexUInt(value: Byte)
    fun writeTaglessFlexUInt(value: Short)
    fun writeTaglessFlexUInt(value: Int)
    fun writeTaglessFlexUInt(value: Long)
    fun writeTaglessFlexUInt(value: BigInteger)

    fun writeTaglessFlexInt(value: Byte)
    fun writeTaglessFlexInt(value: Short)
    fun writeTaglessFlexInt(value: Int)
    fun writeTaglessFlexInt(value: Long)
    fun writeTaglessFlexInt(value: BigInteger)



}
