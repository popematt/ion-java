package com.amazon.ion.v3.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer

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
    fun onSexpStart(symbolText: String?, sid: Int): VisitingReaderCallback {
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

    // TODO: ?
    //  fun onEExpressionArgument(name: String): VisitingReaderCallback? = null

}

internal interface LispyVisitor: VisitingReaderCallback {
    fun onClause(keyword: String): VisitingReaderCallback
}
