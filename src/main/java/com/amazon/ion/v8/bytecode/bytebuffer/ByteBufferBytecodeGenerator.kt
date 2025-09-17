package com.amazon.ion.v8.bytecode.bytebuffer

import com.amazon.ion.Timestamp
import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.v8.BytecodeGenerator
import com.amazon.ion.v8.bytecode.bytebuffer.ByteBufferToBytecode.String
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class ByteBufferBytecodeGenerator(
    private val source: ByteBuffer
): BytecodeGenerator {

    override fun refill(
        destination: IntList,
        constantPool: MutableList<Any?>,
        macroSrc: IntArray,
        macroIndices: IntArray,
        symTab: Array<String?>
    ) {
        // Fill 1 top-level value, or until the bytecode buffer is full, whichever comes first.
        val source = source
        var i = source.position()
        i += ByteBufferToBytecode.compileTopLevel(source, i, destination, constantPool, macroSrc, macroIndices, symTab, source.limit())
        source.position(i)
    }

    override fun readBigIntegerReference(position: Int, length: Int): BigInteger {
        TODO("Not yet implemented")
    }

    override fun readDecimalReference(position: Int, length: Int): BigDecimal {
        return DecimalHelper.readDecimal(source, position, length)
    }

    override fun readShortTimestampReference(position: Int, opcode: Int): Timestamp =
        TimestampHelper.readShortTimestampAt(opcode, source, position)

    override fun readTimestampReference(position: Int, length: Int): Timestamp =
        TimestampHelper.readLongTimestampAt(source, position, length)

    override fun readTextReference(position: Int, length: Int): String {
        // TODO: try the decoder pool, as in IonContinuableCoreBinary
        return String(source, position, length, StandardCharsets.UTF_8)
    }

    override fun readBytesReference(position: Int, length: Int): ByteArray {
        TODO("Not yet implemented")
    }
}
