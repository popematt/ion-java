package com.amazon.ion.v3.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import com.amazon.ion.Timestamp
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer

class TranscoderVisitor(private val writer: IonWriter): VisitingReaderCallback {
    override fun onAnnotation(annotations: AnnotationIterator) = apply {
        while (annotations.hasNext()) {
            annotations.next()
            writer.setTypeAnnotations(annotations.getText())
        }
    }
    override fun onField(fieldName: String?, fieldSid: Int) = apply { writer.setFieldName(fieldName) }
    override fun onValue(type: TokenType): VisitingReaderCallback = this

    override fun onListStart() = writer.stepIn(IonType.LIST)
    override fun onSexpStart() = writer.stepIn(IonType.SEXP)
    override fun onStructStart() = writer.stepIn(IonType.STRUCT)
    override fun onListEnd() = writer.stepOut()
    override fun onSexpEnd() = writer.stepOut()
    override fun onStructEnd() = writer.stepOut()

    override fun onNull(value: IonType) = writer.writeNull(value)
    override fun onBoolean(value: Boolean) = writer.writeBool(value)
    override fun onLongInt(value: Long) = writer.writeInt(value)
    override fun onBigInt(value: BigInteger) = writer.writeInt(value)
    override fun onFloat(value: Double) = writer.writeFloat(value)
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

    fun flush() = writer.flush()
    fun close() = writer.close()
}
