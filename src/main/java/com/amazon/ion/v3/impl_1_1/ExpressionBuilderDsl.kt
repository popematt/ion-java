// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.Expression.*
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
    fun <T> annotated(annotation0: String?, valueFn: KFunction1<T, Unit>, value: T) = annotated(arrayOf(annotation0), valueFn, value)
    fun <T> annotated(annotation: SystemSymbols_1_1, valueFn: KFunction1<T, Unit>, value: T) = annotated(arrayOf(annotation.text), valueFn, value)
    fun <T> annotated(annotations: Array<String?>, valueFn: KFunction1<T, Unit>, value: T)
    fun nullValue(value: IonType = IonType.NULL)
    fun bool(value: Boolean)
    fun int(value: Long)
    fun int(value: BigInteger)
    fun float(value: Double)
    fun decimal(value: Decimal)
    fun decimal(value: BigDecimal) = decimal(Decimal.valueOf(value))
    fun timestamp(value: Timestamp)
    fun symbol(value: String?)
    fun symbol(value: SystemSymbols_1_1) = symbol(value.text)
    fun string(value: String)
    fun clob(value: ByteArray)
    fun blob(value: ByteArray)

    /** Helper interface for use when building the content of a struct */
    interface Fields {
        fun fieldName(fieldName: String?)
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
    fun macro(macro: FlatMacro, arguments: TemplateDsl.() -> Unit)
    fun variable(signatureIndex: Int)
    fun list(content: TemplateDsl.() -> Unit)
    fun sexp(content: TemplateDsl.() -> Unit)
    fun struct(content: Fields.() -> Unit)
    fun expressionGroup(content: TemplateDsl.() -> Unit)

    @ExpressionBuilderDslMarker
    interface Fields : ValuesDsl.Fields, TemplateDsl
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
        fun templateBody(block: TemplateDsl.() -> Unit): List<TemplateBodyExpressionModel> = Template().apply(block).build()
    }

    protected val expressions = mutableListOf<TemplateBodyExpressionModel>()
    private var pendingAnnotations = emptyArray<String?>()
    private var pendingFieldName: String? = null

    override fun <T> annotated(annotations: Array<String?>, valueFn: KFunction1<T, Unit>, value: T) {
        pendingAnnotations = annotations
        valueFn.invoke(value)
    }

    override fun nullValue(value: IonType) = scalar(TemplateBodyExpressionModel.Kind.NULL, value)
    override fun bool(value: Boolean) = scalar(TemplateBodyExpressionModel.Kind.BOOL, value)
    override fun int(value: Long) = scalar(TemplateBodyExpressionModel.Kind.INT, value)
    override fun int(value: BigInteger) = scalar(TemplateBodyExpressionModel.Kind.INT, value)
    override fun float(value: Double) = scalar(TemplateBodyExpressionModel.Kind.FLOAT, value)
    override fun decimal(value: Decimal) = scalar(TemplateBodyExpressionModel.Kind.DECIMAL, value)
    override fun timestamp(value: Timestamp) = scalar(TemplateBodyExpressionModel.Kind.TIMESTAMP, value)
    override fun symbol(value: String?) = scalar(TemplateBodyExpressionModel.Kind.SYMBOL, value)
    override fun string(value: String) = scalar(TemplateBodyExpressionModel.Kind.STRING, value)
    override fun clob(value: ByteArray) = scalar(TemplateBodyExpressionModel.Kind.CLOB, value)
    override fun blob(value: ByteArray) = scalar(TemplateBodyExpressionModel.Kind.BLOB, value)

    override fun fieldName(fieldName: String?) { pendingFieldName = fieldName }

    protected fun newStruct(annotations: Array<String?>, structStart: Int, structEndExclusive: Int): TemplateBodyExpressionModel {
        return TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.STRUCT, structEndExclusive - structStart, pendingFieldName, annotations, null)
    }

    fun build(): List<TemplateBodyExpressionModel> = expressions.toList()

    // Helpers
    private fun takePendingAnnotations(): Array<String?> = pendingAnnotations.also { pendingAnnotations = emptyArray() }

    private fun takePendingFieldName(): String? = pendingFieldName.also { pendingFieldName = null }

    protected fun <T> scalar(kind: Int, value: T) {
        expressions.add(TemplateBodyExpressionModel(kind, 1, takePendingFieldName(), takePendingAnnotations(), value))
    }

    protected fun <T> container(kind: Int, content: T.() -> Unit) {
        val start = expressions.size
        val startExpression = TemplateBodyExpressionModel(kind, 1, takePendingFieldName(), takePendingAnnotations(), null)
        expressions.add(startExpression)
        (this as T).content()
        val end = expressions.size
        startExpression.length = end - start
    }

    // Subclasses for each expression variant so that we don't have conflicting signatures between their list, sexp, etc. implementations.

    class Template : ExpressionBuilderDsl(), TemplateDsl, TemplateDsl.Fields {
        override fun list(content: TemplateDsl.() -> Unit) = container(TemplateBodyExpressionModel.Kind.LIST, content)
        override fun sexp(content: TemplateDsl.() -> Unit) = container(TemplateBodyExpressionModel.Kind.SEXP, content)
        override fun struct(content: TemplateDsl.Fields.() -> Unit) = container(TemplateBodyExpressionModel.Kind.STRUCT, content)
        override fun variable(signatureIndex: Int) { scalar(TemplateBodyExpressionModel.Kind.VARIABLE, signatureIndex) }
        override fun macro(macro: FlatMacro, arguments: TemplateDsl.() -> Unit) = container(TemplateBodyExpressionModel.Kind.INVOCATION, arguments)
        override fun expressionGroup(content: TemplateDsl.() -> Unit) = container(TemplateBodyExpressionModel.Kind.EXPRESSION_GROUP, content)
    }
}
