package com.amazon.ion.v3.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer


interface ComplicatedVisitor {

    fun onAnnotation(annotations: AnnotationIterator) : ComplicatedVisitor?
    fun onField(fieldName: String?, fieldSid: Int) : ComplicatedVisitor?
    fun onValue(type: TokenType) : ComplicatedVisitor?
    fun onIVM(major: Int, minor: Int): ComplicatedVisitor = this

    fun onListStart(): ComplicatedVisitor?
    fun onListEnd(): ComplicatedVisitor?
    fun onSexpStart(): ComplicatedVisitor?
    fun onSexpEnd(): ComplicatedVisitor?
    fun onStructStart(): ComplicatedVisitor?
    fun onStructEnd(): ComplicatedVisitor?
    fun onNull(value: IonType): ComplicatedVisitor?
    fun onBoolean(value: Boolean): ComplicatedVisitor?
    fun onLongInt(value: Long): ComplicatedVisitor?
    fun onBigInt(value: BigInteger): ComplicatedVisitor?
    fun onFloat(value: Double): ComplicatedVisitor?
    fun onDecimal(value: Decimal): ComplicatedVisitor?
    fun onTimestamp(value: Timestamp): ComplicatedVisitor?
    fun onString(value: String): ComplicatedVisitor?
    fun onSymbol(value: String?, sid: Int): ComplicatedVisitor?
    fun onClob(value: ByteBuffer): ComplicatedVisitor?
    fun onBlob(value: ByteBuffer): ComplicatedVisitor?

    /**
     * Only implement this method if you want to bypass the macro evaluation
     */
    fun onEExpression(macro: Macro): ComplicatedVisitor? = null

    // TODO: For bypassing macro evaluation?
    //  fun onEExpressionArgument(name: String): VisitingReaderCallback? = null
}


interface ReaderVisitor {
    /**
     * Return a [ReaderVisitor] that should be used to read the value.
     * If null, then skip the value.
     */
    fun onField(fieldName: String?, fieldSid: Int) : ReaderVisitor? = this

    /**
     * Return a [ReaderVisitor] that should be used to read the value.
     * If null, then skip the value.
     */
    fun onAnnotation(annotations: AnnotationIterator) : ReaderVisitor? = this

    /**
     * Exposes that an IVM has been encountered.
     */
    fun onIVM(major: Int, minor: Int): Unit = Unit

    fun onStart()
    fun onEnd()

    fun onNull(value: IonType)
    fun onBoolean(value: Boolean)
    fun onLongInt(value: Long)
    fun onBigInt(value: BigInteger)
    fun onFloat(value: Double)
    fun onDecimal(value: Decimal)
    fun onTimestamp(value: Timestamp)
    fun onString(value: String)
    fun onSymbol(value: String?, sid: Int)
    fun onClob(value: ByteBuffer)
    fun onBlob(value: ByteBuffer)
    fun onList(): ReaderVisitor
    fun onSexp(): ReaderVisitor
    fun onStruct(): ReaderVisitor

    /**
     * Only implement this method if you want to bypass the macro evaluation.
     *
     * If bypassing macro evaluation, the returned visitor will get `onStart()`,
     * all the macro arguments in order, followed by `onEnd()`.
     * TODO: How to represent expression groups? Or maybe argument names?
     */
    fun onMacro(macro: Macro): ReaderVisitor? = null

    fun onArgument(name: String): ReaderVisitor? = null
}

/**
 *
 *
 * TODO: Naming. Perhaps "ReadingVisitor"?
 *
 * TODO: How do we step out early? Can we? We could have a singleton/sentinel instance of VisitingReaderCallback that
 *       signals to the "driver" to step out of a container early.
 *
 * TODO: Should there be a separate System-level visitor interface, or can we make this one work for both
 * [ApplicationReaderDriver] and [SystemReaderDriver]?
 */
interface VisitingReaderCallback {

    /**
     * Return a [VisitingReaderCallback] that should be used to read the value.
     * If null, then skip the value.
     */
    fun onAnnotation(annotations: AnnotationIterator) : VisitingReaderCallback? = this

    /**
     * Return a [VisitingReaderCallback] that should be used to read the value.
     * If null, then skip the value.
     *
     * (TODO: What do we think of this caveat?)
     * NOTE—this may be followed by 0 values or more than one value for the
     * given field name. It may also be called more than once with the same field name.
     */
    fun onField(fieldName: String?, fieldSid: Int) : VisitingReaderCallback? = this

    /**
     * Return a [VisitingReaderCallback] that should be used to read the value.
     * If null, then skip the value.
     */
    fun onValue(type: TokenType) : VisitingReaderCallback? = this

    /**
     * Exposes that an IVM has been encountered.
     */
    fun onIVM(major: Int, minor: Int): Unit = Unit

    fun onListStart()
    fun onListEnd()
    fun onSexpStart()

    /**
     * Start reading an s-expression.
     * Return the visitor that should be used for the tail of the s-expression.
     */
    fun onClause(symbolText: String?, sid: Int): VisitingReaderCallback {
        onSexpStart()
        onValue(TokenType.SYMBOL)?.onSymbol(symbolText, sid)
        return this
    }

    fun onSexpEnd()
    fun onStructStart()
    fun onStructEnd()

    fun onNull(value: IonType)
    fun onBoolean(value: Boolean)
    fun onLongInt(value: Long)
    fun onBigInt(value: BigInteger)
    fun onFloat(value: Double)
    fun onDecimal(value: Decimal)
    fun onTimestamp(value: Timestamp)
    fun onString(value: String)
    fun onSymbol(value: String?, sid: Int)
    fun onClob(value: ByteBuffer)
    fun onBlob(value: ByteBuffer)

    /**
     * Only implement this method if you want to bypass the macro evaluation
     */
    fun onEExpression(macro: Macro): VisitingReaderCallback? = null

    // TODO: For bypassing macro evaluation?
    //  fun onEExpressionArgument(name: String): VisitingReaderCallback? = null

}

internal interface LispyVisitor: VisitingReaderCallback {
    fun onClause(keyword: String): VisitingReaderCallback
}
