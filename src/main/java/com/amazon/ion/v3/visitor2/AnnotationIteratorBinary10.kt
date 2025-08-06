package com.amazon.ion.v3.visitor2

import com.amazon.ion.v3.impl_1_0.*
import java.nio.ByteBuffer

class AnnotationIteratorBinary10(
    private var symbolTable: Array<String?>,
    private var source: ByteBuffer,
): AnnotationIterator {

    private var position: Int = -1
    private var end: Int = -1
    private var sid = -1

    fun init(start: Int, length: Int, symbolTable: Array<String?>) {
        this.position = start
        this.end = start + length
        this.symbolTable = symbolTable
        sid = -1
    }

    fun clear() {
        position = -1
        end = -1
        sid = -1
    }

    fun firstSid(): Int {
        return (VarIntHelper.readVarUIntValueAndLength(source, position) ushr 8).toInt()
    }

    override fun hasNext(): Boolean = position < end

    override fun next(): String? {
        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, position)
        val sid = (valueAndLength ushr 8).toInt()
        val length = valueAndLength.toInt() and 0xFF
        this.sid = sid
        position += length
        return symbolTable[sid]
    }

    override fun getSid(): Int = sid
    override fun getText(): String? = symbolTable[sid]

    override fun restart() {
        TODO("Not yet implemented")
    }

    override fun toStringArray(): Array<String?> {
        val strings = ArrayList<String?>(4)
        while (this.hasNext()) {
            strings.add(next())
        }
        return strings.toTypedArray()
    }
}
