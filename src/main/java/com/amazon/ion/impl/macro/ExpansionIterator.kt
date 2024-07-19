package com.amazon.ion.impl.macro

import com.amazon.ion.IonException

// Almost ready for testing
// BEWARE: The indices might be off because of the use of sublist()
class ExpansionIterator(
    private var kind: Kind,
    // TODO: Replace with something cheaper, like a start index, end index, and the source list...
    //       but maybe sublist() is ok for now
    var expressions: List<Expression>,
    private var environment: Environment,
    private var encodingContext: EncodingContext,
): Iterable<Expression>, Iterator<Expression> {
    fun initialize(kind: Kind, expressions: List<Expression>, environment: Environment, encodingContext: EncodingContext) {
        this.kind = kind
        this.expressions = expressions
        this.environment = environment
        this.encodingContext = encodingContext
        this.i = 0
    }

    fun close() {
        this.expressions = emptyList()
        this.environment = Environment.EMPTY
        this.encodingContext = EncodingContext.EMPTY
    }

    override fun iterator(): Iterator<Expression> = this


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

    override fun next(): Expression {
        if (!hasNext()) throw NoSuchElementException("No more elements!")
        val next = expressions[i]
        // This iterator needs to skip over child containers, expr groups, and macro invocations
        // All of those will be evaluated by their own iterators
        if (next is Expression.Container) i = next.endInclusive
        if (next is Expression.MacroInvocation) i = next.endInclusive
        if (next is Expression.EExpression) i = next.endInclusive
        if (next is Expression.ExpressionGroup) i = next.endInclusive
        i++
        return next
    }

    fun iterate(expression: Expression): ExpansionIterator {
        return when (expression) {
            is Expression.MacroInvocation -> {
                val macro = encodingContext.macroTable.get(expression.address)!!
                when (macro) {
                    is TemplateMacro -> {
                        val argExpander = ExpansionIterator(
                            Kind.ARGUMENTS_EXPANDER,
                            environment = environment,
                            expressions = expressions.subList(expression.startInclusive + 1, expression.endInclusive + 1),
                            encodingContext = encodingContext,
                        )


                        val args = macro.signature.map {
                            if (argExpander.hasNext()) {
                                val arg = argExpander.next()
                                when (arg) {
                                    is Expression.VariableReference -> environment.arguments[arg.signatureIndex]
                                    is Expression.HasStartAndEnd -> {
                                        val resolveVariablesExpressions = mutableListOf<Expression>()
                                        expressions.subList(arg.startInclusive + 1, arg.endInclusive + 1)
                                            .forEach {
                                                when (it) {
                                                    is Expression.VariableReference -> {
                                                        resolveVariablesExpressions.addAll(environment.arguments[it.signatureIndex])
                                                    }
                                                    else -> resolveVariablesExpressions.add(it)
                                                }
                                            }
                                        resolveVariablesExpressions
                                    }
                                    else -> listOf(arg)
                                }
                            } else {
                                emptyList()
                            }
                        }

                        ExpansionIterator(
                            Kind.TEMPLATE_EXPANDER,
                            environment = Environment(args),
                            expressions = macro.body,
                            encodingContext = encodingContext
                        )
                    }
                    SystemMacro.MakeString -> {
                        val theString = expressions
                            .subList(expression.startInclusive + 1, expression.endInclusive + 1)
                            .joinToString("") {
                                when (it) {
                                    is Expression.TextValue -> it.stringValue
                                    is Expression.MacroInvocation -> iterate(it).joinToString("") {
                                        when (it) {
                                            is Expression.TextValue -> it.stringValue
                                            is Expression.NullValue -> ""
                                            else -> throw IonException("Cannot make string from a ${it.type}")
                                        }
                                    }
                                    else -> throw IonException("Cannot make string from a ${it.type}")
                                }
                            }

                        ExpansionIterator(
                            Kind.TEMPLATE_EXPANDER,
                            environment = environment,
                            encodingContext = encodingContext,
                            expressions = listOf(Expression.StringValue(emptyList(), theString))
                        )
                    }
                    SystemMacro.Values -> {
                        ExpansionIterator(
                            Kind.TEMPLATE_EXPANDER,
                            environment = environment,
                            encodingContext = encodingContext,
                            expressions = expressions.subList(expression.startInclusive + 1, expression.endInclusive + 1)
                        )
                    }
                    else -> TODO("Unreachable")
                }
            }
            is Expression.VariableReference -> {
                val variableExpressions = environment.arguments[expression.signatureIndex]
                ExpansionIterator(Kind.VARIABLE_EXPANDER, variableExpressions, environment, encodingContext)
            }
            is Expression.StructValue -> {
                ExpansionIterator(
                    Kind.STRUCT_EXPANDER,
                    this.expressions.subList(expression.startInclusive + 1, expression.endInclusive + 1),
                    environment,
                    encodingContext,
                )
            }
            is Expression.Container -> {
                ExpansionIterator(
                    Kind.SEQUENCE_EXPANDER,
                    this.expressions.subList(expression.startInclusive + 1, expression.endInclusive + 1),
                    environment,
                    encodingContext,
                )
            }
            else -> throw IllegalStateException("Cannot step into expression of type ${expression.javaClass}")
        }
    }
}
