package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl._Private_RecyclingStack
import java.util.*

class FlatEvaluator(private val encodingContext: EncodingContext) {

    companion object {
        @JvmStatic
        val VALUES_TEMPLATE_BODY = listOf(Expression.VariableReference(0))

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



            while (hasNext()) {
                val expr = defaultNextFun(this, it)
                val text = when (expr) {
                    is Expression.ExpressionGroup -> {
                        // We'll just ignore the expression group marker and look at the args directly.
                        i = expr.startInclusive + 1
                        continue
                    }
                    is Expression.StringValue -> expr.value
                    is Expression.SymbolValue -> expr.value.assumeText()
                    is Expression.NullValue -> ""
                    is Expression.MacroInvocation -> {
                        it.pushMacroExpansion(expressions!!, i)
                        TODO("Read the expressions produced by this macro expansion")
                    }
                    // TODO: References
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
        @JvmField var environment: Environment3? = null
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
        val currentEnvironment = expansionStack.peek()?.environment ?: Environment3.EMPTY

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
                expansionInfo.expansionKind = ExpansionKind.ExpressionGroup

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


        println("Starting expandNext()")

        currentExpr = null
        while (!expansionStack.isEmpty) {
            println(expansionStack.peek())
            if (!expansionStack.peek().hasNext()) {
                if (expansionStack.peek().expansionKind == ExpansionKind.Container) {
                    // End of container. User needs to step out.
                    println("User needs to step out")
                    return null
                } else {
                    // End of a macro invocation or something else that is not part of the data model,
                    // so we seamlessly close this out and continue with the parent expansion.
                    expansionStack.pop()
                    println("Pop expansion")
                    continue
                }
            }
            when (val currentExpr = expansionStack.peek().next().also { println(it) }) {
                is Expression.MacroInvocation -> {
                    pushMacroExpansion(expansionStack.peek().expressions!!, currentExpr.startInclusive)
                }
                is Expression.VariableReference -> {
                    val currentEnvironment = expansionStack.peek().environment ?: Environment3.EMPTY
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
                        it.expansionKind = ExpansionKind.ExpressionGroup
                        it.expressions = currentEnvironment.arguments
                        it.environment = currentEnvironment.parentEnvironment
                    }
                }
                is Expression.EExpression -> TODO()
                is Expression.ExpressionGroup -> {
                    val currentExpansion = expansionStack.peek()
                    expansionStack.push {
                        it.expansionKind = ExpansionKind.ExpressionGroup
                        it.type = null
                        it.i = currentExpr.startInclusive + 1
                        it.startInclusive = currentExpr.startInclusive + 1
                        it.endExclusive = currentExpr.endInclusive + 1
                        it.expressions = currentExpansion.expressions
                        it.environment = currentExpansion.environment
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

    private fun pushExpressionGroup(expr: Expression.ExpressionGroup) {
        val currentEnvironment = expansionStack.peek().environment ?: Environment3.EMPTY
        expansionStack.push {
            it.i = expr.startInclusive
            it.type = null
            it.startInclusive = expr.startInclusive
            it.endExclusive = expr.endInclusive + 1
            it.expansionKind = ExpansionKind.ExpressionGroup
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
