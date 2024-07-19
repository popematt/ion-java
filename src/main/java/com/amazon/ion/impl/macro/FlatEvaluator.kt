package com.amazon.ion.impl.macro

import com.amazon.ion.*
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


        var currentExpr: Expression? = null
        do {
            if (!macroStack.peek().hasNext()) {
                macroStack.pop()
                continue
            }
            currentExpr = macroStack.peek().next()
            when (currentExpr) {
                is Expression.MacroInvocation -> {

                }
                is Expression.VariableReference ->
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



        } while (!macroStack.empty())
    }




}
