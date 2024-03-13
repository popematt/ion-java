package com.amazon.ion

import com.amazon.ion.system.IonTextWriterBuilder
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

// We could provide an IonElement adapter that points to an IonValue that is read only.
// Compatibility use cases to consider:
//   - library accepts IonValue, MyService uses "new thing"
//   - library accepts IonValue and expects to mutate, MyService uses "new thing"
//   - library accepts "new thing", MyService uses IonValue
//   - library gives IonValue, MyService uses "new thing"
//   - library gives "new thing", MyService uses IonValue
// TODO: Make sure we cover all cases for (accepts, uses, modifies?, returns)

// Possible solutions:
// - IonElementWrapsIonValue
// - ReadOnlyIonValue
// - ImmutableIonValue (just no mutations)
// - ImmutableIonValue (additional restrictions to avoid parent references, etc.)
// - [Probably infeasible] IonValueWrapsIonElement
// - [Possible dependency cycle] interface IntoIonValue/IntoIonElement


/**
 * Implementations of this interface are _not_ guaranteed to be immutable. This interface
 * is just a view of Ion that does not expose any mutators.
 *
 * This interface preferred over [IonValue] for APIs that consume Ion data, but do not need to perform
 * any in-place modification of the data so that users of the library can provide alternative implementations.
 *
 * Unlike [IonValue], which may not be implemented outside the ion-java library, this interface
 * may safely be implemented by anyone.
 *
 * TODO: Is this going to be a pain point?
 * There is no `ReadOnlyIonNull` because it would have no methods that are different than [ReadOnlyIonValue].
 *
 * TODO: What about the field name... is this way of modeling it going to be a pain?
 *
 * TODO: Can we omit a read-only IonDatagram in favor of something like Iterable<ReadOnlyIonView>?
 */
interface ReadOnlyIonValue {

    fun getType(): IonType
    fun isNullValue(): Boolean

    /**
     * Determines whether this value is read-only.  Such values are safe for
     * simultaneous read from multiple threads.
     */
    fun isReadOnly(): Boolean

    fun getTypeAnnotations(): Array<String>
    fun getTypeAnnotationSymbols(): Array<SymbolToken>
    fun hasTypeAnnotation(annotation: String): Boolean

    fun writeTo(writer: IonWriter)

    /**
     * Entry point for visitor pattern.  Implementations of this method by
     * concrete classes will simply call the appropriate `visit`
     * method on the `visitor`.  For example, instances of
     * [IonBool] will invoke [ValueVisitor.visit].
     *
     * @param visitor will have one of its `visit` methods called.
     * @throws Exception any exception thrown by the visitor is propagated.
     * @throws NullPointerException if `visitor` is
     * `null`.
     */
    // TODO: Is this necessary?
    fun accept(visitor: ValueVisitor?)

    /**
     * Returns a *non-canonical* Ion-formatted ASCII representation of
     * this value.
     *
     * For more configurable rendering, see
     * [com.amazon.ion.system.IonTextWriterBuilder].
     *
     * This is *not* the correct way to retrieve the content of an
     * [IonString] or [IonSymbol]!
     * Use [IonText.stringValue] for that purpose.
     * <pre>
     * ionSystem.newString("Levi's").toString()     =&gt;  "\"Levi's\""
     * ionSystem.newString("Levi's").stringValue()  =&gt;  "Levi's"
     * ionSystem.newSymbol("Levi's").toString()     =&gt;  "'Levi\\'s'"
     * ionSystem.newSymbol("Levi's").stringValue()  =&gt;  "Levi's"
    </pre> *
     *
     * @return Ion text data equivalent to this value.
     */
    override fun toString(): String

    fun toString(writerBuilder: IonTextWriterBuilder): String

    /**
     * Compares two Ion values for structural equality, which means that they
     * represent the exact same semantics, including annotations, numeric
     * precision, and so on.  This is a "deep" comparison that recursively
     * traverses the hierarchy, and as such it should be considered an
     * expensive operation.
     *
     * @see com.amazon.ion.util.Equivalence
     *
     * @param   other   The value to compare with.
     *
     * @return  A boolean, true if the argument is an [IonValue] that
     * is semantically identical within the Ion data model, including
     * precision and annotations.
     */
    override fun equals(other: Any?): Boolean

    /**
     * Returns a hash code consistent with [.equals].
     *
     * {@inheritDoc}
     */
    override fun hashCode(): Int

    // These are the only two methods that are not already part of IonValue.

    /**
     * Returns this value as an [IonValue]. This may result in allocating new IonValue instances, or
     * it may return a reference to an existing [IonValue].
     */
    fun toIonValue(): IonValue

    /**
     * Returns an immutable *copy* of this value. If this value is already immutable, returns `this`.
     * Implementations of this method _may not_ modify the existing value to make it read-only.
     */
    fun immutableCopy(): ReadOnlyIonValue
}

interface ReadOnlyIonBool: ReadOnlyIonValue {
    fun booleanValue(): Boolean
    override fun toIonValue(): IonBool
    override fun immutableCopy(): ReadOnlyIonBool
}

