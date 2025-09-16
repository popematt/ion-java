package com.amazon.ion.v8

enum class TaglessScalarType(val opcode: Int) {
    INT(Ops.TE_FLEX_INT),
    INT_8(Ops.INT_8),
    INT_16(Ops.INT_16),
    INT_32(Ops.INT_32),
    INT_64(Ops.INT_64),
    UINT(Ops.TE_FLEX_UINT),
    UINT_8(Ops.TE_UINT_8),
    UINT_16(Ops.TE_UINT_16),
    UINT_32(Ops.TE_UINT_32),
    UINT_64(Ops.TE_UINT_64),
    FLOAT_16(Ops.FLOAT_16),
    FLOAT_32(Ops.FLOAT_32),
    FLOAT_64(Ops.FLOAT_64),
    SYMBOL_ID(Ops.SYMBOL_VALUE_SID),
    SYMBOL_TEXT(Ops.VARIABLE_LENGTH_INLINE_SYMBOL),
    SYMBOL(Ops.TE_ANY_SYMBOL),
    STRING(Ops.VARIABLE_LENGTH_STRING),
}
