package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.v2.AnnotationIterator
import com.amazon.ion.v2.impl_1_1.ValueReaderBase.Companion.TID_NONE
import java.nio.ByteBuffer
import kotlin.math.absoluteValue

/**
 * If the opcode is:
 * - `0xE4`, a single FlexUInt-encoded symbol address follows.
 * - `0xE5`, two FlexUInt-encoded symbol addresses follow.
 * - `0xE6`, a FlexUInt follows that represents the number of bytes needed to encode the annotations sequence, which can be made up of any number of FlexUInt symbol addresses.
 * - `0xE7`, a single FlexSym-encoded symbol follows.
 * - `0xE8`, two FlexSym-encoded symbols follow.
 * - `0xE9`, a FlexUInt follows that represents the byte length of the annotations sequence, which is made up of any number of annotations encoded as FlexSyms.
 */
internal class AnnotationIteratorImpl(
    private var opcode: Int,
    var source: ByteBuffer,
    var symbolTable: Array<String?>,
    val pool: ResourcePool,
): AnnotationIterator {

    override fun clone(): AnnotationIterator {
        val start = source.position()
        val length = source.limit() - start
        return pool.getAnnotations(opcode, start, length, symbolTable)
    }

    companion object {
        const val END = 0xF0
    }

    private var sid = -1
    private var text: String? = null

    override fun hasNext(): Boolean = opcode != END
    override fun next() {
        if (!hasNext()) throw NoSuchElementException()

        opcode = when (opcode) {
            0xE4 -> {
                sid = IntHelper.readFlexUInt(source)
                text = symbolTable[sid]
                END
            }
            0xE5 -> {
                sid = IntHelper.readFlexUInt(source)
                text = symbolTable[sid]
                0xE5
            }
            0xE6 -> {
                sid = IntHelper.readFlexUInt(source)
                text = symbolTable[sid]
                if (source.hasRemaining()) 0xE6 else END
            }
            0xE7 -> {
                readFlexSym()
                END
            }
            0xE8 -> {
                readFlexSym()
                0xE7
            }
            0xE9 -> {
                readFlexSym()
                if (source.hasRemaining()) 0xE9 else END
            }
            else -> opcode
        }

    }

    private fun readFlexSym() {
        val flexSym = IntHelper.readFlexIntAsLong(source).toInt()

        if (flexSym == 0) {
            val systemSid = (source.get().toInt() and 0xFF) - 0x60
            sid = if (systemSid == 0) 0 else -1
            text = SystemSymbols_1_1[systemSid]?.text
        } else if (flexSym > 0) {
            sid = flexSym
            text = symbolTable[sid]
        } else {
            sid = -1
            val length = -flexSym
            val position = source.position()
            val scratchBuffer = pool.scratchBuffer
            scratchBuffer.limit(position + length)
            scratchBuffer.position(position)
            source.position(position + length)
            text = pool.utf8Decoder.decode(scratchBuffer, length)
        }
    }


    override fun getSid(): Int = sid
    override fun getText(): String? = text

    override fun close() {
        pool.annotations.add(this)
    }

    fun init(opcode: Int, start: Int, length: Int, symbolTable: Array<String?>) {
        this.opcode = opcode
        source.limit(start + length)
        source.position(start)
        this.symbolTable = symbolTable
        sid = -1
        text = null
    }
}
