package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer

/**
 * Singleton for empty expression groups.
 */
object NoneReader: TemplateReader, SequenceReader {
    override fun macroValue(): MacroV2 {
        TODO("Not yet implemented")
    }

    override fun nextToken(): Int = TokenTypeConst.END
    override fun currentToken(): Int = TokenTypeConst.END
    override fun isTokenSet(): Boolean = true
    override fun ionType(): IonType? = null

    override fun valueSize(): Int {
        TODO("Not yet implemented")
    }

    override fun skip() {
        TODO("Not yet implemented")
    }

    override fun nullValue(): IonType {
        TODO("Not yet implemented")
    }

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

    override fun structValue(): StructReader {
        TODO("Not yet implemented")
    }

    override fun annotations(): AnnotationIterator {
        TODO("Not yet implemented")
    }

    override fun timestampValue(): Timestamp {
        TODO("Not yet implemented")
    }

    override fun doubleValue(): Double {
        TODO("Not yet implemented")
    }

    override fun decimalValue(): Decimal {
        TODO("Not yet implemented")
    }

    override fun ivm(): Short {
        TODO("Not yet implemented")
    }

    override fun getIonVersion(): Short = 0x0101

    override fun seekTo(position: Int) {
        TODO("Not yet implemented")
    }

    override fun position(): Int {
        TODO("Not yet implemented")
    }

    // Nothing to do.
    override fun close() = Unit

    override fun expressionGroup(): SequenceReader {
        TODO("Not yet implemented")
    }
}
