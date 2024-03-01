package com.amazon.ion

import com.amazon.ion.impl.*
import com.amazon.ion.system.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.Charset
import java.util.*

private fun Any.readOnlyErr(): Nothing = throw ReadOnlyValueException(this.javaClass)

// TODO: clean up isNull vs null values
// TODO: implement equals for all types
// TODO: implement hashcode for all types
sealed class ImmutableIonValue(protected val fieldName: SymbolToken?, protected val annotations: Array<out SymbolToken>, protected val isNull: Boolean, protected val type: IonType): IonValue {

    final override fun isNullValue(): Boolean = isNull

    final override fun getFieldName(): String? = fieldName?.assumeText()
    final override fun getFieldNameSymbol(): SymbolToken? = fieldName
    final override fun getFieldId(): Int = fieldName?.takeIf { it.text != null }?.sid ?: 0

    final override fun getTypeAnnotations(): Array<String?> = Array(annotations.size) { i -> annotations[i].text }
    final override fun getTypeAnnotationSymbols(): Array<out SymbolToken> = annotations.copyOf()
    final override fun hasTypeAnnotation(annotation: String): Boolean = annotations.any { it.text == annotation }

    final override fun getType(): IonType = type
    final override fun isReadOnly(): Boolean = true
    final override fun makeReadOnly() = Unit

    final override fun removeFromContainer(): Boolean = readOnlyErr()
    final override fun removeTypeAnnotation(annotation: String?) = readOnlyErr()
    final override fun clearTypeAnnotations() = readOnlyErr()
    final override fun setTypeAnnotations(vararg annotations: String?) = readOnlyErr()
    final override fun setTypeAnnotationSymbols(vararg annotations: SymbolToken?) = readOnlyErr()
    final override fun addTypeAnnotation(annotation: String?) = readOnlyErr()


    // TODO: Can we get away with this?
    final override fun getContainer(): IonContainer? = null

    // TODO: Contract specifies that this is thrown for IonDatagrams, but can we just throw this for all?
    final override fun topLevelValue(): IonValue = throw UnsupportedOperationException()


    override fun getSystem(): IonSystem {
        TODO("Not yet implemented")
    }

    override fun toString(writerBuilder: IonTextWriterBuilder?): String {
        TODO("Not yet implemented")
    }

    override fun toPrettyString(): String = toString(IonTextWriterBuilder.pretty())

    // TODO: I think this is okay. The contract allows returning null when not backed by Ion binary. However the DOM
    //       seems like it should be independent of the encoding.
    override fun getSymbolTable(): SymbolTable? = null


    protected inline fun IonWriter.superWriteTo(writeNonNullValue: IonWriter.() -> Unit) {
        setTypeAnnotationSymbols(*annotations)
        fieldName?.let { setFieldNameSymbol(it) }
        if (isNull) {
            writeNull(type)
        } else {
            writeNonNullValue()
        }
    }

    protected inline fun <T> returnIfNotNull(lazyValue: () -> T): T = if (!isNull) lazyValue() else throw NullValueException()

    protected inline fun <reified T: IonValue> Any?.isEquivalent(valueCheck: (T) -> Boolean): Boolean = isEquivalent(this, valueCheck)

    protected inline fun <reified T: IonValue> isEquivalent(other: Any?, valueCheck: (T) -> Boolean): Boolean {
        if (other !is T) return false
        // TODO: Do we check the field name? I hope not.
        if (!this.annotations.contentEquals(other.typeAnnotationSymbols)) return false
        if (this.isNull != other.isNullValue) return false
        return valueCheck(other)
    }
}

class ImmutableIonNull(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean): IonNull, ImmutableIonValue(fieldName, annotations, isNull, IonType.NULL) {
    override fun clone(): IonNull = this
    override fun writeTo(writer: IonWriter) { writer.superWriteTo { /* empty because this is always null */ } }
    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }
}

