package com.amazon.ion.v3

/**
 * Used by the visitor API.
 *
 * If this is only used for `onValue()`, then we might be able to just get rid of it and use `IonType` instead.
 * Although this does represent something slightly different from `IonType` w.r.t. typed nulls.
 */
enum class TokenType {
    /** Any null value of any type */
    NULL,
    /** A non null Boolean value */
    BOOL,
    /** A non-null Integer value */
    INT,
    FLOAT,
    DECIMAL,
    TIMESTAMP,
    STRING,
    SYMBOL,
    CLOB,
    BLOB,
    LIST,
    SEXP,
    STRUCT,
    ANNOTATIONS,
    FIELD_NAME,
    // TODO: We can probably remove this. It shouldn't be exposed by the visitor API.
    NOP,
    // TODO: We can probably remove this. It shouldn't be exposed by the visitor API.
    NONE,
    END,
    IVM,
    // TODO: Do we want the visitor to allow exposing invalid data?
    INVALID,
}
