package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl._Private_RecyclingStack
import java.util.*

class FlatEvaluator(private val encodingContext: EncodingContext) {

    companion object {
        @JvmStatic
        private fun defaultNextFun(ei: ExpansionInfo, fe: FlatEvaluator): Expression = with(ei) {
            val next = expressions!![i]
            if (next is Expression.Container) i = next.endInclusive
            if (next is Expression.MacroInvocation) i = next.endInclusive
            if (next is Expression.EExpression) i = next.endInclusive
            if (next is Expression.ExpressionGroup) i = next.endInclusive
            i++
            next
        }
    }
    // TODO: See if this is more performant without the lambda functions
    private enum class ExpansionKind(val nextFn: ExpansionInfo.(FlatEvaluator) -> Expression) {
        Container(::defaultNextFun),
        ExpressionGroup(::defaultNextFun),
        TemplateBody(::defaultNextFun),
        MakeString({
            val sb = StringBuilder()
            for (j in startInclusive .. endExclusive) {
                val text = when (val expr = expressions!![j]) {
                    // There might be an expression group or just a single arg.
                    is Expression.ExpressionGroup -> continue
                    is Expression.StringValue -> expr.value
                    is Expression.SymbolValue -> expr.value.assumeText()
                    is Expression.NullValue -> ""
                    is Expression.MacroInvocation -> {
                        it.pushMacroExpansion(expressions!!, j)
                        TODO("Read the expressions produced by this macro expansion")
                    }
                    else -> throw IonException("Not a valid argument for make_string: $expr")
                }
                sb.append(text)
            }
            Expression.StringValue(value = sb.toString())
        });
    }

    private inner class ExpansionInfo: Iterator<Expression> {
        // The type of container, if this is a container.
        @JvmField var type: IonType? = null
        @JvmField var environment: Environment? = null
        // The Expressions being expanded
        @JvmField var expressions: List<Expression>? = null
        // Start of the content being expanded.
        @JvmField var startInclusive: Int = 0
        @JvmField var endExclusive: Int = 0
        // Current position
        @JvmField var i: Int = 0

        @JvmField var expansionKind: ExpansionKind = ExpansionKind.TemplateBody

        override fun hasNext(): Boolean = i < endExclusive

        override fun next(): Expression {
            return expansionKind.nextFn(this@ExpansionInfo, this@FlatEvaluator)
        }

        override fun toString() = """
        ExpansionInfo(
            expansionKind: $expansionKind,
            type: $type,
            environment: $environment,
            expressions: [
                ${expressions!!.joinToString(",\n                ") { it.toString() } }
            ],
            startInclusive: $startInclusive,
            endExclusive: $endExclusive,
            i: $i,
        )
        """.trimIndent()
    }

    private val expansionStack = _Private_RecyclingStack(8) { ExpansionInfo() }

    private var currentExpr: Expression? = null

    private fun pushMacroExpansion(encodingExpressions: List<Expression>, i: Int) {
        val argsStartInclusive = i + 1
        val (address, argsEndExclusive) = when (val it = encodingExpressions[i]) {
            is Expression.EExpression -> it.address to it.endInclusive + 1
            is Expression.MacroInvocation -> it.address to it.endInclusive + 1
            else -> throw IllegalStateException("Attempted to push macro for an expression that is not a macro: $it")
        }

        val macro = encodingContext.macroTable[address]

        val expansionInfo = expansionStack.push {
            it.startInclusive = 0
            it.i = 0
            it.type = null
            it.expressions = null
        }

        when (macro) {
            null -> throw IonException("No such macro: ${address}")
            is TemplateMacro -> {
                expansionInfo.endExclusive = macro.body.size
                expansionInfo.expressions = macro.body
                expansionInfo.expansionKind = ExpansionKind.TemplateBody
            }
            SystemMacro.Values -> {
                expansionInfo.expansionKind = ExpansionKind.ExpressionGroup
                expansionInfo.startInclusive++
                expansionInfo.expressions = encodingExpressions
            }
            SystemMacro.MakeString -> {
                expansionInfo.expansionKind = ExpansionKind.MakeString
                expansionInfo.type = IonType.STRING
                expansionInfo.expressions = encodingExpressions
            }
        }

        val args = mutableListOf<List<Expression>>()
        var args_i = argsStartInclusive
        for (p in macro.signature) {
            val arg = mutableListOf<Expression>()

            if (args_i >= argsEndExclusive) {
                if (!p.cardinality.canBeVoid) {
                    throw IonException("No value provided for parameter ${p.variableName}")
                }
            } else {
                val argumentExpression = encodingExpressions[args_i]

                if (argumentExpression is Expression.HasStartAndEnd) {
                    val argEndExclusive = argumentExpression.endInclusive + 1
                    arg.addAll(encodingExpressions.subList(args_i, argEndExclusive))
                    args_i = argEndExclusive
                } else {
                    arg.add(argumentExpression)
                    args_i++
                }
            }
            args.add(arg)
        }

        // Need to eagerly evaluate arguments, for now.
        // If we look up arguments by name, then we might be able to lazily evaluate them...?

        expansionInfo.environment = Environment(args)
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
                is Expression.MacroInvocation -> {
                    pushMacroExpansion(expansionStack.peek().expressions!!, currentExpr.startInclusive)
                }
                is Expression.VariableReference -> {
                    val arg = expansionStack.peek().environment!!.arguments[currentExpr.signatureIndex]
                    expansionStack.push {
                        it.i = 0
                        it.type = null
                        it.startInclusive = 0
                        it.endExclusive = arg.size
                        it.expansionKind = ExpansionKind.ExpressionGroup
                        it.expressions = arg
                        it.environment = Environment.EMPTY
                    }
                }
                is Expression.EExpression -> TODO()
                is Expression.ExpressionGroup -> {
                    val currentExpansion = expansionStack.peek()
                    expansionStack.push {
                        it.environment = currentExpansion.environment
                        it.type = IonType.LIST
                        it.expressions = currentExpansion.expressions
                        it.startInclusive = currentExpr.startInclusive + 1
                        it.endExclusive = currentExpr.endInclusive - 1
                        it.i = it.startInclusive
                        it.expansionKind = ExpansionKind.ExpressionGroup
                    }
                    continue
                }

                Expr.Placeholder -> TODO("unreachable")
                Expression.Placeholder -> TODO("unreachable")

                else -> {
                    this.currentExpr = currentExpr
                    return currentExpr
                }
            }
        }
        return currentExpr
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