class ImmutableIonBool(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, private val value: Boolean): IonBool, ImmutableIonValue(fieldName, annotations, isNull, IonType.BOOL) {
    override fun clone(): IonBool = this
    override fun writeTo(writer: IonWriter) { writer.superWriteTo { writeBool(value) } }
    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }
    override fun booleanValue(): Boolean = returnIfNotNull { value }
    override fun setValue(b: Boolean) = readOnlyErr()
    override fun setValue(b: Boolean?) = readOnlyErr()

    // This works even with IonValueLite subclasses... no guarantee it works the other way though.
    override fun equals(other: Any?): Boolean = other.isEquivalent<IonBool> { it.booleanValue() == this.booleanValue() }
}

class ImmutableIonInt private constructor(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, private val longValue: Long, private val bigIntegerValue: BigInteger?): IonInt, ImmutableIonValue(fieldName, annotations, isNull, IonType.INT) {
    constructor(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, longValue: Long): this(fieldName, annotations, isNull, longValue, null)
    constructor(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, bigIntegerValue: BigInteger): this(fieldName, annotations, isNull, 0, bigIntegerValue)

    override fun clone(): IonInt = this
    override fun writeTo(writer: IonWriter) {
        writer.superWriteTo {
            if (bigIntegerValue == null) {
                writeInt(longValue)
            } else {
                writeInt(bigIntegerValue)
            }
        }
    }

    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun intValue(): Int = returnIfNotNull {bigIntegerValue?.toInt() ?: longValue.toInt() }
    override fun longValue(): Long = returnIfNotNull {bigIntegerValue?.toLong() ?: longValue }
    override fun bigIntegerValue(): BigInteger = returnIfNotNull {bigIntegerValue ?: longValue.toBigInteger() }
    override fun bigDecimalValue(): BigDecimal = returnIfNotNull {bigIntegerValue?.toBigDecimal() ?: longValue.toBigDecimal() }

    override fun getIntegerSize(): IntegerSize? {
        return when {
            isNull -> null
            bigIntegerValue != null -> when (bigIntegerValue.bitLength()) {
                in 0..31 -> IntegerSize.INT
                in 32..63 -> IntegerSize.LONG
                else -> IntegerSize.BIG_INTEGER
            }
            longValue > Int.MAX_VALUE ||
                longValue < Int.MIN_VALUE -> IntegerSize.INT
            else -> IntegerSize.LONG
        }
    }

    override fun setValue(value: Int) = readOnlyErr()
    override fun setValue(value: Long) = readOnlyErr()
    override fun setValue(content: Number?) = readOnlyErr()

    override fun isNumericValue(): Boolean = !isNull
}

class ImmutableIonFloat(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, private val doubleValue: Double): IonFloat, ImmutableIonValue(fieldName, annotations, isNull, IonType.FLOAT) {

    override fun clone(): IonFloat = this
    override fun writeTo(writer: IonWriter) {
        writer.superWriteTo { writeFloat(doubleValue) }
    }

    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun isNumericValue(): Boolean = !isNull && doubleValue.isFinite()

    override fun floatValue(): Float = returnIfNotNull { doubleValue.toFloat() }
    override fun doubleValue(): Double = returnIfNotNull { doubleValue }
    override fun bigDecimalValue(): BigDecimal = returnIfNotNull { doubleValue.toBigDecimal() }

    override fun setValue(value: Float) = readOnlyErr()
    override fun setValue(value: Double) = readOnlyErr()
    override fun setValue(value: BigDecimal?) = readOnlyErr()
}

class ImmutableIonDecimal(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, private val bigDecimalValue: BigDecimal): IonDecimal, ImmutableIonValue(fieldName, annotations, isNull, IonType.DECIMAL) {

    override fun clone(): IonDecimal = this
    override fun writeTo(writer: IonWriter) {
        writer.superWriteTo { writeDecimal(bigDecimalValue) }
    }

    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun isNumericValue(): Boolean = !isNull
    override fun decimalValue(): Decimal = returnIfNotNull { Decimal.valueOf(bigDecimalValue) }
    override fun bigDecimalValue(): BigDecimal = returnIfNotNull { bigDecimalValue }
    override fun floatValue(): Float = returnIfNotNull { bigDecimalValue.toFloat() }
    override fun doubleValue(): Double = returnIfNotNull { bigDecimalValue.toDouble() }

    override fun setValue(value: Long) = readOnlyErr()
    override fun setValue(value: Float) = readOnlyErr()
    override fun setValue(value: Double) = readOnlyErr()
    override fun setValue(value: BigDecimal?) = readOnlyErr()
}

