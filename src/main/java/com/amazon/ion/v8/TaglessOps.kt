package com.amazon.ion.v8

import com.amazon.ion.IonException

fun getTaglessOpName(op: Int): String {
    return when (op) {
        Ops.INT_8 -> "\$int8"
        Ops.INT_16 -> "\$int16"
        Ops.INT_32 -> "\$int32"
        Ops.INT_64 -> "\$int64"
        Ops.TE_UINT_8 -> "\$uint8"
        Ops.TE_UINT_16 -> "\$uint16"
        Ops.TE_UINT_32 -> "\$uint32"
        Ops.TE_UINT_64 -> "\$uint64"
        Ops.TE_FLEX_INT -> "\$int"
        Ops.TE_FLEX_UINT -> "\$uint"
        Ops.FLOAT_16 -> "\$float16"
        Ops.FLOAT_32 -> "\$float32"
        Ops.FLOAT_64 -> "\$float64"
        Ops.VARIABLE_LENGTH_STRING -> "\$string"
        Ops.TE_ANY_SYMBOL -> "\$symbol"
        Ops.VARIABLE_LENGTH_INLINE_SYMBOL -> "\$symbolText"
        Ops.SYMBOL_VALUE_SID -> "\$symbolId"
        else -> throw IonException("Not a valid tagless op: $op")
    }
}
