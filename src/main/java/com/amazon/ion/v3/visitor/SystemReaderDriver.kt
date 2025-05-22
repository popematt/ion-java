package com.amazon.ion.v3.visitor

import com.amazon.ion.IonException
import com.amazon.ion.v3.StructReader
import com.amazon.ion.v3.TokenType
import com.amazon.ion.v3.TokenTypeConst
import com.amazon.ion.v3.ValueReader
import com.amazon.ion.v3.impl_1_0.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer

// TODO: Make sure we can unify this over text and binary
//       Or have subclasses that are Text vs Binary.
/**
 * This is incomplete.
 *
 * The intention is that there's a "driver" for visitors that visits at the "system" level.
 * I.e. it exposes system values and may or may not (TBD) resolve symbol IDs.
 *
 *
 */
class SystemReaderDriver(
    source: ByteBuffer,
    // TODO: options?
): AutoCloseable {

    override fun close() {
        ion10Reader.close()
        ion11Reader.close()
    }

    private val ion10Reader: ValueReader by lazy { StreamReader_1_0(source) }
    private val ion11Reader: ValueReader by lazy { StreamReaderImpl(source) }
    private var reader: ValueReader = if (source.getInt(0).toUInt() == 0xE00101EAu) {
        ion11Reader
    } else {
        ion10Reader
    }

    /**
     * Reads all values on the reader
     */
    fun readAll(visitor: VisitingReaderCallback) {
        while (readValue(reader, visitor)) {}
    }

    /**
     * Reads one value on the reader
     */
    fun read(visitor: VisitingReaderCallback) {
        readValue(reader, visitor)
    }

    private fun readValue(initialReader: ValueReader, visitor: VisitingReaderCallback): Boolean {
        var reader = initialReader
        val token = reader.nextToken()

        // println(TokenTypeConst(token))
        when (token) {
            TokenTypeConst.IVM -> {
                val currentVersion = reader.getIonVersion().toInt()
                val version = reader.ivm().toInt()
                // If the version is the same, then we know it's a valid version, and we can skip validation
                if (version != currentVersion) {
                    val position = reader.position()
                    this.reader = when (version) {
                        0x0100 -> ion10Reader
                        0x0101 -> ion11Reader
                        else -> throw IonException("Unknown Ion Version ${version ushr 8}.${version and 0xFF}")
                    }
                    this.reader.seekTo(position)
                }
                visitor.onIVM(version ushr 8, version and 0xFF)
            }
            TokenTypeConst.NULL -> visitor.onValue(TokenType.NULL)?.onNull(reader.nullValue()) ?: reader.skip()
            TokenTypeConst.BOOL -> visitor.onValue(TokenType.BOOL)?.onBoolean(reader.booleanValue()) ?: reader.skip()
            TokenTypeConst.INT -> visitor.onValue(TokenType.INT)?.onLongInt(reader.longValue()) ?: reader.skip()
            TokenTypeConst.FLOAT -> visitor.onValue(TokenType.FLOAT)?.onFloat(reader.doubleValue()) ?: reader.skip()
            TokenTypeConst.DECIMAL -> visitor.onValue(TokenType.DECIMAL)?.onDecimal(reader.decimalValue()) ?: reader.skip()
            TokenTypeConst.TIMESTAMP -> visitor.onValue(TokenType.TIMESTAMP)?.onTimestamp(reader.timestampValue()) ?: reader.skip()
            TokenTypeConst.STRING -> visitor.onValue(TokenType.STRING)?.onString(reader.stringValue()) ?: reader.skip()
            TokenTypeConst.SYMBOL -> visitor.onValue(TokenType.SYMBOL)?.let { v ->
                val sid = reader.symbolValueSid()
                val text = if (sid < 0) reader.symbolValue() else null
                v.onSymbol(text, sid)
            } ?: reader.skip()
            TokenTypeConst.CLOB -> visitor.onValue(TokenType.CLOB)?.onClob(reader.clobValue()) ?: reader.skip()
            TokenTypeConst.BLOB -> visitor.onValue(TokenType.BLOB)?.onBlob(reader.blobValue()) ?: reader.skip()
            // IMPLEMENTATION NOTE:
            //     For containers, we're going to step in, and then immediately step out if we need to skip.
            //     This might be slightly less efficient, but it works for both delimited and prefixed containers.
            TokenTypeConst.LIST -> reader.listValue().use { r ->
                visitor.onValue(TokenType.LIST)?.let { v ->
                    v.onListStart()
                    while (readValue(r, v))
                    v.onListEnd()
                }
            }
            TokenTypeConst.SEXP -> reader.sexpValue().use { r ->
                visitor.onValue(TokenType.SEXP)?.let { v ->
                    v.onSexpStart()
                    while (readValue(r, v))
                    v.onSexpEnd()
                }
            }
            TokenTypeConst.STRUCT -> reader.structValue().use { r ->
                visitor.onValue(TokenType.STRUCT)?.let { v ->
                    v.onStructStart()
                    while (readStructField(r, v)) {}
                    v.onStructEnd()
                }
            }
            TokenTypeConst.ANNOTATIONS -> reader.annotations().use { a ->
                visitor.onAnnotation(a)?.let { v ->
                    readValue(reader, v)
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
        val text = if (sid < 0) reader.symbolValue() else null

        val fieldVisitor = visitor.onField(text, sid)

        if (fieldVisitor != null) {
            readValue(reader, fieldVisitor)
        } else {
            // We might need to skip annotations.
            if (reader.nextToken() == TokenTypeConst.ANNOTATIONS) reader.nextToken()
            // TODO: Will this work for skipping delimited containers?
            reader.skip()
        }
        return true
    }
}
