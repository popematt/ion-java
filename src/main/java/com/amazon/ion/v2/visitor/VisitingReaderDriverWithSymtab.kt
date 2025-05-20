package com.amazon.ion.v2.visitor

import com.amazon.ion.v2.StreamReader
import com.amazon.ion.v2.StructReader
import com.amazon.ion.v2.TokenType
import com.amazon.ion.v2.TokenTypeConst
import com.amazon.ion.v2.ValueReader
import com.amazon.ion.v2.impl_1_1.*

class VisitingReaderDriverWithSymtab(
    private var reader: ValueReader
    // TODO: Catalog
) {
    companion object {
        // TODO: Add static methods here, which will transparently create a driver instance, if necessary.
    }

    fun readAll(visitor: VisitingReaderCallback) {
        while (readValue(reader, visitor)) {}
    }

    fun read(visitor: VisitingReaderCallback) {
        readValue(reader, visitor)
    }

    private fun readValue(initialReader: ValueReader, visitor: VisitingReaderCallback): Boolean {
        // FIXME: This doesn't work. It probably needs to be an instance variable so that we can change the top-level reader if an IVM is encountered.
        var reader = initialReader
        val token = reader.nextToken()

        when (token) {
            TokenTypeConst.IVM -> {
                reader = (reader as StreamReader).ivm()
                return true
            }
            // TODO: if these visitor methods return null, we still need to skip the values.
            TokenTypeConst.NULL -> visitor.onScalar(TokenType.NULL)?.onNull(reader.nullValue())
            TokenTypeConst.BOOL -> visitor.onScalar(TokenType.BOOL)?.onBoolean(reader.booleanValue())
            TokenTypeConst.INT -> visitor.onScalar(TokenType.INT)?.onLongInt(reader.longValue())
            TokenTypeConst.FLOAT -> visitor.onScalar(TokenType.FLOAT)?.onDouble(reader.doubleValue())
            TokenTypeConst.DECIMAL -> visitor.onScalar(TokenType.DECIMAL)?.onDecimal(reader.decimalValue())
            TokenTypeConst.TIMESTAMP -> visitor.onScalar(TokenType.TIMESTAMP)?.onTimestamp(reader.timestampValue())
            TokenTypeConst.STRING -> visitor.onScalar(TokenType.STRING)?.onString(reader.stringValue())
            TokenTypeConst.SYMBOL -> visitor.onScalar(TokenType.SYMBOL)?.let {
                val sid = reader.symbolValueSid()
                val text = if (sid < 0) {
                    reader.symbolValue()
                } else {
                    reader.lookupSid(sid)
                }
                it.onSymbol(text, sid)
            }
            TokenTypeConst.CLOB -> visitor.onScalar(TokenType.CLOB)?.onClob(reader.clobValue())
            TokenTypeConst.BLOB -> visitor.onScalar(TokenType.BLOB)?.onBlob(reader.blobValue())
            TokenTypeConst.LIST -> reader.listValue().use { r ->
                visitor.onList()?.let { v ->
                    while (readValue(r, v))
                    visitor.onListEnd()
                }
            }

            TokenTypeConst.SEXP -> reader.sexpValue().use { r ->
                visitor.onSexp()?.let { v ->
                    while (readValue(r, v)) {}
                    visitor.onSexpEnd()
                }
            }
            TokenTypeConst.STRUCT -> reader.structValue().use { r ->
                visitor.onStruct()?.let { v ->
                    while (readStructField(r, v)) {}
                    visitor.onStructEnd()
                }
            }
            TokenTypeConst.ANNOTATIONS -> {
                reader.annotations().use {
                    val valueVisitor = visitor.onAnnotation(it)
                    valueVisitor?.let { readValue(reader, visitor) }
                }
            }
            TokenTypeConst.END -> return false
            else -> TODO("Unreachable: ${TokenTypeConst(token)}")
        }
        return true
    }

    private fun readStructField(reader: StructReader, visitor: VisitingReaderCallback): Boolean {
        val token = reader.nextToken()
        if (token == TokenTypeConst.END) return false

        val sid = reader.fieldNameSid()
        val text = if (sid < 0) {
            reader.fieldName()
        } else {
            reader.lookupSid(sid)
        }

        val fieldVisitor = visitor.onField(text, sid)

        if (fieldVisitor != null) {
            readValue(reader, fieldVisitor)
        } else {
            // We might need to skip annotations.
            if (reader.nextToken() == TokenTypeConst.ANNOTATIONS) reader.nextToken()
            reader.skip()
        }
        return true
    }
}
