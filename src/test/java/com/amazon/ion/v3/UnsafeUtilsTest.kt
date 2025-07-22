package com.amazon.ion.v3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnsafeUtilsTest {

    @Test
    fun testWriteReadDoubleUnsafely() {
        val bytecode = IntArray(24)

        val a = 1.2345
        val b = Double.NaN
        val c = Double.POSITIVE_INFINITY
        val d = Double.NEGATIVE_INFINITY
        val e = 1.0
        val f = -0.0

        bytecode.unsafePutDoubleAt(0, a)
        bytecode.unsafePutDoubleAt(2, b)
        bytecode.unsafePutDoubleAt(4, c)
        bytecode.unsafePutDoubleAt(6, d)
        bytecode.unsafePutDoubleAt(8, e)
        bytecode.unsafePutDoubleAt(10, f)


        println(bytecode.contentToString())

        assertEquals(a, bytecode.unsafeGetDoubleAt(0))
        assertEquals(b, bytecode.unsafeGetDoubleAt(2))
        assertEquals(c, bytecode.unsafeGetDoubleAt(4))
        assertEquals(d, bytecode.unsafeGetDoubleAt(6))
        assertEquals(e, bytecode.unsafeGetDoubleAt(8))
        assertEquals(f, bytecode.unsafeGetDoubleAt(10))
    }

    @Test
    fun testAppendDoubleUnsafely() {
        val bytecodeList = mutableListOf<Int>()

        val a = 1.2345
        val b = Double.NaN
        val c = Double.POSITIVE_INFINITY
        val d = Double.NEGATIVE_INFINITY
        val e = 1.0
        val f = -0.0

        bytecodeList.unsafeAddDouble(a)
        bytecodeList.unsafeAddDouble(b)
        bytecodeList.unsafeAddDouble(c)
        bytecodeList.unsafeAddDouble(d)
        bytecodeList.unsafeAddDouble(e)
        bytecodeList.unsafeAddDouble(f)

        val bytecode = bytecodeList.toIntArray()

        println(bytecode.contentToString())

        assertEquals(a, bytecode.unsafeGetDoubleAt(0))
        assertEquals(b, bytecode.unsafeGetDoubleAt(2))
        assertEquals(c, bytecode.unsafeGetDoubleAt(4))
        assertEquals(d, bytecode.unsafeGetDoubleAt(6))
        assertEquals(e, bytecode.unsafeGetDoubleAt(8))
        assertEquals(f, bytecode.unsafeGetDoubleAt(10))
    }

    @Test
    fun testWriteReadLongUnsafely() {
        val bytecode = IntArray(20)

        val a = 0L
        val b = 1L
        val c = Long.MIN_VALUE
        val d = Long.MAX_VALUE

        bytecode.unsafePutLongAt(0, a)
        bytecode.unsafePutLongAt(2, b)
        bytecode.unsafePutLongAt(4, c)
        bytecode.unsafePutLongAt(6, d)

        assertEquals(a, bytecode.unsafeGetLongAt(0))
        assertEquals(b, bytecode.unsafeGetLongAt(2))
        assertEquals(c, bytecode.unsafeGetLongAt(4))
        assertEquals(d, bytecode.unsafeGetLongAt(6))
    }

    @Test
    fun testWriteReadObjectUnsafely() {
        val bytecode = IntArray(20)

        val a = "foo"
        val b = listOf(1, 2, 3)
        val c = Unit
        val d = 1 to 2

        bytecode.unsafePutReferenceAt(0, a)
        bytecode.unsafePutReferenceAt(2, b)
        bytecode.unsafePutReferenceAt(4, c)
        bytecode.unsafePutReferenceAt(6, d)

        assertEquals(a, bytecode.unsafeGetReferenceAt(0))
        assertEquals(b, bytecode.unsafeGetReferenceAt(2))
        assertEquals(c, bytecode.unsafeGetReferenceAt(4))
        assertEquals(d, bytecode.unsafeGetReferenceAt(6))
    }

}
