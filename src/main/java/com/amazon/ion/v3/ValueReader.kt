package com.amazon.ion.v3

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import java.math.BigDecimal
import java.nio.ByteBuffer

/**
 * The usage pattern is generally `nextToken()` followed by a method call that is suitable for handling
 * that token. You cannot call `nextToken()` twice in a row.
 *
 * Once you call a method to read a value, you cannot read that value again. The only exception is
 * methods ending in `Sid`. If and only if one such method returns `-1` (for unknown symbol id), you
 * can call the non-`Sid` version to get the symbol token text instead.
 *
 * Most users should not use this API. It requires adherence to some strict rules about the order in
 * which methods are called, and it includes very little in the way of safety checks. If you call the
 * wrong method, you will probably start reading incorrect data rather than getting a helpful exception.
 *
 * Instead, most users should consider [StreamReaderAsIonReader] for the classic [IonReader] interface,
 * or [ApplicationReaderDriver][com.amazon.ion.v3.visitor.ApplicationReaderDriver] for a modern,
 * high-performance API.
 *
 * TODO: Rename this to "TokenReader" or "ExpressionReader" or similar.
 *
 * TODO: Consider adding a reader config option that determines whether to...
 *   (a) update the position of the parent when the child is fully consumed OR
 *   (b) calculate the position of the parent when the child is created
 *   This is really only relevant if you need to be simultaneously reading two
 *   containers, and the one that appears earlier in the stream is a delimited
 *   container.
 */
interface ValueReader: AutoCloseable {

    /**
     * Positions this reader on the next token in the data stream.
     *
     * TODO: List which [TokenTypeConst] values this can return.
     */
    fun nextToken(): Int

    /**
     * Returns the type of token that the reader is positioned on, or `null` if it is not positioned on a value token.
     */
    fun currentToken(): Int

    /**
     * Returns the [IonType] of the current value, or `null` if the reader is not positioned on a value.
     *
     * TODO: Is this absolutely necessary?
     */
    fun ionType(): IonType?

    /**
     * Returns the size of the value, or `-1` if the token size is not known (i.e. the value is a delimited container).
     * This is mostly useful for determining the required integer or float size.
     *
     * TODO: Should this be `tokenSize` or `expressionSize` instead?
     */
    fun valueSize(): Int

    /**
     * Skips the current token.
     *
     * Skip can be called whenever the reader is positioned on a value token.
     * An IVM may not be skipped.
     */
    fun skip()

    fun nullValue(): IonType

    fun booleanValue(): Boolean

    fun longValue(): Long
    // TODO: fun bigIntegerValue(): BigInteger

    fun stringValue(): String
    fun symbolValue(): String?
    fun symbolValueSid(): Int

    /**
     * If you used [symbolValueSid] or another SID method and got a valid SID, you cannot then call
     * the non-SID version of the method (because the reader has moved on). Instead, you can call this
     * method to get the symbol text. It must be called immediately, before calling [nextToken], because
     * calling [nextToken] (at the top level) could result in the reader having a different symbol table.
     */
    fun lookupSid(sid: Int): String?

    /**
     * Returns a [ByteBuffer] with the `position` set to the start of the clob content and the `limit` set to the
     * end of the clob content.
     *
     * After this method has been called, the reader is ready for [nextToken].
     *
     * TODO: Specify the endianness of the returned ByteBuffer.
     *
     * TODO: Consider adding a "recycle: Boolean" parameter. That way, if the client is immediately copying things out
     *   of the [ByteBuffer], they can indicate that it is safe to recycle, and we don't have to allocate a new one for
     *   each call to this method.
     */
    fun clobValue(): ByteBuffer
    /** See [clobValue]. */
    fun blobValue(): ByteBuffer

    /**
     * Returns a reader for this container. The current reader is not guaranteed to be positioned correctly for the
     * next value until this reader is closed.
     */
    fun listValue(): ListReader
    /**
     * Returns a reader for this container. The current reader is not guaranteed to be positioned correctly for the
     * next value until this reader is closed.
     */
    fun sexpValue(): SexpReader
    /**
     * Returns a reader for this container. The current reader is not guaranteed to be positioned correctly for the
     * next value until this reader is closed.
     */
    fun structValue(): StructReader

    /**
     * Returns an iterator over the annotations.
     * It is safe to call [nextToken] after this is called, but before the [AnnotationIterator] has been closed.
     */
    fun annotations(): AnnotationIterator

    fun timestampValue(): Timestamp

    fun doubleValue(): Double

    fun decimalValue(): Decimal

    // TODO: Change this to return a macro ID. Then, add another method that can be used to
    //       read an E-Expr that accepts a macro definition as its argument, or maybe there's
    //       a separate evaluating reader that gets used instead.
    /**
     * Returns the current macro id.
     * If the returned value is positive, it is exactly the macro address in user space.
     * If the returned value is negative, it is `(id or Int.MIN_VALUE)` where `id` is a System Macro ID.
     */
    fun eexpValue(): Int = throw IonException("E-expressions are not supported by this reader")

    fun eexpMacroRef(): MacroRef = throw IonException("E-expressions are not supported by this reader")

    /**
     * Very low level API. Do not use unless you are trying to bypass macro evaluation.
     * For example, you would use this for transcoding E-expressions between text and binary or
     * for hydrating a POJO with a known macro definition without using the evaluator.
     */
    fun eexpArgs(signature: List<Macro.Parameter>): EExpArgumentReader = TODO()

    /**
     * Returns the major/minor version of the current IVM as two bytes in a short.
     * This method should only be called when positioned on an IVM.
     * * For Ion 1.0, returns 0x0100
     * * For Ion 1.1, returns 0x0101
     *
     * Once this method has been called, the next call must be to [nextToken].
     *
     * You cannot call [skip] when positioned on an IVM.
     */
    fun ivm(): Short


    /**
     * Returns the major/minor version of _this_ reader, each as one of the bytes of a short.
     * * For Ion 1.0, returns 0x0100
     * * For Ion 1.1, returns 0x0101
     */
    fun getIonVersion(): Short

    /**
     * Sets the position of this reader.
     *
     * DO NOT USE unless you really know what you're doing.
     *
     * TODO: Move this to a `ValueReaderInternal` interface?
     */
    fun seekTo(position: Int)

    /**
     * Gets the current position of this reader.
     *
     * TODO: Move this to a `ValueReaderInternal` interface?
     */
    fun position(): Int
}

interface SequenceReader: ValueReader
interface ListReader: SequenceReader, ValueReader
interface SexpReader: SequenceReader, ValueReader

// TODO: Do we really need this?
interface EexpReader: ValueReader

// TODO: Do we really need this?
interface ExpressionGroupReader: SequenceReader, ValueReader {
    // Encoding Type?
}

interface EExpArgumentReader: ValueReader {
    /**
     * Like [nextToken], but allows seeking to arbitrary arguments of an E-Expression.
     *
     * NOTE: Seeking backwards is _not_ free when one of the arguments is a delimited expression group or container.
     *
     * TODO: List which [TokenTypeConst] values this can return.
     */
    fun seekToArgument(signatureIndex: Int): Int

    /**
     * Returns a sequence of values.
     */
    fun expressionGroup(): SequenceReader
}

interface StructReader: ValueReader {
    /**
     * If it returns -1, the caller must try again, calling `fieldName()` instead.
     */
    fun fieldNameSid() : Int

    /**
     * If you call this, it moves on from the field name, and you can't call `fieldName()` again.
     * If you must know the SID, you must call `fieldNameSid` before this method.
     */
    fun fieldName() : String?
}

interface StreamReader: ValueReader


