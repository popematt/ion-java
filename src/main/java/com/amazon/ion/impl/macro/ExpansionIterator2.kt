package com.amazon.ion.impl.macro

import com.amazon.ion.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.NoSuchElementException


class Encoding2Context(
    val macroTable: Map<MacroRef, Macro2>
) {
    companion object {
        @JvmStatic
        val EMPTY = Encoding2Context(emptyMap())
    }
}

class Environment2(
    val arguments: List<Expr>,
    val parent: Environment2? = null,
) {
    fun resolve(variable: Expr.VariableReference): Expr.ExprIterator {
        TODO()
    }

    companion object {
        @JvmStatic
        val EMPTY = Environment2(emptyList())
    }
}

sealed interface Expr {

    sealed interface IterableExpr {
        fun iterator(environment: Environment2, encodingContext: Encoding2Context): ExprIterator
    }

    interface ExprIterator: Iterator<Expr> {
        fun stepInto(expression: IterableExpr): ExprIterator
    }

    /**
     * A temporary placeholder that is used only while a macro is partially compiled.
     */
    object Placeholder : TemplateBodyExpression, EncodingExpression

    data class Annotations(val values: List<SymbolToken>) : Expr
    data class FieldName(val value: SymbolToken) : Expr

    // Scalars
    data class NullValue(val type: IonType) : Expr
    data class BoolValue(val value: Boolean) :Expr
    sealed interface IntValue
    data class LongIntValue(val value: Long) : Expr, IntValue
    data class BigIntValue(val value: BigInteger) : Expr, IntValue
    data class FloatValue(val value: Double) : Expr
    data class DecimalValue(val value: BigDecimal) : Expr
    data class TimestampValue(val value: Timestamp) : Expr
    data class StringValue(val value: String) : Expr
    data class SymbolValue(val value: SymbolToken) : Expr
    data class BlobValue(val value: ByteArray) : Expr {
        override fun equals(other: Any?): Boolean = this === other || (other is BlobValue && value.contentEquals(other.value))
        override fun hashCode(): Int = value.contentHashCode()
    }
    data class ClobValue(val value: ByteArray) : Expr {
        override fun equals(other: Any?): Boolean = this === other || (other is ClobValue && value.contentEquals(other.value))
        override fun hashCode(): Int = value.contentHashCode()
    }

    // Containers

    data class ListValue(val expressions: List<Expr>) : Expr, IterableExpr {
        override fun iterator(environment: Environment2, encodingContext: Encoding2Context): ExprIterator {
            return ExpansionIterator2(
                ExpansionIterator2.Kind.SEQUENCE_EXPANDER,
                expressions,
                environment,
                encodingContext
            )
        }
    }

    data class SExpValue(val expressions: List<Expr>) : Expr, IterableExpr {
        override fun iterator(environment: Environment2, encodingContext: Encoding2Context): ExprIterator {
            return ExpansionIterator2(
                ExpansionIterator2.Kind.SEQUENCE_EXPANDER,
                expressions,
                environment,
                encodingContext
            )
        }
    }

    data class StructValue(val expressions: List<Expr>, val templateStructIndex: Map<String, List<Int>>) : Expr, IterableExpr {
        override fun iterator(environment: Environment2, encodingContext: Encoding2Context): ExprIterator {
            return ExpansionIterator2(
                ExpansionIterator2.Kind.STRUCT_EXPANDER,
                expressions,
                environment,
                encodingContext
            )
        }
    }

    data class ExpressionGroup(val expressions: List<Expr>) : Expr, IterableExpr {
        override fun iterator(environment: Environment2, encodingContext: Encoding2Context): ExprIterator {
            TODO()
        }
    }

    /**
     * A reference to a variable that needs to be substituted with the real value during evaluation
     */
    data class VariableReference(val signatureIndex: Int) : Expr

    data class MacroInvocation(val address: MacroRef, val argumentExpressions: List<Expr>) : Expr, IterableExpr {
        override fun iterator(environment: Environment2, encodingContext: Encoding2Context): ExprIterator {
            // TODO: map the argument expressions using the current environment
            return when (val macro = encodingContext.macroTable[address]) {
                null -> TODO()
                is TemplateMacro2 -> TemplateExpansion(
                    Environment2(argumentExpressions),
                    encodingContext,
                    macro.body,
                )
                SystemMacro.MakeString -> TODO()
                SystemMacro.Values -> TODO()
            }
        }
    }

