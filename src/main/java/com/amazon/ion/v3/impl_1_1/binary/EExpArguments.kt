package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
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
): ArgumentBytecode {



    override fun constantPool(): Array<Any?> {
        return constants
    }

    override fun iterator(): Iterator<IntArray> = object : Iterator<IntArray> {
        private var i = 0
        override fun hasNext(): Boolean = i < arguments.size
        override fun next(): IntArray = arguments[i++]
    }

    override fun getArgument(parameterIndex: Int): IntArray {
        return arguments[parameterIndex]
    }

    override fun getList(start:Int, length:Int): ListReader {
        return pool.getList(start, length, symbolTable, macroTable)
    }

    override fun getSexp(start:Int, length:Int): SexpReader {
        return pool.getPrefixedSexp(start, length, symbolTable, macroTable)
    }

    override fun getStruct(start: Int, length: Int, flexsymMode: Boolean): StructReader {
        val struct = pool.getStruct(start, length, symbolTable, macroTable)
        struct as StructReaderImpl
        struct.flexSymMode = flexsymMode
        return struct
    }

    override fun getMacro(macroAddress: Int): MacroV2 {
        return macroTable[macroAddress]
    }

    override fun getSymbol(sid: Int): String? {
        if (sid == 0) throw Exception("SID = 0")
        return symbolTable[sid]
    }

    override fun readStringRef(position: Int, length: Int): String {
        val scratchBuffer = pool.scratchBuffer
        scratchBuffer.limit(position + length)
        scratchBuffer.position(position)
        return StandardCharsets.UTF_8.decode(scratchBuffer).toString()
        // return pool.utf8Decoder.decode(scratchBuffer, length)
    }
}
