package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.impl.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

internal object FlexSymHelper {
    internal interface FlexSymDestination {
        var _text: String?
        var _sid: Int
    }

    @JvmStatic
    fun skipFlexSym(source: ByteBuffer) {
        val flexSym = IntHelper.readFlexIntAsLong(source).toInt()
        if (flexSym == 0) {
            source.get()
        } else if (flexSym < 0) {
            val length = -flexSym
            val position = source.position()
            source.position(position + length)
        }
    }

    @JvmStatic
    fun skipFlexSymAt(source: ByteBuffer, position: Int): Int {
        var i = position
        i = IntHelper.skipFlexUIntAt(source, i)
        val flexSym = IntHelper.readFlexIntAt(source, position)
        if (flexSym == 0) {
            i++
        } else if (flexSym < 0) {
            i += -flexSym
        }
        return i
    }

    @JvmStatic
    fun lengthOfFlexSymAt(source: ByteBuffer, position: Int): Int {
        var i = position
        val flexSym = IntHelper.readFlexIntAt(source, position)
        i = IntHelper.skipFlexUIntAt(source, i)
        if (flexSym == 0) {
            i++
        } else if (flexSym < 0) {
            i += -flexSym
        }
        return i - position
    }

    @JvmStatic
    fun readFlexSymSidAt(source: ByteBuffer, position: Int): Int {
        val flexSym = IntHelper.readFlexIntAt(source, position)
        if (flexSym <= 0) return -1
        return flexSym
    }

    @JvmStatic
    fun readFlexSym(source: ByteBuffer, destination: FlexSymDestination, pool: ResourcePool, symbolTable: Array<String?>) {
        val flexSym = IntHelper.readFlexIntAsLong(source).toInt()
        if (flexSym == 0) {
            val systemSid = (source.get().toInt() and 0xFF) - 0x60
            destination._sid = if (systemSid == 0) 0 else -1
            destination._text = SystemSymbols_1_1[systemSid]?.text
        } else if (flexSym > 0) {
            destination._sid = flexSym
            destination._text = symbolTable[flexSym]
        } else {
            destination._sid = -1
            val length = -flexSym
            destination._text = pool.utf8Decoder.decode(source, length)
        }
    }
}

class FlexSymReader(private val pool: ResourcePool) {

    var text: String? = null
        private set

    var sid: Int = -1
        private set

    fun readFlexSym(source: ByteBuffer) {
        val flexSym = IntHelper.readFlexIntAsLong(source).toInt()
        if (flexSym == 0) {
            val systemSid = (source.get().toInt() and 0xFF) - 0x60
            sid = if (systemSid == 0) 0 else -1
            text = SystemSymbols_1_1[systemSid]?.text
        } else if (flexSym > 0) {
            sid = flexSym
            text = null
        } else {
            sid = -1
            val length = -flexSym
            // TODO: This scratch buffer shouldn't be necessary.
            val position = source.position()
            val scratchBuffer = pool.scratchBuffer
            scratchBuffer.limit(position + length)
            scratchBuffer.position(position)
            source.position(position + length)
            try {

                text = StandardCharsets.UTF_8.decode(scratchBuffer).toString()
                // text = pool.utf8Decoder.decode(scratchBuffer, length)
            } catch (e: IonException) {
                System.err.println("Starting at $position Error: ${e.message}")
                throw e
            }
        }
    }
}
