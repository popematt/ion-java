package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.impl.macro.Expression.*
import com.amazon.ion.v3.*

class TemplateStructReaderImpl(
    pool: TemplateResourcePool,
    info: TemplateResourcePool.TemplateInvocationInfo,
    startInclusive: Int,
    endExclusive: Int,
): ValueReader, StructReader, TemplateReaderBase(pool, info, startInclusive, endExclusive) {

    // The text should have been resolved when the macro was compiled. Even if there is a SID,
    // it doesn't necessarily correspond to the current symbol table.
    override fun fieldNameSid(): Int = -1

    override fun fieldName(): String? = takeCurrentExpression<FieldName>().value.text

    override fun close() {
        pool.structs.add(this)
    }
}
