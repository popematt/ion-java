package com.amazon.ion.v3.application

import com.amazon.ion.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

open class ApplicationValueReader(
    protected var valueReader: ValueReader
): ValueReader {
    override fun nextToken(): Int {
        TODO("Not yet implemented")
    }

    override fun currentToken(): Int = valueReader.currentToken()
    override fun isTokenSet(): Boolean = valueReader.isTokenSet()
    override fun ionType(): IonType? = valueReader.ionType()
    override fun valueSize(): Int = valueReader.valueSize()
    override fun skip() = valueReader.skip()
    override fun nullValue(): IonType = valueReader.nullValue()

    override fun booleanValue(): Boolean {
        TODO("Not yet implemented")
    }

    override fun longValue(): Long {
        TODO("Not yet implemented")
    }

    override fun stringValue(): String {
        TODO("Not yet implemented")
    }

    override fun symbolValue(): String? {
        TODO("Not yet implemented")
    }

    override fun symbolValueSid(): Int {
        TODO("Not yet implemented")
    }

    override fun lookupSid(sid: Int): String? {
        TODO("Not yet implemented")
    }

    override fun clobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun blobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun listValue(): ListReader {
        TODO("Not yet implemented")
    }

    override fun sexpValue(): SexpReader {
        TODO("Not yet implemented")
    }

    override fun structValue(): StructReader = ApplicationStructReader(valueReader.structValue())

    override fun annotations(): AnnotationIterator = valueReader.annotations()
    override fun timestampValue(): Timestamp = valueReader.timestampValue()
    override fun doubleValue(): Double = valueReader.doubleValue()
    override fun decimalValue(): Decimal = valueReader.decimalValue()
    override fun ivm(): Short = valueReader.ivm()

    override fun getIonVersion(): Short = valueReader.getIonVersion()

    override fun seekTo(position: Int) {
        TODO("Not yet implemented")
    }

    override fun position(): Int {
        TODO("Not yet implemented")
    }

    override fun close() {
        valueReader.close()
    }

    override fun expressionGroup(): SequenceReader {
        TODO("Not yet implemented")
    }

    class ApplicationStructReader(valueReader: StructReader): ApplicationValueReader(valueReader), StructReader {
        override fun fieldNameSid(): Int = (valueReader as StructReader).fieldNameSid()
        override fun fieldName(): String? = (valueReader as StructReader).fieldName()
    }
}