class ImmutableIonTimestamp(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, private val timestampValue: Timestamp?): IonTimestamp, ImmutableIonValue(fieldName, annotations, timestampValue == null, IonType.TIMESTAMP) {

    override fun clone(): IonTimestamp = this
    override fun writeTo(writer: IonWriter) { writer.superWriteTo { writeTimestamp(timestampValue) } }
    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun timestampValue(): Timestamp? = timestampValue
    override fun dateValue(): Date? = timestampValue?.dateValue()
    override fun getMillis(): Long = returnIfNotNull { timestampValue!!.millis }
    override fun getDecimalMillis(): BigDecimal? =  timestampValue?.decimalMillis
    override fun getLocalOffset(): Int? = returnIfNotNull { timestampValue?.localOffset }

    override fun setValue(timestamp: Timestamp?) = readOnlyErr()
    override fun setValue(millis: BigDecimal?, localOffset: Int?) = readOnlyErr()
    override fun setValue(millis: Long, localOffset: Int?) = readOnlyErr()
    override fun setMillis(millis: Long) = readOnlyErr()
    override fun setDecimalMillis(millis: BigDecimal?) = readOnlyErr()
    override fun setMillisUtc(millis: Long) = readOnlyErr()
    override fun setTime(value: Date?) = readOnlyErr()
    override fun setCurrentTime() = readOnlyErr()
    override fun setCurrentTimeUtc() = readOnlyErr()
    override fun setLocalOffset(minutes: Int) = readOnlyErr()
    override fun setLocalOffset(minutes: Int?) = readOnlyErr()
    override fun makeNull() = readOnlyErr()
}

class ImmutableIonString(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, private val value: String?): IonString, ImmutableIonValue(fieldName, annotations, isNull, IonType.STRING) {
    override fun clone(): IonString = this
    override fun writeTo(writer: IonWriter) { writer.superWriteTo { writeString(value) } }
    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun stringValue(): String? = value
    override fun setValue(value: String?) = readOnlyErr()
}

class ImmutableIonSymbol(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, private val value: SymbolToken?): IonSymbol, ImmutableIonValue(fieldName, annotations, isNull, IonType.SYMBOL) {
    override fun clone(): IonSymbol = this
    override fun writeTo(writer: IonWriter) { writer.superWriteTo { writeSymbolToken(value) } }
    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun stringValue(): String? = value?.assumeText()
    override fun symbolValue(): SymbolToken? = value
    override fun getSymbolId(): Int = returnIfNotNull { value!!.sid }

    override fun setValue(value: String?) = readOnlyErr()
}

class ImmutableIonBlob(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, private val value: ByteArray?): IonBlob, ImmutableIonValue(fieldName, annotations, isNull, IonType.BLOB) {
    override fun clone(): IonBlob = this
    override fun writeTo(writer: IonWriter) { writer.superWriteTo { writeBlob(value) } }
    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun printBase64(out: Appendable) = returnIfNotNull {
        newInputStream().use { byteStream -> _Private_Utils.writeAsBase64(byteStream, out) }
    }

    override fun newInputStream(): InputStream? = value?.let { ByteArrayInputStream(it) }

    override fun getBytes(): ByteArray? = value?.copyOf()
    override fun byteSize(): Int = returnIfNotNull { value!!.size }

    override fun setBytes(bytes: ByteArray?) = readOnlyErr()
    override fun setBytes(bytes: ByteArray?, offset: Int, length: Int) = readOnlyErr()
}

class ImmutableIonClob(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, private val value: ByteArray?): IonClob, ImmutableIonValue(fieldName, annotations, isNull, IonType.CLOB) {
    override fun clone(): IonClob = this
    override fun writeTo(writer: IonWriter) { writer.superWriteTo { writeBlob(value) } }
    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun newReader(cs: Charset): Reader? = newInputStream()?.let { InputStreamReader(it, cs) }
    override fun stringValue(cs: Charset): String? = value?.let { _Private_Utils.decode(it, cs) }
    override fun newInputStream(): InputStream? = value?.let { ByteArrayInputStream(it) }
    override fun getBytes(): ByteArray? = value?.copyOf()
    override fun byteSize(): Int = returnIfNotNull { value!!.size }

