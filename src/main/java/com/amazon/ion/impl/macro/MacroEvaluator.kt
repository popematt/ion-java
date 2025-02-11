// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl._Private_RecyclingStack
import com.amazon.ion.impl._Private_Utils.newSymbolToken
import com.amazon.ion.util.unreachable
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import kotlin.collections.ArrayList

/**
 * Evaluates an EExpression from a List of [EExpressionBodyExpression] and the [TemplateBodyExpression]s
 * given in the macro table of the [EncodingContext].
 *
 * General Usage:
 *  - To start evaluating an e-expression, call [initExpansion]
 *  - Call [expandNext] to get the next field name or value, or null
 *    if the end of the container or end of expansion has been reached.
 *  - Call [stepIn] when positioned on a container to step into that container.
 *  - Call [stepOut] to step out of the current container.
 *
 * TODO: Make expansion limit configurable.
 *
 * ### Implementation Overview:
 *
 * The macro evaluator consists of a stack of containers, each of which has an implicit stream (i.e. the
 * expressions in that container) which is modeled as an expansion frame ([ExpansionInfo]).
 *
 * When calling [expandNext], the evaluator looks at the top container in the stack and requests the next value from
 * its expansion frame. That expansion frame may produce a result all on its own (i.e. if the next value is a literal
 * value), or it may create and delegate to a child expansion frame if the next source expression is something that
 * needs to be expanded (e.g. macro invocation, variable expansion, etc.). When delegating to a child expansion frame,
 * the value returned by the child could be intercepted and inspected, modified, or consumed.
 * In this way, the expansion frames model a lazily constructed expression tree over the flat list of expressions in the
 * input to the macro evaluator.
 */
class MacroEvaluator {

    /**
     * Holds state that is shared across all macro evaluations that are part of this evaluator.
     * This state pertains to a single "session" of the macro evaluator, and is reset every time [initExpansion] is called.
     * For now, this includes managing the pool of [ExpansionInfo] and tracking the expansion step limit.
     */
    private class Session(
        /** Number of expansion steps at which the evaluation session should be aborted. */
        private val expansionLimit: Int = 1_000_000
    ) {
        /** Internal state for tracking the number of expansion steps. */
        private var numExpandedExpressions = 0
        /** Pool of [ExpansionInfo] to minimize allocation and garbage collection. */
        private val expanderPool: ArrayList<ExpansionInfo> = ArrayList(32)

        /** Pool of [ExpressionA] to minimize allocation and garbage collection. */
        private val expressionPool: ArrayList<ExpressionA> = ArrayList(32)



        /** Gets an [ExpansionInfo] from the pool (or allocates a new one if necessary), initializing it with the provided values. */
        fun getExpander(expansionKind: ExpansionKind, expressions: List<ExpressionA>, startInclusive: Int, endExclusive: Int, environment: Environment): ExpansionInfo {
            val expansion = expanderPool.removeLastOrNull() ?: ExpansionInfo(this)
            expansion.isInPool = false
            expansion.expansionKind = expansionKind
            expansion.expressions = expressions
            expansion.i = startInclusive
            expansion.endExclusive = endExclusive
            expansion.environment = environment
            expansion.additionalState = null
            expansion.childExpansion = null
            return expansion
        }

        /** Reclaims an [ExpansionInfo] to the available pool. */
        fun reclaimExpander(ex: ExpansionInfo) {
            // Ensure that we are not doubly-adding an ExpansionInfo instance to the pool.
            if (!ex.isInPool) {
                ex.isInPool = true
                expanderPool.add(ex)
            }
        }

        fun incrementStepCounter() {
            numExpandedExpressions++
            if (numExpandedExpressions > expansionLimit) {
                // Technically, we are not counting "steps" because we don't have a true definition of what a "step" is,
                // but this is probably a more user-friendly message than trying to explain what we're actually counting.
                throw IonException("Macro expansion exceeded limit of $expansionLimit steps.")
            }
        }

        fun reset() {
            numExpandedExpressions = 0
        }
    }

    /**
     * A container in the macro evaluator's [containerStack].
     */
    private data class ContainerInfo(var type: Type = Type.Uninitialized, private var _expansion: ExpansionInfo? = null) {
        enum class Type { TopLevel, List, Sexp, Struct, Uninitialized }

        fun close() {
            _expansion?.close()
            _expansion = null
            type = Type.Uninitialized
        }

        var expansion: ExpansionInfo
            get() = _expansion!!
            set(value) { _expansion = value }

        fun produceNext(): ExpressionA {
            return expansion.produceNext()
        }
    }

