package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl._Private_RecyclingStack

class MacroEvaluator(
    private val encodingContext: EncodingContext,
    // TODO: private val maxNumberOfExpandedValues: Int = 1_000_000,
) {

    /**
     * Implementations must update [ExpansionInfo.i] in order for [ExpansionInfo.hasNext] to work properly.
     */
    private fun interface Expander {
        fun nextExpression(expansionInfo: ExpansionInfo, macroEvaluator: MacroEvaluator): Expression
    }

    private object DefaultExpander: Expander {
        override fun nextExpression(expansionInfo: ExpansionInfo, macroEvaluator: MacroEvaluator): Expression {
            with(expansionInfo) {
                // Get the next expression, and increment i
                val next = expressions!![i++]
                // But if `next` is an expression that contains other expressions, jump ahead to the end of its content
                if (next is Expression.HasStartAndEnd) i = next.endInclusive + 1
                return next
            }
        }
    }

    private object MakeStringExpander: Expander {
        override fun nextExpression(expansionInfo: ExpansionInfo, macroEvaluator: MacroEvaluator): Expression {
            // Tell the macro evaluator to treat this as a values expansion...
            macroEvaluator.expansionStack.peek().expansionKind = ExpansionKind.Values
            val minDepth = macroEvaluator.expansionStack.size()
            // ...But capture the output and turn it into a String
            val sb = StringBuilder()
            while(true) {
                when (val expr: ResolvedExpression? = macroEvaluator.expandNext(minDepth)) {
                    is Expression.StringValue -> sb.append(expr.value)
                    is Expression.SymbolValue -> sb.append(expr.value.assumeText())
                    is Expression.NullValue -> {}
                    null -> break
                    else -> throw IonException("Invalid argument type for 'make_string': ${expr?.type}")
                }
            }
            return Expression.StringValue(value = sb.toString())
        }
    }

    private enum class ExpansionKind(val nextFn: Expander) {
        Container(DefaultExpander),
        TemplateBody(DefaultExpander),
        Values(DefaultExpander),
        MakeString(MakeStringExpander);

        companion object {
            @JvmStatic
            fun forSystemMacro(macro: SystemMacro): ExpansionKind {
                return when (macro) {
                    SystemMacro.Values -> Values
                    SystemMacro.MakeString -> MakeString
                }
            }
        }
    }

    private inner class ExpansionInfo: Iterator<Expression> {
        /** The [ExpansionKind]. */
        @JvmField var expansionKind: ExpansionKind = ExpansionKind.Values
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
        /** Start of [expressions] that are applicable for this [ExpansionInfo] */
        // TODO: Do we actually need this for anything other than debugging?
        @JvmField var startInclusive: Int = 0
        /** End of [expressions] that are applicable for this [ExpansionInfo] */
        @JvmField var endExclusive: Int = 0
        /** Current position within [expressions] of this expansion */
        @JvmField var i: Int = 0


        override fun hasNext(): Boolean = i < endExclusive

        override fun next(): Expression {
            return expansionKind.nextFn.nextExpression(this, this@MacroEvaluator)
        }

        override fun toString() = """
        |ExpansionInfo(
        |    expansionKind: $expansionKind,
        |    environment: $environment,
        |    expressions: [
        |        ${expressions!!.joinToString(",\n|        ") { it.toString() } }
        |    ],
        |    startInclusive: $startInclusive,
        |    endExclusive: $endExclusive,
        |    i: $i,
        |)
        """.trimMargin()

        fun reset() {
            startInclusive = -1
            endExclusive = -1
            expansionKind = ExpansionKind.Values


            i = startInclusive
        }
    }

    private val expansionStack = _Private_RecyclingStack(8) { ExpansionInfo() }

    private var currentExpr: ResolvedExpression? = null

    fun initExpansion(encodingExpressions: List<Expression>) {
        pushEExpressionExpansion(encodingExpressions, 0)
    }

    fun expandNext(): Expression? = expandNext(-1)

    private fun expandNext(minDepth: Int): ResolvedExpression? {
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
                    if (expansionStack.size() > minDepth){
                        expansionStack.pop()
                        continue
                    } else {
                        return null
                    }
                }
            }
            when (val currentExpr = expansionStack.peek().next()) {
                Expression.Placeholder -> TODO("unreachable")

                is Expression.MacroInvocation -> pushTdlMacroExpansion(currentExpr)
                is Expression.EExpression -> pushEExpressionExpansion(currentExpr)
                is Expression.VariableReference -> pushVariableExpansion(currentExpr)

                is Expression.ExpressionGroup -> {
                    val currentExpansion = expansionStack.peek()
                    expansionStack.push {
                        it.expansionKind = ExpansionKind.Values
                        it.i = currentExpr.startInclusive + 1
                        it.endExclusive = currentExpr.endInclusive + 1
                        it.expressions = currentExpansion.expressions
                        it.environment = currentExpansion.environment
                    }
                    continue
                }
                is ResolvedExpression -> {
                    this.currentExpr = currentExpr
                    break
                }
            }
        }
        return currentExpr
    }

    fun stepOut() {
        // step out of anything we find until we have stepped out of a container.
        while (expansionStack.pop().expansionKind != ExpansionKind.Container) {}
    }

    fun stepIn() {
        val expression = requireNotNull(currentExpr) { "Not positioned on a value" }
        expression as? Expression.Container ?: throw IonException("Not positioned on a container.")
        val currentExpansion = expansionStack.peek()
        expansionStack.push {
            it.expansionKind = ExpansionKind.Container
            it.environment = currentExpansion.environment
            it.expressions = currentExpansion.expressions
            it.endExclusive = expression.endInclusive + 1
            it.i = expression.startInclusive + 1
        }
    }

    private fun pushVariableExpansion(expression: Expression.VariableReference) {
        val currentEnvironment = expansionStack.peek().environment ?: Environment.EMPTY
        val argumentExpressionIndex = currentEnvironment.argumentIndices[expression.signatureIndex]

        if (argumentExpressionIndex < 0) {
            // Argument was elided; push an empty expansion
            // TODO: See if we can skip this altogether
            expansionStack.push {
                it.expansionKind = ExpansionKind.Values
                it.i = 0
                it.endExclusive = 0
                it.expressions = emptyList()
                it.environment = currentEnvironment.parentEnvironment
            }
        } else {
            expansionStack.push {
                it.expansionKind = ExpansionKind.Values
                it.i = argumentExpressionIndex
                // There can only be one expression for an argument. It's either a value, macro, or expression group.
                it.endExclusive = argumentExpressionIndex + 1
                it.expressions = currentEnvironment.arguments
                it.environment = currentEnvironment.parentEnvironment
            }
        }
    }


    private fun pushExpressionGroup(expr: Expression.ExpressionGroup) {
        val currentEnvironment = expansionStack.peek().environment ?: Environment.EMPTY
        expansionStack.push {
            it.i = expr.startInclusive + 1
            it.endExclusive = expr.endInclusive + 1
            it.expansionKind = ExpansionKind.Values
            it.expressions = currentEnvironment.arguments
            it.environment = currentEnvironment.parentEnvironment
        }
    }

    /**
     * Push a macro expression, found in the current expansion, to the expansion stack
     */
    private fun pushTdlMacroExpansion(expression: Expression.MacroInvocation) {
        val currentExpansion = expansionStack.peek()
        val currentEnvironment = currentExpansion.environment!!
        pushMacro(
            currentEnvironment,
            address = expression.address,
            encodingExpressions = currentExpansion.expressions!!,
            argsStartInclusive = expression.startInclusive + 1,
            argsEndExclusive = expression.endInclusive + 1,
        )
    }

    /**
     * Push a macro from `encodingExpressions[i]` onto the expansionStack, handling concerns such as
     * looking up the macro reference, setting up the environment, etc.
     */
    private fun pushEExpressionExpansion(encodingExpressions: List<Expression>, i: Int) {
        val currentEnvironment = Environment.EMPTY

        val argsStartInclusive = i + 1
        val (address, argsEndExclusive) = when (val it = encodingExpressions[i]) {
            is Expression.EExpression -> it.address to it.endInclusive + 1
            else -> throw IllegalStateException("Attempted to push macro for an expression that is not a macro: $it")
        }
        pushMacro(currentEnvironment, address, encodingExpressions, argsStartInclusive, argsEndExclusive)
    }

    private fun pushEExpressionExpansion(expression: Expression.EExpression) {
        val currentExpansion = expansionStack.peek()
        pushMacro(
            currentEnvironment = Environment.EMPTY,
            address = expression.address,
            encodingExpressions = currentExpansion.expressions!!,
            argsStartInclusive = expression.startInclusive + 1,
            argsEndExclusive = expression.endInclusive + 1,
        )
    }

    private fun pushMacro(
        currentEnvironment: Environment,
        address: MacroRef,
        encodingExpressions: List<Expression>,
        argsStartInclusive: Int,
        argsEndExclusive: Int,
    ) {
        val macro = encodingContext.macroTable[address] ?: throw IonException("No such macro: $address")

        val argIndices = calculateArgumentIndices(macro, encodingExpressions, argsStartInclusive, argsEndExclusive)

        when (macro) {
            is TemplateMacro -> expansionStack.push {
                it.i = 0
                it.endExclusive = macro.body.size
                it.expressions = macro.body
                it.expansionKind = ExpansionKind.TemplateBody
                it.environment = currentEnvironment.createChild(encodingExpressions, argIndices)
            }
            // TODO: Values and MakeString have the same code in their blocks. As we get further along, see
            //       if this is generally applicable for all system macros.
            SystemMacro.Values,
            SystemMacro.MakeString -> expansionStack.push {
                it.expansionKind = ExpansionKind.forSystemMacro(macro as SystemMacro)
                it.i = argsStartInclusive
                it.endExclusive = argsEndExclusive
                it.expressions = encodingExpressions
                it.environment = currentEnvironment
            }
        }
    }


    /**
     * Given a [Macro] (or more specifically, its signature), calculates the position of each of its arguments
     * in [encodingExpressions]. The result is a list that can be used to map from a parameter's
     * signature index to the encoding expression index. Any trailing, optional arguments that are
     * elided have a value of -1.
     *
     * This function also validates that the correct number of parameters are present. If there are
     * too many parameters or too few parameters, this will throw [IonException].
     */
    private fun calculateArgumentIndices(
        macro: Macro,
        encodingExpressions: List<Expression>,
        argsStartInclusive: Int,
        argsEndExclusive: Int
    ): List<Int> {
        var i = 0
        val argsIndices = IntArray(macro.signature.size)
        var currentArgIndex = argsStartInclusive
        for (p in macro.signature) {
            if (currentArgIndex >= argsEndExclusive) {
                if (!p.cardinality.canBeVoid) {
                    throw IonException("No value provided for parameter ${p.variableName}")
                } else {
                    // Elided rest parameter.
                    argsIndices[i] = -1
                }
            } else {
                argsIndices[i] = currentArgIndex
                val argumentExpression = encodingExpressions[currentArgIndex]
                if (argumentExpression is Expression.HasStartAndEnd) {
                    val argEndExclusive = argumentExpression.endInclusive + 1
                    currentArgIndex = argEndExclusive
                } else {
                    currentArgIndex++
                }
            }
            i++
        }
        while (currentArgIndex < argsEndExclusive) {
            argsIndices[i] = currentArgIndex
            val argumentExpression = encodingExpressions[currentArgIndex]
            if (argumentExpression is Expression.HasStartAndEnd) {
                val argEndExclusive = argumentExpression.endInclusive + 1
                currentArgIndex = argEndExclusive
            } else {
                currentArgIndex++
            }
        }
        if (argsIndices.size != macro.signature.size) {
            throw IonException("Too many arguments. Expected ${macro.signature.size}, but found ${argsIndices.size}; they were $argsIndices")
        }
        return argsIndices.toList()
    }
}
