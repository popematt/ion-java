package com.amazon.ion.v3

import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.*
import com.amazon.ion.v3.impl_1_1.template.*

/**
 * Goal:
 *   This holds the macro definition, a way to iterate over the arguments, and a way to evaluate the macro.
 *
 * When given to user code, the user code must choose exactly one of iterate over the arguments or evaluate the macro (and potentially skip the macro).
 *
 * Ideally, reading the arguments is done lazily so that there isn't going to be double work done.
 */
class MacroInvocation(
    val macro: MacroV2,
    val arguments: () -> ArgumentBytecode,
    val evaluate: () -> ValueReader,
    val iterateArguments: () -> Iterator<ValueReader>
)