    /**
     * Stateless functions that operate on the expansion frames (i.e. [ExpansionInfo]).
     */
    // TODO(PERF): It might be possible to optimize this by changing it to an enum without any methods (or even a set of
    //             integer constants) and converting all their implementations to static methods.
    private enum class ExpansionKind {
        Uninitialized {
            override fun produceNext(thisExpansion: ExpansionInfo): Nothing = throw IllegalStateException("ExpansionInfo not initialized.")
        },
        Empty {
            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA = ExpressionA.END_OF_EXPANSION
        },
        Stream {
            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                // If there's a delegate, we'll try that first.
                val delegate = thisExpansion.childExpansion
                check(thisExpansion != delegate)
                if (delegate != null) {
                    val result = delegate.produceNext()
                    return if (result.kind == ExpressionKind.EndOfExpansion) {
                        delegate.close()
                        thisExpansion.childExpansion = null
                        ExpressionA.CONTINUE_EXPANSION
                    } else {
                        result
                    }
                }

                if (thisExpansion.i >= thisExpansion.endExclusive) {
                    thisExpansion.expansionKind = Empty
                    return ExpressionA.CONTINUE_EXPANSION
                }

                val next = thisExpansion.expressions[thisExpansion.i]
                thisExpansion.i++
                if (next.kind.hasStartAndEnd()) thisExpansion.i = next.endExclusive

                return when (next.kind) {
                    // is DataModelExpression -> see "else"
                    // is InvokableExpression
                    ExpressionKind.MacroInvocation,
                    ExpressionKind.EExpression -> {
                        val macro = next.value as Macro
                        val argIndices = calculateArgumentIndices(macro, thisExpansion.expressions, next.startInclusive, next.endExclusive)
                        val newEnvironment = thisExpansion.environment.createChild(thisExpansion.expressions, argIndices)
                        val expansionKind = ExpansionKind.forMacro(macro)
                        thisExpansion.childExpansion = thisExpansion.session.getExpander(
                            expansionKind = expansionKind,
                            expressions = macro.body ?: emptyList(),
                            startInclusive = 0,
                            endExclusive = macro.body?.size ?: 0,
                            environment = newEnvironment,
                        )
                        ExpressionA.CONTINUE_EXPANSION
                    }
                    ExpressionKind.ExpressionGroup -> {
                        thisExpansion.childExpansion = thisExpansion.session.getExpander(
                            expansionKind = ExprGroup,
                            expressions = thisExpansion.expressions,
                            startInclusive = next.startInclusive,
                            endExclusive = next.endExclusive,
                            environment = thisExpansion.environment,
                        )

                        ExpressionA.CONTINUE_EXPANSION
                    }
                    ExpressionKind.VariableRef -> {
                        thisExpansion.childExpansion = thisExpansion.readArgument(next.value as Int)
                        ExpressionA.CONTINUE_EXPANSION
                    }
                    ExpressionKind.Placeholder -> unreachable()
                    else -> next
                }
            }
        },
        /** Alias of [Stream] to aid in debugging */
        Variable {
            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                return Stream.produceNext(thisExpansion)
            }
        },
        /** Alias of [Stream] to aid in debugging */
        TemplateBody {
            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                return Stream.produceNext(thisExpansion)
            }
        },
        /** Alias of [Stream] to aid in debugging */
        ExprGroup {
            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                return Stream.produceNext(thisExpansion)
            }
        },
        ExactlyOneValueStream {
            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                if (thisExpansion.additionalState != 1) {
                    val firstValue = Stream.produceNext(thisExpansion)
                    return when (firstValue.kind) {
                        ExpressionKind.ContinueExpansion -> firstValue
                        ExpressionKind.EndOfExpansion -> throw IonException("Expected one value, found 0")
                        else -> {
                            thisExpansion.additionalState = 1
                            firstValue
                        }
                    }
                } else {
                    val secondValue = Stream.produceNext(thisExpansion)
                    return when (secondValue.kind) {
                        ExpressionKind.ContinueExpansion -> ExpressionA.CONTINUE_EXPANSION
                        ExpressionKind.EndOfExpansion -> secondValue
                        else -> throw IonException("Expected one value, found multiple")
                    }
                }
            }
        },

        IfNone {
            override fun produceNext(thisExpansion: ExpansionInfo) = thisExpansion.branchIf { it == 0 }
        },
        IfSome {
            override fun produceNext(thisExpansion: ExpansionInfo) = thisExpansion.branchIf { it > 0 }
        },
        IfSingle {
            override fun produceNext(thisExpansion: ExpansionInfo) = thisExpansion.branchIf { it == 1 }
        },
        IfMulti {
            override fun produceNext(thisExpansion: ExpansionInfo) = thisExpansion.branchIf { it > 1 }
        },
        Annotate {

            private val ANNOTATIONS_ARG = 0
            private val VALUE_TO_ANNOTATE_ARG = 1

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                // TODO: Avoid allocating SymbolTokens
                val annotations = thisExpansion.map(ANNOTATIONS_ARG) {
                    if (it.kind.isTextValue()) {
                        val annotation = it.value
                        if (annotation is String) {
                            newSymbolToken(annotation)
                        } else {
                            annotation as SymbolToken
                        }
                    } else {
                        throw IonException("Invalid argument type for 'annotate': ${it.ionType}")
                    }
                }


                val valueToAnnotateExpansion = thisExpansion.readArgument(VALUE_TO_ANNOTATE_ARG)

                val annotatedExpression = valueToAnnotateExpansion.produceNext().also {
                    it.withAnnotations(annotations + it.annotations)
                }
                if (valueToAnnotateExpansion.produceNext().kind != ExpressionKind.EndOfExpansion) {
                    throw IonException("Can only annotate exactly one value")
                }

                return annotatedExpression.also {
                    thisExpansion.tailCall(valueToAnnotateExpansion)
                }
            }
        },
        MakeString {
            private val STRINGS_ARG = 0

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                val sb = StringBuilder()
                thisExpansion.forEach(STRINGS_ARG) {
                    if (it.kind.isTextValue()) {
                        val text = it.value
                        sb.append(if (text is SymbolToken) text.assumeText() else text as String)
                    } else {
                        throw IonException("Invalid argument type for 'make_string': ${it.ionType}")
                    }
                }
                thisExpansion.expansionKind = Empty
                return ExpressionA.newString(sb.toString())
            }
        },
        MakeSymbol {
            private val STRINGS_ARG = 0

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                val sb = StringBuilder()
                thisExpansion.forEach(STRINGS_ARG) {
                    if (it.kind.isTextValue()) {
                        val text = it.value
                        sb.append(if (text is SymbolToken) text.assumeText() else text as String)
                    } else {
                        throw IonException("Invalid argument type for 'make_symbol': ${it.ionType}")
                    }
                }
                thisExpansion.expansionKind = Empty
                return ExpressionA.newSymbol(sb.toString())
            }
        },
        MakeBlob {
            private val LOB_ARG = 0

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                val baos = ByteArrayOutputStream()
                thisExpansion.forEach(LOB_ARG) {
                    if (it.kind.isLobValue()) {
                        baos.write(it.value as ByteArray)
                    } else {
                        throw IonException("Invalid argument type for 'make_blob': ${it.ionType}")
                    }
                }
                thisExpansion.expansionKind = Empty
                return ExpressionA.newBlob(baos.toByteArray())
            }
        },
        MakeDecimal {
            private val COEFFICIENT_ARG = 0
            private val EXPONENT_ARG = 1

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                val coefficient = thisExpansion.readExactlyOneArgumentExpression(COEFFICIENT_ARG, IonType.INT).value.let { if (it is Long) it.toBigInteger() else it as BigInteger  }
                val exponent = thisExpansion.readExactlyOneArgumentExpression(EXPONENT_ARG, IonType.INT).value.let { if (it is BigInteger) it.longValueExact() else it as Long  }
                thisExpansion.expansionKind = Empty
                return ExpressionA.newDecimal(value = BigDecimal(coefficient, -1 * exponent.toInt()))
            }
        },
        MakeTimestamp {
            private val YEAR_ARG = 0
            private val MONTH_ARG = 1
            private val DAY_ARG = 2
            private val HOUR_ARG = 3
            private val MINUTE_ARG = 4
            private val SECOND_ARG = 5
            private val OFFSET_ARG = 6

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                val year = thisExpansion.readExactlyOneArgumentExpression(YEAR_ARG, IonType.INT).dataAsInt()
                val month = thisExpansion.readZeroOrOneArgument(MONTH_ARG, IonType.INT)?.dataAsInt()
                val day = thisExpansion.readZeroOrOneArgument(DAY_ARG, IonType.INT)?.dataAsInt()
                val hour = thisExpansion.readZeroOrOneArgument(HOUR_ARG, IonType.INT)?.dataAsInt()
                val minute = thisExpansion.readZeroOrOneArgument(MINUTE_ARG, IonType.INT)?.dataAsInt()
                val second = thisExpansion.readZeroOrOneArgument(SECOND_ARG, IonType.INT, IonType.DECIMAL)?.value?.let {
                    when (it) {
                        is BigDecimal -> it
                        is Long -> it.toBigDecimal()
                        is BigInteger -> it.toBigDecimal()
                        else -> throw IonException("second must be an integer or decimal")
                    }
                }

                val offsetMinutes = thisExpansion.readZeroOrOneArgument(OFFSET_ARG, IonType.INT)?.dataAsInt()

                try {
                    val ts = if (second != null) {
                        month ?: throw IonException("make_timestamp: month is required when second is present")
                        day ?: throw IonException("make_timestamp: day is required when second is present")
                        hour ?: throw IonException("make_timestamp: hour is required when second is present")
                        minute ?: throw IonException("make_timestamp: minute is required when second is present")
                        Timestamp.forSecond(year, month, day, hour, minute, second, offsetMinutes)
                    } else if (minute != null) {
                        month ?: throw IonException("make_timestamp: month is required when minute is present")
                        day ?: throw IonException("make_timestamp: day is required when minute is present")
                        hour ?: throw IonException("make_timestamp: hour is required when minute is present")
                        Timestamp.forMinute(year, month, day, hour, minute, offsetMinutes)
                    } else if (hour != null) {
                        throw IonException("make_timestamp: minute is required when hour is present")
                    } else {
                        if (offsetMinutes != null) throw IonException("make_timestamp: offset_minutes is prohibited when hours and minute are not present")
                        if (day != null) {
                            month ?: throw IonException("make_timestamp: month is required when day is present")
                            Timestamp.forDay(year, month, day)
                        } else if (month != null) {
                            Timestamp.forMonth(year, month)
                        } else {
                            Timestamp.forYear(year)
                        }
                    }
                    thisExpansion.expansionKind = Empty
                    return ExpressionA.newTimestamp(ts)
                } catch (e: IllegalArgumentException) {
                    throw IonException(e.message)
                }
            }
        },
        _Private_MakeFieldNameAndValue {
            private val FIELD_NAME = 0
            private val FIELD_VALUE = 1

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                val fieldName = thisExpansion.readExactlyOneArgumentExpression(FIELD_NAME, IonType.STRING, IonType.SYMBOL).value
                val fieldNameExpression = when (fieldName) {
                    is SymbolToken -> ExpressionA.newFieldName(fieldName)
                    is String -> ExpressionA.newFieldName(newSymbolToken(fieldName))
                    else -> throw IonException("Illegal argument for field name: $fieldName")
                }

                thisExpansion.readExactlyOneArgumentExpression(FIELD_VALUE, *IonType.entries.toTypedArray())

                val valueExpansion = thisExpansion.readArgument(FIELD_VALUE)

                return fieldNameExpression.also {
                    thisExpansion.tailCall(valueExpansion)
                    thisExpansion.expansionKind = ExactlyOneValueStream
                }
            }
        },

        _Private_FlattenStruct {
            private val STRUCTS = 0

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                var argumentExpansion: ExpansionInfo? = thisExpansion.additionalState as ExpansionInfo?
                if (argumentExpansion == null) {
                    argumentExpansion = thisExpansion.readArgument(STRUCTS)
                    thisExpansion.additionalState = argumentExpansion
                }

                val currentChildExpansion = thisExpansion.childExpansion
                val next = currentChildExpansion?.produceNext()
                return when (next?.kind) {
                    ExpressionKind.EndOfExpansion -> thisExpansion.closeDelegateAndContinue()
                    // Only possible if expansionDelegate is null
                    null -> {
                        val nextSequence = argumentExpansion.produceNext()
                        when (nextSequence.kind) {
                            ExpressionKind.StructValue -> {
                                thisExpansion.childExpansion = thisExpansion.session.getExpander(
                                    expansionKind = Stream,
                                    expressions = argumentExpansion.top().expressions,
                                    startInclusive = nextSequence.startInclusive,
                                    endExclusive = nextSequence.endExclusive,
                                    environment = argumentExpansion.top().environment,
                                )
                                ExpressionA.CONTINUE_EXPANSION
                            }
                            ExpressionKind.EndOfExpansion -> ExpressionA.END_OF_EXPANSION
                            else -> throw IonException("invalid argument; make_struct expects structs")
                        }
                    }

                    else -> next
                }
            }
        },

        /**
         * Iterates over the sequences, returning the values contained in the sequences.
         * The expansion for the sequences argument is stored in [ExpansionInfo.additionalState].
         * When
         */
        Flatten {
            private val SEQUENCES = 0

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                var argumentExpansion: ExpansionInfo? = thisExpansion.additionalState as ExpansionInfo?
                if (argumentExpansion == null) {
                    argumentExpansion = thisExpansion.readArgument(SEQUENCES)
                    thisExpansion.additionalState = argumentExpansion
                }

                val currentChildExpansion = thisExpansion.childExpansion

                val next = currentChildExpansion?.produceNext()
                return when (next?.kind) {
                    ExpressionKind.EndOfExpansion -> thisExpansion.closeDelegateAndContinue()
                    // Only possible if expansionDelegate is null
                    null -> {
                        val nextSequence = argumentExpansion.produceNext()
                        when (nextSequence.kind) {
                            ExpressionKind.SExpValue,
                            ExpressionKind.ListValue -> {
                                thisExpansion.childExpansion = thisExpansion.session.getExpander(
                                    expansionKind = Stream,
                                    expressions = argumentExpansion.top().expressions,
                                    startInclusive = nextSequence.startInclusive,
                                    endExclusive = nextSequence.endExclusive,
                                    environment = argumentExpansion.top().environment,
                                )
                                ExpressionA.CONTINUE_EXPANSION
                            }
                            ExpressionKind.EndOfExpansion -> ExpressionA.END_OF_EXPANSION
                            else -> throw IonException("invalid argument; flatten expects sequences")
                        }
                    }
                    else -> next
                }
            }
        },
        Sum {
            private val ARG_A = 0
            private val ARG_B = 1

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                // TODO(PERF): consider checking whether the value would fit in a long and returning a `LongIntValue`.
                val a = thisExpansion.readExactlyOneArgumentExpression(ARG_A, IonType.INT).value
                    .let { if (it is Long) it.toBigInteger() else it as BigInteger }
                val b = thisExpansion.readExactlyOneArgumentExpression(ARG_B, IonType.INT).value
                    .let { if (it is Long) it.toBigInteger() else it as BigInteger }
                thisExpansion.expansionKind = Empty
                return ExpressionA.newInt(a + b)
            }
        },
        Delta {
            private val ARGS = 0

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                // TODO(PERF): Optimize to use LongIntValue when possible
                var delegate = thisExpansion.childExpansion
                val runningTotal = thisExpansion.additionalState as? BigInteger ?: BigInteger.ZERO
                if (delegate == null) {
                    delegate = thisExpansion.readArgument(ARGS)
                    thisExpansion.childExpansion = delegate
                }

                val nextExpandedArg = delegate.produceNext()
                if (nextExpandedArg.kind.isIntValue()) {
                    val nextDelta = nextExpandedArg.value
                        .let { if (it is Long) it.toBigInteger() else it as BigInteger }
                    val nextOutput = runningTotal + nextDelta
                    thisExpansion.additionalState = nextOutput
                    return ExpressionA.newInt(nextOutput)
                } else if (nextExpandedArg.kind == ExpressionKind.EndOfExpansion) {
                    return ExpressionA.END_OF_EXPANSION
                } else {
                    throw IonException("delta arguments must be integers")
                }
            }
        },
        Repeat {
            private val COUNT_ARG = 0
            private val THING_TO_REPEAT = 1

            override fun produceNext(thisExpansion: ExpansionInfo): ExpressionA {
                var n = thisExpansion.additionalState as Long?
                if (n == null) {
                    // TODO: Could this be a BigInteger?
                    n = thisExpansion.readExactlyOneArgumentExpression(COUNT_ARG, IonType.INT).value as Long
                    if (n < 0) throw IonException("invalid argument; 'n' must be non-negative")
                    thisExpansion.additionalState = n
                }

                if (thisExpansion.childExpansion == null) {
                    if (n > 0) {
                        thisExpansion.childExpansion = thisExpansion.readArgument(THING_TO_REPEAT)
                        thisExpansion.additionalState = n - 1
                    } else {
                        return ExpressionA.END_OF_EXPANSION
                    }
                }

                val repeated = thisExpansion.childExpansion!!
                val maybeNext = repeated.produceNext()
                return if (maybeNext.kind == ExpressionKind.EndOfExpansion) {
                    thisExpansion.closeDelegateAndContinue()
                } else {
                    maybeNext
                }
            }
        },
        ;

        /**
         * Produces the next value, [EndOfExpansion], or [ExpressionA.CONTINUE_EXPANSION].
         * Each enum variant must implement this method.
         */
        abstract fun produceNext(thisExpansion: ExpansionInfo): ExpressionA

        /** Helper function for the `if_*` macros */
        inline fun ExpansionInfo.branchIf(condition: (Int) -> Boolean): ExpressionA {
            val argToTest = 0
            val trueBranch = 1
            val falseBranch = 2

            val testArg = readArgument(argToTest)
            var n = 0
            while (n < 2) {
                if (testArg.produceNext().kind == ExpressionKind.EndOfExpansion) break
                n++
            }
            testArg.close()

            val branch = if (condition(n)) trueBranch else falseBranch

            tailCall(readArgument(branch))
            return ExpressionA.CONTINUE_EXPANSION
        }

        /**
         * Returns an expansion for the given variable.
         */
        fun ExpansionInfo.readArgument(signatureIndex: Int): ExpansionInfo {
            val argIndex = environment.argumentIndices[signatureIndex]
            if (argIndex < 0) {
                // Argument was elided.
                return session.getExpander(Empty, emptyList(), 0, 0, Environment.EMPTY)
            }
            val firstArgExpression = environment.arguments[argIndex]
            val startInclusive = if (firstArgExpression.kind == ExpressionKind.ExpressionGroup) firstArgExpression.startInclusive else argIndex
            val endExclusive = if (firstArgExpression.kind.hasStartAndEnd()) firstArgExpression.endExclusive else argIndex + 1
            return session.getExpander(Variable, environment.arguments, startInclusive, endExclusive, environment.parentEnvironment!!)
        }

        /**
         * Performs the given [action] for each value produced by the expansion of [variableRef].
         */
        inline fun ExpansionInfo.forEach(signatureIndex: Int, action: (ExpressionA) -> Unit) {
            val variableExpansion = readArgument(signatureIndex)
            while (true) {
                val next = variableExpansion.produceNext()
                if (next.kind == ExpressionKind.EndOfExpansion) {
                    return
                } else {
                    action(next)
                }
            }
        }

        /**
         * Performs the given [transform] on each value produced by the expansion of [variableRef], returning a list
         * of the results.
         */
        inline fun <T> ExpansionInfo.map(signatureIndex: Int, transform: (ExpressionA) -> T): List<T> {
            val variableExpansion = readArgument(signatureIndex)
            val result = mutableListOf<T>()
            while (true) {
                val next = variableExpansion.produceNext()
                if (next.kind == ExpressionKind.EndOfExpansion) {
                    return result
                } else {
                    result.add(transform(next))
                }
            }
        }

        /**
         * Reads and returns zero or one values from the expansion of the given [variableRef].
         * Throws an [IonException] if more than one value is present in the variable expansion.
         * Throws an [IonException] if the value is not the expected type [T].
         */
        fun ExpansionInfo.readZeroOrOneArgument(signatureIndex: Int, vararg expectedType: IonType): ExpressionA? {
            val argExpansion = readArgument(signatureIndex)
            var argValue: ExpressionA? = null
            while (true) {
                val it = argExpansion.produceNext()
                if (it.kind == ExpressionKind.EndOfExpansion) {
                    break
                } else if (it.kind.toIonType() in expectedType) {
                    if (argValue == null) {
                        argValue = it
                    } else {
                        throw IonException("invalid argument; too many values")
                    }
                } else {
                    throw IonException("invalid argument; found ${it.ionType}")
                }
            }
            argExpansion.close()
            return argValue
        }

        /**
         * Reads and returns exactly one value from the expansion of the given [variableRef].
         * Throws an [IonException] if the expansion of [variableRef] does not produce exactly one value.
         * Throws an [IonException] if the value is not the expected type [T].
         */
        inline fun ExpansionInfo.readExactlyOneArgumentExpression(signatureIndex: Int, vararg expectedType: IonType): ExpressionA {
            return readZeroOrOneArgument(signatureIndex, *expectedType) ?: throw IonException("invalid argument; no value when one is expected")
        }

        companion object {
            /**
             * Gets the [ExpansionKind] for the given [macro].
             */
            @JvmStatic
            fun forMacro(macro: Macro): ExpansionKind {
                return if (macro.body != null) {
                    TemplateBody
                } else when (macro as SystemMacro) {
                    SystemMacro.IfNone -> IfNone
                    SystemMacro.IfSome -> IfSome
                    SystemMacro.IfSingle -> IfSingle
                    SystemMacro.IfMulti -> IfMulti
                    SystemMacro.Annotate -> Annotate
                    SystemMacro.MakeString -> MakeString
                    SystemMacro.MakeSymbol -> MakeSymbol
                    SystemMacro.MakeDecimal -> MakeDecimal
                    SystemMacro.MakeTimestamp -> MakeTimestamp
                    SystemMacro.MakeBlob -> MakeBlob
                    SystemMacro.Repeat -> Repeat
                    SystemMacro.Sum -> Sum
                    SystemMacro.Delta -> Delta
                    SystemMacro.Flatten -> Flatten
                    SystemMacro._Private_FlattenStruct -> _Private_FlattenStruct
                    SystemMacro._Private_MakeFieldNameAndValue -> _Private_MakeFieldNameAndValue
                    else -> TODO("Not implemented yet: ${macro.name}")
                }
            }
        }
    }

    /**
     * Represents a frame in the expansion stack for a particular container.
     *
     * TODO: "info" is very non-specific; rename to ExpansionFrame next time there's a
     *       non-functional refactoring in this class.
     *       Alternately, consider ExpansionOperator to reflect the fact that these are
     *       like operators in an expression tree.
     */
    private class ExpansionInfo(@JvmField val session: Session) {

        /** The [ExpansionKind]. */
        @JvmField var expansionKind: ExpansionKind = ExpansionKind.Uninitialized
        /**
         * The evaluation [Environment]—i.e. variable bindings.
         */
        @JvmField var environment: Environment = Environment.EMPTY
        /**
         * The [Expression]s being expanded. This MUST be the original list, not a sublist because
         * (a) we don't want to be allocating new sublists all the time, and (b) the
         * start and end indices of the expressions may be incorrect if a sublist is taken.
         */
        @JvmField var expressions: List<ExpressionA> = emptyList()
        /** End of [expressions] that are applicable for this [ExpansionInfo] */
        @JvmField var endExclusive: Int = 0
        /** Current position within [expressions] of this expansion */
        @JvmField var i: Int = 0

        /**
         * Field for storing any additional state required by an ExpansionKind.
         */
        @JvmField
        var additionalState: Any? = null

        @JvmField
        var isInPool = false

        /**
         * Additional state in the form of a child [ExpansionInfo].
         */
        var childExpansion: ExpansionInfo? = null
            // TODO: if childExpansion == this, it will cause an infinite loop or stack overflow somewhere.
            // In practice, it should never happen, so we may wish to remove the custom setter to avoid any performance impact.
            set(value) {
                check(value != this)
                field = value
            }

        /**
         * Convenience function to close the [childExpansion] and return it to the pool.
         */
        fun closeDelegateAndContinue(): ExpressionA {
            childExpansion?.close()
            childExpansion = null
            return ExpressionA.CONTINUE_EXPANSION
        }

        /**
         * Gets the [ExpansionInfo] at the top of the stack of [childExpansion]s.
         */
        fun top(): ExpansionInfo = childExpansion?.top() ?: this

        /**
         * Returns this [ExpansionInfo] to the expander pool, recursively closing [childExpansion]s in the process.
         * Could also be thought of as a `free` function.
         */
        fun close() {
            expansionKind = ExpansionKind.Uninitialized
            environment = Environment.EMPTY
            expressions = emptyList()
            additionalState?.let { if (it is ExpansionInfo) it.close() }
            additionalState = null
            childExpansion?.close()
            childExpansion = null
            session.reclaimExpander(this)
        }

        /**
         * Replaces the state of `this` [ExpansionInfo] with the state of [other]—effectively a tail-call optimization.
         * After transferring the state, `other` is returned to the expansion pool.
         */
        fun tailCall(other: ExpansionInfo) {
            this.expansionKind = other.expansionKind
            this.expressions = other.expressions
            this.i = other.i
            this.endExclusive = other.endExclusive
            this.childExpansion = other.childExpansion
            this.additionalState = other.additionalState
            this.environment = other.environment
            // Close `other`
            other.childExpansion = null
            other.close()
        }

        /**
         * Produces the next value from this expansion.
         */
        fun produceNext(): ExpressionA {
            while (true) {
                val next = expansionKind.produceNext(this)
                // println("ExpansionInfo (${System.identityHashCode(this)}:$expansionKind) - next=$next")
                if (next.kind == ExpressionKind.ContinueExpansion) continue
                // This the only place where we count the expansion steps.
                // It is theoretically possible to have macro expansions that are millions of levels deep because this
                // only counts macro invocations at the end of their expansion, but this will still work to catch things
                // like a  billion laughs attack because it does place a limit on the number of _values_ produced.
                // This counts every value _at every level_, so most values will be counted multiple times. If possible
                // without impacting performance, count values only once in order to have more predictable behavior.
                session.incrementStepCounter()
                return next
            }
        }

        override fun toString() = """
        |ExpansionInfo(
        |    expansionKind: $expansionKind,
        |    environment: ${environment.toString().lines().joinToString("\n|        ")},
        |    expressions: [
        |        ${expressions.mapIndexed { i, expr -> "$i. $expr" }.joinToString(",\n|        ") }
        |    ],
        |    endExclusive: $endExclusive,
        |    i: $i,
        |    child: ${childExpansion?.expansionKind}
        |    additionalState: $additionalState,
        |)
        """.trimMargin()
    }

    fun dump() {
        // println(containerStack.peek())
    }

    private val session = Session(expansionLimit = 1_000_000)
    private val containerStack = _Private_RecyclingStack(8) { ContainerInfo() }
    private var currentExpr: ExpressionA? = null

    /**
     * Returns the e-expression argument expressions that this MacroEvaluator would evaluate.
     */
    fun getArguments(): List<ExpressionA> {
        return containerStack.iterator().next().expansion.expressions
    }

    /**
     * Initialize the macro evaluator with an E-Expression.
     */
    fun initExpansion(encodingExpressions: List<ExpressionA>) {
        session.reset()
        containerStack.push { ci ->
            ci.type = ContainerInfo.Type.TopLevel
            ci.expansion = session.getExpander(ExpansionKind.Stream, encodingExpressions, 0, encodingExpressions.size, Environment.EMPTY)
        }
    }

    /**
     * Evaluate the macro expansion until the next [DataModelExpression] can be returned.
     * Returns null if at the end of a container or at the end of the expansion.
     */
    fun expandNext(): ExpressionA? {
        currentExpr = null
        val currentContainer = containerStack.peek()
        val nextExpansionOutput = currentContainer.produceNext()
        if (nextExpansionOutput.kind.isDataModelExpression()) {
            currentExpr = nextExpansionOutput
        } else if (nextExpansionOutput.kind == ExpressionKind.EndOfExpansion) {
            if (currentContainer.type == ContainerInfo.Type.TopLevel) {
                currentContainer.close()
                containerStack.pop()
            }
        }
        return currentExpr
    }

    /**
     * Steps out of the current [DataModelContainer].
     */
    fun stepOut() {
        // TODO: We should be able to step out of a "TopLevel" container and/or we need some way to close the evaluation early.
        if (containerStack.size() <= 1) throw IonException("Nothing to step out of.")
        val popped = containerStack.pop()
        popped.close()
    }

    /**
     * Steps in to the current [DataModelContainer].
     * Throws [IonException] if not positioned on a container.
     */
    fun stepIn() {
        val expression = requireNotNull(currentExpr) { "Not positioned on a value" }
        // println("Stepping in to $expression")
        if (expression.kind.isDataModelContainer()) {
            val currentContainer = containerStack.peek()
            val topExpansion = currentContainer.expansion.top()
            containerStack.push { ci ->
                ci.type = when (expression.ionType) {
                    IonType.LIST -> ContainerInfo.Type.List
                    IonType.SEXP -> ContainerInfo.Type.Sexp
                    IonType.STRUCT -> ContainerInfo.Type.Struct
                    else -> unreachable()
                }
                ci.expansion = session.getExpander(
                    expansionKind = ExpansionKind.Stream,
                    expressions = topExpansion.expressions,
                    startInclusive = expression.startInclusive,
                    endExclusive = expression.endExclusive,
                    environment = topExpansion.environment,
                )
            }
            currentExpr = null
        } else {
            throw IonException("Not positioned on a container.")
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
    encodingExpressions: List<ExpressionA>,
    argsStartInclusive: Int,
    argsEndExclusive: Int
): IntArray {
    // TODO: For TDL macro invocations, see if we can calculate this during the "compile" step.
    var numArgs = 0
    val argsIndices = IntArray(macro.signature.size) // TODO performance: pool these
    var currentArgIndex = argsStartInclusive

    for (p in macro.signature) {
        if (currentArgIndex >= argsEndExclusive) {
            if (!p.cardinality.canBeVoid) throw IonException("No value provided for parameter ${p.variableName}")
            // Elided rest parameter.
            argsIndices[numArgs] = -1
        } else {
            argsIndices[numArgs] = currentArgIndex
            // while (encodingExpressions[currentArgIndex].kind == ExpressionKind.ContinueExpansion) currentArgIndex++
            val expr = encodingExpressions[currentArgIndex]
            currentArgIndex = when {
                expr.kind.hasStartAndEnd() -> expr.endExclusive
                else -> currentArgIndex + 1
            }
        }
        numArgs++
    }
    while (currentArgIndex < argsEndExclusive) {
        val expr = encodingExpressions[currentArgIndex]
        // println("Found extra argument $numArgs: $expr")
        // while (encodingExpressions[currentArgIndex].kind == ExpressionKind.Annotations || encodingExpressions[currentArgIndex].kind == ExpressionKind.ContinueExpansion) currentArgIndex++
        currentArgIndex = if (expr.kind.hasStartAndEnd()) {
            expr.endExclusive
        } else {
            currentArgIndex + 1
        }
        numArgs++
    }
    if (numArgs > macro.signature.size) {
        throw IonException("Too many arguments. Expected ${macro.signature.size}, but found $numArgs")
    }
    return argsIndices
}
