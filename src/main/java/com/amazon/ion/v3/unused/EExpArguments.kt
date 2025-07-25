package com.amazon.ion.v3.unused

import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.ListReader
import com.amazon.ion.v3.SexpReader
import com.amazon.ion.v3.StructReader
import com.amazon.ion.v3.impl_1_1.MacroV2
import com.amazon.ion.v3.impl_1_1.binary.ResourcePool
import com.amazon.ion.v3.impl_1_1.binary.StructReaderImpl
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class EExpArguments(
    @JvmField
    var arguments: Array<IntArray>,
    @JvmField
    var constants: Array<Any?>,
    @JvmField
    val source: ByteBuffer,
    @JvmField
    var signature: Array<Macro.Parameter>,
    @JvmField
    val symbolTable: Array<String?>,
    @JvmField
    val macroTable: Array<MacroV2>,
    @JvmField
    val pool: ResourcePool,
) {

    fun getSource(): ByteBuffer = source

    fun constantPool(): Array<Any?> {
        return constants
    }

    fun iterator(): Iterator<IntArray> = object : Iterator<IntArray> {
        private var i = 0
        override fun hasNext(): Boolean = i < arguments.size
        override fun next(): IntArray = arguments[i++]
    }

    fun getArgument(parameterIndex: Int): IntArray {
        return arguments[parameterIndex]
    }

    fun getList(start:Int, length:Int): ListReader {
        return pool.getList(start, length, symbolTable, macroTable)
    }

    fun getSexp(start:Int, length:Int): SexpReader {
        return pool.getPrefixedSexp(start, length, symbolTable, macroTable)
    }

    fun getStruct(start: Int, length: Int, flexsymMode: Boolean): StructReader {
        val struct = pool.getStruct(start, length, symbolTable, macroTable)
        struct as StructReaderImpl
        struct.flexSymMode = flexsymMode
        return struct
    }

    fun getMacro(macroAddress: Int): MacroV2 {
        return macroTable[macroAddress]
    }

    fun getSymbol(sid: Int): String? {
        if (sid == 0) throw Exception("SID = 0")
        return symbolTable[sid]
    }

    fun readStringRef(position: Int, length: Int): String {
        val scratchBuffer = pool.scratchBuffer
        scratchBuffer.limit(position + length)
        scratchBuffer.position(position)
        return StandardCharsets.UTF_8.decode(scratchBuffer).toString()
        // return pool.utf8Decoder.decode(scratchBuffer, length)
    }
}
