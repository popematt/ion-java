package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.impl.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Implementation of delimited structs.
 *
 * TODO: See if we can combine the delimited and length prefixed implementations.
 *       There may be a slight performance benefit because calls to `StructReader`
 *       could be optimized more if there are fewer implementations to choose from.
 *       See https://shipilev.net/blog/2015/black-magic-method-dispatch/
 *
 * To combine them, we probably just need to add a "isDelimited" flag, make sure
 * that `flexSymMode` is always enabled for delimited structs, and check for the
 * delimited end marker.
 */
class DelimitedStructReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
    @JvmField
    var parent: ValueReaderBase,
    symbolTable: Array<String?>,
    macroTable: Array<MacroV2>,
): ValueReaderBase(source, pool, symbolTable, macroTable), StructReader {

    // TODO: Consolidate field name logic with the logic that looks for the end delimiter.

    override fun close() {
        while (nextToken() != TokenTypeConst.END) {
            skip()
        }
//        if (this in pool.delimitedStructs) throw IllegalStateException("Already closed: $this")
        parent.i = this.i
        pool.delimitedStructs.add(this)
    }

    override fun nextToken(): Int {
        if (i >= limit) return TokenTypeConst.END
        val opcode = opcode.toInt()
        // Do we need to check for a field name first?
        if (opcode == TID_UNSET.toInt()) {
            // Check for FlexSym escape followed by delimited end.
            val p = i
            val flexSym = source.getShort(p).toInt() and 0xFFFF
            if (flexSym == 0xF001) {
                this.opcode = TID_UNSET
                // Update the buffer to account for `0xF001`
                i = p + 2
                limit = p + 2
                return TokenTypeConst.END
            }
            this.opcode = TID_ON_FIELD_NAME
            return TokenTypeConst.FIELD_NAME
        }
        if (opcode == TID_AFTER_FIELD_NAME.toInt()) {
            this.opcode = TID_UNSET
        }
        return super.nextToken()
    }

    override fun fieldName(): String? {
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

    override fun fieldNameSid(): Int {
        val p = i
        val sid = FlexSymHelper.readFlexSymSidAt(source, p)
        if (sid > 0) {
            opcode = TID_AFTER_FIELD_NAME
            i += IntHelper.lengthOfFlexUIntAt(source, p)
        }
        return sid
    }
}