    data class EExpression(val address: MacroRef, val argumentExpressions: List<Expr>): Expr, IterableExpr {
        override fun iterator(environment: Environment2, encodingContext: Encoding2Context): ExprIterator {
            return when (val macro = encodingContext.macroTable[address]) {
                null -> TODO()
                is TemplateMacro2 -> TemplateExpansion(
                    Environment2(argumentExpressions),
                    encodingContext,
                    macro.body,
                )
                SystemMacro.MakeString -> TODO()
                SystemMacro.Values -> TODO()
            }
        }
    }
}


class VariableExpansion(
    val signatureIndex: Int,
    val environment: Environment2,
    val context: Encoding2Context,
    val body: Expr,
): Expr.ExprIterator {
    override fun hasNext(): Boolean {
        TODO("Not yet implemented")
    }

    override fun next(): Expr {
        TODO("Not yet implemented")
    }

    override fun stepInto(expression: Expr.IterableExpr): Expr.ExprIterator {
        TODO("Not yet implemented")
    }

}

class TemplateExpansion(
    val environment: Environment2,
    val context: Encoding2Context,
    val body: Expr,

): Expr.ExprIterator {
    override fun stepInto(expression: Expr.IterableExpr): Expr.ExprIterator {
        TODO("Not yet implemented")
    }

    override fun hasNext(): Boolean {
        TODO("Not yet implemented")
    }

    override fun next(): Expr {
        TODO("Not yet implemented")
    }

}

// Almost ready for testing
class ExpansionIterator2(
    private var kind: Kind,
    // TODO: Replace with something cheaper, like a start index, end index, and the source list...
    //       but maybe sublist() is ok for now
    var expressions: List<Expr>,
    private var environment: Environment2,
    private var encodingContext: Encoding2Context,
): Expr.ExprIterator {
    fun initialize(kind: Kind, expressions: List<Expr>, environment: Environment2, encodingContext: Encoding2Context) {
        this.kind = kind
        this.expressions = expressions
        this.environment = environment
        this.encodingContext = encodingContext
        this.i = 0
    }

    fun close() {
        this.expressions = emptyList()
        this.environment = Environment2.EMPTY
        this.encodingContext = Encoding2Context.EMPTY
    }

    enum class Kind {
        ARGUMENTS_EXPANDER,
        VARIABLE_EXPANDER,
        TEMPLATE_EXPANDER,
        SEQUENCE_EXPANDER,
        STRUCT_EXPANDER,
        EEXP_EXPANDER,
    }

    private var i = 0

    override fun hasNext(): Boolean = i < expressions.size

    override fun next(): Expr {
        if (!hasNext()) throw NoSuchElementException("No more elements!")
        val next = expressions[i++]
        return when (next) {
            // TODO: is Expr.VariableReference -> environment.resolve(next)
            is Expr.MacroInvocation -> {
                evalMacro(next.address, next.argumentExpressions, environment, encodingContext)
            }
            is Expr.EExpression -> {
                evalMacro(next.address, next.argumentExpressions, environment, encodingContext)
            }
            else -> next
        }
    }

    private fun evalMacro(macroRef: MacroRef, args: List<Expr>, environment: Environment2, encodingContext: Encoding2Context): Expr {
        return when (val macro = encodingContext.macroTable[macroRef]) {
            null -> TODO()
            is TemplateMacro2 -> macro.body
            SystemMacro.MakeString -> TODO()
            SystemMacro.Values -> TODO()
        }
    }

    override fun stepInto(expression: Expr.IterableExpr): Expr.ExprIterator {
        return expression.iterator(environment, encodingContext)
    }
}


class Evaluator2(val encodingContext: Encoding2Context) {
    fun evaluate(encodingExpression: Expr.EExpression): Expr.ExprIterator {
        return encodingExpression.iterator(Environment2.EMPTY, encodingContext)
    }
}
