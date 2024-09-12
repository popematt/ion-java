// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl

import java.lang.StringBuilder
import java.util.Arrays
import kotlin.math.max

/**
 * A mapping from keys to sequential, non-negative integers
 *
 *     Macro table operations:
 *
 *     startMacro(String, Macro):
 *         Get ID for name
 *         Get Macro for ID
 *
 *     startMacro(Macro)
 *         Get ID for Macro
 *
 *     writeMacroTable
 *         Iterate IDs in order
 *         Get macro for ID
 *         Get name for ID
 *
 *     Current complexity:
 *
 *     Macro -> ID : O(1)
 *     ID -> Name  : O(1)
 *     ID -> Macro : O(1)
 *     Name -> ID  : O(n)
 *
 * Symbol Table Operations:
 *
 */
interface AddressMap<K> : Iterable<K>, Collection<K> {
    /** Returns null if not found */
    operator fun get(address: Int): K?

    /** Returns -1 if not found */
    operator fun get(key: K): Int

    fun assign(key: K): Int
    fun getOrAssign(key: K): Int

    override operator fun contains(element: K): Boolean

    override val size: Int

    fun clear()
}

class NaiveAddressMap<K : Any>(val addressesToKeys: MutableList<K> = mutableListOf()) : AddressMap<K>, Collection<K> by addressesToKeys {

    val keysToAddress = mutableMapOf<K, Int>()
    val keysToExtraAddresses = mutableMapOf<K, MutableList<Int>>()

    override fun get(address: Int): K? = if (address < addressesToKeys.size) addressesToKeys[address] else null

    override fun get(key: K): Int = keysToAddress[key] ?: -1

    override fun getOrAssign(key: K): Int {
        var address = get(key)
        if (address < 0) {
            address = assign(key)
        }
        return address
    }

    override fun assign(key: K): Int {
        val newAddress = size
        addressesToKeys.add(key)
        if (key !in keysToAddress) {
            keysToAddress[key] = newAddress
        } else {
            keysToExtraAddresses.getOrElse(key) { mutableListOf() }.add(newAddress)
        }
        return newAddress
    }

    override fun clear() {
        addressesToKeys.clear()
        keysToAddress.clear()
        keysToExtraAddresses.clear()
    }
}

/**
 *
 * It occurred to me that there is not only a computational cost to having boxed integers,
 * but there is also a memory cost—instead of having a 4 byte integer, a boxed integer requires
 * a 4 byte reference to a 16 byte object.
 *
 * Symbol tables are in the hot-ish path for the reader and writer, so even a small improvement could help.
 *
 * This is modelled after [HashMap], but by adding several constraints, we're
 * able to make certain specialized optimizations.
 *
 * Assumptions/Constraints:
 *  - The map values are integers that are sequentially assigned to values as they get added.
 *  - It is no need to remove a single value. Instead, you must clear the whole map.
 *  - We don't need to expose entry sets.
 *  - A key cannot be null
 *  - A key can have multiple values, but we don't need to access all of them.
 *
 * How is it different from a regular map?
 *  - It preserves insertion order, but without the use of a linked list
 *  - The value is the primitive int, rather than needing to be a boxed int.
 *  - We don't need any remove methods. The only thing we have is "clear".
 *  - The Nodes are pooled so that we don't have to reallocate after calling clear.
 *  - It has O(1) lookup by both key and value
 *  - It's sort of a multimap. A key can have multiple values. However, you
 *    can only find all the values for a key by iterating the entry set.
 */
class AddressMapImpl<K>(initialCapacity: Int = 64) : AddressMap<K> {
    /**
     * This class has an object size of 32 with a loss of 4. Can we use those 4 bytes
     * for some optimization or reduce the size by 4 bytes to make it smaller?
     * Actually, we might not need [addresses].
     *
     * A `last` field would be useful for some cases, but in practice,
     * the chain of nodes should only infrequently (~1.5% be more than 2 deep)
     * we don't expect the chain of nodes to ever be more than 8 deep (less than 1 in a million).
     */
    private class Node(
        @JvmField var hash: Int,
        @JvmField var address: Int,
        // @JvmField var addresses: IntList?,
        @JvmField var next: Node?,
    )

    private var nodePool: Node? = null

    private fun newNode(hash: Int, index: Int): Node {
        if (nodePool != null) {
            val newNode = nodePool!!
            nodePool = newNode.next
            newNode.hash = hash
            newNode.address = index
            newNode.next = null
            return newNode
        } else {
            return Node(hash, index, null)
        }
    }

    private fun releaseNodes(head: Node) {
        var tail = head
        while (tail.next != null) {
            // tail.addresses?.clear()
            tail = tail.next!!
        }
        // tail.addresses?.clear()
        tail.next = nodePool
        nodePool = head
    }

