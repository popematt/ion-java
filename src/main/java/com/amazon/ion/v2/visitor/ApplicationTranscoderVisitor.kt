package com.amazon.ion.v2.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import com.amazon.ion.Timestamp
import com.amazon.ion.v2.AnnotationIterator
import com.amazon.ion.v2.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer

class ApplicationTranscoderVisitor(val writer: IonWriter): VisitingReaderCallback {
    override fun onAnnotation(annotations: AnnotationIterator) = apply {
        writer.setTypeAnnotations(*annotations.toStringArray())
    }

    override fun onList(): VisitingReaderCallback = apply { writer.stepIn(IonType.LIST) }
    override fun onSexp(): VisitingReaderCallback = apply { writer.stepIn(IonType.SEXP) }
    override fun onStruct(): VisitingReaderCallback = apply { writer.stepIn(IonType.STRUCT) }

    override fun onListEnd() = writer.stepOut()
    override fun onSexpEnd() = writer.stepOut()
    override fun onStructEnd() = writer.stepOut()

    override fun onField(fieldName: String?, fieldSid: Int) = apply { writer.setFieldName(fieldName) }

    override fun onScalar(type: TokenType): VisitingReaderCallback = this
    override fun onNull(value: IonType) = writer.writeNull(value)
    override fun onBoolean(value: Boolean) = writer.writeBool(value)
    override fun onLongInt(value: Long) = writer.writeInt(value)
    override fun onBigInt(value: BigInteger) = writer.writeInt(value)
    override fun onDouble(value: Double) = writer.writeFloat(value)
    override fun onDecimal(value: Decimal) = writer.writeDecimal(value)
    override fun onTimestamp(value: Timestamp) = writer.writeTimestamp(value)
    override fun onString(value: String) = writer.writeString(value)
    override fun onSymbol(value: String?, sid: Int) = writer.writeSymbol(value)

    override fun onClob(value: ByteBuffer) {
        val len = value.limit() - value.position()
        val buffer = ByteArray(len)
        value.get(buffer)
        writer.writeClob(buffer)
    }

    override fun onBlob(value: ByteBuffer) {
        val len = value.limit() - value.position()
        val buffer = ByteArray(len)
        value.get(buffer)
        writer.writeBlob(buffer)
    }
}
