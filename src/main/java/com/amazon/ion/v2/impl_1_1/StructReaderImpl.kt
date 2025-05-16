package com.amazon.ion.v2.impl_1_1

import com.amazon.ion.impl.*
import com.amazon.ion.v2.*
import java.nio.ByteBuffer

class StructReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
): ValueReaderBase(source, pool, symbolTable), StructReader {

    private var flexSymMode = false
    private var fieldNameSid = -1
    private var fieldName: String? = null

    override fun close() {
        pool.structs.add(this)
    }

    override fun nextToken(): Int {
        if (!source.hasRemaining()) return TokenTypeConst.END
        if (opcode == TID_NONE) return TokenTypeConst.FIELD_NAME
        return super.nextToken()
    }

    override fun fieldName(): String? {
        opcode = TID_AFTER_FIELD_NAME
        if (!flexSymMode) {
            val sid = IntHelper.readFlexUInt(source)
            if (sid == 0) {
                flexSymMode = true
                return fieldName
            } else {
                return symbolTable[sid]
            }
        }
        readFlexSym()
        return fieldName
    }

    override fun fieldNameSid(): Int {
        opcode = TID_AFTER_FIELD_NAME
        if (!flexSymMode) {
            val sid = IntHelper.readFlexUInt(source)
            if (sid == 0) {
                flexSymMode = true
            } else {
                return sid
            }
        }
        readFlexSym()
        return fieldNameSid
    }

    private fun readFlexSym() {
        val flexSym = IntHelper.readFlexIntAsLong(source).toInt()
        if (flexSym == 0) {
            val systemSid = (source.get().toInt() and 0xFF) - 0x60
            fieldNameSid = if (systemSid == 0) 0 else -1
            fieldName = SystemSymbols_1_1[systemSid]?.text
        } else if (flexSym > 0) {
            fieldNameSid = flexSym
            fieldName = symbolTable[fieldNameSid]
        } else {
            fieldNameSid = -1
            val length = -flexSym
            val position = source.position()
            val scratchBuffer = pool.scratchBuffer
            scratchBuffer.limit(position + length)
            scratchBuffer.position(position)
            source.position(position + length)
            fieldName = pool.utf8Decoder.decode(scratchBuffer, length)
        }
    }
}
