package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.*

class TemplateStructReader(pool: TemplateResourcePool): TemplateReaderImpl(pool), StructReader {

    override fun fieldName(): String? = _fieldName()
    override fun fieldNameSid(): Int = _fieldNameSid()

    override fun close() {
        pool.returnStruct(this)
    }
}
