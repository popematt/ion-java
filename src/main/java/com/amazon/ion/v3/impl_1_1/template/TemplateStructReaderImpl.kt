package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.v3.*

class TemplateStructReaderImpl(
    pool: TemplateResourcePool,
    info: TemplateResourcePool.TemplateInvocationInfo,
    startInclusive: Int,
    endExclusive: Int,
    isArgumentOwner: Boolean
): ValueReader, StructReader, TemplateReaderBase(pool, info, startInclusive, endExclusive, isArgumentOwner),
    SequenceReader {

    override fun fieldName(): String? = consumeCurrentExpression(
        { it.symbolValue() },
        { expr: Expression.FieldName -> expr.value.text }
    )

    override fun fieldNameSid(): Int {
        val sid = inspectCurrentExpression(
            { it.symbolValueSid() },
            // The text should have been resolved when the macro was compiled. Even if there is a SID,
            // it doesn't necessarily correspond to the current symbol table.
            { expr: Expression.FieldName -> -1 }
        )
        if (sid >= 0) {
            currentExpression = null
        }
        return sid
    }




    override fun returnToPool() {
        pool.structs.add(this)
    }
}
