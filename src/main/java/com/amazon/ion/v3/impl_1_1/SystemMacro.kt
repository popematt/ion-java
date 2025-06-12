// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.SystemSymbols.DEFAULT_MODULE
import com.amazon.ion.impl.*
import com.amazon.ion.impl.SystemSymbols_1_1.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.ParameterFactory.exactlyOneTagged
import com.amazon.ion.impl.macro.ParameterFactory.zeroOrOneTagged
import com.amazon.ion.impl.macro.ParameterFactory.zeroToManyTagged
import com.amazon.ion.v3.impl_1_1.ExpressionBuilderDsl.Companion.templateBody

/**
 * Macros that are built in, rather than being defined by a template.
 */
object SystemMacro {
    @JvmStatic
    // Technically not system macros, but special forms. However, it's easier to model them as if they are macros in TDL.
    // We give them an ID of -1 to distinguish that they are not addressable outside TDL.
    val IfNone = MacroV2(
        listOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch")),
        body = null,
        systemName = IF_NONE,
        systemAddress = -1,
    )

    @JvmStatic
    val IfSome = MacroV2(
        signature = listOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch")),
        body = null,
        systemName = IF_SOME,
        systemAddress = -1,
    )
    @JvmStatic
    val IfSingle = MacroV2(
        signature = listOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch")),
        body = null,
        systemName = IF_SINGLE,
        systemAddress = -1,
    )
    @JvmStatic
    val IfMulti = MacroV2(
        signature = listOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch")),
        body = null,
        systemName = IF_MULTI,
        systemAddress = -1,
    )

    // Unnameable, unaddressable macros used for the internals of certain other system macros
    // TODO: See if we can move these somewhere else so that they are not visible

    // The real macros

    @JvmStatic
    val None = MacroV2(
        signature = emptyList(),
        body = null,
        systemName = NONE,
        systemAddress = 0,
    )

    @JvmStatic
    val Values = MacroV2(
        signature = listOf(zeroToManyTagged("values")),
        body = templateBody { variable(0) },
        systemName = VALUES,
        systemAddress = 1
    )

    @JvmStatic
    val Default = MacroV2(
        signature = listOf(zeroToManyTagged("expr"), zeroToManyTagged("default_expr")),
        body = null,
        systemName = DEFAULT,
        systemAddress = 2,
    )

    @JvmStatic
    val Meta = MacroV2(
        signature = listOf(zeroToManyTagged("values")),
        body = templateBody { macro(None) {} },
        systemName = META,
        systemAddress = 3,
    )

    @JvmStatic
    val Repeat = MacroV2(
        listOf(exactlyOneTagged("n"), zeroToManyTagged("value")),
        body = null,
        systemName = REPEAT,
        systemAddress = 4,
    )

    @JvmStatic
    val Flatten = MacroV2(
        listOf(zeroToManyTagged("values")),
        body = null,
        systemName = FLATTEN,
        systemAddress = 5,
    )

    @JvmStatic
    val Delta = MacroV2(
        listOf(zeroToManyTagged("deltas")),
        body = null,
        systemName = DELTA,
        systemAddress = 6,
    )

    @JvmStatic
    val Sum = MacroV2(
        signature = listOf(exactlyOneTagged("a"), exactlyOneTagged("b")),
        body = null,
        systemName = SUM,
        systemAddress = 7,
    )

    @JvmStatic
    val Annotate = MacroV2(
        listOf(zeroToManyTagged("ann"), exactlyOneTagged("value")),
        body = null,
        systemName = ANNOTATE,
        systemAddress = 8,
    )

    @JvmStatic
    val MakeString = MacroV2(
        listOf(zeroToManyTagged("text")),
        body = null,
        systemName = MAKE_STRING,
        systemAddress = 9,
    )

    @JvmStatic
    val MakeSymbol = MacroV2(
        listOf(zeroToManyTagged("text")),
        body = null,
        systemName = MAKE_SYMBOL,
        systemAddress = 10,
    )

