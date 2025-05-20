package com.amazon.ion.v3.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer

// TODO: System-level visitor?
//       Or perhaps the symbol table management should move into the visitor driver?
interface VisitingReaderCallback {

    // Return a [ReaderCallback] that should be used to read the value.
    // If null, then skip the value.
    fun onAnnotation(annotations: AnnotationIterator) : VisitingReaderCallback?
    fun onField(fieldName: String?, fieldSid: Int) : VisitingReaderCallback?
    fun onValue(type: TokenType) : VisitingReaderCallback?

    fun onIVM(major: Int, minor: Int): Unit = Unit

    fun onListStart()
    fun onListEnd()
    fun onSexpStart()
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

    // TODO: How do we step out early? Can we? We could have a singleton instance of ReaderCallback that allows early return.
}
