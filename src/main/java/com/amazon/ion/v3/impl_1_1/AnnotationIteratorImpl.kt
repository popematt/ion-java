package com.amazon.ion.v3.impl_1_1

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
    var source: ByteBuffer,
    val pool: ResourcePool,
    private var symbolTable: Array<String?>,
): AnnotationIterator, PrivateAnnotationIterator {

    override fun clone(): AnnotationIterator {
        val start = source.position()
        val length = source.limit() - start
        return pool.getAnnotations(opcode, start, length, symbolTable)
    }

    companion object {
        const val END = 0xF0

        /**
         * Must be called before getting the new annotations.
         */
        @JvmStatic
        fun calculateLength(opcode: Int, source: ByteBuffer): Int {
            return when (opcode) {
                0xE4 -> {
                    // Peek at the size of the only FlexUInt SID
                    source.get(source.position()).toInt().countTrailingZeroBits() + 1
                }
                0xE5 -> {
                    // Peek at the size of the two FlexUInt SIDs
                    val position = source.position()
                    val lengthOfFirstSid = source.get(position).toInt().countTrailingZeroBits() + 1
                    source.get(position + lengthOfFirstSid).countTrailingZeroBits() + 1 + lengthOfFirstSid
                }
                0xE6 -> {
                    IntHelper.readFlexUInt(source)
                }
                0xE7 -> {
                    val start = source.position()
                    FlexSymHelper.skipFlexSym(source)
                    val end = source.position()
                    source.position(start)
                    end - start
                }
                0xE8 -> {
                    val start = source.position()
                    FlexSymHelper.skipFlexSym(source)
                    FlexSymHelper.skipFlexSym(source)
                    val end = source.position()
                    source.position(start)
                    end - start
                }
                0xE9 -> IntHelper.readFlexUInt(source)
                else -> TODO("Not a valid annotation op-code")
            }
        }
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
        sid = -1
        text = null
        this.symbolTable = symbolTable
    }

    override fun peek() {
        source.mark()
        next()
        source.reset()
    }

    override fun toStringArray(): Array<String?> {
        val strings = ArrayList<String?>(4)
        while (this.hasNext()) {
            next()
            strings.add(getText())
        }
        return strings.toTypedArray()
    }
}
