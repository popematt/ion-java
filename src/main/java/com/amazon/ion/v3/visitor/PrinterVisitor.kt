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
            printSymbolToken(annotations.getText(), annotations.getSid())
            print("::")
        }
    }
    override fun onField(fieldName: String?, fieldSid: Int) = apply {
        printSymbolToken(fieldName, fieldSid)
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
    override fun onFloat(value: Double) = print("${value}, ")
    override fun onDecimal(value: Decimal) = print("$value, ")
    override fun onTimestamp(value: Timestamp) = print("$value, ")
    override fun onString(value: String) = print("\"$value\", ")

    val identifierSymbolRegex = Regex("[A-Za-z_\$][A-Za-z0-9_\$]*")

    override fun onSymbol(value: String?, sid: Int) {
        printSymbolToken(value, sid)
        print(", ")
    }

    override fun onClob(value: ByteBuffer) = print("<clob>, ")
    override fun onBlob(value: ByteBuffer) = print("<blob>, ")

    private fun printSymbolToken(value: String?, sid: Int) {
        if (value == null) {
            print("\$$sid")
        } else if (value.matches(identifierSymbolRegex)) {
            print(value)
        } else {
            print("'$value'")
        }
    }
}
