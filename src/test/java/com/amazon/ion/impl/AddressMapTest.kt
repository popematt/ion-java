package com.amazon.ion.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddressMapTest {

    @Test
    fun `get something that is not in the map`() {
        val map = AddressMapImpl<String>()
        assertEquals(-1, map["foo"])
        assertEquals(null, map[0])
    }

    @Test
    fun `add one to the address map`() {
        val map = AddressMapImpl<String>()
        assertEquals(0, map.assign("foo"))
        assertEquals(0, map["foo"])
        assertEquals("foo", map[0])
    }

    @Test
    fun `add two things to the address map`() {
        val map = AddressMapImpl<String>()
        assertEquals(0, map.assign("foo"))
        assertEquals(1, map.assign("bar"))
        assertEquals(0, map["foo"])
        assertEquals("foo", map[0])
        assertEquals(1, map["bar"])
        assertEquals("bar", map[1])
    }

    @Test
    fun `add a duplicate thing to the address map`() {
        val map = AddressMapImpl<String>()
        assertEquals(0, map.assign("foo"))
        assertEquals(1, map.assign("bar"))
        assertEquals(2, map.assign("foo"))

        assertEquals(0, map["foo"])
        assertEquals("foo", map[0])
        assertEquals("foo", map[2])
        assertEquals(1, map["bar"])
        assertEquals("bar", map[1])
    }

    @Test
    fun `add a lot of duplicate things to the address map`() {
        val map = AddressMapImpl<String>()
        (0..65).forEach {  assertEquals(it, map.assign("foo")) }

    }

    class HardCodedHash(val name: String, val hash: Int = 1) {
        override fun hashCode(): Int = hash
    }

    @Test
    fun `add distinct items with a hash collision to the address map`() {
        val map = AddressMapImpl<HardCodedHash>()
        val foo = HardCodedHash("foo", 1)
        val bar = HardCodedHash("bar", 1)
        val baz = HardCodedHash("baz", 1)

        assertEquals(0, map.assign(foo))
        assertEquals(1, map.assign(bar))
        assertEquals(2, map.assign(baz))


        assertEquals(0, map[foo])
        assertEquals(foo, map[0])
        assertEquals(1, map[bar])
        assertEquals(bar, map[1])
        assertEquals(2, map[baz])
        assertEquals(baz, map[2])
    }

    @Test
    fun `add distinct items with a hash collision to the address map and truncate`() {
        val map = AddressMapImpl<HardCodedHash>()
        val foo = HardCodedHash("foo", 1)
        val bar = HardCodedHash("bar", 1)
        val baz = HardCodedHash("baz", 1)

        assertEquals(0, map.assign(foo))
        assertEquals(1, map.assign(bar))
        assertEquals(2, map.assign(baz))


        assertEquals(0, map[foo])
        assertEquals(foo, map[0])
        assertEquals(1, map[bar])
        assertEquals(bar, map[1])
        assertEquals(2, map[baz])
        assertEquals(baz, map[2])

        map.truncate(2)

        assertEquals(2, map.assign(foo))
        assertEquals(3, map.assign(bar))
        assertEquals(4, map.assign(baz))

        assertEquals(0, map[foo])
        assertEquals(foo, map[0])
        assertEquals(1, map[bar])
        assertEquals(bar, map[1])
        assertEquals(foo, map[2])
        assertEquals(bar, map[3])
        assertEquals(4, map[baz])
        assertEquals(baz, map[4])
    }

    @Test
    fun `add lots of things to the map`() {
        val map = AddressMapImpl<String>()

        ('a' .. 'z').forEachIndexed { i, c ->
            assertEquals(i, map.assign("$c"))
        }

        ('a' .. 'z').forEachIndexed { i, c ->
            assertEquals(i, map["$c"])
            assertEquals("$c", map[i])
        }
    }

    @Test
    fun `add lots of things to the map and then clear`() {
        val map = AddressMapImpl<String>()

        ('a' .. 'z').forEachIndexed { i, c ->
            assertEquals(i, map.assign("$c"))
        }

        map.clear()

        ('a' .. 'z').forEachIndexed { i, c ->
            assertEquals(-1, map["$c"])
            assertEquals(null, map[i])
        }
    }

    @Test
    fun `add lots of things to the map and then truncate`() {
        val map = AddressMapImpl<String>()

        ('a' .. 'z').forEachIndexed { i, c ->
            assertEquals(i, map.assign("$c"))
        }
        map.truncate(10)
        ('a' .. 'j').forEachIndexed { i, c ->
            assertEquals(i, map["$c"])
            assertEquals("$c", map[i])
        }
        ('k' .. 'z').forEachIndexed { i, c ->
            assertEquals(-1, map["$c"])
            assertEquals(null, map[i + 10])
        }
    }

    @Test
    fun `add lots of things to the map and then truncate and then add more`() {
        val map = AddressMapImpl<String>()
        ('a' .. 'z').forEachIndexed { i, c -> assertEquals(i, map.assign("$c")) }

        map.truncate(10)

        ('a' .. 'j').forEachIndexed { i, c ->
            assertEquals(i, map["$c"])
            assertEquals("$c", map[i])
        }
        ('k' .. 'z').forEachIndexed { i, c ->
            assertEquals(-1, map["$c"])
            assertEquals(null, map[i + 10])
        }

        ('a' .. 'z').forEachIndexed { i, c -> assertEquals(i + 10, map.assign("$c")) }

        ('a' .. 'j').forEachIndexed { i, c ->
            assertEquals(i, map["$c"])
            assertEquals("$c", map[i])
        }
        ('a' .. 'j').forEachIndexed { i, c ->
            assertEquals("$c", map[i + 10])
        }

        ('k' .. 'z').forEachIndexed { i, c ->
            assertEquals(i + 20, map["$c"])
            assertEquals("$c", map[i + 20])
        }
    }

    @Test
    fun `isExtensionOf should return true when they are identical`() {
        val mapA = AddressMapImpl<String>()
        ('a' .. 'z').forEachIndexed { i, c -> assertEquals(i, mapA.assign("$c")) }
        val mapB = AddressMapImpl<String>()
        ('a' .. 'z').forEachIndexed { i, c -> assertEquals(i, mapB.assign("$c")) }

        assertEquals(mapA, mapB)
        assertTrue(mapA.isExtensionOf(mapB))
        assertTrue(mapB.isExtensionOf(mapA))
        assertEquals(mapA.hashCode(), mapB.hashCode())
    }

    @Test
    fun `isExtensionOf should return true when this has the same symbols as the other`() {
        val mapA = AddressMapImpl<String>()
        ('a' .. 'z').forEachIndexed { i, c -> assertEquals(i, mapA.assign("$c")) }
        val mapB = AddressMapImpl<String>()
        ('a' .. 'y').forEachIndexed { i, c -> assertEquals(i, mapB.assign("$c")) }

        assertTrue(mapA.isExtensionOf(mapB))
        assertFalse(mapB.isExtensionOf(mapA))
    }
}
