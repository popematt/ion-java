package com.amazon.ion.impl

import com.amazon.ion.*

enum class _Private_IonEncodingType {
    NULL,
    BOOL,
    INT,
    FLOAT,
    DECIMAL,
    TIMESTAMP,
    SYMBOL,
    STRING,
    BLOB,
    CLOB,
    LIST,
    SEXP,
    STRUCT,
    MACRO_INVOCATION;

    fun toIonType(): IonType? = when(this) {
        NULL -> IonType.NULL
        BOOL -> IonType.BOOL
        INT -> IonType.INT
        FLOAT -> IonType.FLOAT
        DECIMAL -> IonType.DECIMAL
        TIMESTAMP -> IonType.TIMESTAMP
        SYMBOL -> IonType.SYMBOL
        STRING -> IonType.STRING
        BLOB -> IonType.BLOB
        CLOB -> IonType.CLOB
        LIST -> IonType.LIST
        SEXP -> IonType.SEXP
        STRUCT -> IonType.STRUCT
        MACRO_INVOCATION -> null
    }

    companion object {
        @JvmStatic
        @JvmName("fromIonTypeID")
        internal fun fromIonTypeID(tid: IonTypeID): _Private_IonEncodingType? {
            return when (tid.type) {
                IonType.NULL -> NULL
                IonType.BOOL -> BOOL
                IonType.INT -> INT
                IonType.FLOAT -> FLOAT
                IonType.DECIMAL -> DECIMAL
                IonType.TIMESTAMP -> TIMESTAMP
                IonType.SYMBOL -> SYMBOL
                IonType.STRING -> STRING
                IonType.CLOB -> CLOB
                IonType.BLOB -> BLOB
                IonType.LIST -> LIST
                IonType.SEXP -> SEXP
                IonType.STRUCT -> STRUCT
                IonType.DATAGRAM -> null
                null -> if (tid.isMacroInvocation) { MACRO_INVOCATION } else null
            }
        }
    }
}
