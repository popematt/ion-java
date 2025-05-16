package com.amazon.ion.v2.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.v2.AnnotationIterator
import com.amazon.ion.v2.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer

// TODO: System-level visitor?
//       Or perhaps the symbol table management should move into the visitor driver?
interface VisitingReaderCallback {

    // Return a [ReaderCallback] that should be used to read the value.
    // If null, then skip the value.
    fun onAnnotation(annotations: AnnotationIterator) : VisitingReaderCallback?
    fun onField(fieldName: String?, fieldSid: Int) : VisitingReaderCallback?
    fun onList() : VisitingReaderCallback?
    fun onListEnd()
    fun onSexp() : VisitingReaderCallback?
    fun onSexpEnd()
    fun onStruct() : VisitingReaderCallback?
    fun onStructEnd()
    fun onScalar(type: TokenType) : VisitingReaderCallback?

    fun onNull(value: IonType)
    fun onBoolean(value: Boolean)
    fun onInt(value: Int) = onLongInt(value.toLong())
    fun onLongInt(value: Long)
    fun onBigInt(value: BigInteger)
    fun onFloat(value: Float) = onDouble(value.toDouble())
    fun onDouble(value: Double)
    fun onDecimal(value: Decimal)
    fun onTimestamp(value: Timestamp)
    fun onString(value: String)
    fun onSymbol(value: String?, sid: Int)
    fun onClob(value: ByteBuffer)
    fun onBlob(value: ByteBuffer)

    // TODO: How do we step out early? Can we? We could have a singleton instance of ReaderCallback that allows early return.
}