    companion object {
        private const val LOAD_FACTOR: Float = 0.75f

        /**
         * Copied from [java.util.HashMap].
         *
         * Computes key.hashCode() and spreads (XORs) higher bits of hash
         * to lower.  Because the table uses power-of-two masking, sets of
         * hashes that vary only in bits above the current mask will
         * always collide. (Among known examples are sets of Float keys
         * holding consecutive whole numbers in small tables.)  So we
         * apply a transform that spreads the impact of higher bits
         * downward. There is a tradeoff between speed, utility, and
         * quality of bit-spreading. Because many common sets of hashes
         * are already reasonably distributed (so don't benefit from
         * spreading), and because we use trees to handle large sets of
         * collisions in bins, we just XOR some shifted bits in the
         * cheapest possible way to reduce systematic lossage, as well as
         * to incorporate impact of the highest bits that would otherwise
         * never be used in index calculations because of table bounds.
         */
        @JvmStatic
        fun hash(key: Any?): Int {
            if (key == null) return 0
            val h: Int = key.hashCode()
            return (h xor (h ushr 16)) and Int.MAX_VALUE
        }
    }

    // It's not very useful to have an initial capacity lower than 8
    private var nodes = arrayOfNulls<Node?>(max(initialCapacity, 8))
    private var keysById = arrayOfNulls<Any?>(max(initialCapacity, 8)) as Array<K?>
    private var resizeThreshold: Int = initialCapacity.times(LOAD_FACTOR).toInt() + 1

    override var size = 0
        private set

    private fun grow() {
        // TODO: It is assumed that the majority of the Keys will have exactly one address
        //       If that is not the case, consider growing `nodes` and `keysById` separately.

        // TODO: Tune the growth rate
        val newCapacity = keysById.size + resizeThreshold + 1

        val newKeysById = keysById.copyOf(newCapacity)
        val newNodes = arrayOfNulls<Node?>(newCapacity)

        for (i in nodes.indices) {
            var currentNode = nodes[i]
            while (currentNode != null) {
                val nextNode = currentNode.next
                currentNode.next = null
                newNodes.insertNode(currentNode)
                currentNode = nextNode
            }
        }

        keysById = newKeysById
        nodes = newNodes
        resizeThreshold = newCapacity.times(LOAD_FACTOR).toInt() + 1
    }

    override fun get(address: Int): K? {
        return if (address < size) keysById[address] else null
    }

    override fun get(key: K): Int {
        return getNode(hash(key), key)?.address ?: -1
    }

    private fun getNode(hash: Int, key: K): Node? {
        val bucket = hash % keysById.size
        var currentNode = nodes[bucket]
        while (currentNode != null) {
            if (currentNode.hash == hash) {
                if (keysById[currentNode.address] == key) {
                    return currentNode
                }
            }
            currentNode = currentNode.next
        }
        return null
    }

    override fun assign(key: K): Int {
        if (size == resizeThreshold) grow()

        val hash = hash(key)
        val node = getNode(hash, key)
        if (node == null) {
            val newNode = newNode(hash, size++)
            nodes.insertNode(newNode)
            keysById[newNode.address] = key
            return newNode.address
        } else {
            val newAddress = size++
            keysById[newAddress] = key
            // val additionalAddresses = node.addresses ?: IntList().also { node.addresses = it }
            // additionalAddresses.add(newAddress)
            return newAddress
        }
    }

    private fun Array<Node?>.insertNode(node: Node) {
        val bucket = node.hash % this@insertNode.size
        var currentNode = this[bucket]
        if (currentNode == null) {
            this[bucket] = node
        } else {
            while (true) {
                currentNode!!
                if (currentNode.next == null) {
                    currentNode.next = node
                    return
                }
                currentNode = currentNode.next
            }
        }
    }

    override fun getOrAssign(key: K): Int {
        val hash = hash(key)
        var node = getNode(hash, key)
        if (node == null) {
            if (size == resizeThreshold) grow()
            node = newNode(hash, size++)
            nodes.insertNode(node)
        }
        return node.address
    }

    override fun contains(element: K): Boolean {
        return getNode(hash(element), element) != null
    }

    override fun containsAll(elements: Collection<K>): Boolean = elements.all { it in this }


    fun isExtensionOf(other: AddressMapImpl<K>): Boolean {
        if (size < other.size) return false
        val maxCommonId = size - 1
        for (i in maxCommonId downTo 0) {
            if (keysById[i] != other.keysById[i]) return false
        }
        return true
    }

    override fun isEmpty(): Boolean = size == 0

    override fun clear() {
        truncate(0)
    }

    fun truncate(newSize: Int) {
        size = newSize
        for (i in newSize until size) {
            if (nodes[i] != null) {
                releaseNodes(nodes[i]!!)
                nodes[i] = null
            }
        }
        // TODO: See if there's an intrinsic method that can null out an entire array
        keysById.fill(null, fromIndex = newSize)
    }

    override fun iterator(): Iterator<K> = iterator(0)

    fun iterator(index: Int): Iterator<K> {
        return IteratorImpl(keysById, index, size)
    }

    private class IteratorImpl<K>(
        private val values: Array<K?>,
        private var i: Int,
        private val endExclusive: Int
    ) : Iterator<K> {
        override fun hasNext(): Boolean = i < endExclusive
        override fun next(): K = values[i++] as K
        /** Address of the element that will be returned by the next call to `next()` */
        fun nextAddress() = i
        /** Address of the element last retrieved by `next()` */
        fun lastAddress() = i - 1
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("{")
        for (i in 0 until size) {
            if (i > 0) sb.append(",")
            sb.append(keysById[i].toString())
            sb.append("=")
            sb.append(i)
        }
        sb.append("}")
        return sb.toString()
    }
}
