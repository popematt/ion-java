// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*

/**
 * Represents a template macro. A template macro is defined by a signature, and a list of template expressions.
 * A template macro only gains a name and/or ID when it is added to a macro table.
 */
data class MacroV2 internal constructor(
    override val signature: List<Macro.Parameter>,
    override val body: List<TemplateBodyExpressionModel>?,
    val systemAddress: Byte = -1,
    val systemName: SystemSymbols_1_1? = null,
) : FlatMacro {

    constructor(signature: List<Macro.Parameter>, body: List<TemplateBodyExpressionModel>) : this(signature, body, -1, null)

    // TODO: Consider rewriting the body of the macro if we discover that there are any macros invoked using only
    //       constants as arguments—either at compile time or lazily.
    //       For example, the body of: (macro foo (x)  (values (make_string "foo" "bar") x))
    //       could be rewritten as: (values "foobar" x)

    private val cachedHashCode by lazy { signature.hashCode() * 31 + body.hashCode() }
    override fun hashCode(): Int = cachedHashCode

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MacroV2) return false
        // Check the hashCode as a quick check before we dive into the actual data.
        if (cachedHashCode != other.cachedHashCode) return false
        if (signature != other.signature) return false
        if (body != other.body) return false
        return true
    }

    override val dependencies: List<FlatMacro> by lazy {
        if (body != null) {
            body.mapNotNull { it.value as? FlatMacro }.distinct()
        } else {
            emptyList()
        }
    }
}
