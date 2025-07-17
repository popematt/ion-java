package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.instructionToOp

class TemplateStructReaderImpl(pool: TemplateResourcePool): ValueReader, StructReader, TemplateReaderBase(pool), SequenceReader {

    override fun fieldName(): String? {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_FIELD_NAME -> {
//                val constantPoolIndex = instruction and 0xFFFFFF
                instruction = INSTRUCTION_NOT_SET
                val i = this.i
                val fieldName = bytecode.unsafeGetReferenceAt<String?>(i)
                this.i = i + 2
                return fieldName
            }
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
    }

    override fun fieldNameSid(): Int {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_FIELD_NAME -> return -1
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
    }

    override fun returnToPool() {
//        if (pool.structs.contains(this)) {
//            throw IllegalStateException("Cannot doubly add to the pool.")
//        }
        pool.structs.add(this)
    }
}
