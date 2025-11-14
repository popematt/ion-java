// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.bytecode.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StringPoolTest {

    @Test
    fun `constructor creates empty string pool with correct initial capacity`() {
        val pool = StringPool(5)
        assertEquals(0, pool.size)
        assertTrue(pool.isEmpty())
    }

    @Test
    fun `size returns correct number of elements`() {
        val stringPool = StringPool(10)
        assertEquals(0, stringPool.size)

        stringPool.add("test")
        assertEquals(1, stringPool.size)

        stringPool.add("42")
        assertEquals(2, stringPool.size)
    }

    @Test
    fun `isEmpty returns true for empty pool and false for non-empty pool`() {
        val stringPool = StringPool(10)
        assertTrue(stringPool.isEmpty())

        stringPool.add("test")
        assertFalse(stringPool.isEmpty())

        stringPool.clear()
        assertTrue(stringPool.isEmpty())
    }

    @Test
    fun `add returns correct index and stores value`() {
        val stringPool = StringPool(10)
        val index1 = stringPool.add("first")
        assertEquals(0, index1)
        assertEquals("first", stringPool.get(0))

        val index2 = stringPool.add("second")
        assertEquals(1, index2)
        assertEquals("second", stringPool.get(1))

        val index3 = stringPool.add(null)
        assertEquals(2, index3)
        assertNull(stringPool.get(2))
    }

    @Test
    fun `get throws IndexOutOfBoundsException for negative index`() {
        val stringPool = StringPool(10)
        stringPool.add("test")

        val exception = assertThrows<IndexOutOfBoundsException> {
            stringPool[-1]
        }
        assertTrue(exception.message!!.contains("Invalid index -1"))
    }

    @Test
    fun `get throws IndexOutOfBoundsException for index greater than or equal to size`() {
        val stringPool = StringPool(10)
        stringPool.add("test")

        val exception = assertThrows<IndexOutOfBoundsException> {
            stringPool.get(1)
        }
        assertTrue(exception.message!!.contains("Invalid index 1"))

        val exception2 = assertThrows<IndexOutOfBoundsException> {
            stringPool.get(10)
        }
        assertTrue(exception2.message!!.contains("Invalid index 10"))
    }

    @Test
    fun `get throws IndexOutOfBoundsException for empty pool`() {
        val stringPool = StringPool(10)
        val exception = assertThrows<IndexOutOfBoundsException> {
            stringPool.get(0)
        }
        assertTrue(exception.message!!.contains("Invalid index 0"))
    }

    @Test
    fun `clear empties the string pool`() {
        val stringPool = StringPool(10)
        stringPool.add("test1")
        stringPool.add("test2")
        stringPool.add("test3")
        assertEquals(3, stringPool.size)
        assertFalse(stringPool.isEmpty())

        stringPool.clear()
        assertEquals(0, stringPool.size)
        assertTrue(stringPool.isEmpty())

        // Should be able to add new items starting from index 0
        val index = stringPool.add("new item")
        assertEquals(0, index)
        assertEquals("new item", stringPool.get(0))
    }

    @Test
    fun `truncate reduces size to specified length`() {
        val stringPool = StringPool(10)
        stringPool.add("item0")
        stringPool.add("item1")
        stringPool.add("item2")
        stringPool.add("item3")
        assertEquals(4, stringPool.size)

        stringPool.truncate(2)
        assertEquals(2, stringPool.size)
        assertEquals("item0", stringPool.get(0))
        assertEquals("item1", stringPool.get(1))

        // Should throw exception when trying to access truncated items
        assertThrows<IndexOutOfBoundsException> {
            stringPool.get(2)
        }

        // Should be able to add new items starting from truncated size
        val newIndex = stringPool.add("new item")
        assertEquals(2, newIndex)
        assertEquals("new item", stringPool.get(2))
    }

    @Test
    fun `truncate to zero makes pool empty`() {
        val stringPool = StringPool(10)
        stringPool.add("item1")
        stringPool.add("item2")

        stringPool.truncate(0)
        assertEquals(0, stringPool.size)
        assertTrue(stringPool.isEmpty())
    }

    @Test
    fun `truncate throws exception when length exceeds number of values`() {
        val stringPool = StringPool(10)
        stringPool.add("item1")
        stringPool.add("item2")

        val exception = assertThrows<IllegalArgumentException> {
            stringPool.truncate(3)
        }
        assertEquals("length exceeds number of values", exception.message)
    }

    @Test
    fun `truncate allows truncating to current size`() {
        val stringPool = StringPool(10)
        stringPool.add("item1")
        stringPool.add("item2")

        // Should not throw exception
        stringPool.truncate(2)
        assertEquals(2, stringPool.size)
    }

    @Test
    fun `pool grows automatically when capacity is exceeded`() {
        val smallPool = StringPool(2)

        // Add items beyond initial capacity
        for (i in 0..5) {
            val index = smallPool.add("item$i")
            assertEquals(i, index)
        }

        assertEquals(6, smallPool.size)

        // Verify all items are accessible
        for (i in 0..5) {
            assertEquals("item$i", smallPool.get(i))
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 5, 10, 100, 1000])
    fun `pool handles various initial capacities`(initialCapacity: Int) {
        val pool = StringPool(initialCapacity)
        assertTrue(pool.isEmpty())
        assertEquals(0, pool.size)

        // Add one item to verify it works
        val index = pool.add("test")
        assertEquals(0, index)
        assertEquals("test", pool.get(0))
    }

    @Test
    fun `toArray returns defensive copy with correct size`() {
        val stringPool = StringPool(10)
        stringPool.add("item1")
        stringPool.add("item2")
        stringPool.add(null)

        val array = stringPool.toArray()
        assertEquals(3, array.size)
        assertEquals("item1", array[0])
        assertEquals("item2", array[1])
        assertNull(array[2])

        // Verify it's a defensive copy by modifying the returned array
        array[0] = "modified"
        assertEquals("item1", stringPool.get(0)) // Original should be unchanged
    }

    @Test
    fun `toArray returns empty array for empty pool`() {
        val stringPool = StringPool(10)
        val array = stringPool.toArray()
        assertEquals(0, array.size)
    }

    @Test
    fun `unsafeGetArray returns backing array`() {
        val stringPool = StringPool(10)
        stringPool.add("item1")
        stringPool.add("item2")

        val array = stringPool.unsafeGetArray()

        // Array should contain the items
        assertEquals("item1", array[0])
        assertEquals("item2", array[1])

        // If we make changes it should be reflected in the BytecodeBuffer
        // DON'T ACTUALLY DO THIS OUTSIDE OF TEST CODE!
        array[0] = "item3"
        assertEquals("item3", stringPool.get(0))
    }

    @Test
    fun `toString returns correct string representation`() {
        val stringPool = StringPool(10)
        val emptyString = stringPool.toString()
        assertEquals("StringPool(data=[])", emptyString)

        stringPool.add("hello")
        stringPool.add("world")

        val string = stringPool.toString()
        assertEquals("StringPool(data=[hello,world,])", string)
    }

    @Test
    fun `equals returns true for identical pools`() {
        val pool1 = StringPool(5)
        val pool2 = StringPool(10) // Different capacity

        // Empty pools should be equal
        assertEquals(pool1, pool2)

        // Add same items to both
        pool1.add("test")
        pool1.add("42")
        pool1.add(null)

        pool2.add("test")
        pool2.add("42")
        pool2.add(null)

        assertEquals(pool1, pool2)
    }

    @Test
    fun `equals returns false for pools with different content`() {
        val pool1 = StringPool(5)
        val pool2 = StringPool(5)

        pool1.add("test1")
        pool2.add("test2")

        assertNotEquals(pool1, pool2)
    }

    @Test
    fun `equals returns false for pools with different sizes`() {
        val pool1 = StringPool(5)
        val pool2 = StringPool(5)

        pool1.add("test")
        pool2.add("test")
        pool2.add("extra")

        assertNotEquals(pool1, pool2)
    }

    @Test
    fun `equals returns true for same instance`() {
        val stringPool = StringPool(10)
        assertEquals(stringPool, stringPool)
    }

    @Test
    fun `equals returns false for null and different types`() {
        val stringPool = StringPool(10)
        assertNotEquals(stringPool, null)
        assertNotEquals(stringPool, "not a string pool")
        assertNotEquals(stringPool, listOf<Any>())
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val pool1 = StringPool(5)
        val pool2 = StringPool(10)

        // Empty pools
        assertEquals(pool1.hashCode(), pool2.hashCode())

        // Add same content
        pool1.add("test")
        pool1.add("42")

        pool2.add("test")
        pool2.add("42")

        assertEquals(pool1.hashCode(), pool2.hashCode())
    }

    @Test
    fun `hashCode differs for different content`() {
        val pool1 = StringPool(5)
        val pool2 = StringPool(5)

        pool1.add("test1")
        pool2.add("test2")

        assertNotEquals(pool1.hashCode(), pool2.hashCode())
    }

    @Test
    fun `growth multiplier is applied correctly`() {
        // Create a small pool to test growth
        val smallPool = StringPool(1)

        // Add items to force growth
        smallPool.add("item1")
        smallPool.add("item2") // This should trigger growth

        // Verify both items are accessible
        assertEquals("item1", smallPool.get(0))
        assertEquals("item2", smallPool.get(1))
        assertEquals(2, smallPool.size)

        // The backing array should have grown by GROWTH_MULTIPLIER
        val backingArray = smallPool.unsafeGetArray()
        assertTrue(backingArray.size >= 2) // Should be at least 2 (1 * GROWTH_MULTIPLIER)
    }

    @Test
    fun `large number of items can be stored and retrieved`() {
        val largePool = StringPool(10)
        val itemCount = 1000

        // Add many items
        for (i in 0 until itemCount) {
            val index = largePool.add("item$i")
            assertEquals(i, index)
        }

        assertEquals(itemCount, largePool.size)

        // Verify all items can be retrieved
        for (i in 0 until itemCount) {
            assertEquals("item$i", largePool.get(i))
        }
    }

    @Test
    fun `pool handles null values correctly`() {
        val stringPool = StringPool(10)
        val index1 = stringPool.add(null)
        val index2 = stringPool.add("not null")
        val index3 = stringPool.add(null)

        assertEquals(0, index1)
        assertEquals(1, index2)
        assertEquals(2, index3)

        assertNull(stringPool.get(0))
        assertEquals("not null", stringPool.get(1))
        assertNull(stringPool.get(2))

        assertEquals(3, stringPool.size)
    }

    @Test
    fun `operations work correctly after clear`() {
        val stringPool = StringPool(10)
        // Add some items
        stringPool.add("item1")
        stringPool.add("item2")
        assertEquals(2, stringPool.size)

        // Clear and verify
        stringPool.clear()
        assertEquals(0, stringPool.size)
        assertTrue(stringPool.isEmpty())

        // Add new items after clear
        val index1 = stringPool.add("new1")
        val index2 = stringPool.add("new2")

        assertEquals(0, index1)
        assertEquals(1, index2)
        assertEquals("new1", stringPool.get(0))
        assertEquals("new2", stringPool.get(1))
        assertEquals(2, stringPool.size)
    }

    @Test
    fun `operations work correctly after truncate`() {
        val stringPool = StringPool(10)
        // Add some items
        stringPool.add("item1")
        stringPool.add("item2")
        stringPool.add("item3")
        stringPool.add("item4")
        assertEquals(4, stringPool.size)

        // Truncate to 2
        stringPool.truncate(2)
        assertEquals(2, stringPool.size)

        // Add new items after truncate
        val index1 = stringPool.add("new1")
        val index2 = stringPool.add("new2")

        assertEquals(2, index1)
        assertEquals(3, index2)
        assertEquals("item1", stringPool.get(0))
        assertEquals("item2", stringPool.get(1))
        assertEquals("new1", stringPool.get(2))
        assertEquals("new2", stringPool.get(3))
        assertEquals(4, stringPool.size)
    }
}
