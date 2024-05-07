package com.amazon.ion.view

import com.amazon.ion.IonWriter


public interface ByteArrayView : Iterable<Byte> {
    public fun size(): Int
    public operator fun get(index: Int): Byte
    public fun copyOfBytes(): ByteArray
    public fun contentEquals(other: ByteArrayView): Boolean
    public fun contentEquals(other: ByteArray): Boolean

    public fun writeAsBlobTo(writer: IonWriter)
    public fun writeAsClobTo(writer: IonWriter)

    /** Two [ByteArrayView]s should be equal if their content is equal. */
    public override fun equals(other: Any?): Boolean

    /** Should return the same values as calling [java.util.Arrays.hashCode] for a [ByteArray] with equal content. */
    public override fun hashCode(): Int

    class DefaultImpl(private val bytes: ByteArray): ByteArrayView {
        override fun size(): Int = bytes.size
        override fun get(index: Int): Byte = bytes[index]
        override fun copyOfBytes(): ByteArray = bytes.clone()
        override fun contentEquals(other: ByteArrayView): Boolean = other.contentEquals(bytes)
        override fun contentEquals(other: ByteArray): Boolean = other.contentEquals(bytes)

        override fun iterator(): Iterator<Byte> = bytes.iterator()

        override fun writeAsBlobTo(writer: IonWriter) = writer.writeBlob(bytes)
        override fun writeAsClobTo(writer: IonWriter) = writer.writeClob(bytes)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ByteArrayView) return false
            return this.contentEquals(other)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}
