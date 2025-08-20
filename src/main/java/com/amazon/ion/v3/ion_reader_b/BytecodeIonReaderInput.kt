package com.amazon.ion.v3.ion_reader_b

import com.amazon.ion.Decimal
import com.amazon.ion.Timestamp

/**
 * TODO: Work on this once we've proved the merits of this approach using just byte arrays.
 */
interface BytecodeIonReaderInput {
    /**
     * This is allowed to refill up to the end of the bytecode array OR until a system value is encountered.
     * Or we could add in a bytecode instruction that updates the symbol table...?
     *
     * If we don't add in a symbol table instruction, then BytecodeProvider needs to be able to update the symbol
     * table and macro tables of BytecodeIonReader.
     */
    fun refill(bytecode: ByteArray): Boolean

    fun readTextRef(start: Int, length: Int): String
    fun readDecimalRef(start: Int, length: Int): Decimal
    fun readShortTimestampRef(start: Int, opcode: Int): Timestamp
    fun readLongTimestampRef(start: Int, length: Int): Timestamp

    // TODO: Read lobs, BigIntegers(?)
}

// Alternately, we could abstract over the bytes, rather than over the bytecode.
interface BytesInput {
    fun get(index: Int): Byte
    fun get2Bytes(index: Int): Short
    fun get3Bytes(index: Int): Int
    fun get4Bytes(index: Int): Int
    fun get5Bytes(index: Int): Long
    fun get6Bytes(index: Int): Long
    fun get7Bytes(index: Int): Long
    fun get8Bytes(index: Int): Long
    fun getNBytes(n: Int, index: Int): Long
    // Add in args for copying.
    fun copyBytes()
}

