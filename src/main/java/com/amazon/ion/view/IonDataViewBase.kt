package com.amazon.ion.view

import com.amazon.ion.SymbolToken
import com.amazon.ion.view.IonDataType.*
import com.amazon.ion.*
import java.math.BigInteger
import java.util.function.BiConsumer
import java.util.function.Consumer

abstract class IonDataViewBase : IonDataView {

    /**
     * NOTE: This may require allocating new SymbolToken instances if the backing implementation does not use SymbolTokens.
     * For performance and/or memory sensitive applications, prefer to use [forEachAnnotation].
     */
    abstract fun annotationSymbolsIterator(): Iterator<SymbolToken>

    override fun hasTypeAnnotation(annotation: String): Boolean {
        var result = false
        forEachAnnotation { it, _ -> if (annotation == it) result = true }
        return result
    }

    // TODO: Should there be `textValue`, `lobValue`, `sequenceValues`, etc?
    override fun boolValue(): Boolean = typeError()

    override fun longValue(): Long = typeError()
    override fun bigIntegerValue(): BigInteger = typeError()
    override fun floatValue(): Float = typeError()
    override fun doubleValue(): Double = typeError()
    override fun decimalValue(): Decimal = typeError()
    override fun timestampValue(): Timestamp = typeError()

    override fun stringValue(): String = typeError()

    override fun symbolTextValue(): String? = typeError()
    override fun symbolIdValue(): Int = typeError()

    // Must return a defensive copy
    override fun blobValue(): ByteArrayView = typeError()
    // Must return a defensive copy
    override fun clobValue(): ByteArrayView = typeError()

    override fun listValuesIterator(): Iterator<IonDataView> = typeError()
    override fun forEachListValue(action: Consumer<IonDataView>): Unit = typeError()

    override fun sexpValuesIterator(): Iterator<IonDataView> = typeError()
    override fun forEachSexpValue(action: Consumer<IonDataView>): Unit = typeError()

    override fun structValuesIterator(): Iterator<Field> = typeError()

    /**
     * NOTE: This may require allocating new [Field] instances if the backing implementation does not use [Field].
     * For performance and/or memory sensitive applications, prefer to use [forEachStructValue].
     */
    override fun forEachStructValue(action: (String?, Int, IonDataView) -> Unit): Unit = typeError()

    override fun forEachStructValue(action: BiConsumer<String?, IonDataView>): Unit = typeError()


    /** Iterator of all field names with _known_ text. */
    override fun structFieldNamesIterator(): Iterator<String> = typeError()

    /** Iterator of all values for a given field name */
    override fun structFieldValuesIterator(name: String): Iterator<IonDataViewBase> = typeError()

    private inline fun <reified T> typeError(): T = throw IonException("Operation not applicable for $ionDataType")

    // Most implementations will probably override this.
    override fun writeTo(writer: IonWriter) {
        forEachAnnotation { it, _ -> writer.addTypeAnnotation(it) }
        if (isNull) {
            writer.writeNull(ionDataType.toIonType())
        } else {
            when (ionDataType) {
                NULL -> TODO("Unreachable")
                BOOL -> writer.writeBool(boolValue())
                INT -> TODO()
                FLOAT -> writer.writeFloat(doubleValue())
                DECIMAL -> writer.writeDecimal(decimalValue())
                TIMESTAMP -> writer.writeTimestamp(timestampValue())
                STRING -> writer.writeString(stringValue())
                SYMBOL -> writer.writeSymbol(symbolTextValue())
                // TODO: Optimize the lobs
                BLOB -> writer.writeBlob(blobValue().copyOfBytes())
                CLOB -> writer.writeClob(clobValue().copyOfBytes())
                LIST -> {
                    writer.stepIn(IonType.LIST)
                    forEachListValue { it.writeTo(writer) }
                    writer.stepOut()
                }
                SEXP -> {
                    writer.stepIn(IonType.SEXP)
                    forEachSexpValue { it.writeTo(writer) }
                    writer.stepOut()
                }
                STRUCT -> {
                    writer.stepIn(IonType.STRUCT)
                    forEachStructValue { name, _, value -> writer.setFieldName(name); value.writeTo(writer) }
                    writer.stepOut()
                }
            }
        }
    }
}