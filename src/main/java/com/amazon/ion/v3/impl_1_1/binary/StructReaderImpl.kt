package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.impl.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class StructReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
    macroTable: Array<MacroV2>,
): ValueReaderBase(source, pool, symbolTable, macroTable), StructReader {

    @JvmField
    var flexSymMode: Boolean = false

    override fun close() {
        if (this in pool.structs) throw IllegalStateException("Already closed: $this")
        pool.structs.add(this)
    }

    override fun resetState() {
        flexSymMode = false
    }

    // e0, 01, 01, ea,
    // e0, 01, 01, ea,
    // e7, 01, 63,      $ion_symbol_table::
    // fd, 42, 8c,      {
    // 01,                 // switch to flexsym mode
    // 01, 66,             imports:
    // ee, 03,                      $ion_symbol_table,
    // 01, 67,             symbols:
    // fb, 1a, 8c,                 [
    // 98, 24, 69, 6f, 6e, 5f, 6c    "...
    override fun nextToken(): Int {
        if (i >= limit) return TokenTypeConst.END
        if (opcode == TID_UNSET) return TokenTypeConst.FIELD_NAME
        return super.nextToken()
    }

    override fun fieldName(): String? {
        if (flexSymMode) {
            return flexSymFieldName()
        }
        var p = i
        val flexUIntValueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
        val sid = (flexUIntValueAndLength ushr 8).toInt()
        p += (flexUIntValueAndLength and 0xFF).toInt()
        i = p
        if (sid == 0) {
            flexSymMode = true
            return flexSymFieldName()
        }
        opcode = TID_AFTER_FIELD_NAME
        return symbolTable[sid]
    }

    override fun fieldNameSid(): Int {
        if (flexSymMode) {
            return flexSymFieldNameSid()
        }
        var p = i
        val flexUIntValueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
        val sid = (flexUIntValueAndLength ushr 8).toInt()
        p += (flexUIntValueAndLength and 0xFF).toInt()
        i = p

        if (sid == 0) {
            flexSymMode = true
            return flexSymFieldNameSid()
        }
        opcode = TID_AFTER_FIELD_NAME
        return sid
    }

    private fun flexSymFieldName(): String? {
        var p = i
        val flexSymLengthAndValue = IntHelper.readFlexIntValueAndLengthAt(source, p)
        val flexSym = (flexSymLengthAndValue shr 8).toInt()
        p += (flexSymLengthAndValue and 0xFF).toInt()
        val text = if (flexSym == 0) {
            val systemSid = (source.get(p++).toInt() and 0xFF) - 0x60
            i = p
            SystemSymbols_1_1[systemSid]?.text
        } else if (flexSym > 0) {
            symbolTable[flexSym]
        } else {
            val textLength = -flexSym
            val scratchBuffer = pool.scratchBuffer
            scratchBuffer.limit(p + textLength)
            scratchBuffer.position(p)
            p += textLength
            StandardCharsets.UTF_8.decode(scratchBuffer).toString()
        }
        i = p
        opcode = TID_AFTER_FIELD_NAME
        return text
    }

    private fun flexSymFieldNameSid(): Int {
        val p = i
        val flexIntValueAndLength = IntHelper.readFlexIntValueAndLengthAt(source, p)
        val length = (flexIntValueAndLength and 0xFF).toInt()
        val flexSym = (flexIntValueAndLength shr 8).toInt()
        if (flexSym > 0) {
            opcode = TID_AFTER_FIELD_NAME
            i += length
            return flexSym
        }
        return -1
    }
}
