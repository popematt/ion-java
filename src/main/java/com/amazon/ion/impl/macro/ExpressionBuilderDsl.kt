// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.ExpressionA
import com.amazon.ion.impl.macro.ExpressionA.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KFunction1

/**
 * Nothing in this file should be made public because it would expose the shaded kotlin std library in our public API.
 */

/** A marker annotation for a [type-safe builder](https://kotlinlang.org/docs/type-safe-builders.html). */
@DslMarker
internal annotation class ExpressionBuilderDslMarker

/** Base DSL; functions are common for [DataModelExpression], [TemplateBodyExpression], and [EExpressionBodyExpression]. */
internal interface ValuesDsl {
    fun <T> annotated(annotations: List<SymbolToken>, valueFn: KFunction1<T, Unit>, value: T)
    fun <T> annotated(annotation: SystemSymbols_1_1, valueFn: KFunction1<T, Unit>, value: T) =
        annotated(listOf(annotation.token), valueFn, value)
    fun nullValue(value: IonType = IonType.NULL)
    fun bool(value: Boolean)
    fun int(value: Long)
    fun int(value: BigInteger)
    fun float(value: Double)
    fun decimal(value: Decimal)
    fun timestamp(value: Timestamp)
    fun symbol(value: SymbolToken)
    fun symbol(value: String) = symbol(_Private_Utils.newSymbolToken(value))
    fun symbol(value: SystemSymbols_1_1) = symbol(value.token)
    fun string(value: String)
    fun clob(value: ByteArray)
    fun blob(value: ByteArray)

    /** Helper interface for use when building the content of a struct */
    interface Fields {
        fun fieldName(fieldName: SymbolToken)
        fun fieldName(fieldName: String) = fieldName(_Private_Utils.newSymbolToken(fieldName))
    }
}

/** DSL for building [DataModelExpression] lists. */
@ExpressionBuilderDslMarker
internal interface DataModelDsl : ValuesDsl {
    fun list(content: DataModelDsl.() -> Unit)
    fun sexp(content: DataModelDsl.() -> Unit)
    fun struct(content: Fields.() -> Unit)

    @ExpressionBuilderDslMarker
    interface Fields : ValuesDsl.Fields, DataModelDsl
}

/** DSL for building [TemplateBodyExpression] lists. */
@ExpressionBuilderDslMarker
internal interface TemplateDsl : ValuesDsl {
    fun macro(macro: Macro, arguments: InvocationBody.() -> Unit)
    fun variable(signatureIndex: Int)
    fun list(content: TemplateDsl.() -> Unit)
    fun sexp(content: TemplateDsl.() -> Unit)
    fun struct(content: Fields.() -> Unit)

    @ExpressionBuilderDslMarker
    interface Fields : ValuesDsl.Fields, TemplateDsl

    @ExpressionBuilderDslMarker
    interface InvocationBody : TemplateDsl {
        fun expressionGroup(content: TemplateDsl.() -> Unit)
    }
}

/** DSL for building [EExpressionBodyExpression] lists. */
@ExpressionBuilderDslMarker
internal interface EExpDsl : ValuesDsl {
    fun eexp(macro: Macro, arguments: InvocationBody.() -> Unit)
    fun list(content: EExpDsl.() -> Unit)
    fun sexp(content: EExpDsl.() -> Unit)
    fun struct(content: Fields.() -> Unit)

    @ExpressionBuilderDslMarker
    interface Fields : ValuesDsl.Fields, EExpDsl

    @ExpressionBuilderDslMarker
    interface InvocationBody : EExpDsl {
        fun expressionGroup(content: EExpDsl.() -> Unit)
    }
}

/**
 * The implementation of all the expression builder DSL interfaces.
 *
 * How does this work? We implement everything in one class, but methods are exposed by being selective
 * about which interface we are using at any given time. For example, if you want to build a template
 * expression, you will get an interface that will not allow you to create an E-Expression. Likewise, if
 * you are building a struct, you will not get an interface with a method to create an expression group
 * in the middle of a struct (you must create a macro/eexp first).
 */
internal sealed class ExpressionBuilderDsl : ValuesDsl, ValuesDsl.Fields {

    companion object {
        // Entry points to the DSL builders.
        fun templateBody(block: TemplateDsl.() -> Unit): List<ExpressionA> = Template().apply(block).build()
        fun dataModel(block: DataModelDsl.() -> Unit): List<ExpressionA> = DataModel().apply(block).build()
        fun eExpBody(block: EExpDsl.() -> Unit): List<ExpressionA> = EExp().apply(block).build()
    }

