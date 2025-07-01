package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*

class TemplateStructReaderImpl(
    pool: TemplateResourcePool,
    source: Array<TemplateBodyExpressionModel>,
    arguments: ArgumentReader,
    isArgumentOwner: Boolean,
): ValueReader, StructReader, TemplateReaderBase(pool, source, arguments, isArgumentOwner),
    SequenceReader {

    override fun toString(): String {
        return "TemplateStructReaderImpl(source=$source)"
    }

    override fun fieldName(): String? = consumeCurrentExpression(
        { it.symbolValue() },
        { it.fieldName }
    )

    override fun fieldNameSid(): Int {
        val sid = inspectCurrentExpression(
            { it.symbolValueSid() },
            // The text should have been resolved when the macro was compiled. Even if there is a SID,
            // it doesn't necessarily correspond to the current symbol table.
            { -1 }
        )
        if (sid >= 0) {
            currentExpression = null
        }
        return sid
    }




    override fun returnToPool() {
//        if (pool.structs.contains(this)) {
//            throw IllegalStateException("Cannot doubly add to the pool.")
//        }
        pool.structs.add(this)
    }
}