    override fun setBytes(bytes: ByteArray?) = readOnlyErr()
    override fun setBytes(bytes: ByteArray?, offset: Int, length: Int) = readOnlyErr()
}

// TODO: Make this use an array
sealed class ImmutableIonSequence(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, protected val elements: List<ImmutableIonValue>, type: IonType): IonSequence, ImmutableIonValue(fieldName, annotations, isNull, type) {

    override fun isEmpty(): Boolean = returnIfNotNull { elements.isEmpty() }
    override val size: Int get() = if (isNull) 0 else elements.size
    override fun get(index: Int): IonValue = returnIfNotNull { elements[index] }

    final override fun iterator(): MutableIterator<IonValue> = if (isNull) EMPTY_ITERATOR else ImmutableIterator(elements.listIterator())

    final override fun listIterator(): MutableListIterator<IonValue> = if (isNull) EMPTY_ITERATOR else ImmutableIterator(elements.listIterator())

    final override fun listIterator(index: Int): MutableListIterator<IonValue> = when {
        !isNull -> ImmutableIterator(elements.listIterator(index))
        index == 0 -> EMPTY_ITERATOR
        else -> throw IndexOutOfBoundsException()
    }

    // TODO: Apparently these use _reference_ equality. :facepalm:
    final override fun contains(o: IonValue): Boolean = o in elements
    final override fun containsAll(elements: Collection<IonValue>): Boolean = elements.all { it in this.elements }

    final override fun indexOf(o: IonValue?): Int {
        TODO("Not yet implemented")
    }
    override fun lastIndexOf(o: IonValue?): Int {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<IonValue> {
        // TODO: Make a proper immutable list view
        return elements.subList(fromIndex, toIndex).toMutableList()
    }

    override fun toArray(): Array<IonValue> = if (isNull) IonValue.EMPTY_ARRAY else elements.toTypedArray()

    override fun <T : Any?> toArray(a: Array<out T>?): Array<T> {
        val size: Int = this.size
        if (a!!.size < size) {
            // TODO JDK 1.6 this could use Arrays.copyOf
            val type = a.javaClass.componentType
            // generates unchecked warning
            a = java.lang.reflect.Array.newInstance(type, size) as Array<T>
        }
        if (size > 0) {
            System.arraycopy(elements, 0, a, 0, size)
        }
        if (size < a.size) {
            // A surprising bit of spec.
            // this is required even with a 0 entries
            a[size] = null
        }
        return a
    }

    final override fun clear() = readOnlyErr()
    final override fun makeNull() = readOnlyErr()
    final override fun add(element: IonValue?): Boolean = readOnlyErr()
    final override fun add(): ValueFactory = readOnlyErr()
    final override fun add(index: Int, element: IonValue?) = readOnlyErr()
    final override fun add(index: Int): ValueFactory = readOnlyErr()
    final override fun set(index: Int, element: IonValue?): IonValue = readOnlyErr()
    final override fun removeAt(index: Int): IonValue = readOnlyErr()
    final override fun remove(o: IonValue?): Boolean = readOnlyErr()
    final override fun removeAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
    final override fun retainAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
    final override fun addAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
    final override fun addAll(index: Int, elements: Collection<IonValue>): Boolean = readOnlyErr()
    final override fun <T : IonValue?> extract(type: Class<T>?): Array<T> = readOnlyErr()

    private class ImmutableIterator(delegate: ListIterator<IonValue>): ListIterator<IonValue> by delegate, MutableListIterator<IonValue> {
        override fun add(element: IonValue) = readOnlyErr()
        override fun remove() = readOnlyErr()
        override fun set(element: IonValue) = readOnlyErr()
    }

    companion object {
        private val EMPTY_ITERATOR = ImmutableIterator(emptyList<IonValue>().listIterator())
    }
}

class ImmutableIonList(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, elements: List<ImmutableIonValue>): IonList, ImmutableIonSequence(fieldName, annotations, isNull, elements, IonType.LIST) {
    override fun clone(): IonList = this
    override fun writeTo(writer: IonWriter) {
        writer.superWriteTo {
            stepIn(IonType.LIST)
            elements.forEach { it.writeTo(writer) }
            stepOut()
        }
    }

    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }
}
