package com.amazon.ion.v3.visitor

import com.amazon.ion.*
import com.amazon.ion.v3.*
import java.math.BigInteger
import java.nio.ByteBuffer

class IonDatagramHydrator(dg: IonDatagram): VisitingReaderCallback {

    private var currentContainer: IonContainer = dg
    private val ion: IonSystem = dg.system
    private var annotations: Array<String?> = emptyArray()
    private var fieldName: String? = null

    override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback {
        this.annotations = annotations.toStringArray()
        return this
    }

    override fun onField(fieldName: String?, fieldSid: Int): VisitingReaderCallback {
        this.fieldName = fieldName
        return this
    }

    private fun addValue(value: IonValue) {
        value.setTypeAnnotations(*annotations)
        annotations = emptyArray()
        val parent = currentContainer
        when (parent) {
            is IonSequence -> parent.add(value)
            is IonStruct -> {
                parent.add(fieldName, value)
                fieldName = null
            }
        }
    }

    override fun onListStart() {
        val child = ion.newEmptyList()
        addValue(child)
        currentContainer = child
    }

    override fun onListEnd() {
        currentContainer = currentContainer.container
    }

    override fun onSexpStart() {
        val child = ion.newEmptySexp()
        addValue(child)
        currentContainer = child
    }

    override fun onSexpEnd() {
        currentContainer = currentContainer.container
    }

    override fun onStructStart() {
        val child = ion.newEmptyStruct()
        addValue(child)
        currentContainer = child
    }

    override fun onStructEnd() {
        currentContainer = currentContainer.container
    }

    override fun onNull(value: IonType) {
        addValue(ion.newNull(value))
    }

    override fun onBoolean(value: Boolean) {
        addValue(ion.newBool(value))
    }

    override fun onLongInt(value: Long) {
        addValue(ion.newInt(value))
    }

    override fun onBigInt(value: BigInteger) {
        addValue(ion.newInt(value))
    }

    override fun onFloat(value: Double) {
        addValue(ion.newFloat(value))
    }

    override fun onDecimal(value: Decimal) {
        addValue(ion.newDecimal(value))
    }

    override fun onTimestamp(value: Timestamp) {
        addValue(ion.newTimestamp(value))
    }

    override fun onString(value: String) {
        addValue(ion.newString(value))
    }

    override fun onSymbol(value: String?, sid: Int) {
        addValue(ion.newSymbol(value))
    }

    override fun onClob(value: ByteBuffer) {
        val len = value.limit() - value.position()
        val byteArray = ByteArray(len)
        value.get(byteArray)
        addValue(ion.newClob(byteArray))
    }

    override fun onBlob(value: ByteBuffer) {
        val len = value.limit() - value.position()
        val byteArray = ByteArray(len)
        value.get(byteArray)
        addValue(ion.newBlob(byteArray))
    }
}
