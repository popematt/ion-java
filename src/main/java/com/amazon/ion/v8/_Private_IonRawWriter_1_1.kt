// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v8


/**
 * Allows us to write encoding directives in a more optimized and/or readable way.
 * Could be used to construct invalid data if used in the wrong way, so we don't
 * expose this to users.
 *
 * Some functions may be meaningless to a particular underlying implementation.
 */
internal interface _Private_IonRawWriter_1_1 : IonRawWriter_1_1 {

    fun writeTaggedPlaceholder()

    fun writeTaggedPlaceholderWithDefault(default: (IonRawWriter_1_1) -> Unit)

    // TODO: Figure out the correct signature for this
    fun writeTaglessPlaceholder(opcode: Int)

    // TODO: Make this more robust instead of just using an [Ops] value.
    fun stepInDirective(directive: Int) {}

    /**
     * Sets a flag that can override the newlines that are normally inserted by a pretty printer.
     *
     * Ignored by binary implementations.
     *
     * TODO: Once system symbols are implemented, consider replacing this with dedicated
     *       `startClause(SystemSymbol)` and `endClause()`, or similar.
     * * This will allow the text writer to
     *    * start the clauses without added newlines
     *    * Skip checking whether to write annotations
     * * This will allow the binary writer to
     *    * Leverage macros, when possible
     *    * Skip checking whether to write annotations
     *    * Skip checking whether a given string or SID is a system symbol
     *      * E.g. `startClause(SystemSymbol_1_1.MACRO_TABLE)` could directly write the
     *        bytes `F2 EF` followed by `E3` for "macro_table".
     */
    fun forceNoNewlines(boolean: Boolean) = Unit
}
