package com.amazon.ion.v2

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import java.nio.ByteBuffer

/**
 * Once you call a method to read a value, you cannot read that value again. The only exception is
 * methods ending in `Sid`. If and only if one such method returns `-1` (for unknown symbol id), you
 * can call the non-`Sid` version to get the symbol token text instead.
 */
interface ValueReader: AutoCloseable {
    fun nextToken(): Int
    fun currentToken(): Int
    fun ionType(): IonType?

    fun valueSize(): Int

    fun skip()

    fun nullValue(): IonType

    fun booleanValue(): Boolean

    fun longValue(): Long

    fun stringValue(): String
    fun symbolValue(): String?
    fun symbolValueSid(): Int

    fun clobValue(): ByteBuffer
    fun blobValue(): ByteBuffer

    fun lookupSid(sid: Int): String?

    fun listValue(): ListReader
    fun sexpValue(): SexpReader
    fun structValue(): StructReader

    fun annotations(): AnnotationIterator
    fun timestampValue(): Timestamp
    fun doubleValue(): Double
    fun decimalValue(): Decimal

    fun eexpValue(): EexpReader

    /**
     * Very low level API. Do not use unless you are trying to bypass macro evaluation.
     */
    fun eexpArgs(): EexpArgsReader = TODO()

    /**
     * Returns the major/minor version of the current IVM as two bytes in a short.
     * This method should only be called when positioned on an IVM.
     * * For Ion 1.0, returns 0x0100
     * * For Ion 1.1, returns 0x0101
     */
//    fun ivmVersion(): Short

    /**
     * Returns the major/minor version of this reader, each as one of the bytes of a short.
     * * For Ion 1.0, returns 0x0100
     * * For Ion 1.1, returns 0x0101
     */
    fun getIonVersion(): Short
}

interface ListReader: ValueReader

interface SexpReader: ValueReader

interface EexpReader: ValueReader

interface EexpArgsReader: ValueReader

interface StructReader: ValueReader {
    fun fieldNameSid() : Int
    fun fieldName() : String?
}

interface StreamReader: ValueReader {
    fun ivm(): StreamReader
}


