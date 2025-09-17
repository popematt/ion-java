package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.bin.IntList
import java.math.BigDecimal
import java.math.BigInteger

/**
 * This abstracts a particular input source (e.g. [ByteArray], [ByteBuffer][java.nio.ByteBuffer]) and Ion version out
 * of the [BytecodeIonReader].
 *
 * Currently unused, but aspirational.
 */
interface BytecodeGenerator {

    // TODO: Does this method need to return the symbol table and/or constant pool as well?
    //       No, we're not going to update the encoding context in the bytecode generator.
    //       That might limit the applicability because the bytecode needs to contain directives
    //       for all possible Ion Versions... but we'll deal with that later.
    fun refill(
        /** The IntList that is to be filled with the bytecode */
        destination: IntList,
        /** A container for holding object instances */
        constantPool: MutableList<Any?>,
        /** Bytecode for each macro in the effective macro table */
        macroSrc: IntArray,
        /**
         * A lookup table indicating for each macro address, where to find the first
         * instruction for that macro in [macroSrc]
         */
        macroIndices: IntArray,
        /** The current symbol table */
        symTab: Array<String?>,
    )

    fun readBigIntegerReference(position: Int, length: Int): BigInteger
    fun readDecimalReference(position: Int, length: Int): BigDecimal
    fun readShortTimestampReference(position: Int, opcode: Int): Timestamp
    fun readTimestampReference(position: Int, length: Int): Timestamp
    fun readTextReference(position: Int, length: Int): String
    fun readBytesReference(position: Int, length: Int): ByteArray

    // TODO: If we add uint64 references, add overrides of these methods that support a `long` position.

}