    @JvmStatic
    val MakeDecimal = MacroV2(
        listOf(exactlyOneTagged("coefficient"), exactlyOneTagged("exponent")),
        body = null,
        systemName = MAKE_DECIMAL,
        systemAddress = 11,
    )

    @JvmStatic
    val MakeTimestamp = MacroV2(
        listOf(
            exactlyOneTagged("year"),
            zeroOrOneTagged("month"),
            zeroOrOneTagged("day"),
            zeroOrOneTagged("hour"),
            zeroOrOneTagged("minute"),
            zeroOrOneTagged("second"),
            zeroOrOneTagged("offset_minutes"),
        ),
        body = null,
        systemName = MAKE_TIMESTAMP,
        systemAddress = 12,
    )

    @JvmStatic
    val MakeBlob = MacroV2(
        listOf(zeroToManyTagged("bytes")),
        body = null,
        systemName = MAKE_BLOB,
        systemAddress = 13,
    )

    @JvmStatic
    val MakeList = MacroV2(
        listOf(zeroToManyTagged("sequences")),
        body = templateBody {
            list {
                macro(Flatten) {
                    variable(0)
                }
            }
        },
        systemName = MAKE_LIST,
        systemAddress = 14,
    )

    @JvmStatic
    val MakeSExp = MacroV2(
        listOf(zeroToManyTagged("sequences")),
        templateBody {
            sexp {
                macro(Flatten) {
                    variable(0)
                }
            }
        },
        systemName = MAKE_SEXP,
        systemAddress = 15,
    )

    @JvmStatic
    val MakeField = MacroV2(
        listOf(exactlyOneTagged("fieldName"), exactlyOneTagged("value")),
        body = null,
        systemName = MAKE_SYMBOL,
        systemAddress = 17,
    )

    @JvmStatic
    val MakeStruct = MacroV2(
        listOf(zeroToManyTagged("structs")),
        body = null,
        systemName = MAKE_STRUCT,
        systemAddress = 18,
    )

    @JvmStatic
    val ParseIon = MacroV2(
        listOf(zeroToManyTagged("data")),
        body = null,
        systemName = PARSE_ION,
        systemAddress = 19,
    )

    /**
     * ```ion
     * (macro set_symbols (symbols*)
     *        $ion::(module _
     *          (macros _)
     *          (symbols [(%symbols)])
     *        ))
     * ```
     */
    @JvmStatic
    val SetSymbols = MacroV2(
        listOf(zeroToManyTagged("symbols")),
        templateBody {
            annotated(ION, ::sexp) {
                symbol(MODULE)
                symbol(DEFAULT_MODULE)
                sexp {
                    symbol(MACROS)
                    symbol(DEFAULT_MODULE)
                }
                sexp {
                    symbol(SYMBOLS)
                    list { variable(0) }
                }
            }
        },
        systemName = SET_SYMBOLS,
        systemAddress = 20,
    )

    /**
     * ```ion
     * (macro add_symbols (symbols*)
     *        $ion::(module _
     *          (macros _)
     *          (symbols _ [(%symbols)])
     *        ))
     * ```
     */
    @JvmStatic
    val AddSymbols = MacroV2(
        listOf(zeroToManyTagged("symbols")),
        templateBody {
            annotated(ION, ::sexp) {
                symbol(MODULE)
                symbol(DEFAULT_MODULE)
                sexp {
                    symbol(MACROS)
                    symbol(DEFAULT_MODULE)
                }
                sexp {
                    symbol(SYMBOLS)
                    symbol(DEFAULT_MODULE)
                    list { variable(0) }
                }
            }
        },
        systemName = ADD_SYMBOLS,
        systemAddress = 21,
    )

    /**
     * ```ion
     * (macro set_macros (macros*)
     *        $ion::(module _
     *          (macros (%macros))
     *          (symbols _)
     *        ))
     * ```
     */
    @JvmStatic
    val SetMacros = MacroV2(
        listOf(zeroToManyTagged("macros")),
        templateBody {
            annotated(ION, ::sexp) {
                symbol(MODULE)
                symbol(DEFAULT_MODULE)
                sexp {
                    symbol(MACROS)
                    variable(0)
                }
                sexp {
                    symbol(SYMBOLS)
                    symbol(DEFAULT_MODULE)
                }
            }
        },
        systemName = SET_MACROS,
        systemAddress = 22,
    )

