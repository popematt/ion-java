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
interface BytecodeProvider {

    // TODO: Does this method need to return the symbol table and/or constant pool as well?
    fun refill(destination: IntList)

    // fun getCurrentSymbolTable(): Array<String?>
    // fun getCurrentConstantPool()

    fun readBigIntegerReference(position: Int, length: Int): BigInteger
    fun readDecimalReference(position: Int, length: Int): BigDecimal
    fun readShortTimestampReference(position: Int, length: Int): Timestamp
    fun readLongTimestampReference(position: Int, length: Int): Timestamp
    fun readTextReference(position: Int, length: Int): String

    // TODO: fun getEffectiveModule(), returns the current, active symbol and macro tables so that they can be used as a shared module?

    // TODO: If we add uint64 references, add overrides of these methods that support a `long` position.

}
