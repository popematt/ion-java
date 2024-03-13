package com.amazon.ion


import com.amazon.ion.impl.*
import com.amazon.ion.system.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.Charset
import java.util.*
import kotlin.NoSuchElementException

private fun Any.readOnlyErr(): Nothing = throw ReadOnlyValueException(this.javaClass)

// TODO: clean up isNull vs null values, especially w.r.t. constructors
// TODO: implement equals for all types
// TODO: implement hashcode for all types
// TODO: Do these need to implement the _Private_IonValue, _Private_IonDatagram, _Private_IonSymbol, and
//       _Private_IonContainer. interfaces?
sealed class ImmutableIonValue(
    protected val fieldName: SymbolToken?,
    protected val annotations: Array<out SymbolToken>,
    protected val isNull: Boolean,
    protected val type: IonType
): IonValue {

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
    final override fun getContainer(): Nothing = throw UnsupportedOperationException("ImmutableIonValue does not track references to parents.")

    // TODO: Contract specifies that this is thrown for IonDatagrams, but can we just throw this for all?
    final override fun topLevelValue(): Nothing = throw UnsupportedOperationException("ImmutableIonValue does not track references to parents.")


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

    companion object {
        @JvmStatic
        val EMPTY_ARRAY = arrayOf<ImmutableIonValue>()
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

sealed class ImmutableIonContainer(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, protected val elements: Array<ImmutableIonValue>, type: IonType): IonContainer, ImmutableIonValue(fieldName, annotations, isNull, type) {
    final override fun size(): Int = if (isNull) 0 else elements.size
    final override fun isEmpty(): Boolean = returnIfNotNull { elements.isEmpty() }
    final override fun clear() = readOnlyErr()
    final override fun makeNull() = readOnlyErr()
    final override fun remove(element: IonValue?): Boolean = readOnlyErr()

    final override fun iterator(): MutableIterator<IonValue> = if (isNull) EMPTY_ITERATOR else ImmutableIterator(elements)

    protected class ImmutableIterator(private val elements: Array<ImmutableIonValue>, private var i: Int = 0): MutableListIterator<IonValue> {
        override fun add(element: IonValue) = readOnlyErr()
        override fun remove() = readOnlyErr()
        override fun set(element: IonValue) = readOnlyErr()

        override fun hasNext(): Boolean = i + 1 < elements.size
        override fun hasPrevious(): Boolean = i > 0

        override fun nextIndex(): Int = i + 1
        override fun previousIndex(): Int = i - 1

        override fun next(): IonValue = if (hasNext()) elements[++i] else throw NoSuchElementException()
        override fun previous(): IonValue = if (hasPrevious()) elements[--i] else throw NoSuchElementException()
    }

    companion object {
        @JvmStatic
        protected val EMPTY_ITERATOR: MutableListIterator<IonValue> = ImmutableIterator(emptyArray())
    }
}

sealed class ImmutableIonSequence(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, elements: Array<ImmutableIonValue>, type: IonType): IonSequence, ImmutableIonContainer(fieldName, annotations, isNull, elements, type) {

    override val size: Int get() = if (isNull) 0 else elements.size
    override fun get(index: Int): IonValue = returnIfNotNull { elements[index] }

    final override fun listIterator(): MutableListIterator<IonValue> = if (isNull) EMPTY_ITERATOR else ImmutableIterator(elements)

    final override fun listIterator(index: Int): MutableListIterator<IonValue> = when {
        !isNull -> ImmutableIterator(elements, index)
        index == 0 -> EMPTY_ITERATOR
        else -> throw IndexOutOfBoundsException()
    }

    // TODO: Apparently these use _reference_ equality. :facepalm:
    final override fun contains(o: IonValue): Boolean = elements.any { it === o }
    final override fun containsAll(elements: Collection<IonValue>): Boolean = elements.all { it in this }
    final override fun indexOf(o: IonValue): Int = elements.indexOfFirst { it === o }
    final override fun lastIndexOf(o: IonValue): Int = elements.indexOfLast { it === o }

    final override fun subList(fromIndex: Int, toIndex: Int): MutableList<IonValue> {
        // TODO: Make a proper immutable list view
        return elements.slice(fromIndex..toIndex).toMutableList()
    }

    final override fun toArray(): Array<ImmutableIonValue> = if (isNull) EMPTY_ARRAY else elements.copyOf()


    /**
     * Returns an array containing all of the elements in this sequence in
     * proper order; the runtime type of the returned array is that of the
     * specified array. Obeys the general contract of the
     * {@link Collection#toArray()} method.
     * <p>
     * If this sequence is an {@linkplain #isNullValue() Ion null value}, it
     * will behave like an empty sequence.
     *
     * @param a the array into which the elements of this sequence are to be
     *        stored, if it is big enough; otherwise, a new array of the same
     *        runtime type is allocated for this purpose.
     *
     * @return an array containing all of the elements in this sequence in
     *         proper order.
     *
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in this
     *         sequence.
     * @throws NullPointerException if the specified array is <code>null</code>.
     */
    final override fun <T : Any> toArray(a: Array<T?>): Array<T?> {
        var destination = a
        val size: Int = this.size
        if (a.size < size) {
            // TODO JDK 1.6 this could use Arrays.copyOf
            val type = a.javaClass.componentType
            // generates unchecked warning
            destination = java.lang.reflect.Array.newInstance(type, size) as Array<T?>
        }
        if (size > 0) {
            System.arraycopy(elements, 0, destination, 0, size)
        }
        if (size < a.size) {
            // A surprising bit of spec.
            // this is required even with a 0 entries
            destination[size] = null
        }
        return destination
    }

    final override fun add(): ValueFactory = readOnlyErr()
    final override fun add(element: IonValue?): Boolean = readOnlyErr()
    final override fun add(index: Int, element: IonValue?) = readOnlyErr()
    final override fun add(index: Int): ValueFactory = readOnlyErr()
    final override fun set(index: Int, element: IonValue?): IonValue = readOnlyErr()
    final override fun removeAt(index: Int): IonValue = readOnlyErr()
    final override fun removeAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
    final override fun retainAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
    final override fun addAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
    final override fun addAll(index: Int, elements: Collection<IonValue>): Boolean = readOnlyErr()
    final override fun <T : IonValue?> extract(type: Class<T>?): Array<T> = readOnlyErr()
}

class ImmutableIonList(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, elements: Array<ImmutableIonValue>): IonList, ImmutableIonSequence(fieldName, annotations, isNull, elements, IonType.LIST) {
    override fun clone(): IonList = this
    override fun writeTo(writer: IonWriter) {
        writer.superWriteTo {
            stepIn(IonType.LIST)
            elements.forEach { it.writeTo(writer) }
            stepOut()
        }
    }

    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    // TODO: It would be good to have builders for all of the containers.
    fun toBuilder() = Builder(annotations.toMutableList(), elements.toMutableList())

    class Builder(
        private val annotations: MutableList<SymbolToken> = mutableListOf(),
        private val elements: MutableList<ImmutableIonValue> = mutableListOf()
    ) {
        fun add(element: ImmutableIonValue) = apply { elements.add(element) }
        fun add(index: Int, element: ImmutableIonValue) = apply { elements.add(index, element) }
        operator fun set(index: Int, element: ImmutableIonValue)= apply { elements.set(index, element) }
        fun removeAt(index: Int): IonValue = readOnlyErr()
        fun removeAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
        fun retainAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
        fun addAll(elements: Collection<IonValue>): Boolean = readOnlyErr()
        fun addAll(index: Int, elements: Collection<IonValue>): Boolean = readOnlyErr()
    }
}

class ImmutableIonSexp(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, elements: Array<ImmutableIonValue>): IonSexp, ImmutableIonSequence(fieldName, annotations, isNull, elements, IonType.SEXP) {
    override fun clone(): IonSexp = this
    override fun writeTo(writer: IonWriter) {
        writer.superWriteTo {
            stepIn(IonType.SEXP)
            elements.forEach { it.writeTo(writer) }
            stepOut()
        }
    }

    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }
}


class ImmutableIonStruct(fieldName: SymbolToken?, annotations: Array<out SymbolToken>, isNull: Boolean, elements: Array<ImmutableIonValue>): IonStruct, ImmutableIonContainer(fieldName, annotations, isNull, elements, IonType.STRUCT) {
    override fun clone(): IonStruct = this

    override fun writeTo(writer: IonWriter) {
        writer.superWriteTo {
            stepIn(IonType.STRUCT)
            elements.forEach { it.writeTo(writer) }
            stepOut()
        }
    }

    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun containsKey(fieldName: Any): Boolean = get(fieldName as String) != null

    // Reference equality! :(
    override fun containsValue(value: Any?): Boolean = elements.any { it === value }

    // Guaranteed to be thread-safe because:
    // 1. The initialization occurs in a synchronized method.
    // 2. The field is only ever set with a fully-populated map rather than starting with an empty map and building it up.
    lateinit var fieldsByName: Map<String, ImmutableIonValue>
    @Synchronized
    private fun initFieldNamesMap() {
        if (this::fieldsByName.isInitialized) return
        fieldsByName = elements.filter { it.fieldName != null }.associateBy { it.fieldName!! }
    }

    override fun get(fieldName: String): IonValue? {
        if (!this::fieldsByName.isInitialized) initFieldNamesMap()
        return fieldsByName[fieldName]
    }

    override fun put(fieldName: String?, child: IonValue?) = readOnlyErr()
    override fun put(fieldName: String?) = readOnlyErr()
    override fun putAll(m: MutableMap<out String, out IonValue>?) = readOnlyErr()
    override fun add(fieldName: String?, child: IonValue?) = readOnlyErr()
    override fun add(fieldName: SymbolToken?, child: IonValue?) = readOnlyErr()
    override fun add(fieldName: String?): ValueFactory = readOnlyErr()
    override fun remove(fieldName: String?): IonValue = readOnlyErr()
    override fun removeAll(vararg fieldNames: String?) = readOnlyErr()
    override fun retainAll(vararg fieldNames: String?) = readOnlyErr()

    override fun cloneAndRemove(vararg fieldNames: String?): IonStruct {
        return ImmutableIonStruct(fieldName, annotations, isNull, elements.filter { it.fieldName !in fieldNames }.toTypedArray())
    }

    override fun cloneAndRetain(vararg fieldNames: String?): IonStruct {
        return ImmutableIonStruct(fieldName, annotations, isNull, elements.filter { it.fieldName in fieldNames }.toTypedArray())
    }
}

class ImmutableIonDatagram(elements: Array<ImmutableIonValue>): IonDatagram, ImmutableIonSequence(null, emptyArray(), false, elements, IonType.DATAGRAM) {
    override fun clone(): IonDatagram = this
    override fun writeTo(writer: IonWriter) {
        elements.forEach { it.writeTo(writer) }
    }

    override fun accept(visitor: ValueVisitor) { visitor.visit(this) }

    override fun systemSize(): Int {
        TODO("Not yet implemented")
    }

    override fun systemGet(index: Int): IonValue {
        TODO("Not yet implemented")
    }

    override fun systemIterator(): MutableListIterator<IonValue> {
        TODO("Not yet implemented")
    }

    override fun byteSize(): Int {
        TODO("Not yet implemented")
    }

    override fun toBytes(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun getBytes(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun getBytes(dst: ByteArray?): Int {
        TODO("Not yet implemented")
    }

    override fun getBytes(dst: ByteArray?, offset: Int): Int {
        TODO("Not yet implemented")
    }

    override fun getBytes(out: OutputStream?): Int {
        TODO("Not yet implemented")
    }
}

