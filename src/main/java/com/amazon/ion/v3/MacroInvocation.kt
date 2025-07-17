package com.amazon.ion.v3

import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*

class MacroInvocation(
    val macro: MacroV2,
    val arguments: ArgumentBytecode,
    val evaluate: (TemplateResourcePool) -> ValueReader
)
