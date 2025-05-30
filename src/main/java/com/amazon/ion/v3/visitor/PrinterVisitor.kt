package com.amazon.ion.v3.visitor

import com.amazon.ion.Decimal
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import com.amazon.ion.Timestamp
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.TokenType
import java.math.BigInteger
import java.nio.ByteBuffer

object PrinterVisitorTop: VisitingReaderCallbackBase() {
    override fun onValue(type: TokenType): VisitingReaderCallback? {
        println()
        return PrinterVisitor.onValue(type)
    }
    override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback? {
        println()
        return PrinterVisitor.onAnnotation(annotations)
    }
}

object PrinterVisitor: VisitingReaderCallback {
    override fun onAnnotation(annotations: AnnotationIterator) = apply {
        while (annotations.hasNext()) {
            annotations.next()
            print(annotations.getText() ?: "\$0")
            print("::")
        }
    }
    override fun onField(fieldName: String?, fieldSid: Int) = apply {
        print(fieldName ?: "\$fieldSid")
        print(": ")
    }
    override fun onValue(type: TokenType): VisitingReaderCallback = this

    override fun onListStart() = print("[")
    override fun onSexpStart() = print("(")
    override fun onStructStart() = print("{")
    override fun onListEnd() = print("], ")
    override fun onSexpEnd() = print("), ")
    override fun onStructEnd() = print("}, ")

    override fun onNull(value: IonType) = print("null.$value, ")
    override fun onBoolean(value: Boolean) = print("$value, ")
    override fun onLongInt(value: Long) = print("$value, ")
    override fun onBigInt(value: BigInteger) = print("$value, ")
    override fun onFloat(value: Double) = print("$value, ")
    override fun onDecimal(value: Decimal) = print("$value, ")
    override fun onTimestamp(value: Timestamp) = print("$value, ")
    override fun onString(value: String) = print("\"$value\", ")
    override fun onSymbol(value: String?, sid: Int) = print("${value ?: "\$sid"}, ")

    override fun onClob(value: ByteBuffer) = print("<clob>, ")
    override fun onBlob(value: ByteBuffer) = print("<blob>, ")
}
