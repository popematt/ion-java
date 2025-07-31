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
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction

/**
 * Macros that are built in, rather than being defined by a template.
 */
object SystemMacro {

    const val IF_NONE_ADDRESS: Int = -5
    const val IF_SOME_ADDRESS: Int = -2
    const val IF_SINGLE_ADDRESS: Int = -3
    const val IF_MULTI_ADDRESS: Int = -4
    const val NONE_ADDRESS: Int = 0
    const val VALUES_ADDRESS: Int = 1
    const val DEFAULT_ADDRESS: Int = 2
    const val META_ADDRESS: Int = 3
    const val REPEAT_ADDRESS: Int = 4
    const val FLATTEN_ADDRESS: Int = 5
    const val DELTA_ADDRESS: Int = 6
    const val SUM_ADDRESS: Int = 7
    const val ANNOTATE_ADDRESS: Int = 8
    const val MAKE_STRING_ADDRESS: Int = 9
    const val MAKE_SYMBOL_ADDRESS: Int = 10
    const val MAKE_DECIMAL_ADDRESS: Int = 11
    const val MAKE_TIMESTAMP_ADDRESS: Int = 12
    const val MAKE_BLOB_ADDRESS: Int = 13
    const val MAKE_LIST_ADDRESS: Int = 14
    const val MAKE_SEXP_ADDRESS: Int = 15
    const val MAKE_FIELD_ADDRESS: Int = 16
    const val MAKE_STRUCT_ADDRESS: Int = 17
    const val PARSE_ION_ADDRESS: Int = 18
    const val SET_SYMBOLS_ADDRESS: Int = 19
    const val ADD_SYMBOLS_ADDRESS: Int = 20
    const val SET_MACROS_ADDRESS: Int = 21
    const val ADD_MACROS_ADDRESS: Int = 22
    const val USE_ADDRESS: Int = 23



    @JvmStatic
    // Technically not system macros, but special forms. However, it's easier to model them as if they are macros in TDL.
    // We give them negative addresses to distinguish that they are not addressable outside TDL.
    val IfNone = MacroV2(
        arrayOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch")),
        body = null,
        systemName = IF_NONE,
        systemAddress = IF_NONE_ADDRESS,
    )

    @JvmStatic
    val IfSome = MacroV2(
        signature = arrayOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch")),
        body = null,
        systemName = IF_SOME,
        systemAddress = IF_SOME_ADDRESS,
    )
    @JvmStatic
    val IfSingle = MacroV2(
        signature = arrayOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch")),
        body = null,
        systemName = IF_SINGLE,
        systemAddress = IF_SINGLE_ADDRESS,
    )
    @JvmStatic
    val IfMulti = MacroV2(
        signature = arrayOf(zeroToManyTagged("stream"), zeroToManyTagged("true_branch"), zeroToManyTagged("false_branch")),
        body = null,
        systemName = IF_MULTI,
        systemAddress = IF_MULTI_ADDRESS,
    )

    // Unnameable, unaddressable macros used for the internals of certain other system macros
    // TODO: See if we can move these somewhere else so that they are not visible

    // The real macros

    @JvmStatic
    val Values = MacroV2(
        signature = arrayOf(zeroToManyTagged("values")),
        body = templateBody { variable("values", 0) },
        systemName = VALUES,
        systemAddress = VALUES_ADDRESS
    )

    @JvmStatic
    val None = MacroV2(
        signature = emptyArray(),
        body = templateBody { macro(Values) { expressionGroup {  } } },
        systemName = NONE,
        systemAddress = NONE_ADDRESS,
//         bytecode = IntArray(MacroBytecode.OP_RETURN.opToInstruction()),
//         constants = emptyArray(),
    )


    @JvmStatic
    val Default = MacroV2(
        signature = arrayOf(zeroToManyTagged("expr"), zeroToManyTagged("default_expr")),
        body = null,
        systemName = DEFAULT,
        systemAddress = DEFAULT_ADDRESS,
    )

    @JvmStatic
    val Meta = MacroV2(
        signature = arrayOf(zeroToManyTagged("values")),
        body = templateBody { macro(None) {} },
        systemName = META,
        systemAddress = META_ADDRESS,
//        bytecode = IntArray(MacroBytecode.OP_RETURN.opToInstruction()),
//        constants = emptyArray(),
    )

    @JvmStatic
    val Repeat = MacroV2(
        arrayOf(exactlyOneTagged("n"), zeroToManyTagged("value")),
        body = null,
        systemName = REPEAT,
        systemAddress = REPEAT_ADDRESS,
    )

    @JvmStatic
    val Flatten = MacroV2(
        arrayOf(zeroToManyTagged("values")),
        body = null,
        systemName = FLATTEN,
        systemAddress = FLATTEN_ADDRESS,
    )

    @JvmStatic
    val Delta = MacroV2(
        arrayOf(zeroToManyTagged("deltas")),
        body = null,
        systemName = DELTA,
        systemAddress = DELTA_ADDRESS,
    )

