package com.amazon.ion.impl

//import com.amazon.ion.*
//import com.amazon.ion.IonCursor.RestorePoint
//import com.amazon.ion.impl.macro.*
//import com.amazon.ion.util.confirm
//
//class IonReaderContinuableMacroExpander(
//    private val reader: IonReaderContinuableCore,
//    private val macroTable: java.util.function.Function<MacroRef, Macro>,
//    private val symbolTable: java.util.function.Function<Int, String>
//): IonReaderContinuableCore by reader {
//
//    /*
//    How does the reader handle macros?
//
//    When calling "next", if there's a macro, we automatically step into it and start evaluating it enough to get the type of the output.
//    Once we reach the end of the macro, we automatically step out.
//     */
//
//    /// A macro in the process of being evaluated. Stores both the state of the evaluation and the
//    /// syntactic element that represented the macro invocation.
//    //pub struct MacroExpansion<'top, D: LazyDecoder> {
//    //    kind: MacroExpansionKind<'top, D>,
//    //    invocation: MacroExpr<'top, D>,
//    //}
//
//    /**
//     * A sequence of not-yet-evaluated expressions passed as arguments to a macro invocation.
//     *
//     * The number of expressions is required to match the number of parameters in the macro's signature,
//     * and the order of the expressions corresponds to the order of the parameters.
//     *
//     * For example, given this macro definition:
//     * ```ion_1_1
//     *     (macro foo (x y z) [x, y, z])
//     * ```
//     * and this invocation:
//     * ```ion_1_1
//     *     (:foo 1 2 (:values 3))
//     * ```
//     * The `Environment` would contain the expressions `1`, `2` and `3`, corresponding to parameters
//     * `x`, `y`, and `z` respectively.
//     */
//    private class MacroEnvironment(val variables: List<RestorePoint>)
//
//    private class MacroExpansionContext {
//        var macro: Macro? = null
//        var isTemplate: Boolean = false
//        var expressions: Iterator<TemplateBodyExpression>? = null
//        val variables: MutableMap<String, RestorePoint> = mutableMapOf()
//    }
//
//    /**
//     * A stack with the most recent macro invocations at the top. This stack grows each time a macro
//     * of any kind begins evaluation.
//     */
//    private val macroExpansionStack = _Private_RecyclingStack(8, ::MacroExpansionContext)
//
//    /**
//     * A stack of _template_ macro invocation environments. This stack only grows when a template
//     * macro is invoked from any context. For example, given these template definitions:
//     * ```
//     *     (macro foo (x) (values 1 2 x))
//     *     (macro bar (y) (foo y))
//     * ```
//     * and this invocation:
//     * ```
//     *     (:bar 3)
//     * ```
//     * A new environment `[y=3]` would be pushed for the invocation of `bar`, and another
//     * environment `[x=y=3]` would be pushed for the invocation of `foo` within `bar`. However,
//     * no environment would be created/pushed for the invocation of the `values` macro within `foo`.
//     * For any macro being evaluated, the current environment is always the one at the top of the
//     * environment stack.
//     */
//    private val environmentStack = ArrayList<MacroEnvironment>()
//
//    /**
//     * Stack of restore points to set the reader back to where it was.
//     */
//    private val restorePointStack = ArrayList<RestorePoint>()
//
//    /**
//     * Caller is responsible for ensuring that the reader is positioned at a macro invocation, but not stepped into
//     * the macro.
//     */
//    fun initMacro() {
//        confirm(!reader.hasAnnotations()) { "E-Expressions may not be annotated" }
//        reader.stepIntoContainer()
//        reader.nextValue()
//        val macroRef = when (reader.type) {
//            IonType.SYMBOL -> MacroRef.ByName(readResolvedSymbolText())
//            IonType.INT -> MacroRef.ById(reader.longValue())
//            else -> throw IonException("macro invocation must start with an id (int) or identifier (symbol); found ${reader.type ?: "nothing"}\"")
//        }
//        macroExpansionStack.push {
//            val macro = macroTable.apply(macroRef)
//            it.macro = macro
//            it.isTemplate = macro is TemplateMacro
//            it.expressions = (macro as? TemplateMacro)?.body?.listIterator()
//            it.variables.clear()
//            readArguments(macro.signature, it.variables)
//        }
//
//        (reader as IonReaderContinuableCoreBinary).valueTid.isMacroInvocation
//    }
//
//    /**
//     * Caller should not position the reader on the first macro argument (i.e. the first value after the
//     * macro name).
//     */
//    private fun readArguments(signature: List<Macro.Parameter>, variables: MutableMap<String, RestorePoint>) {
//        for (param in signature) {
//            // TODO: reading grouped params
//            reader.nextValue()
//            variables[param.variableName] = reader.newRestorePoint()
//        }
//    }
//
//
//    /**
//     * Gets the text of a symbol, using the symbol table to resolve if necessary.
//     */
//    private fun readResolvedSymbolText(): String = when (val id = reader.symbolValueId()) {
//        -1 -> reader.stringValue() ?: throw UnknownSymbolException("Unknown symbol text!")
//        0 -> throw UnknownSymbolException("Unknown symbol text!")
//        else -> symbolTable.apply(id)
//    }
//}
