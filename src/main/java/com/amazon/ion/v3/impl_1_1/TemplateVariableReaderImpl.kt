package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.Expression.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

// TODO: See if there's a good way to get rid of this and just read the values directly.
//       Or pretend that everything is in an expression group so that we can hand out an
//       expression group (sequence) reader instead.
class TemplateVariableReaderImpl(
    val pool: TemplateResourcePool,
    var signatureIndex: Int,
    var arguments: ArgumentReader,
): ValueReader, ListReader, SexpReader {
    var hasAnnotations = false

    fun init(
        signatureIndex: Int,
        arguments: ArgumentReader,
     ) {
        this.arguments = arguments
        this.signatureIndex = signatureIndex
        this.hasAnnotations = false
    }

    override fun nextToken(): Int {
        val i = signatureIndex
        // TODO: Is this the correct order?
        if (i < 0) {
            return TokenTypeConst.END
        }
        if (!hasAnnotations) {
            val token = arguments.seekToArgument(i)
            if (token == TokenTypeConst.ANNOTATIONS) {
                hasAnnotations = true
            } else {
                // Nothing more to be found in this variable ref.
                signatureIndex = -1
            }
            return token
        } else {
            val token = arguments.nextToken()
            hasAnnotations = false
            signatureIndex = -1
            return token
        }
    }

    // TODO: Make sure that this also returns END when at the end of the input.
    override fun skip() = TODO("Cannot skip a variable reference. Only the values it contains.")
    override fun currentToken(): Int = arguments.currentToken()
    override fun ionType(): IonType? = arguments.ionType()
    override fun valueSize(): Int = arguments.valueSize()
    override fun nullValue(): IonType = arguments.nullValue()
    override fun booleanValue(): Boolean = arguments.booleanValue()
    override fun longValue(): Long = arguments.longValue()
    override fun stringValue(): String = arguments.stringValue()
    override fun symbolValue(): String? = arguments.symbolValue()
    override fun symbolValueSid(): Int = arguments.symbolValueSid()
    override fun lookupSid(sid: Int): String? = arguments.lookupSid(sid)
    override fun timestampValue(): Timestamp = arguments.timestampValue()
    override fun clobValue(): ByteBuffer = arguments.clobValue()
    override fun blobValue(): ByteBuffer = arguments.blobValue()
    override fun listValue(): ListReader = arguments.listValue()
    override fun sexpValue(): SexpReader = arguments.sexpValue()
    override fun structValue(): StructReader = arguments.structValue()
    override fun annotations(): AnnotationIterator = arguments.annotations()
    override fun doubleValue(): Double = arguments.doubleValue()
    override fun decimalValue(): Decimal = arguments.decimalValue()

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")

    override fun getIonVersion(): Short = 0x0101

    override fun seekTo(position: Int) = TODO("This method only applies to raw readers.")
    override fun position(): Int  = TODO("This method only applies to raw readers.")

    override fun close() {
        pool.variables.add(this)
    }
}
