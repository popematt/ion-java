package com.amazon.ion.impl.macro

import com.amazon.ion.IonException
import java.util.Stack

class EncodingContext(
    val macroTable: Map<MacroRef, Macro>
)

class Environment(
    val arguments: List<EncodingExpression>,
    val argumentsBySignatureIndex: List<Expression>
)



class TemplateMacroExpansionIterator(
    override val expressions: List<Expression>,
    val templateBody: List<Expression>,
    val environment: Environment,
): Expander {
    private var templateBodyIndex: Int = 0

    override fun hasNext(): Boolean = templateBodyIndex < templateBody.size
    override fun next(): Expression {
        if (!hasNext()) throw NoSuchElementException("No more elements!")
        val next = expressions[templateBodyIndex]
        // This container iterator needs to skip over child containers, expr groups, and macro invocations
        // All of those will be evaluated by their own iterators
        if (next is Expression.Container) templateBodyIndex = next.endInclusive
        if (next is Expression.MacroInvocation) templateBodyIndex = next.endInclusive
        if (next is Expression.ExpressionGroup) templateBodyIndex = next.endInclusive
        templateBodyIndex++
        return next
    }
}

class MacroExpansionIterator(override val expressions: List<Expression>): Expander {
    override fun hasNext(): Boolean = TODO("Not yet implemented")
    override fun next(): Expression = TODO("Not yet implemented")
}

// TODO: Make this reusable?
class Evaluator(
    override val expressions: List<EncodingExpression>,
    private val environment: Environment,
    private val encodingContext: EncodingContext,
    private val maxNumberOfExpandedValues: Int = 1_000_000,
): Expander {

    private var numberOfExpandedValues: Int = 0

    private val iteratorStack: Stack<Expander> = Stack()
    private val environmentStack: Stack<Environment> = Stack()

    private var current: ResolvedExpression? = null
    private var next: ResolvedExpression? = null
    private var noMoreValues = false

    init {
        pushMacro(expressions, expressions[0] as Expression.MacroInvocation)
    }

    private fun queueNext() {
        if (noMoreValues) return
        if (next != null) return

        if (numberOfExpandedValues++ > maxNumberOfExpandedValues) throw IonException("Reached macro expansion limit")

        var nextCandidate: Expression? = null

        while (nextCandidate == null && iteratorStack.isNotEmpty()) {
            val currentExpander = iteratorStack.peek()

            if (currentExpander.hasNext()) {
                nextCandidate = currentExpander.next()

                when (nextCandidate) {
                    is ResolvedExpression -> {
                        next = nextCandidate
                        return
                    }
                    is Expression.MacroInvocation -> pushMacro(currentExpander.expressions, nextCandidate)
                    is Expression.ExpressionGroup -> {
                        // push expression group?
                    }
                    is Expression.VariableReference -> {
                        // TODO: do we need to push a variable expander in order to look at a different list of expressions?
                        val signatureIndex = nextCandidate.signatureIndex
                        nextCandidate = environment.argumentsBySignatureIndex[signatureIndex]
                    }
                    Expression.Placeholder -> TODO("Unreachable")
                }
            } else {
                when (currentExpander) {
                    is MacroExpansionIterator -> popMacro()
                    // If it's a container, require the caller to call stepOut()
                    is ContainerIterator -> break
                    is ExpressionGroupIterator -> {}
                    else -> TODO("Unreachable")
                }
            }
        }
    }

    override fun hasNext(): Boolean {
        queueNext()
        return next != null
    }

    override fun next(): ResolvedExpression {
        if (next == null) queueNext()
        current = next
        next = null
        return current ?: throw NoSuchElementException("No more elements")
    }

    private fun pushMacro(expressions: List<Expression>, macroInvocation: Expression.MacroInvocation) {

        // TODO:
        //   1. Look up macro in macro table
        //   2. ?

        iteratorStack.push(MacroExpansionIterator(expressions))
    }

    private fun popMacro() {
        // TODO: Is this correct?
        noMoreValues = false
    }

    fun stepIn() {
        iteratorStack.push(ContainerIterator(iteratorStack.peek().expressions, current as Expression.Container))
    }

    fun stepOut() {
        // TODO: Consider checking the state to see if we can step out _before_ modifying the state
        while (iteratorStack.isNotEmpty()) {
            val popped = iteratorStack.pop()
            if (popped is ContainerIterator) return
        }
        throw IonException("Nothing to step out of.")
    }
}
