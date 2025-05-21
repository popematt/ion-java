package com.amazon.ion.v3.unused

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.macro.SystemMacro
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

class SystemMacroReader(
    private var macro: SystemMacro,
    private var args: ArgumentReader,
): ValueReader {

    companion object {
        private const val S_READY = 0
    }

    private var state = S_READY


    override fun nextToken(): Int {
        TODO("Not yet implemented")
    }

    override fun currentToken(): Int {
        TODO("Not yet implemented")
    }

    override fun isTokenSet(): Boolean {
        TODO("Not yet implemented")
    }

    override fun ionType(): IonType? {
        TODO("Not yet implemented")
    }

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

    override fun getIonVersion(): Short {
        TODO("Not yet implemented")
    }

    override fun seekTo(position: Int) {
        TODO("Not yet implemented")
    }

    override fun position(): Int {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun expressionGroup(): SequenceReader {
        TODO("Not yet implemented")
    }
}
