package com.amazon.ion.v3.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * FIXME: This might be redundant given that we can have default methods on interfaces.
 */
abstract class VisitingReaderCallbackBase: VisitingReaderCallback {
    override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback? = this
    override fun onField(fieldName: String?, fieldSid: Int): VisitingReaderCallback? = this
    override fun onValue(type: TokenType): VisitingReaderCallback? = this

    override fun onListStart() = Unit
    override fun onListEnd() = Unit
    override fun onSexpStart() = Unit
    override fun onSexpEnd() = Unit
    override fun onStructStart() = Unit
    override fun onStructEnd() = Unit

    override fun onNull(value: IonType) = Unit
    override fun onBoolean(value: Boolean) = Unit
    override fun onLongInt(value: Long) = Unit
    override fun onBigInt(value: BigInteger) = Unit
    override fun onFloat(value: Double) = Unit
    override fun onDecimal(value: Decimal) = Unit
    override fun onTimestamp(value: Timestamp) = Unit
    override fun onString(value: String) = Unit
    override fun onSymbol(value: String?, sid: Int) = Unit
    override fun onClob(value: ByteBuffer) = Unit
    override fun onBlob(value: ByteBuffer) = Unit
}
