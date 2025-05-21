package com.amazon.ion.v3.visitor

import com.amazon.ion.*
import com.amazon.ion.v3.*
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * This is questionably useful.
 */
class SplitterVisitor(val v1: VisitingReaderCallback, val v2: VisitingReaderCallback): VisitingReaderCallback {

    private fun handleSplitDecision(method: VisitingReaderCallback.() -> VisitingReaderCallback?): VisitingReaderCallback? {
        val visitor1Response = v1.method()
        val visitor2Response = v2.method()
        if (visitor1Response == v1 && visitor2Response == v2) {
            return this
        } else if (visitor1Response == null) {
            return visitor2Response
        } else if (visitor2Response == null) {
            return visitor1Response
        } else {
            return SplitterVisitor(visitor1Response, visitor2Response)
        }
    }

    override fun onAnnotation(annotations: AnnotationIterator) = handleSplitDecision { onAnnotation(annotations.clone()) }
    override fun onField(fieldName: String?, fieldSid: Int) = handleSplitDecision { onField(fieldName, fieldSid) }
    override fun onValue(type: TokenType) = handleSplitDecision { onValue(type) }

    override fun onListStart() {
        v1.onListStart()
        v2.onListStart()
    }

    override fun onSexpStart() {
        v1.onSexpStart()
        v2.onSexpStart()
    }

    override fun onStructStart() {
        v1.onStructStart()
        v2.onStructStart()
    }

    override fun onListEnd() {
        v1.onListEnd()
        v2.onListEnd()
    }

    override fun onSexpEnd() {
        v1.onSexpEnd()
        v2.onSexpEnd()
    }

    override fun onStructEnd() {
        v1.onStructEnd()
        v2.onStructEnd()
    }

    override fun onNull(value: IonType) {
        v1.onNull(value)
        v2.onNull(value)
    }

    override fun onBoolean(value: Boolean) {
        v1.onBoolean(value)
        v2.onBoolean(value)
    }

    override fun onLongInt(value: Long) {
        v1.onLongInt(value)
        v2.onLongInt(value)
    }

    override fun onBigInt(value: BigInteger) {
        v1.onBigInt(value)
        v2.onBigInt(value)
    }

    override fun onFloat(value: Double) {
        v1.onFloat(value)
        v2.onFloat(value)
    }

    override fun onDecimal(value: Decimal) {
        v1.onDecimal(value)
        v2.onDecimal(value)
    }

    override fun onTimestamp(value: Timestamp) {
        v1.onTimestamp(value)
        v2.onTimestamp(value)
    }

    override fun onString(value: String) {
        v1.onString(value)
        v2.onString(value)
    }

    override fun onSymbol(value: String?, sid: Int) {
        v1.onSymbol(value, sid)
        v2.onSymbol(value, sid)
    }

    override fun onClob(value: ByteBuffer) {
        v1.onClob(value.slice())
        v2.onClob(value.slice())
    }

    override fun onBlob(value: ByteBuffer) {
        v1.onBlob(value.slice())
        v2.onBlob(value.slice())
    }
}
