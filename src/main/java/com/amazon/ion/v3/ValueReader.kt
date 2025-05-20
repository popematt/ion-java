package com.amazon.ion.v3

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import java.nio.ByteBuffer

/**
 * The usage pattern is `nextToken()` followed by a method call that is suitable for handling that token.
 * You can't call `nextToken()` twice in a row.
 *
 * Once you call a method to read a value, you cannot read that value again. The only exception is
 * methods ending in `Sid`. If and only if one such method returns `-1` (for unknown symbol id), you
 * can call the non-`Sid` version to get the symbol token text instead.
 *
 * TODO: Rename this to "TokenReader" or "ExpressionReader" or similar.
 *
 *
 *
 * TODO: Consider adding a reader config option that determines whether to
 *   - update the position of the parent when the child is fully consumed
 *   - calculate the position of the parent when the child is created
 */
interface ValueReader: AutoCloseable {

    /**
     * Positions this reader on the next token in the data stream.
     */
    fun nextToken(): Int

    /**
     * Returns the type of token that the reader is positioned on.
     */
    fun currentToken(): Int

    /**
     * Returns the [IonType] of the current value, or `null` if the reader is not positioned on a value.
     */
    fun ionType(): IonType?

    fun valueSize(): Int

    /**
     * Skips the current token.
     */
    fun skip()

    fun nullValue(): IonType

    fun booleanValue(): Boolean

    fun longValue(): Long

    fun stringValue(): String
    fun symbolValue(): String?
    fun symbolValueSid(): Int

    fun clobValue(): ByteBuffer
    fun blobValue(): ByteBuffer

    fun listValue(): ListReader
    fun sexpValue(): SexpReader
    fun structValue(): StructReader

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
     */
    fun ivm(): Short


    /**
     * Returns the major/minor version of this reader, each as one of the bytes of a short.
     * * For Ion 1.0, returns 0x0100
     * * For Ion 1.1, returns 0x0101
     */
    fun getIonVersion(): Short

    // TODO: Move this to an `internal` interface?
    fun seekTo(position: Int)
    fun position(): Int
}

interface ListReader: ValueReader

interface SexpReader: ValueReader

interface EexpReader: ValueReader

interface ExpressionGroupReader: ValueReader {
    // Encoding Type?
}

interface EExpArgumentReader: ValueReader {
    // Returns one of the constants from `TokenTypeConst`
    fun seekToArgument(signatureIndex: Int): Int
    fun expressionGroup(): ListReader
}

// TODO: This should be changed so that fieldName and fieldNameSid can be safely called repeatedly (maybe?)
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


