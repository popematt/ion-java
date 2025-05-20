package com.amazon.ion.v3

/**
 * Used by the visitor API.
 */
enum class TokenType {
    NULL,
    BOOL,
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
    NOP,
    NONE,
    END,
    IVM,
    INVALID,
}