    @JvmStatic
    val Sum = MacroV2(
        signature = arrayOf(exactlyOneTagged("a"), exactlyOneTagged("b")),
        body = null,
        systemName = SUM,
        systemAddress = SUM_ADDRESS,
    )

    @JvmStatic
    val Annotate = MacroV2(
        arrayOf(zeroToManyTagged("ann"), exactlyOneTagged("value")),
        body = null,
        systemName = ANNOTATE,
        systemAddress = ANNOTATE_ADDRESS,
    )

    @JvmStatic
    val MakeString = MacroV2(
        arrayOf(zeroToManyTagged("text")),
        body = null,
        systemName = MAKE_STRING,
        systemAddress = MAKE_STRING_ADDRESS,
    )

    @JvmStatic
    val MakeSymbol = MacroV2(
        arrayOf(zeroToManyTagged("text")),
        body = null,
        systemName = MAKE_SYMBOL,
        systemAddress = MAKE_SYMBOL_ADDRESS,
    )

    @JvmStatic
    val MakeDecimal = MacroV2(
        arrayOf(exactlyOneTagged("coefficient"), exactlyOneTagged("exponent")),
        body = null,
        systemName = MAKE_DECIMAL,
        systemAddress = MAKE_DECIMAL_ADDRESS,
    )

    @JvmStatic
    val MakeTimestamp = MacroV2(
        arrayOf(
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
        systemAddress = MAKE_TIMESTAMP_ADDRESS,
    )

    @JvmStatic
    val MakeBlob = MacroV2(
        arrayOf(zeroToManyTagged("bytes")),
        body = null,
        systemName = MAKE_BLOB,
        systemAddress = MAKE_BLOB_ADDRESS,
    )

    @JvmStatic
    val MakeList = MacroV2(
        arrayOf(zeroToManyTagged("sequences")),
        body = templateBody {
            list {
                macro(Flatten) {
                    variable("sequences", 0)
                }
            }
        },
        systemName = MAKE_LIST,
        systemAddress = MAKE_LIST_ADDRESS,
    )

    @JvmStatic
    val MakeSExp = MacroV2(
        arrayOf(zeroToManyTagged("sequences")),
        templateBody {
            sexp {
                macro(Flatten) {
                    variable("sequences", 0)
                }
            }
        },
        systemName = MAKE_SEXP,
        systemAddress = MAKE_SEXP_ADDRESS,
    )

    @JvmStatic
    val MakeField = MacroV2(
        arrayOf(exactlyOneTagged("fieldName"), exactlyOneTagged("value")),
        body = null,
        systemName = MAKE_FIELD,
        systemAddress = MAKE_FIELD_ADDRESS,
    )

    @JvmStatic
    val MakeStruct = MacroV2(
        arrayOf(zeroToManyTagged("structs")),
        body = null,
        systemName = MAKE_STRUCT,
        systemAddress = MAKE_STRUCT_ADDRESS,
    )

    @JvmStatic
    val ParseIon = MacroV2(
        arrayOf(zeroToManyTagged("data")),
        body = null,
        systemName = PARSE_ION,
        systemAddress = PARSE_ION_ADDRESS,
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
        arrayOf(zeroToManyTagged("symbols")),
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
                    list { variable("symbols", 0) }
                }
            }
        },
        systemName = SET_SYMBOLS,
        systemAddress = SET_SYMBOLS_ADDRESS,
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
        arrayOf(zeroToManyTagged("symbols")),
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
                    list { variable("symbols", 0) }
                }
            }
        },
        systemName = ADD_SYMBOLS,
        systemAddress = ADD_SYMBOLS_ADDRESS,
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
        arrayOf(zeroToManyTagged("macros")),
        templateBody {
            annotated(ION, ::sexp) {
                symbol(MODULE)
                symbol(DEFAULT_MODULE)
                sexp {
                    symbol(MACROS)
                    variable("macros", 0)
                }
                sexp {
                    symbol(SYMBOLS)
                    symbol(DEFAULT_MODULE)
                }
            }
        },
        systemName = SET_MACROS,
        systemAddress = SET_MACROS_ADDRESS,
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
        arrayOf(zeroToManyTagged("macros")),
        templateBody {
            annotated(ION, ::sexp) {
                symbol(MODULE)
                symbol(DEFAULT_MODULE)
                sexp {
                    symbol(MACROS)
                    symbol(DEFAULT_MODULE)
                    variable("macros", 0)
                }
                sexp {
                    symbol(SYMBOLS)
                    symbol(DEFAULT_MODULE)
                }
            }
        },
        systemName = ADD_MACROS,
        systemAddress = ADD_MACROS_ADDRESS,
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
        arrayOf(exactlyOneTagged("catalog_key"), zeroOrOneTagged("version")),
        templateBody {
            val theModule = "the_module"
            annotated(ION, ::sexp) {
                symbol(MODULE)
                symbol(DEFAULT_MODULE)
                sexp {
                    symbol(IMPORT)
                    symbol(theModule)
                    variable("catalog_key", 0)
                    // The version is optional in the import clause so we don't need to specify the default.
                    variable("version", 1)
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
        systemAddress = USE_ADDRESS,
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
