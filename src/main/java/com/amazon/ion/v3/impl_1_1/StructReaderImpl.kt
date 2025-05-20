package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.v3.*
import java.nio.ByteBuffer

class StructReaderImpl internal constructor(
    source: ByteBuffer,
    pool: ResourcePool,
): ValueReaderBase(source, pool), StructReader {

    private var flexSymReader: FlexSymReader = FlexSymReader(pool)
    private var flexSymMode: Boolean = false

    override fun close() {
        pool.structs.add(this)
    }

    override fun nextToken(): Int {
        if (!source.hasRemaining()) return TokenTypeConst.END
        if (opcode == TID_UNSET) return TokenTypeConst.FIELD_NAME
        return super.nextToken()
    }

    override fun fieldName(): String? {
        if (opcode == TID_AFTER_FIELD_NAME) {
            return if (flexSymMode) {
                flexSymReader.text
            } else {
                null
            }
        }
        opcode = TID_AFTER_FIELD_NAME
        if (!flexSymMode) {
            val sid = IntHelper.readFlexUInt(source)
            if (sid == 0) {
                flexSymMode = true
            } else {
                return null
            }
        }
        flexSymReader.readFlexSym(source)
        return flexSymReader.text
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
        flexSymReader.readFlexSym(source)
        return flexSymReader.sid
    }
}
