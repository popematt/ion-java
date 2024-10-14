// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl.macro

import com.amazon.ion.impl.macro.ParameterFactory.exactlyOneFlexInt
import com.amazon.ion.impl.macro.ParameterFactory.exactlyOneTagged
import com.amazon.ion.impl.macro.ParameterFactory.oneToManyTagged
import com.amazon.ion.impl.macro.ParameterFactory.zeroToManyTagged

/**
 * Macros that are built in, rather than being defined by a template.
 */
enum class SystemMacro(val id: Byte, val macroName: String, override val signature: List<Macro.Parameter>) : Macro {
    None(0, "none", emptyList()),
    Values(1, "values", listOf(zeroToManyTagged("values"))),
    Annotate(2, "annotate", listOf(zeroToManyTagged("ann"), exactlyOneTagged("value"))),
    MakeString(3, "make_string", listOf(zeroToManyTagged("text"))),
    MakeSymbol(4, "make_symbol", listOf(zeroToManyTagged("text"))),
    MakeBlob(5, "make_blob", listOf(zeroToManyTagged("bytes"))),
    MakeDecimal(6, "make_decimal", listOf(exactlyOneFlexInt("coefficient"), exactlyOneFlexInt("exponent"))),

    Repeat(17, "repeat", listOf(exactlyOneTagged("n"), oneToManyTagged("value"))),

    MakeField(
        22, "make_field",
        listOf(
            Macro.Parameter("field_name", Macro.ParameterEncoding.CompactSymbol, Macro.ParameterCardinality.ExactlyOne), exactlyOneTagged("value")
        )
    ),

    // TODO: Other system macros

    // Technically not system macros, but special forms. However, it's easier to model them as if they are macros in TDL.
    // We give them an ID of -1 to distinguish that they are not addressable outside TDL.
    IfNone(-1, "if_none", listOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch"))),
    IfSome(-1, "if_some", listOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch"))),
    IfSingle(-1, "if_single", listOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch"))),
    IfMulti(-1, "if_multi", listOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch"))),
    ;

    override val dependencies: List<Macro>
        get() = emptyList()

    companion object {

        private val MACROS_BY_NAME: Map<String, SystemMacro> = SystemMacro.entries.associateBy { it.macroName }

        // TODO: Once all of the macros are implemented, replace this with an array as in SystemSymbols_1_1
        private val MACROS_BY_ID: Map<Byte, SystemMacro> = SystemMacro.entries
            .filterNot { it.id < 0 }
            .associateBy { it.id }

        @JvmStatic
        fun size() = MACROS_BY_ID.size

        /** Gets a [SystemMacro] by its address in the system table */
        @JvmStatic
        operator fun get(id: Int): SystemMacro? = MACROS_BY_ID[id.toByte()]

        /** Gets, by name, a [SystemMacro] with an address in the system table (i.e. that can be invoked as E-Expressions) */
        @JvmStatic
        operator fun get(name: String): SystemMacro? = MACROS_BY_NAME[name]?.takeUnless { it.id < 0 }

        @JvmStatic
        operator fun get(address: MacroRef): SystemMacro? {
            return when (address) {
                is MacroRef.ById -> get(address.id)
                is MacroRef.ByName -> get(address.name)
            }
        }

        /** Gets a [SystemMacro] by name, including those which are not in the system table (i.e. special forms) */
        @JvmStatic
        fun getMacroOrSpecialForm(name: String): SystemMacro? = MACROS_BY_NAME[name]
    }
}
