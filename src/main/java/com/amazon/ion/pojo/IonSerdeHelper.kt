package com.amazon.ion.pojo

import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.macro.Macro

interface IonSerdeHelper<T> {
    val MACRO_NAME: String
    val MACRO: Macro
    fun writeArguments(value: T, buffer: WriteBuffer)

    companion object {
        @JvmStatic
        fun writeMacroAddress(addr: Int, buffer: WriteBuffer) {
            TODO()
        }

        @JvmStatic
        fun writeTaggedInteger(x: Int, buffer: WriteBuffer) {
            val xLength = FixedInt.fixedIntLength(x)
            buffer.writeByte((OpCodes.INTEGER_ZERO_LENGTH + xLength).toByte())
            for (i in 0 until xLength) {
                buffer.writeByte((x shr (i * 8)).toByte())
            }
        }
    }
}
