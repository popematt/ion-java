package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.impl.macro.Macro

sealed interface FlatMacro {
    val signature: List<Macro.Parameter>
    val body: List<TemplateBodyExpressionModel>?
    val dependencies: Iterable<FlatMacro>
}
