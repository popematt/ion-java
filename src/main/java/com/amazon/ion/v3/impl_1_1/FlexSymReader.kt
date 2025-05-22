package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.impl.*
import java.nio.ByteBuffer

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
//            val position = source.position()
//            val scratchBuffer = pool.scratchBuffer
//            scratchBuffer.limit(position + length)
//            scratchBuffer.position(position)
            // source.position(position + length)
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
//            val position = source.position()
//            val scratchBuffer = pool.scratchBuffer
//            scratchBuffer.limit(position + length)
//            scratchBuffer.position(position)
//            source.position(position + length)
            text = pool.utf8Decoder.decode(source, length)
        }
    }
}
