package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

/**
 * TODO: Combine this with [SeqReaderImpl]
 */
class DelimitedSequenceSkipperImpl(
    source: ByteBuffer,
    pool: ResourcePool,
    var parent: ValueReaderBase,
    symbolTable: Array<String?>,
    macroTable: Array<Macro>,
): ValueReaderBase(source, pool, symbolTable, macroTable), ListReader, SexpReader {

    override fun nextToken(): Int {
        val token = super.nextToken()
        if (token == TokenTypeConst.END) {
            source.limit(source.position())
        }
        return token
    }

//    override fun skip() {
//        val opcode = opcode.toInt()
//        if (this.opcode == TID_END) {
//            TODO()
//        }
//        val length = IdMappings.length(opcode, source)
//        if (length >= 0) {
//            source.position(source.position() + length)
//        } else {
//            if (opcode < 0x60) {
//                val macro = macroValue()
//                val end = macroArguments(macro.signature).use { it.calculateEndPosition() }
//                source.position(end)
//            } else when (opcode) {
//                // Symbol with FlexUInt SID
//                0xE3 -> symbolValueSid()
//
//                // TODO: make this better for delimited containers. Because of the way that `listValue()`
//                //   et al are implemented for delimited containers, this does a double read of the delimited
//                //   containers in order to skip it once. This problem is compounded when nested. Delimited
//                //   containers that are nested 1 layer deep will be skipped with a double read for each read
//                //   of the parent, so 4 reads to skip it once.
//                // FIXME: 14% of all
//
//                // Delimited List and sexp
//                0xF1, 0xF2 -> {
//                    val start = source.position()
//                    pool.getDelimitedSeqSkipper(start, this, symbolTable, macroTable).close()
//                }
//                // Opcode F3
//                0xF3 -> structValue().use {
//                    while (it.nextToken() != TokenTypeConst.END) {
////                        it.fieldName()
////                        it.nextToken()
//                        it.skip()
//                    }
//                    source.position((it as ValueReaderBase).source.position())
//                }
//                0xE4, 0xE5, 0xE6,
//                0xE7, 0xE8, 0xE9  -> annotations().close()
//                0xEF, 0xF4 -> {
//                    val macro = macroValue()
//                    val end = macroArguments(macro.signature).use { it.calculateEndPosition() }
//                    source.position(end)
//                }
//                else -> {
//                    TODO("Skipping an opcode: 0x${opcode.toString(16)}")
//                }
//            }
//        }
//        this.opcode = TID_UNSET
//    }

    override fun close() {
        while (nextToken() != TokenTypeConst.END) {
            skip()
        }

        parent.source.position(this.source.position())
    }
}
