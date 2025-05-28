package com.amazon.ion.v3.ion_reader

import com.amazon.ion.IonException
import com.amazon.ion.v3.*
import java.io.Closeable

internal class ReaderManager: Closeable {

    companion object {
        private inline fun <reified T> Array<T?>.grow(): Array<T?> {
            val newSize = this.size * 2
            val newArray = arrayOfNulls<T>(newSize)
            this.copyInto(newArray)
            return newArray
        }

        private fun IntArray.grow(): IntArray {
            val newSize = this.size * 2
            val newArray = IntArray(newSize)
            this.copyInto(newArray)
            return newArray
        }
    }

    // The top of the readerStack always contains the current reader, so it's never truly empty.
    private var readerStack = arrayOfNulls<ValueReader>(32)
    // Contains indices of `readerStack` that are containers.
    private var containerStack = IntArray(32) { -1 }
    private var readerStackSize: Int = 0
    // Equivalent to "getDepth()"
    private var containerStackSize: Int = 0

    // TODO: @JvmField for performance?
    var isInStruct: Boolean = false
        private set

    fun isTopAContainer(): Boolean {
        if (containerStackSize > 0) {
            val topContainerIndex = containerStack[containerStackSize - 1]
            return topContainerIndex == (readerStackSize - 1)
        } else {
            return false
        }
    }

    // TODO: expose a @JvmField for performance?
    val containerDepth: Int
        get() = containerStackSize

    // TODO: expose a @JvmField for performance?
    val readerDepth: Int
        get() = readerStackSize

    fun pushContainer(reader: ValueReader) {
        isInStruct = reader is StructReader
        if (containerStackSize >= containerStack.size) {
            containerStack = containerStack.grow()
        }
        containerStack[containerStackSize++] = readerStackSize
        pushReader(reader)
    }

    fun pushReader(reader: ValueReader) {
        if (readerStackSize >= readerStack.size) {
            readerStack = readerStack.grow()
        }
        readerStack[readerStackSize++] = reader
    }

    /**
     * Pops readers (closing them) until a container has been popped.
     * Returns the new top of the stack.
     * Throws an exception if there's nothing to step out of.
     */
    fun popContainer(): ValueReader {
        if (containerStackSize == 0) {
            throw IonException("Nothing to step out of")
        }
        val containerIndex = containerStack[--containerStackSize]
        while (containerIndex < readerStackSize) {
            readerStack[--readerStackSize]!!.close()
        }
        if (containerStackSize > 0) {
            isInStruct = readerStack[containerStack[containerStackSize - 1]] is StructReader
        } else {
            isInStruct = false
        }
        return readerStack[readerStackSize - 1]!!
    }

    /**
     * Returns the new top of the stack.
     */
    fun popReader(): ValueReader? {
        readerStack[--readerStackSize]?.close()
        // TODO: Do we need this, or can we require that you call popContainer when there's a container?
        if (containerStackSize > 0) {
            val topMostContainerIndex = containerStack[containerStackSize - 1]
            if (topMostContainerIndex >= readerStackSize) {
                containerStackSize--
                if (containerStackSize > 0) {
                    isInStruct = readerStack[containerStack[containerStackSize - 1]] is StructReader
                } else {
                    isInStruct = false
                }
            }
        }
        if (readerStackSize > 0) {
            return readerStack[readerStackSize - 1]
        } else {
            return null
        }
    }

    override fun close() {
        readerStack.forEach { it?.close() }
        readerStack = arrayOfNulls(0)
        containerStackSize = 0
        readerStackSize = 0
    }
}
