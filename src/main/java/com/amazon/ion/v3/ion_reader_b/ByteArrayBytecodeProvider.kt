package com.amazon.ion.v3.ion_reader_b

import com.amazon.ion.Decimal
import com.amazon.ion.Timestamp
import java.nio.charset.StandardCharsets

class ByteArrayBytecodeProvider(val data: ByteArray) : BytecodeIonReaderInput {

    override fun refill(bytecode: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun readTextRef(start: Int, length: Int): String {
        // TODO: try the decoder pool, as in IonContinuableCoreBinary
        return String(data, start, length, StandardCharsets.UTF_8)
    }

    override fun readDecimalRef(start: Int, length: Int): Decimal {
        TODO("Not yet implemented")
    }

    override fun readShortTimestampRef(start: Int, opcode: Int): Timestamp {
        TODO("Not yet implemented")
    }

    override fun readLongTimestampRef(start: Int, length: Int): Timestamp {
        TODO("Not yet implemented")
    }
}
