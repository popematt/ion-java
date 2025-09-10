// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v8

import com.amazon.ion.*
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
    fun annotated(annotation0: String?, valueFn: () -> Unit) = annotated(arrayOf(annotation0), valueFn)
    fun annotated(annotations: Array<String?>, valueFn: () -> Unit)
    fun <T> annotated(annotation0: String?, valueFn: KFunction1<T, Unit>, value: T) = annotated(arrayOf(annotation0), valueFn, value)
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
    fun string(value: String)
    fun clob(value: ByteArray) = clob(ByteArraySlice(value, 0, value.size))
    fun blob(value: ByteArray) = blob(ByteArraySlice(value, 0, value.size))
    fun clob(value: ByteArraySlice)
    fun blob(value: ByteArraySlice)

    // TODO: Homogeneous lists?

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
    fun variable()
    // TODO: Determine signature of this method.
    fun taglessVariable(): Unit = TODO()
    fun variable(default: TemplateDsl.() -> Unit)

    fun list(content: TemplateDsl.() -> Unit)
    fun sexp(content: TemplateDsl.() -> Unit)
    fun struct(content: Fields.() -> Unit)

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
        fun templateBody(block: TemplateDsl.() -> Unit) = Template().apply(block).build()
    }

    protected val expressions = mutableListOf<TemplateExpression>()

    override fun annotated(annotations: Array<String?>, valueFn: () -> Unit) {
        expressions.add(TemplateExpression(TemplateExpression.Kind.ANNOTATIONS, null, annotations))
        valueFn.invoke()
    }

    override fun <T> annotated(annotations: Array<String?>, valueFn: KFunction1<T, Unit>, value: T) {
        expressions.add(TemplateExpression(TemplateExpression.Kind.ANNOTATIONS, null, annotations))
        valueFn.invoke(value)
    }

    override fun nullValue(value: IonType) = scalar(TemplateExpression.Kind.NULL, value)
    override fun bool(value: Boolean) = scalarPrimitive(TemplateExpression.Kind.BOOL, if (value) 1 else 0)
    override fun int(value: Long) = scalarPrimitive(TemplateExpression.Kind.INT, primitiveValue = value)
    override fun int(value: BigInteger) = scalar(TemplateExpression.Kind.INT, value)
    override fun float(value: Double) = scalarPrimitive(TemplateExpression.Kind.FLOAT, value.toRawBits())
    override fun decimal(value: Decimal) = scalar(TemplateExpression.Kind.DECIMAL, value)
    override fun timestamp(value: Timestamp) = scalar(TemplateExpression.Kind.TIMESTAMP, value)
    override fun symbol(value: String?) = scalar(TemplateExpression.Kind.SYMBOL, value)
    override fun string(value: String) = scalar(TemplateExpression.Kind.STRING, value)
    override fun clob(value: ByteArraySlice) = scalar(TemplateExpression.Kind.CLOB, value)
    override fun blob(value: ByteArraySlice) = scalar(TemplateExpression.Kind.BLOB, value)

    override fun fieldName(fieldName: String?) {
        expressions.add(TemplateExpression(TemplateExpression.Kind.FIELD_NAME, fieldName, emptyArray()))
    }

    fun build(): Array<TemplateExpression> = expressions.toTypedArray()


    protected fun <T> scalar(kind: Int, value: T) {
        expressions.add(TemplateExpression(kind, objectValue = value))
    }
    protected fun scalarPrimitive(kind: Int, primitiveValue: Long) {
        expressions.add(TemplateExpression(kind, primitiveValue = primitiveValue))
    }

    protected fun <T> container(kind: Int, content: T.() -> Unit) {
        val startExpression = TemplateExpression(kind)
        expressions.add(startExpression)
        val start = expressions.size
        (this as T).content()
        val end = expressions.size
        val childExpressions = expressions.subList(start, end)
        startExpression.childValues = childExpressions.toList().toTypedArray()
        childExpressions.clear()
    }


    // Subclasses for each expression variant so that we don't have conflicting signatures between their list, sexp, etc. implementations.

    class Template : ExpressionBuilderDsl(), TemplateDsl, TemplateDsl.Fields {

        override fun list(content: TemplateDsl.() -> Unit) = container(TemplateExpression.Kind.LIST, content)
        override fun sexp(content: TemplateDsl.() -> Unit) = container(TemplateExpression.Kind.SEXP, content)
        override fun struct(content: TemplateDsl.Fields.() -> Unit) = container(TemplateExpression.Kind.STRUCT, content)

        override fun variable() { expressions.add(TemplateExpression(TemplateExpression.Kind.VARIABLE)) }

        override fun variable(default: TemplateDsl.() -> Unit) = container(TemplateExpression.Kind.VARIABLE, content = default)
    }
}
