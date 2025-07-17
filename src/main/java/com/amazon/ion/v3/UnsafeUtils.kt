package com.amazon.ion.v3

import com.amazon.ion.impl.bin.*
import sun.misc.Unsafe

private val unsafe = try {
    with(Unsafe::class.java.getDeclaredField("theUnsafe")) {
        setAccessible(true)
        get(null) as Unsafe
    }
        //.also { println("Acquired instance of sun.misc.Unsafe") }
} catch (t: Throwable) {
    null
}

private val ARRAY_HEADER_SIZE = unsafe!!.arrayBaseOffset(IntArray::class.java)

/**
 * The size of an address is always 4 or 8 bytes. This method always uses 8 bytes to store them,
 * which is always sound, although it could be inefficient in some cases.
 */
fun MutableList<Int>.unsafeAddReference(obj: Any?) {
    unsafe ?: TODO()
    val temp = IntArray(2)
    temp.unsafePutReferenceAt(0, obj)
    this.add(temp[0])
    this.add(temp[1])
}

/**
 * The size of an address is always 4 or 8 bytes. This method always uses 8 bytes to store them,
 * which is always sound, although it could be inefficient in some cases.
 */
fun IntList.unsafeAddReference(obj: Any?) {
    unsafe ?: TODO()
    val temp = IntArray(2)
    temp.unsafePutReferenceAt(0, obj)
    this.add(temp[0])
    this.add(temp[1])
}

/**
 * Callers should assume that 8 bytes (2 ints) are used for storing the reference.
 */
fun IntArray.unsafePutReferenceAt(i: Int, obj: Any?) {
    unsafe ?: TODO()
    val byteOffset = ARRAY_HEADER_SIZE + Int.SIZE_BYTES * i.toLong()
    unsafe.putObject(this, byteOffset, obj)
}

/**
 * Callers should assume that 8 bytes (2 ints) are used for storing the reference.
 */
fun <T> IntArray.unsafeGetReferenceAt(i: Int): T {
    unsafe ?: TODO()
    val byteOffset = ARRAY_HEADER_SIZE + Int.SIZE_BYTES * i.toLong()
    return unsafe.getObject(this, byteOffset) as T
}

fun MutableList<Int>.unsafeAddLong(long: Long) {
    unsafe ?: TODO()
    val temp = IntArray(2)
    temp.unsafePutLongAt(0, long)
    this.add(temp[0])
    this.add(temp[1])
}

fun IntArray.unsafePutLongAt(i: Int, long: Long) {
    unsafe ?: TODO()
    val byteOffset = ARRAY_HEADER_SIZE + Int.SIZE_BYTES * i.toLong()
    return unsafe.putLong(this, byteOffset, long)
}

fun IntArray.unsafeGetLongAt(i: Int): Long {
    unsafe ?: TODO()
    val byteOffset = ARRAY_HEADER_SIZE + Int.SIZE_BYTES * i.toLong()
    return unsafe.getLong(this, byteOffset)
}

fun MutableList<Int>.unsafeAddDouble(double: Double) {
    unsafe ?: TODO()
    val temp = IntArray(2)
    temp.unsafePutDoubleAt(0, double)
    this.add(temp[0])
    this.add(temp[1])
}

fun IntArray.unsafePutDoubleAt(i: Int, double: Double) {
    unsafe ?: TODO()
    val byteOffset = ARRAY_HEADER_SIZE + Int.SIZE_BYTES * i.toLong()
    return unsafe.putDouble(this, byteOffset, double)
}

fun IntArray.unsafeGetDoubleAt(i: Int): Double {
    unsafe ?: TODO()
    val byteOffset = ARRAY_HEADER_SIZE + Int.SIZE_BYTES * i.toLong()
    return unsafe.getDouble(this, byteOffset)
}


class MixedArray(private val data: Array<Any?>) {
    companion object {
        @JvmStatic
        val ARRAY_HEADER_SIZE = unsafe!!.arrayBaseOffset(Array::class.java)
    }

    fun getIntAt(i: Int): Int {
        return unsafe!!.getInt(data, ARRAY_HEADER_SIZE + Int.SIZE_BYTES * i.toLong())
    }

    fun getObjectAt(i: Int): Any? {
        return data[i / 2]
    }

    class Builder {
        var alignmentLosses = 0
            private set

        var pendingData = 0L
        var hasPendingData = false

        val data = ArrayList<Any?>()

        val temp = arrayOfNulls<Any?>(1)

        fun setIntAt(i: Int, value: Int) {
            val dataIndex = i / 2
            val offset = i.mod(2)

            temp[0] = data

            TODO()
        }

        fun addInt(value: Int) {
            if (hasPendingData) {
                pendingData = pendingData or (value.toLong() and 0xFFFFFFFFL)
                unsafe!!.putLong(temp, ARRAY_HEADER_SIZE.toLong(), pendingData)
                data.add(temp[0])
                hasPendingData = false
            } else {
                pendingData = value.toLong() shl 32
            }
        }

        fun addObject(obj: Any?) {
            if (hasPendingData) {
                alignmentLosses++
                unsafe!!.putLong(temp, ARRAY_HEADER_SIZE.toLong(), pendingData)
                data.add(temp[0])
                hasPendingData = false
            }
            data.add(obj)
        }

        fun build(): MixedArray {
            println("AlignmentLosses: $alignmentLosses")
            return MixedArray(data.toTypedArray())
        }

    }
}
