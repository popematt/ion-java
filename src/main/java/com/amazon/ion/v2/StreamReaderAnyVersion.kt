package com.amazon.ion.v2

import com.amazon.ion.*
import java.nio.ByteBuffer

/**
 * Hides the changes in Ion version.
 *
 * Applications using this library should prefer this over using the version-specific stream readers.
 *
 * In-library use cases should eschew this abstraction in favor of managing the version in the next layer of abstraction.
 */
class StreamReaderAnyVersion(private var delegate: StreamReader): StreamReader {
    override tailrec fun nextToken(): Int {
        val token = delegate.nextToken()
        if (token != TokenTypeConst.IVM) {
            return token
        } else {
            delegate = delegate.ivm()
        }
        return nextToken()
    }

    override fun currentToken(): Int = delegate.currentToken()
    override fun ivm(): StreamReader = delegate.ivm()
    override fun ionType(): IonType? = delegate.ionType()
    override fun valueSize(): Int = delegate.valueSize()
    override fun skip() = delegate.skip()
    override fun nullValue(): IonType = delegate.nullValue()
    override fun booleanValue(): Boolean = delegate.booleanValue()
    override fun longValue(): Long = delegate.longValue()
    override fun stringValue(): String = delegate.stringValue()
    override fun symbolValue(): String? = delegate.symbolValue()
    override fun symbolValueSid(): Int = delegate.symbolValueSid()
    override fun clobValue(): ByteBuffer = delegate.clobValue()
    override fun blobValue(): ByteBuffer = delegate.blobValue()
    override fun lookupSid(sid: Int): String? = delegate.lookupSid(sid)
    override fun listValue(): ListReader = delegate.listValue()
    override fun sexpValue(): SexpReader = delegate.sexpValue()
    override fun structValue(): StructReader = delegate.structValue()
    override fun annotations(): AnnotationIterator = delegate.annotations()
    override fun timestampValue(): Timestamp = delegate.timestampValue()
    override fun doubleValue(): Double = delegate.doubleValue()
    override fun decimalValue(): Decimal = delegate.decimalValue()
    override fun eexpValue(): EexpReader = delegate.eexpValue()
    override fun close() = delegate.close()
}
