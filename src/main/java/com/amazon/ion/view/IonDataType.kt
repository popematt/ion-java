package com.amazon.ion.view

import com.amazon.ion.IonType

/**
 * An enum of types that are in the Ion data model.
 */
enum class IonDataType {
    NULL,
    BOOL,
    INT,
    FLOAT,
    DECIMAL,
    TIMESTAMP,
    STRING,
    SYMBOL,
    BLOB,
    CLOB,
    LIST,
    SEXP,
    STRUCT;

    fun toIonType(): IonType = when (this) {
        NULL -> IonType.NULL
        BOOL -> IonType.BOOL
        INT -> IonType.INT
        FLOAT -> IonType.FLOAT
        DECIMAL -> IonType.DECIMAL
        TIMESTAMP -> IonType.TIMESTAMP
        STRING -> IonType.STRING
        SYMBOL -> IonType.SYMBOL
        BLOB -> IonType.BLOB
        CLOB -> IonType.CLOB
        LIST -> IonType.LIST
        SEXP -> IonType.SEXP
        STRUCT -> IonType.STRUCT
    }

    companion object {
        @JvmStatic
        fun fromIonType(ionType: IonType): IonDataType = when (ionType) {
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
            IonType.DATAGRAM -> throw IllegalArgumentException("DATAGRAM is not a valid Ion data type.")
        }
    }
}