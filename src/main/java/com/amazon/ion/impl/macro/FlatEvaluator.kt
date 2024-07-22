package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl._Private_RecyclingStack
import java.util.*

interface MacroExpansion
class TemplateMacroExpansion(
    val macroBody: List<Expression>,
): Iterator<Expression> {
    private var i: Int = 0;

    override fun hasNext(): Boolean = i < macroBody.size

    override fun next(): Expression {
        val next = macroBody[i]
        if (next is Expression.Container) i = next.endInclusive
        if (next is Expression.MacroInvocation) i = next.endInclusive
        if (next is Expression.EExpression) i = next.endInclusive
        if (next is Expression.ExpressionGroup) i = next.endInclusive
        i++
        return next
    }
}


class FlatEvaluator(private val encodingContext: EncodingContext) {

    private class ContainerInfo {
        @JvmField var type: IonType? = null
        @JvmField var iterator: Iterator<Expression>? = null
        @JvmField var environment: Environment? = null
    }


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
    }

    private val expansionStack = _Private_RecyclingStack(8) { ExpansionInfo() }

    private fun pushMacroExpansion(encodingExpressions: List<Expression>, i: Int) {
        val encodingExpression = encodingExpressions[i] as Expression.EExpression
        val macro = encodingContext.macroTable[encodingExpression.address]

        val expansionInfo = expansionStack.push {
            it.startInclusive = 0
            it.i = 0
            it.environment = Environment.EMPTY // TODO
            it.type = null
        }

        when (macro) {
            null -> TODO()
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
            }
        }

    }

    fun initExpansion(encodingExpressions: List<Expression>) {


    }

    fun expandNext(): Expression? {
        while (!expansionStack.isEmpty) {
            if (!macroStack.peek().hasNext()) {
                if (expansionStack.peek().expansionKind == ExpansionKind.Container) {
                    // End of container. Need to step out.
                    return null
                }
                macroStack.pop()
                continue
            }
            when (val currentExpr = expansionStack.peek().next()) {
                is Expression.MacroInvocation -> {
                    pushMacroExpansion(expansionStack.peek().expressions!!, currentExpr.startInclusive)
                }
                is Expression.VariableReference -> {
                    expansionStack.peek().environment!!.arguments[currentExpr.signatureIndex]
                    TODO()
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
                }

                Expr.Placeholder -> TODO("unreachable")
                Expression.Placeholder -> TODO("unreachable")

                else -> return currentExpr
            }
        }
        return null
    }

    fun stepOut() {
        // step out of everything until we have stepped out of a container.
        while (expansionStack.pop().expansionKind != ExpansionKind.Container) {}
    }

    fun stepInto(expression: Expression) {
        when (expression) {
            is Expression.ListValue -> {
                val currentExpansion = expansionStack.peek()
                expansionStack.push {
                    it.environment = currentExpansion.environment
                    it.type = IonType.LIST
                    it.expressions = currentExpansion.expressions
                    it.startInclusive = expression.startInclusive + 1
                    it.endExclusive = expression.endInclusive - 1
                    it.i = it.startInclusive
                    it.expansionKind = ExpansionKind.Container
                }
            }
            is Expression.SExpValue -> {
                val currentExpansion = expansionStack.peek()
                expansionStack.push {
                    it.environment = currentExpansion.environment
                    it.type = IonType.SEXP
                    it.expressions = currentExpansion.expressions
                    it.startInclusive = expression.startInclusive + 1
                    it.endExclusive = expression.endInclusive - 1
                    it.i = it.startInclusive
                    it.expansionKind = ExpansionKind.Container
                }
            }
            is Expression.StructValue -> {
                val currentExpansion = expansionStack.peek()
                expansionStack.push {
                    it.environment = currentExpansion.environment
                    it.type = IonType.STRUCT
                    it.expressions = currentExpansion.expressions
                    it.startInclusive = expression.startInclusive + 1
                    it.endExclusive = expression.endInclusive - 1
                    it.i = it.startInclusive
                    it.expansionKind = ExpansionKind.Container
                }
            }
            else -> throw IonException("Cannot step into $expression")
        }
    }

    private fun expandValue(currentExpr: Expression) {
        when (currentExpr) {
            // Scalars
            is Expression.BigIntValue -> print(" ${currentExpr.value}")
            is Expression.BlobValue -> print(" {{ ${currentExpr.value} }}")
            is Expression.BoolValue -> print(" ${currentExpr.value} ")
            is Expression.ClobValue -> print(" {{ \"${currentExpr.value}\" }}")
            is Expression.DecimalValue -> print(" ${currentExpr.value}")
            is Expression.FieldName -> print(" ${currentExpr.value}:")
            is Expression.FloatValue -> print(" ${currentExpr.value}")
            is Expression.LongIntValue -> print(" ${currentExpr.value}")
            is Expression.StringValue -> print(" \"${currentExpr.value}\"")
            is Expression.SymbolValue -> print(" '${currentExpr.value}'")
            is Expression.TimestampValue -> print(" ${currentExpr.value}")
            is Expression.NullValue -> print(" null.${currentExpr.type}")
            else -> TODO("should be unreachable")
        }
    }


    val macroStack = Stack<Iterator<Expression>>()
    val envStack = Stack<Environment>()

    fun eval(encodingExpressions: List<Expression>) {

        val encodingExpression = encodingExpressions.first() as Expression.EExpression
        val macro = encodingContext.macroTable[encodingExpression.address]

        when (macro) {
            null -> TODO()
            is TemplateMacro -> macroStack.push(TemplateMacroExpansion(macro.body))
            SystemMacro.Values -> TODO()
            SystemMacro.MakeString -> TODO()
        }

        envStack.push(Environment.EMPTY)


        var currentExpr: Expression?
        while (macroStack.isNotEmpty()) {
            if (!macroStack.peek().hasNext()) {
                macroStack.pop()
                continue
            }
            currentExpr = macroStack.peek().next()
            when (currentExpr) {
                is Expression.MacroInvocation -> {

                }
                is Expression.VariableReference -> {
                    envStack.peek().arguments
                }
                is Expression.EExpression -> TODO()
                is Expression.ExpressionGroup -> TODO()

                // Containers
                is Expression.ListValue -> TODO()
                is Expression.SExpValue -> TODO()
                is Expression.StructValue -> TODO()

                // Scalars
                is Expression.BigIntValue -> print(" ${currentExpr.value}")
                is Expression.BlobValue -> print(" {{ ${currentExpr.value} }}")
                is Expression.BoolValue -> print(" ${currentExpr.value} ")
                is Expression.ClobValue -> print(" {{ \"${currentExpr.value}\" }}")
                is Expression.DecimalValue -> print(" ${currentExpr.value}")
                is Expression.FieldName -> print(" ${currentExpr.value}:")
                is Expression.FloatValue -> print(" ${currentExpr.value}")
                is Expression.LongIntValue -> print(" ${currentExpr.value}")
                is Expression.StringValue -> print(" \"${currentExpr.value}\"")
                is Expression.SymbolValue -> print(" '${currentExpr.value}'")
                is Expression.TimestampValue -> print(" ${currentExpr.value}")
                is Expression.NullValue -> print(" null.${currentExpr.type}")

                Expr.Placeholder -> TODO("unreachable")
                Expression.Placeholder -> TODO("unreachable")
            }

        }
    }
}
