package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.impl.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.PrivateAnnotationIterator
import java.nio.ByteBuffer

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
    @JvmField
    var source: ByteBuffer,
    @JvmField
    val pool: ResourcePool,
    private var symbolTable: Array<String?>,
): AnnotationIterator, PrivateAnnotationIterator {

    private var i = -1
    private var limit = -1

    companion object {
        const val END = 0xF0

        /**
         * Must be called before getting the new annotations.
         */
        @JvmStatic
        fun calculateLength(opcode: Int, source: ByteBuffer, start: Int): Int {
            var p = start
            return when (opcode) {
                0xE4 -> IntHelper.lengthOfFlexUIntAt(source, p)
                0xE5 -> {
                    p += IntHelper.lengthOfFlexUIntAt(source, p)
                    p += IntHelper.lengthOfFlexUIntAt(source, p)
                    p - start
                }
                0xE6 -> IntHelper.getLengthPlusValueOfFlexUIntAt(source, p)
                0xE7 -> FlexSymHelper.lengthOfFlexSymAt(source, p)
                0xE8 -> {
                    p += FlexSymHelper.lengthOfFlexSymAt(source, p)
                    p += FlexSymHelper.lengthOfFlexSymAt(source, p)
                    p - start
                }
                0xE9 -> IntHelper.getLengthPlusValueOfFlexUIntAt(source, p)
                else -> TODO("Not a valid annotation op-code")
            }
        }
    }

    private var _sid = -1
    private var _text: String? = null

    override fun hasNext(): Boolean = opcode != END

    override fun next(): String? {
        var p = i
        if (!hasNext()) throw NoSuchElementException()
        opcode = when (opcode) {
            0xE4 -> {
                p += readSidAnnotation(p)
                END
            }
            0xE5 -> {
                p += readSidAnnotation(p)
                0xE4
            }
            0xE6 -> {
                p += readSidAnnotation(p)
                if (i < limit) 0xE6 else END
            }
            0xE7 -> {
                p += readFlexSymAnnotation(p)
                END
            }
            0xE8 -> {
                p += readFlexSymAnnotation(p)
                0xE7
            }
            0xE9 -> {
                p += readFlexSymAnnotation(p)
                if (p >= limit) 0xE9 else END
            }
            else -> opcode
        }
        i = p
        return _text
    }

    /** Returns the number of bytes consumed */
    private fun readSidAnnotation(position: Int): Int {
        val flexUIntValueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, position)
        val bytesConsumed = (flexUIntValueAndLength and 0xFF).toInt()
        val sid = (flexUIntValueAndLength ushr 8).toInt()
        _sid = sid
        _text = symbolTable[sid]
        return bytesConsumed
    }


    /** Returns the number of bytes consumed */
    private fun readFlexSymAnnotation(position: Int): Int {
        var p = position
        val flexIntValueAndLength = IntHelper.readFlexIntValueAndLengthAt(source, p)
        val flexSym = (flexIntValueAndLength shr 8).toInt()
        p += (flexIntValueAndLength and 0xFF).toInt()

        if (flexSym == 0) {
            val systemSid = (source.get(p).toInt() and 0xFF) - 0x60
            _sid = if (systemSid == 0) 0 else -1
            _text = SystemSymbols_1_1[systemSid]?.text
        } else if (flexSym > 0) {
            _sid = flexSym
            _text = symbolTable[_sid]
        } else {
            _sid = -1
            val length = -flexSym
            val scratchBuffer = pool.scratchBuffer
            scratchBuffer.limit(p + length)
            scratchBuffer.position(p)
            _text = pool.utf8Decoder.decode(scratchBuffer, length)
        }
        return p
    }

    override fun getSid(): Int = _sid
    override fun getText(): String? = _text

    override fun close() {
        if (this in pool.annotations) throw IllegalStateException("Already closed: $this")
        pool.annotations.add(this)
    }

    fun init(opcode: Int, start: Int, length: Int, symbolTable: Array<String?>) {
        this.opcode = opcode
        i = start
        limit = length
        _sid = -1
        _text = null
        this.symbolTable = symbolTable
    }

    override fun peek() {
        val i0 = i
        next()
        i = i0
    }

    override fun toStringArray(): Array<String?> {
        val strings = ArrayList<String?>(4)
        while (this.hasNext()) {
            next()
            strings.add(_text)
        }
        return strings.toTypedArray()
    }
}
