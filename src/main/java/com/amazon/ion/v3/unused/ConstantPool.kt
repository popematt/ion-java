package com.amazon.ion.v3.unused

/**
 * A constant pool that can be re-used.
 * Globals can be added when the macro table is being built.
 * Locals can be added at the time of invocation.
 */
class ConstantPool(initialCapacity: Int = 32) {
    private var data: Array<Any?> = arrayOfNulls(initialCapacity)
    private var numGlobals = 0
    private var numLocals = 0

    private fun grow() {
        val newCapacity = data.size * 2
        val newData = arrayOfNulls<Any?>(newCapacity)
        System.arraycopy(data, 0, newData, 0, data.size)
        data = newData
    }

    fun clearLocals() {
        numLocals = 0
    }

    fun clearAll() {
        numLocals = 0
        numGlobals = 0
    }

    fun addLocal(value: Any?): Int {
        val constantPoolIndex = numGlobals + numLocals++
        if (constantPoolIndex + 1 >= data.size) grow()
        data[constantPoolIndex] = value
        return constantPoolIndex
    }

    fun addGlobal(value: Any?): Int {
        if (numLocals > 0) {
            throw IllegalStateException("Cannot add global constant while there are local constants.")
        }
        val constantPoolIndex = numGlobals++
        if (constantPoolIndex >= data.size) grow()
        data[constantPoolIndex] = value
        return constantPoolIndex
    }

    operator fun get(i: Int): Any? {
        return data[i]
    }
}
