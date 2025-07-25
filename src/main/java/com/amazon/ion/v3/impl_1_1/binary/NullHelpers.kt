package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*

fun typeNullValueOf(int: Int): IonType = when (int) {
    0x00 -> IonType.BOOL
    0x01 -> IonType.INT
    0x02 -> IonType.FLOAT
    0x03 -> IonType.DECIMAL
    0x04 -> IonType.TIMESTAMP
    0x05 -> IonType.STRING
    0x06 -> IonType.SYMBOL
    0x07 -> IonType.BLOB
    0x08 -> IonType.CLOB
    0x09 -> IonType.LIST
    0x0A -> IonType.SEXP
    0x0B -> IonType.STRUCT
    else -> throw IonException("Not a valid null value")
}

val TYPED_NULL_ION_TYPES = arrayOf(
    IonType.BOOL,
    IonType.INT,
    IonType.FLOAT,
    IonType.DECIMAL,
    IonType.TIMESTAMP,
    IonType.STRING,
    IonType.SYMBOL,
    IonType.BLOB,
    IonType.CLOB,
    IonType.LIST,
    IonType.SEXP,
    IonType.STRUCT,
)
