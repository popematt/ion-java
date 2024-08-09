package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl._Private_RecyclingStack

class MacroEvaluator(
    private val encodingContext: EncodingContext,
    // TODO: private val maxNumberOfExpandedValues: Int = 1_000_000,
) {

    private fun interface Expander {
        /**
         * Implementations must update [ExpansionInfo.i] in order for [ExpansionInfo.hasNext] to work properly.
         */
        fun nextExpression(expansionInfo: ExpansionInfo, macroEvaluator: MacroEvaluator): Expression
    }
    private object DefaultExpander: Expander {
        override fun nextExpression(expansionInfo: ExpansionInfo, macroEvaluator: MacroEvaluator): Expression {
            with (expansionInfo) {
                val next = expressions!![i]
                if (next is Expression.Container) i = next.endInclusive
                if (next is Expression.MacroInvocation) i = next.endInclusive
                if (next is Expression.EExpression) i = next.endInclusive
                if (next is Expression.ExpressionGroup) i = next.endInclusive
                i++
                return next
            }
        }
    }
    private object MakeStringExpander: Expander {
        override fun nextExpression(expansionInfo: ExpansionInfo, macroEvaluator: MacroEvaluator): Expression {
            with(expansionInfo) {
                val sb = StringBuilder()
                while (hasNext()) {
                    val text = when (val expr = DefaultExpander.nextExpression(this, macroEvaluator)) {
                        is Expression.ExpressionGroup -> {
                            // We'll just ignore the expression group marker and look at the args directly.
                            i = expr.startInclusive + 1
                            continue
                        }

                        is Expression.StringValue -> expr.value
                        is Expression.SymbolValue -> expr.value.assumeText()
                        is Expression.NullValue -> ""
                        is Expression.MacroInvocation -> {
                            macroEvaluator.pushMacroExpansion(expressions!!, i)
                            TODO("Read the expressions produced by this macro expansion")
                        }
                        // TODO: References
                        else -> throw IonException("Not a valid argument for make_string: $expr")
                    }
                    sb.append(text)
                }
                return Expression.StringValue(value = sb.toString())
            }
        }
    }

    /**
     * The core logic of the macro expansion.
     */
    private enum class ExpansionKind(val nextFn: Expander) {
        Container(DefaultExpander),
        TemplateBody(DefaultExpander),
        Values(DefaultExpander),
        MakeString(MakeStringExpander);
    }

    private inner class ExpansionInfo: Iterator<Expression> {
        /**
         * The [IonType] of this expansion, if it is a container.
         * TODO: Do we really need this? Let's leave it for now and re-evaluate after
         *       struct inlining, make_struct, make_list, etc. are complete.
         */
        @JvmField var type: IonType? = null
        /**
         * The evaluation [Environment]—i.e. variable bindings.
         */
        @JvmField var environment: Environment? = null
        /**
         * The [Expression]s being expanded. This MUST be the original list, not a sublist because
         * (a) we don't want to be allocating new sublists all the time, and (b) the
         * start and end indices of the expressions may be incorrect if a sublist is taken.
         */
        @JvmField var expressions: List<Expression>? = null
        /** End of [expressions] that are applicable for this [ExpansionInfo] */
        @JvmField var startInclusive: Int = 0
        /** End of [expressions] that are applicable for this [ExpansionInfo] */
        @JvmField var endExclusive: Int = 0
        /** Current position within [expressions] of this expansion */
        @JvmField var i: Int = 0

        /** The [ExpansionKind]. */
        @JvmField var expansionKind: ExpansionKind = ExpansionKind.TemplateBody

        override fun hasNext(): Boolean = i < endExclusive

        override fun next(): Expression {
            return expansionKind.nextFn.nextExpression(this, this@MacroEvaluator)
        }

        override fun toString() = """
        |ExpansionInfo(
        |    expansionKind: $expansionKind,
        |    type: $type,
        |    environment: $environment,
        |    expressions: [
        |        ${expressions!!.joinToString(",\n|        ") { it.toString() } }
        |    ],
        |    startInclusive: $startInclusive,
        |    endExclusive: $endExclusive,
        |    i: $i,
        |)
        """.trimMargin()
    }

    private val expansionStack = _Private_RecyclingStack(8) { ExpansionInfo() }

    private var currentExpr: Expression? = null

    private fun pushMacroExpansion(encodingExpressions: List<Expression>, i: Int) {
        val currentEnvironment = expansionStack.peek()?.environment ?: Environment.EMPTY

        val argsStartInclusive = i + 1
        val (address, argsEndExclusive) = when (val it = encodingExpressions[i]) {
            is Expression.EExpression -> it.address to it.endInclusive + 1
            is Expression.MacroInvocation -> it.address to it.endInclusive + 1
            else -> throw IllegalStateException("Attempted to push macro for an expression that is not a macro: $it")
        }

        val macro = encodingContext.macroTable[address] ?: throw IonException("No such macro: $address")

        val expansionInfo = expansionStack.push {
            it.startInclusive = -1
            it.endExclusive = -1
            it.i = -1
            it.type = null
            it.expressions = null
            it.environment = null
            it.type = null
        }

        val argIndices = mutableListOf<Int>()
        var args_i = argsStartInclusive
        for (p in macro.signature) {

            if (args_i >= argsEndExclusive) {
                if (!p.cardinality.canBeVoid) {
                    throw IonException("No value provided for parameter ${p.variableName}")
                } else {
                    // Elided rest parameter.
                    argIndices.add(-1)
                }
            } else {
                argIndices.add(args_i)
                val argumentExpression = encodingExpressions[args_i]
                if (argumentExpression is Expression.HasStartAndEnd) {
                    val argEndExclusive = argumentExpression.endInclusive + 1
                    args_i = argEndExclusive
                } else {
                    args_i++
                }
            }
        }
        while (args_i < argsEndExclusive) {
            argIndices.add(args_i)
            val argumentExpression = encodingExpressions[args_i]
            if (argumentExpression is Expression.HasStartAndEnd) {
                val argEndExclusive = argumentExpression.endInclusive + 1
                args_i = argEndExclusive
            } else {
                args_i++
            }
        }
        if (argIndices.size != macro.signature.size) {
            throw IonException("Too many arguments. Expected ${macro.signature.size}, but found ${argIndices.size}; they were $argIndices")
        }

        when (macro) {
            is TemplateMacro -> {
                expansionInfo.i = 0
                expansionInfo.startInclusive = 0
                expansionInfo.endExclusive = macro.body.size
                expansionInfo.expressions = macro.body
                expansionInfo.expansionKind = ExpansionKind.TemplateBody
                expansionInfo.environment = currentEnvironment.createChild(encodingExpressions, argIndices)
                // TODO: See if we can populate this based on static analysis of compiled template
                expansionInfo.type = null
            }
            SystemMacro.Values -> {
                // Expand `values` by expanding its expression group argument
                expansionInfo.expansionKind = ExpansionKind.Values

                expansionInfo.i = argsStartInclusive
                expansionInfo.startInclusive = argsStartInclusive
                expansionInfo.endExclusive = argsEndExclusive

                expansionInfo.expressions = encodingExpressions
                expansionInfo.environment = currentEnvironment
            }
            SystemMacro.MakeString -> {
                expansionInfo.expansionKind = ExpansionKind.MakeString
                expansionInfo.type = IonType.STRING

                expansionInfo.i = argsStartInclusive
                expansionInfo.startInclusive = argsStartInclusive
                expansionInfo.endExclusive = argsEndExclusive

                expansionInfo.expressions = encodingExpressions
                expansionInfo.environment = currentEnvironment
            }
        }
    }

    fun initExpansion(encodingExpressions: List<Expression>) {
        pushMacroExpansion(encodingExpressions, 0)
    }

    fun expandNext(): Expression? {
        // Algorithm:
        // Check the top expansion in the expansion stack
        //    If there is none, return null (macro expansion is over)
        //    If there is one, but it has no more expressions
        //        If the expansion kind is a data-model container type, return null (user needs to step out)
        //        If the expansion kind is not a data-model container type, automatically step out
        //    If there is one, and it has more expressions
        //        If it is a scalar, return that
        //        If it is a container, return that (user needs to step in)
        //        If it is a variable...
        //        If it is an expression group...
        //        If it is a macro invocation...

        currentExpr = null
        while (!expansionStack.isEmpty) {
            if (!expansionStack.peek().hasNext()) {
                if (expansionStack.peek().expansionKind == ExpansionKind.Container) {
                    // End of container. User needs to step out.
                    return null
                } else {
                    // End of a macro invocation or something else that is not part of the data model,
                    // so we seamlessly close this out and continue with the parent expansion.
                    expansionStack.pop()
                    continue
                }
            }
            when (val currentExpr = expansionStack.peek().next()) {
                Expression.Placeholder -> TODO("unreachable")

                is Expression.MacroInvocation -> {
                    pushMacroExpansion(expansionStack.peek().expressions!!, currentExpr.startInclusive)
                }
                is Expression.EExpression -> {
                    // pushMacroExpansion(expansionStack.peek().expressions!!, currentExpr.startInclusive)
                    TODO("EExpression")
                }
                is Expression.VariableReference -> {
                    val currentEnvironment = expansionStack.peek().environment ?: Environment.EMPTY
                    val argumentExpressionIndex = currentEnvironment.argumentIndices[currentExpr.signatureIndex]

                    if (argumentExpressionIndex < 0) {
                        // Argument was elided.
                        continue
                    }

                    expansionStack.push {
                        it.type = null
                        it.i = argumentExpressionIndex
                        it.startInclusive = argumentExpressionIndex
                        // There can only be one expression for an argument. It's either a value, macro, or expression group.
                        it.endExclusive = argumentExpressionIndex + 1
                        it.expansionKind = ExpansionKind.Values
                        it.expressions = currentEnvironment.arguments
                        it.environment = currentEnvironment.parentEnvironment
                    }
                }
                is Expression.ExpressionGroup -> {
                    val currentExpansion = expansionStack.peek()
                    expansionStack.push {
                        it.expansionKind = ExpansionKind.Values
                        it.type = null
                        it.i = currentExpr.startInclusive + 1
                        it.startInclusive = currentExpr.startInclusive + 1
                        it.endExclusive = currentExpr.endInclusive + 1
                        it.expressions = currentExpansion.expressions
                        it.environment = currentExpansion.environment
                    }
                    continue
                }
                else -> {
                    this.currentExpr = currentExpr
                    return currentExpr
                }
            }
        }
        return currentExpr
    }

    private fun pushExpressionGroup(expr: Expression.ExpressionGroup) {
        val currentEnvironment = expansionStack.peek().environment ?: Environment.EMPTY
        expansionStack.push {
            it.i = expr.startInclusive
            it.type = null
            it.startInclusive = expr.startInclusive
            it.endExclusive = expr.endInclusive + 1
            it.expansionKind = ExpansionKind.Values
            it.expressions = currentEnvironment.arguments
            it.environment = currentEnvironment.parentEnvironment
        }
    }

    fun stepOut() {
        // step out of everything until we have stepped out of a container.
        while (expansionStack.pop().expansionKind != ExpansionKind.Container) {}
    }

    fun stepIn() {
        val expression = requireNotNull(currentExpr) { "Not positioned on a value" }
        val startInclusive: Int
        val endInclusive: Int
        val ionType: IonType
        when (expression) {
            is Expression.ListValue -> {
                startInclusive = expression.startInclusive
                endInclusive = expression.endInclusive
                ionType = expression.type
            }
            is Expression.SExpValue -> {
                startInclusive = expression.startInclusive
                endInclusive = expression.endInclusive
                ionType = expression.type
            }
            is Expression.StructValue -> {
                startInclusive = expression.startInclusive
                endInclusive = expression.endInclusive
                ionType = expression.type
            }
            else -> throw IonException("Cannot step into $expression")
        }
        val currentExpansion = expansionStack.peek()
        expansionStack.push {
            it.environment = currentExpansion.environment
            it.type = ionType
            it.expressions = currentExpansion.expressions
            it.startInclusive = startInclusive + 1
            it.endExclusive = endInclusive + 1
            it.i = it.startInclusive
            it.expansionKind = ExpansionKind.Container
        }
        println(currentExpansion)
    }
}
