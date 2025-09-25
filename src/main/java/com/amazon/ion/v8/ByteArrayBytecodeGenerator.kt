package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.bin.utf8.Utf8StringDecoderPool
import com.amazon.ion.v8.Bytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.min

class ByteArrayBytecodeGenerator(
    private val source: ByteArray
): BytecodeGenerator {
    private var i = 0

    private val decoder = Utf8StringDecoderPool.getInstance().getOrCreate()
    private val buffer = ByteBuffer.wrap(source)

    override fun refill(
        destination: IntList,
        constantPool: MutableList<Any?>,
        macroSrc: IntArray,
        macroIndices: IntArray,
        symTab: Array<String?>
    ) {

        // Fill 1 top-level value, or until the bytecode buffer is full, whichever comes first.
        val source = source
        var i = i
        i += smartCompileTopLevel(source, i, destination, constantPool, macroSrc, macroIndices, symTab)
        this.i = i
    }

    override fun readBigIntegerReference(position: Int, length: Int): BigInteger {
        TODO("Not yet implemented")
    }

    override fun readDecimalReference(position: Int, length: Int): BigDecimal {
        return DecimalHelper.readDecimal(source, position, length)
    }

    override fun readShortTimestampReference(position: Int, opcode: Int): Timestamp = TimestampHelper.readShortTimestampAt(opcode, source, position)

    override fun readTimestampReference(position: Int, length: Int): Timestamp = TimestampHelper.readLongTimestampAt(source, position, length)

    override fun readTextReference(position: Int, length: Int): String {
        // TODO: try the decoder pool, as in IonContinuableCoreBinary
//        return String(source, position, length, StandardCharsets.UTF_8)
        buffer.limit(position + length)
        buffer.position(position)
        return decoder.decode(buffer, length)
    }

    override fun readBytesReference(position: Int, length: Int): ByteArray {
        TODO("Not yet implemented")
    }
}
