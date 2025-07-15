package com.amazon.ion.v3

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

fun MutableList<Int>.unsafeAddReference(obj: Any) {
    unsafe ?: return
    val temp = IntArray(2)
    temp.unsafePutReferenceAt(0, obj)
    this.add(temp[0])
    this.add(temp[1])
}

fun IntArray.unsafePutReferenceAt(i: Int, obj: Any) {
    unsafe ?: return
    val byteOffset = 16 + 8 * i.toLong()
    unsafe.putObject(this, byteOffset, obj)
}

fun <T> IntArray.unsafeGetReferenceAt(i: Int): T? {
    unsafe ?: return null
    val byteOffset = 16 + 8 * i.toLong()
    return unsafe.getObject(this, byteOffset) as T
}

fun IntArray.unsafeGetLongAt(i: Int): Long {
    unsafe ?: return 0
    val byteOffset = 16 + 8 * i.toLong()
    return unsafe.getLong(this, byteOffset)
}

fun IntArray.unsafeGetDoubleAt(i: Int): Double {
    unsafe ?: return Double.NaN
    val byteOffset = 16 + 8 * i.toLong()
    return unsafe.getDouble(this, byteOffset)
}

fun IntArray.unsafePutDoubleAt(i: Int, double: Double) {
    unsafe ?: return
    val byteOffset = 16 + 8 * i.toLong()
    return unsafe.putDouble(this, byteOffset, double)
}
