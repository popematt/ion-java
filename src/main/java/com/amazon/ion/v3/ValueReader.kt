package com.amazon.ion.v3

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
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
 * [ValueReader] implementations MUST have a copy of their own symbol/macro tables, but MUST NOT
 * have any special logic for interpreting system values (other than IVMs, which are not strictly values).
 * The abstraction on top of a top-level value reader is responsible for reading the system values and
 * calling `initTables` on the top-level reader.
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

    fun isTokenSet(): Boolean

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

    fun macroInvocation(): MacroInvocation = TODO()

    /**
     * Returns a sequence of values.
     */
    fun expressionGroup(): SequenceReader

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

interface ExpressionGroupReader: SequenceReader, ValueReader {
    // Encoding Type?

    // TODO: Allow reading many tagless values at once. E.g.:
    fun readInts(dest: Array<Int>, offset: Int, count: Int): Int
}

interface ArgumentReader: ValueReader {
    /**
     * Like [nextToken], but allows seeking to arbitrary arguments of an E-Expression.
     *
     * NOTE: Seeking backwards is _not_ free when one of the arguments is a delimited expression group or container.
     *
     * TODO: List which [TokenTypeConst] values this can return.
     *
     * It's probably not a good idea to mix this method with [nextToken]
     */
    fun seekToArgument(signatureIndex: Int): Int

    fun seekToBeforeArgument(signatureIndex: Int)

    val signature: Array<Macro.Parameter>
}

class ArgEnvironment(
    val arguments: ArgumentBytecode,
    val parent: ArgEnvironment? = null,
)

interface ArgumentBytecode {
    fun constantPool(): Array<Any?>

    operator fun iterator(): Iterator<IntArray>

    fun getArgument(parameterIndex: Int): IntArray

    fun getList(start:Int, length:Int): ListReader
    fun getSexp(start:Int, length:Int): SexpReader
    fun getStruct(start:Int, length:Int, flexsymMode: Boolean): StructReader
    fun getMacro(macroAddress: Int): MacroV2
    fun getSymbol(sid: Int): String?

    companion object {

        @JvmStatic
        val EMPTY_ARG = intArrayOf(
            MacroBytecode.END_OF_ARGUMENT_SUBSTITUTION.opToInstruction()
        )

        @JvmStatic
        val _NO_ARGS = object : ArgumentBytecode {
            override fun iterator(): Iterator<IntArray> = object : Iterator<IntArray> {
                override fun hasNext(): Boolean = false
                override fun next(): IntArray = throw NoSuchElementException()
            }
            override fun constantPool(): Array<Any?> = emptyArray()
            override fun getArgument(parameterIndex: Int): IntArray = intArrayOf()
            override fun getList(start:Int, length:Int) = TODO()
            override fun getSexp(start:Int, length:Int) = TODO()
            override fun getStruct(start: Int, length: Int, flexsymMode: Boolean) = TODO()
            override fun getMacro(macroAddress: Int) = TODO()
            override fun getSymbol(sid: Int) = TODO()
        }
    }
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

interface StreamReader: ValueReader {
    // fun initTables()
}

interface TemplateReader: ValueReader {
    // TODO: Consider adding something that exposes the variable index.
}

