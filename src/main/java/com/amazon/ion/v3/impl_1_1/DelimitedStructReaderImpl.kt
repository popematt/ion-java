package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.v3.*
import java.nio.ByteBuffer

class DelimitedStructReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
): ValueReaderBase(source, pool), StructReader {

    private var flexSymReader: FlexSymReader = FlexSymReader(pool)

    override fun close() {
        pool.delimitedStructs.add(this)
    }

    override fun nextToken(): Int {
        if (!source.hasRemaining()) return TokenTypeConst.END
        val opcode = opcode.toInt()
        // Do we need to check for a field name first?
        if (opcode == TID_UNSET.toInt()) {
            // Check for FlexSym escape followed by delimited end.
            val position = source.position()
            val flexSym = source.getShort(position).toInt() and 0xFFFF
            if (flexSym == 0xF001) {
                this.opcode = TID_UNSET
                // Update the buffer to account for `0xF001`
                source.position(position + 2)
                source.limit(position + 2)
                return TokenTypeConst.END
            }
            return TokenTypeConst.FIELD_NAME
        }
        return super.nextToken()
    }

    override fun fieldName(): String? {
        if (opcode == TID_AFTER_FIELD_NAME) {
            return flexSymReader.text
        }
        opcode = TID_AFTER_FIELD_NAME
        flexSymReader.readFlexSym(source)
        return flexSymReader.text
    }

    override fun fieldNameSid(): Int {
        opcode = TID_AFTER_FIELD_NAME
        flexSymReader.readFlexSym(source)
        return flexSymReader.sid
    }
}
