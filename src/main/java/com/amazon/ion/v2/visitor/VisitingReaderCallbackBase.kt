package com.amazon.ion.v2.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.v2.AnnotationIterator
import com.amazon.ion.v2.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer

abstract class VisitingReaderCallbackBase: VisitingReaderCallback {
    override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback? = this
    override fun onField(fieldName: String?, fieldSid: Int): VisitingReaderCallback? = this
    override fun onList(): VisitingReaderCallback? = this
    override fun onSexp(): VisitingReaderCallback? = this
    override fun onStruct(): VisitingReaderCallback? = this
    override fun onScalar(type: TokenType): VisitingReaderCallback? = this

    override fun onNull(value: IonType) = Unit
    override fun onBoolean(value: Boolean) = Unit
    override fun onLongInt(value: Long) = Unit

    override fun onBigInt(value: BigInteger) = Unit
    override fun onDouble(value: Double) = Unit
    override fun onDecimal(value: Decimal) = Unit
    override fun onTimestamp(value: Timestamp) = Unit
    override fun onString(value: String) = Unit
    override fun onSymbol(value: String?, sid: Int) = Unit
    override fun onClob(value: ByteBuffer) = Unit
    override fun onBlob(value: ByteBuffer) = Unit
}