interface ReadOnlyIonNumber: ReadOnlyIonValue {
    fun isNumericValue(): Boolean
    fun bigDecimalValue(): BigDecimal

    override fun toIonValue(): IonNumber
    override fun immutableCopy(): ReadOnlyIonNumber
}

interface ReadOnlyIonInt: ReadOnlyIonNumber {
    fun intValue(): Int
    fun longValue(): Long
    fun bigIntegerValue(): BigInteger?
    fun getIntegerSize(): IntegerSize?

    override fun toIonValue(): IonInt
    override fun immutableCopy(): ReadOnlyIonInt
}

interface ReadOnlyIonFloat: ReadOnlyIonNumber {
    fun floatValue(): Float
    fun doubleValue(): Double

    override fun toIonValue(): IonFloat
    override fun immutableCopy(): ReadOnlyIonFloat
}

interface ReadOnlyIonDecimal: ReadOnlyIonNumber {
    fun floatValue(): Float
    fun doubleValue(): Double
    fun decimalValue(): Decimal

    override fun toIonValue(): IonDecimal
    override fun immutableCopy(): ReadOnlyIonDecimal
}

interface ReadOnlyIonTimestamp: ReadOnlyIonValue {

    /**
     * Gets the value of this `timestamp` in a form suitable for use independent of Ion data.
     * Returns a new copy every time because [Timestamp] is mutable.
     */
    fun timestampValue(): Timestamp?

    /**
     * Gets the value of this Ion `timestamp` as a Java [Date], representing the time in UTC.
     * Returns a new copy every time because [Date] is mutable.
     */
    fun dateValue(): Date?

    /**
     * Gets the value of this Ion `timestamp` as the number of milliseconds since
     * 1970-01-01T00:00:00.000Z, truncating any fractional milliseconds.
     */
    fun getMillis(): Long

    /**
     * Gets the value of this Ion `timestamp` as the number of milliseconds since
     * 1970-01-01T00:00:00Z, including fractional milliseconds.
     */
    fun getDecimalMillis(): BigDecimal?

    override fun toIonValue(): IonTimestamp
    override fun immutableCopy(): ReadOnlyIonTimestamp
}

interface ReadOnlyIonText: ReadOnlyIonValue {
    fun stringValue(): String?
    override fun toIonValue(): IonText
    override fun immutableCopy(): ReadOnlyIonText
}

interface ReadOnlyIonString: ReadOnlyIonText {
    override fun toIonValue(): IonString
    override fun immutableCopy(): ReadOnlyIonString
}

interface ReadOnlyIonSymbol: ReadOnlyIonText {
    fun symbolValue(): SymbolToken?
    override fun toIonValue(): IonSymbol
    override fun immutableCopy(): ReadOnlyIonSymbol
}

interface ReadOnlyIonLob: ReadOnlyIonValue {
    fun byteSize(): Int
    fun getBytes(): ByteArray?
    fun newInputStream(): InputStream?
    override fun toIonValue(): IonLob
    override fun immutableCopy(): ReadOnlyIonLob
}

interface ReadOnlyIonBlob: ReadOnlyIonValue {
    override fun toIonValue(): IonBlob
    override fun immutableCopy(): ReadOnlyIonBlob
}

interface ReadOnlyIonClob: ReadOnlyIonValue {
    override fun toIonValue(): IonClob
    override fun immutableCopy(): ReadOnlyIonClob
}

interface ReadOnlyIonContainer: ReadOnlyIonValue, Iterable<ReadOnlyIonValue> {
    fun size(): Int
    fun isEmpty(): Boolean
    override operator fun iterator(): Iterator<ReadOnlyIonValue>

    override fun toIonValue(): IonContainer
    override fun immutableCopy(): ReadOnlyIonContainer
}

interface ReadOnlyIonSequence: ReadOnlyIonContainer {
    operator fun get(index: Int): ReadOnlyIonValue
    override fun toIonValue(): IonSequence
    override fun immutableCopy(): ReadOnlyIonSequence
}

interface ReadOnlyIonSexp: ReadOnlyIonSequence {
    override fun toIonValue(): IonSexp
    override fun immutableCopy(): ReadOnlyIonSexp
}

interface ReadOnlyIonList: ReadOnlyIonSequence {
    override fun toIonValue(): IonList
    override fun immutableCopy(): ReadOnlyIonList
}

interface ReadOnlyIonStruct: ReadOnlyIonContainer {
    fun containsKey(fieldName: String): Boolean
    operator fun get(fieldName: String): ReadOnlyIonValue?

    override operator fun iterator(): Iterator<ReadOnlyIonValue>

    fun fieldsIterator(): Iterator<Field>

    override fun toIonValue(): IonStruct
    override fun immutableCopy(): ReadOnlyIonStruct

    interface Field {
        fun getFieldName(): String?
        fun getFieldNameSymbol(): SymbolToken?
        fun getFieldValue(): ReadOnlyIonValue

        fun toIonValue(): IonValue
        fun immutableCopy(): Field
    }
}


