package com.amazon.ion.view

import com.amazon.ion.Decimal
import com.amazon.ion.IonWriter
import com.amazon.ion.Timestamp
import java.math.BigInteger
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * An unmodifiable view of Ion data.
 *
 * Instances are not required to be immutable.
 * Any API that consumes this data model is promising that it will not modify the data in any way.
 *
 * [IonDataView] does not expose any way to mutate data, even indirectly. The only way to mutate data is by casting to
 * the concrete implementation of an [IonDataView] instance... but don't do that. If you need to modify the data, you
 * can convert to and from [IonDataView] using an [IonDataViewFactory].
 *
 * Implementations of [IonDataView] MAY extend [IonDataView].
 *
 * TODO: Known implementations include:
 *   - IonValue <-> IonDataView
 *   - IonElement <-> IonDataView
 *
 * TODO: Concerns
 *   - How can we represent equality/hashcode when some implementations
 *     might retain symbol IDs, and other's might not? Can we just do away with equality?
 *   -
 */
interface IonDataView {
    val ionDataType: IonDataType
    val isNull: Boolean
    // fun forEachAnnotation(action: (String?, Int) -> Unit)
    fun forEachAnnotation(action: BiConsumer<String?, Int>)
    fun forEachAnnotation(action: Consumer<String?>)
    fun hasTypeAnnotation(annotation: String): Boolean

    // TODO: Should there be `textValue`, `lobValue`, `sequenceValues`, etc?

    /** Returns the boolean value if this is a non-null Ion boolean. Otherwise, throws IonException. */
    fun boolValue(): Boolean

    fun longValue(): Long

    fun bigIntegerValue(): BigInteger

    fun floatValue(): Float

    fun doubleValue(): Double

    fun decimalValue(): Decimal

    fun timestampValue(): Timestamp

    fun stringValue(): String

    fun symbolTextValue(): String?

    fun symbolIdValue(): Int

    fun blobValue(): ByteArrayView

    fun clobValue(): ByteArrayView

    fun listValuesIterator(): Iterator<IonDataView>

    fun forEachListValue(action: Consumer<IonDataView>)

    fun sexpValuesIterator(): Iterator<IonDataView>

    fun forEachSexpValue(action: Consumer<IonDataView>)

    fun structValuesIterator(): Iterator<Field>

    /**
     * NOTE: This may require allocating new [Field] instances if the backing implementation does not use [Field].
     * For performance and/or memory sensitive applications, prefer to use [forEachStructValue].
     */
    fun forEachStructValue(action: (String?, Int, IonDataView) -> Unit)

    fun forEachStructValue(action: BiConsumer<String?, IonDataView>)

    /** Iterator of all field names with _known_ text. */
    fun structFieldNamesIterator(): Iterator<String>

    /** Iterator of all values for a given field name */
    fun structFieldValuesIterator(name: String): Iterator<IonDataView>

    fun writeTo(writer: IonWriter)
}