    /**
     * ```ion
     * (macro add_macros (macros*)
     *        $ion::(module _
     *          (macros _ (%macros))
     *          (symbols _)
     *        ))
     * ```
     */
    @JvmStatic
    val AddMacros = MacroV2(
        listOf(zeroToManyTagged("macros")),
        templateBody {
            annotated(ION, ::sexp) {
                symbol(MODULE)
                symbol(DEFAULT_MODULE)
                sexp {
                    symbol(MACROS)
                    symbol(DEFAULT_MODULE)
                    variable(0)
                }
                sexp {
                    symbol(SYMBOLS)
                    symbol(DEFAULT_MODULE)
                }
            }
        },
        systemName = ADD_MACROS,
        systemAddress = 23,
    )

    /**
     * ```ion
     * (macro use (catalog_key version?)
     *        $ion::(module _
     *          (import the_module catalog_key (%version))
     *          (macros _ the_module)
     *          (symbols _ the_module)
     *        ))
     * ```
     */
    @JvmStatic
    val Use = MacroV2(
        listOf(exactlyOneTagged("catalog_key"), zeroOrOneTagged("version")),
        templateBody {
            val theModule = "the_module"
            annotated(ION, ::sexp) {
                symbol(MODULE)
                symbol(DEFAULT_MODULE)
                sexp {
                    symbol(IMPORT)
                    symbol(theModule)
                    variable(0)
                    // The version is optional in the import clause so we don't need to specify the default.
                    variable(1)
                }
                sexp {
                    symbol(MACROS)
                    symbol(DEFAULT_MODULE)
                    symbol(theModule)
                }
                sexp {
                    symbol(SYMBOLS)
                    symbol(DEFAULT_MODULE)
                    symbol(theModule)
                }
            }
        },
        systemName = USE,
        systemAddress = 24,
    )


    @JvmStatic
    private val entries = listOf(
        IfNone,
        IfSome,
        IfSingle,
        IfMulti,
        None,
        Values,
        Default,
        Meta,
        Repeat,
        Flatten,
        Delta,
        Sum,
        Annotate,
        MakeString,
        MakeSymbol,
        MakeDecimal,
        MakeTimestamp,
        MakeBlob,
        MakeList,
        MakeSExp,
        MakeField,
        MakeStruct,
        ParseIon,
        SetSymbols,
        AddSymbols,
        SetMacros,
        AddMacros,
        Use,
    )



    private val MACROS_BY_NAME: Map<String, MacroV2> = SystemMacro.entries
        .filter { it.systemName != null }
        .associateBy { it.systemName!!.text }

    @JvmStatic
    internal val MACROS_BY_ID: Array<MacroV2> = SystemMacro.entries
        .filterNot { it.systemAddress < 0 }
        .sortedBy { it.systemAddress }
        .toTypedArray()

    @JvmStatic
    fun size() = 24

    /** Gets a [SystemMacro] by its address in the system table */
    @JvmStatic
    operator fun get(id: Int): MacroV2 = MACROS_BY_ID[id]

    /** Gets, by name, a [SystemMacro] with an address in the system table (i.e. that can be invoked as E-Expressions) */
    @JvmStatic
    operator fun get(name: String): MacroV2? = MACROS_BY_NAME[name]?.takeUnless { it.systemAddress < 0 }

    @JvmStatic
    operator fun get(address: MacroRef): MacroV2? {
        return if (address.hasId()) {
            get(address.id)
        } else {
            get(address.name!!)
        }
    }

    /** Gets a [SystemMacro] by name, including those which are not in the system table (i.e. special forms) */
    @JvmStatic
    fun getMacroOrSpecialForm(ref: MacroRef): MacroV2? {
        return if (ref.hasId()) {
            get(ref.id)
        } else {
            MACROS_BY_NAME[ref.name!!]
        }
    }
}
