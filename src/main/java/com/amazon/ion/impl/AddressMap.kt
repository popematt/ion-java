// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl

import com.amazon.ion.impl.macro.*
import java.lang.StringBuilder
import java.util.*
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

interface NamedAddressMap<K /* = SymbolToken? or Macro */> {
    // Could be useful on the reader side if we cached SymbolToken instances in one of these...

    /** Returns null if not found */
    operator fun get(address: Int): K?
    /** Returns -1 if not found */
    operator fun get(macro: K): Int
    operator fun get(name: String): K?
    fun getName(macro: K): String?
    fun getName(address: Int): String?

    fun assign(macro: K): Int
    fun assign(name: String, macro: K): Int
    fun getOrAssign(macro: K): Int
    fun getOrAssign(name: String, macro: K): Int

    operator fun contains(element: K): Boolean
    operator fun contains(name: String): Boolean

    val size: Int

    fun clear()
}

class NaiveAddressMap<K : Any>(val addressesToKeys: MutableList<K> = mutableListOf()) : AddressMap<K>, Collection<K> by addressesToKeys {

    val keysToAddress = mutableMapOf<K, Int>()

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
        if (key in keysToAddress) {
            return keysToAddress[key]!!
        } else {
            keysToAddress[key] = newAddress
            return newAddress
        }
    }

    override fun clear() {
        addressesToKeys.clear()
        keysToAddress.clear()
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

    private fun addNodesToPool(head: Node) {
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
    }

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
    private fun nodeHash(key: Any?): Int {
        if (key == null) return 0
        val h: Int = key.hashCode()
        return (h xor (h ushr 16)) and Int.MAX_VALUE
    }

    private fun nodeHash(valueHash: Int): Int {
        return (valueHash xor (valueHash ushr 16)) and Int.MAX_VALUE
    }

    override var size = 0
        private set

    // It's not very useful to have an initial capacity lower than 8.
    private var nodes = arrayOfNulls<Node?>(max(initialCapacity, 8))
    private var keysById = arrayOfNulls<Any?>(max(initialCapacity, 8)) as Array<K?>
    private var resizeThreshold: Int = initialCapacity.times(LOAD_FACTOR).toInt() + 1

    private fun grow() {
        // TODO: Tune the growth rate
        // TODO: keysById should grow at the same time as nodes, but only needs to be as big as the resize threshold.
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
        val n = getNode(nodeHash(key), key) ?: return -1
        return n.address
    }

    private fun getNode(hash: Int, key: K): Node? {
        val bucket = hash % nodes.size
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

    // We amortize the calculation of hashCode() by updating it as each element is added
    private var hashCode = 0

    override fun assign(key: K): Int {
        if (size == resizeThreshold) grow()
        val valueHash = key.hashCode()
        hashCode = hashCode * 31 + valueHash
        val nodeHash = nodeHash(valueHash)
        val node = getNode(nodeHash, key)
        if (node == null) {
            val newNode = newNode(nodeHash, size++)
            nodes.insertNode(newNode)
            keysById[newNode.address] = key
            return newNode.address
        } else {
            val newAddress = size++
            keysById[newAddress] = key
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
        val valueHash = key.hashCode()
        val nodeHash = nodeHash(valueHash)
        var node = getNode(nodeHash, key)
        if (node == null) {
            if (size == resizeThreshold) grow()
            node = newNode(nodeHash, size++)
            keysById[node.address] = key
            nodes.insertNode(node)
            hashCode = hashCode * 31 + valueHash
        }
        return node.address
    }

    override fun contains(element: K): Boolean {
        return getNode(nodeHash(element), element) != null
    }

    override fun containsAll(elements: Collection<K>): Boolean = elements.all { it in this }


    fun isExtensionOf(other: AddressMapImpl<K>): Boolean {
        if (size < other.size) return false
        val maxCommonId = other.size - 1
        for (i in maxCommonId downTo 0) {
            if (keysById[i] != other.keysById[i]) return false
        }
        return true
    }

    fun greatestCommonAddress(other: AddressMapImpl<K>): Int {
        val otherSize = other.size
        var gca = -1
        while (gca < size && gca < otherSize) {
            gca++
            if (this[gca] != other[gca]) return gca - 1
        }
        return gca
    }

    override fun isEmpty(): Boolean = size == 0

    override fun clear() {
        for (i in nodes.indices) {
            addNodesToPool(nodes[i] ?: continue)
            nodes[i] != null
        }
        keysById.fill(null)
        size = 0
        hashCode = 0
    }

    fun truncate(newSize: Int) {
        // Depending on the size relative to the capacity, it might be faster to remove things by
        // iterating over the nodes rather than iterating over keysById

        val oldSize = size
        size = newSize
        for (i in newSize until oldSize) {
            val k = keysById[i]
            val h = nodeHash(k)
            val bucket = h % nodes.size
            var n = nodes[bucket] ?: continue
            if (n.address >= newSize) {
                // Move this node to the pool
                // It's guaranteed that all nodes with a higher ids will be children of lower ids, so
                // this safely moves child nodes too.
                nodes[bucket] = null
                addNodesToPool(n)
            } else {
                var child: Node = n.next ?: continue
                while (child.address < newSize) {
                    n = child
                    child = child.next ?: continue
                }
                n.next = null
                addNodesToPool(child)
            }
        }
        // TODO: See if there's an intrinsic method that can null out an entire array
        keysById.fill(null, fromIndex = newSize)
        hashCode = (0..< size).fold(0) { hash, i -> hash * 31 + keysById[i].hashCode() }
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AddressMap<*>) return false
        if (size != other.size) return false
        if (hashCode() != other.hashCode()) return false

        val otherIterator = other.iterator()
        for (i in 0..size) {
            if (keysById[i] != otherIterator.next()) return false
        }
        return true
    }

    override fun hashCode(): Int {
        return hashCode
    }

    fun snapshot(): AddressMapSnapshot<K> = AddressMapSnapshot(keysById.copyOfRange(0, size))
}

class AddressMapSnapshot<K>(private val keysById: Array<K?>): AddressMap<K> {

    private var nodes = arrayOfNulls<Node?>((keysById.size / LOAD_FACTOR).toInt())

    init {
        keysById.forEachIndexed { i, k ->
            nodes.insertNode(newNode(hash(k), i))
        }
    }

    override val size get() = keysById.size

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
        @JvmField var next: Node?,
    )

    private fun newNode(hash: Int, index: Int): Node {
        return Node(hash, index, null)
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

    override fun get(address: Int): K? {
        return if (address < size) keysById[address] else null
    }

    override fun get(key: K): Int {
        val n = getNode(hash(key), key) ?: return -1
        return n.address
    }

    private fun getNode(hash: Int, key: K): Node? {
        val bucket = hash % nodes.size
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

    override fun contains(element: K): Boolean {
        return getNode(hash(element), element) != null
    }

    override fun containsAll(elements: Collection<K>): Boolean = elements.all { it in this }


    fun isExtensionOf(other: AddressMapImpl<K>): Boolean {
        if (size < other.size) return false
        val maxCommonId = other.size - 1
        for (i in maxCommonId downTo 0) {
            if (keysById[i] != other[i]) return false
        }
        return true
    }

    fun greatestCommonAddress(other: AddressMapImpl<K>): Int {
        val otherSize = other.size
        var gca = -1
        while (gca < size && gca < otherSize) {
            gca++
            if (this[gca] != other[gca]) return gca - 1
        }
        return gca
    }

    override fun isEmpty(): Boolean = size == 0

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AddressMap<*>) return false
        if (size != other.size) return false
        if (hashCode() != other.hashCode()) return false

        val otherIterator = other.iterator()
        for (i in 0..size) {
            if (keysById[i] != otherIterator.next()) return false
        }
        return true
    }

    private val hashCode = (0..< size).fold(0) { hash, i -> hash * 31 + keysById[i].hashCode() }

    override fun hashCode(): Int {
        return hashCode
    }

    fun snapshot(): AddressMapSnapshot<K> = this

    fun mutable(): AddressMapImpl<K> {

    }


    override fun clear() {
        TODO("Not yet implemented")
    }


    override fun getOrAssign(key: K): Int = get(key)

    override fun assign(key: K): Int = -1
}
