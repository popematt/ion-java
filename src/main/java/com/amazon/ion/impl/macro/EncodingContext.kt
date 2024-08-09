package com.amazon.ion.impl.macro

class EncodingContext(
    val macroTable: Map<MacroRef, Macro>
) {
    companion object {
        @JvmStatic
        val EMPTY = EncodingContext(emptyMap())
    }
}
