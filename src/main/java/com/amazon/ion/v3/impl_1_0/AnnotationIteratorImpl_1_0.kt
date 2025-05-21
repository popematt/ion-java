package com.amazon.ion.v3.impl_1_0

import com.amazon.ion.v3.*
import com.amazon.ion.v3.PrivateAnnotationIterator
import java.nio.ByteBuffer

internal class AnnotationIteratorImpl_1_0(
    var source: ByteBuffer,
    var symbolTable: Array<String?>,
    val pool: ReaderPool_1_0,
): AnnotationIterator, PrivateAnnotationIterator {
    private var sid = -1
    override fun hasNext(): Boolean = source.hasRemaining()
    override fun next(): String? {
        sid = StaticFunctions.readVarUInt(source).toInt()
        return symbolTable[sid]
    }
    override fun getSid(): Int = sid
    override fun getText(): String? = symbolTable[sid]
    override fun close() {
        pool.annotations.add(this)
    }
    fun init(start: Int, length: Int, symbolTable: Array<String?>) {
        source.limit(start + length)
        source.position(start)
        this.symbolTable = symbolTable
        sid = -1
    }

    override fun clone(): AnnotationIterator {
        val start = source.position()
        val length = source.limit() - start
        return pool.getAnnotations(start, length, symbolTable)
    }

    override fun peek() {
        source.mark()
        next()
        source.reset()
    }

    override fun toStringArray(): Array<String?> {
        val strings = ArrayList<String?>(4)
        while (this.hasNext()) {
            strings.add(next())
        }
        return strings.toTypedArray()
    }
}
