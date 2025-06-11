package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

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
    macroTable: Array<Macro>,
): ValueReaderBase(source, pool, symbolTable, macroTable), StructReader {


    fun findEnd() {
        source.mark()
        while (nextToken() != TokenTypeConst.END) {
            fieldNameSid()
            if (nextToken() == TokenTypeConst.ANNOTATIONS) nextToken()
            skip()
        }
        source.limit(source.position())
        source.reset()
    }

    private var flexSymReader: FlexSymReader = FlexSymReader(pool)

    override fun close() {
        while (nextToken() != TokenTypeConst.END) {
            skip()
        }
//        if (this in pool.delimitedStructs) throw IllegalStateException("Already closed: $this")
        parent.source.position(source.position())
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
            this.opcode = TID_ON_FIELD_NAME
            return TokenTypeConst.FIELD_NAME
        }
        if (opcode == TID_AFTER_FIELD_NAME.toInt()) {
            this.opcode = TID_UNSET
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
