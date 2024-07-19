package com.amazon.ion.impl.macro

import com.amazon.ion.*

interface Expander: Iterator<Expression> {
    // TODO: close/release for pools?
    val expressions: List<Expression>
}

// TODO: Expander pool(s)

class SequenceIterator(override val expressions: List<Expression>, private val container: Expression.Container): Expander {
    // The index of the next element to return
    private var i: Int = container.startInclusive + 1

    override fun hasNext(): Boolean = i <= container.endInclusive
    override fun next(): Expression {
        if (!hasNext()) throw NoSuchElementException("No more elements!")
        val next = expressions[i]
        // This container iterator needs to skip over child containers, expr groups, and macro invocations
        // All of those will be evaluated by their own iterators
        if (next is Expression.MacroInvocation) i = next.endInclusive
        if (next is Expression.ExpressionGroup) i = next.endInclusive
        i++
        return next
    }
}

class ExpressionGroupIterator(override val expressions: List<Expression>, private val group: Expression.ExpressionGroup): Expander {
    // The index of the next element to return
    private var i: Int = group.startInclusive + 1

    override fun hasNext(): Boolean = i <= group.endInclusive
    override fun next(): Expression {
        if (!hasNext()) throw NoSuchElementException("No more elements!")
        val next = expressions[i]
        // This container iterator needs to skip over child containers, expr groups, and macro invocations
        // All of those will be evaluated by their own iterators
        if (next is Expression.Container) i = next.endInclusive
        if (next is Expression.MacroInvocation) i = next.endInclusive
        if (next is Expression.ExpressionGroup) i = next.endInclusive
        i++
        return next
    }
}

class VariableExpansionIterator(override val expressions: List<Expression>, val wrapped: Expander, private val cardinality: Macro.ParameterCardinality): Expander {

    init { initCheckForVoid() }

    private fun initCheckForVoid() {
        if (cardinality.canBeVoid) return
        if (!wrapped.hasNext()) {
            throw IonException("Expected $cardinality arguments, found 0")
        }
    }

    private fun checkMulti() {
        if (cardinality.canBeMulti) return
        if (wrapped.hasNext()) {
            throw IonException("Expected $cardinality arguments, found at least 2")
        }
    }

    override fun hasNext(): Boolean = wrapped.hasNext()
    override fun next(): Expression = wrapped.next().also { checkMulti() }
}
