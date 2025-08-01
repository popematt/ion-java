package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object SymbolHelper {

    class TextResult(var consumedBytes: Int = -1, var text: String? = null)

    @JvmStatic
    private val ZERO_LENGTH_SYMBOL = TextResult(0, "")

    private val FUNCTIONS = Array(256) {
        when (it) {
            0xA0 -> ::readSymbol0xA0
            0xA1 -> ::readSymbol0xA1
            0xA2 -> ::readSymbol0xA2
            0xA3 -> ::readSymbol0xA3
            0xA4 -> ::readSymbol0xA4
            else -> ::notASymbol
        }
    }

    fun notASymbol(source: ByteBuffer, position: Int, scratchBuffer: ByteBuffer, symbolTable: Array<String?>): TextResult {
        throw IonException("Not positioned on a symbol")
    }

    fun readSymbol0xA0(source: ByteBuffer, position: Int, scratchBuffer: ByteBuffer, symbolTable: Array<String?>): TextResult {
        return ZERO_LENGTH_SYMBOL
    }

    fun readSymbol0xA1(source: ByteBuffer, position: Int, scratchBuffer: ByteBuffer, symbolTable: Array<String?>): TextResult {
        val byte = source.get(position)
        if (byte < 0) throw IllegalArgumentException("invalid UTF-8 character $position")
        return TextResult(1, byte.toInt().toChar().toString())
    }

    fun readSymbol0xA2(source: ByteBuffer, position: Int, scratchBuffer: ByteBuffer, symbolTable: Array<String?>): TextResult {
        val bytes = ByteArray(2)
        scratchBuffer.limit(position + 2)
        scratchBuffer.position(position)
        scratchBuffer.get(bytes)
        return TextResult(2, String(bytes, Charsets.UTF_8))
    }

    fun readSymbol0xA3(source: ByteBuffer, position: Int, scratchBuffer: ByteBuffer, symbolTable: Array<String?>): TextResult {
        val bytes = ByteArray(3)
        scratchBuffer.limit(position + 3)
        scratchBuffer.position(position)
        scratchBuffer.get(bytes)
        return TextResult(3, StandardCharsets.UTF_8.decode(scratchBuffer).toString())
    }

    fun readSymbol0xA4(source: ByteBuffer, position: Int, scratchBuffer: ByteBuffer, symbolTable: Array<String?>): TextResult {
        val bytes = ByteArray(4)
        scratchBuffer.limit(position + 4)
        scratchBuffer.position(position)
        scratchBuffer.get(bytes)
        return TextResult(4, StandardCharsets.UTF_8.decode(scratchBuffer).toString())
    }
}