    protected val expressions = mutableListOf<ExpressionA>()
    private var pendingAnnotations = mutableListOf<SymbolToken>()

    override fun <T> annotated(annotations: List<SymbolToken>, valueFn: KFunction1<T, Unit>, value: T) {
        pendingAnnotations.addAll(annotations)
        valueFn.invoke(value)
    }

    override fun nullValue(value: IonType) = scalar(ExpressionA::newNull, value)
    override fun bool(value: Boolean) = scalar(ExpressionA::newBool, value)
    override fun int(value: Long) = scalar(ExpressionA::newInt, value)
    override fun int(value: BigInteger) = scalar(ExpressionA::newInt, value)
    override fun float(value: Double) = scalar(ExpressionA::newFloat, value)
    override fun decimal(value: Decimal) = scalar(ExpressionA::newDecimal, value)
    override fun timestamp(value: Timestamp) = scalar(ExpressionA::newTimestamp, value)
    override fun symbol(value: SymbolToken) = scalar(ExpressionA::newSymbol, value)
    override fun string(value: String) = scalar(ExpressionA::newString, value)
    override fun clob(value: ByteArray) = scalar(ExpressionA::newClob, value)
    override fun blob(value: ByteArray) = scalar(ExpressionA::newBlob, value)

    override fun fieldName(fieldName: SymbolToken) { expressions.add(ExpressionA.newFieldName(fieldName)) }

    fun build(): List<ExpressionA> = expressions

    // Helpers
    private fun takePendingAnnotations(): List<SymbolToken> = pendingAnnotations.also { pendingAnnotations = mutableListOf() }

    private fun <T> scalar(constructor: (T) -> ExpressionA, value: T) {
        expressions.add(constructor(value).withAnnotations(takePendingAnnotations()))
    }

    protected fun <T : ExpressionBuilderDsl> container(content: T.() -> Unit, constructor: (Int, Int) -> ExpressionA) {
        val ann = takePendingAnnotations()
        val selfIndex = expressions.size
        expressions.add(ExpressionA())
        (this as T).content()
        expressions[selfIndex] = constructor(/* startInclusive= */ selfIndex + 1, /* endExclusive= */ expressions.size).withAnnotations(ann)
    }

    // Subclasses for each expression variant so that we don't have conflicting signatures between their list, sexp, etc. implementations.

    class DataModel : ExpressionBuilderDsl(), DataModelDsl, DataModelDsl.Fields {
        override fun list(content: DataModelDsl.() -> Unit) = container(content, ExpressionA::newList)
        override fun sexp(content: DataModelDsl.() -> Unit) = container(content, ExpressionA::newSexp)
        override fun struct(content: DataModelDsl.Fields.() -> Unit) = container(content, ExpressionA::newStruct)
    }

    class EExp : ExpressionBuilderDsl(), EExpDsl, EExpDsl.Fields, EExpDsl.InvocationBody {
        override fun sexp(content: EExpDsl.() -> Unit) = container(content, ExpressionA::newSexp)
        override fun list(content: EExpDsl.() -> Unit) = container(content, ExpressionA::newList)
        override fun struct(content: EExpDsl.Fields.() -> Unit) = container(content, ExpressionA::newStruct)
        override fun eexp(macro: Macro, arguments: EExpDsl.InvocationBody.() -> Unit) = container(arguments) { start, end -> ExpressionA.newEExpression(macro, start, end) }
        override fun expressionGroup(content: EExpDsl.() -> Unit) = container(content, ExpressionA::newExpressionGroup)
    }

    class Template : ExpressionBuilderDsl(), TemplateDsl, TemplateDsl.Fields, TemplateDsl.InvocationBody {
        override fun list(content: TemplateDsl.() -> Unit) = container(content, ExpressionA::newList)
        override fun sexp(content: TemplateDsl.() -> Unit) = container(content, ExpressionA::newSexp)
        override fun struct(content: TemplateDsl.Fields.() -> Unit) = container(content, ExpressionA::newStruct)
        override fun variable(signatureIndex: Int) { expressions.add(ExpressionA.newVariableRef(signatureIndex)) }
        override fun macro(macro: Macro, arguments: TemplateDsl.InvocationBody.() -> Unit) = container(arguments) { start, end ->
            val argIndices = macro.calculateArgumentIndices(expressions, start, end)
            ExpressionA.newMacroInvocation(macro, argIndices, start, end)
        }
        override fun expressionGroup(content: TemplateDsl.() -> Unit) = container(content, ExpressionA::newExpressionGroup)
    }
}
