// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.Expression.*
import com.amazon.ion.impl.macro.Macro
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.math.exp
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
    fun clob(value: ByteArray) = clob(ByteBuffer.wrap(value))
    fun blob(value: ByteArray) = blob(ByteBuffer.wrap(value))
    fun clob(value: ByteBuffer)
    fun blob(value: ByteBuffer)

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
    fun macro(macro: MacroV2, arguments: TemplateDsl.() -> Unit)
    fun variable(name: String, signatureIndex: Int)

    fun variable(signatureIndex: Int)
    fun variable(name: String)

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
        fun templateBody(block: TemplateDsl.() -> Unit) = Template().apply(block).build()
        fun templateBody(signature: List<Macro.Parameter>, block: TemplateDsl.() -> Unit) = Template(signature).apply(block).build()
    }

    protected val expressions = mutableListOf<TemplateBodyExpressionModel>()
    private var pendingAnnotations = emptyArray<String?>()
    private var pendingFieldName: String? = null

    override fun <T> annotated(annotations: Array<String?>, valueFn: KFunction1<T, Unit>, value: T) {
        expressions.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.ANNOTATIONS, 0, null, annotations, null))
        // pendingAnnotations = annotations
        valueFn.invoke(value)
    }

    override fun nullValue(value: IonType) = scalar(TemplateBodyExpressionModel.Kind.NULL, value)
    override fun bool(value: Boolean) = scalarPrimitive(TemplateBodyExpressionModel.Kind.BOOL, if (value) 1 else 0)
    override fun int(value: Long) = scalarPrimitive(TemplateBodyExpressionModel.Kind.INT, primitiveValue = value)
    override fun int(value: BigInteger) = scalar(TemplateBodyExpressionModel.Kind.INT, value)
    override fun float(value: Double) = scalarPrimitive(TemplateBodyExpressionModel.Kind.FLOAT, value.toRawBits())
    override fun decimal(value: Decimal) = scalar(TemplateBodyExpressionModel.Kind.DECIMAL, value)
    override fun timestamp(value: Timestamp) = scalar(TemplateBodyExpressionModel.Kind.TIMESTAMP, value)
    override fun symbol(value: String?) = scalar(TemplateBodyExpressionModel.Kind.SYMBOL, value)
    override fun string(value: String) = scalar(TemplateBodyExpressionModel.Kind.STRING, value)
    override fun clob(value: ByteBuffer) = scalar(TemplateBodyExpressionModel.Kind.CLOB, value)
    override fun blob(value: ByteBuffer) = scalar(TemplateBodyExpressionModel.Kind.BLOB, value)

    override fun fieldName(fieldName: String?) {
        expressions.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.FIELD_NAME, 0, fieldName, emptyArray(), null))
//        pendingFieldName = fieldName
    }

    protected fun newStruct(annotations: Array<String?>, structStart: Int, structEndExclusive: Int): TemplateBodyExpressionModel {
        return TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.STRUCT, structEndExclusive - structStart, pendingFieldName, annotations, null)
    }

    fun build(): Array<TemplateBodyExpressionModel> = expressions.toTypedArray()

    // Helpers
    protected fun takePendingAnnotations(): Array<String?> {
        val ann = pendingAnnotations.also { pendingAnnotations = emptyArray() }
//        expressions.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.ANNOTATIONS, 1, null, ann, null))
        return emptyArray()
    }

    protected fun takePendingFieldName(): String? {
//        val fname = pendingFieldName.also { pendingFieldName = null }
//        expressions.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.FIELD_NAME, 1, fname, emptyArray(), null))
        return null
    }

    protected fun <T> scalar(kind: Int, value: T) {
        expressions.add(TemplateBodyExpressionModel(kind, 0, takePendingFieldName(), takePendingAnnotations(), value))
    }
    protected fun scalarPrimitive(kind: Int, primitiveValue: Long) {
        expressions.add(TemplateBodyExpressionModel(kind, 0, takePendingFieldName(), takePendingAnnotations(), primitiveValue = primitiveValue))
    }

    protected fun <T> container(kind: Int, content: T.() -> Unit) {
        val startExpression = TemplateBodyExpressionModel(kind, 0, takePendingFieldName(), takePendingAnnotations(), null)
        expressions.add(startExpression)
        val start = expressions.size
        (this as T).content()
        val end = expressions.size
        val childExpressions = expressions.subList(start, end)
        startExpression.value = childExpressions.toList().toTypedArray()
        childExpressions.clear()
    }

    // Subclasses for each expression variant so that we don't have conflicting signatures between their list, sexp, etc. implementations.

    class Template(val signature: List<Macro.Parameter>? = null) : ExpressionBuilderDsl(), TemplateDsl, TemplateDsl.Fields {

        override fun list(content: TemplateDsl.() -> Unit) = container(TemplateBodyExpressionModel.Kind.LIST, content)
        override fun sexp(content: TemplateDsl.() -> Unit) = container(TemplateBodyExpressionModel.Kind.SEXP, content)
        override fun struct(content: TemplateDsl.Fields.() -> Unit) = container(TemplateBodyExpressionModel.Kind.STRUCT, content)
        override fun macro(macro: MacroV2, arguments: TemplateDsl.() -> Unit) {
            val startExpression = TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.INVOCATION, 0, null, emptyArray(), macro)
            expressions.add(startExpression)
            val start = expressions.size
            this.arguments()
            val end = expressions.size
            val childExpressions = expressions.subList(start, end)
            startExpression.additionalValue = childExpressions.toList().toTypedArray()
            childExpressions.clear()
        }
        override fun expressionGroup(content: TemplateDsl.() -> Unit) = container(TemplateBodyExpressionModel.Kind.EXPRESSION_GROUP, content)

        override fun variable(name: String, signatureIndex: Int) {
            expressions.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.VARIABLE, 0, takePendingFieldName(), takePendingAnnotations(), name, primitiveValue = signatureIndex.toLong()))
        }

        override fun variable(name: String) {
            val signatureIndex = signature?.indexOfFirst { it.variableName == name } ?: -1
            if (signatureIndex < 0) TODO("Name not found in signature")
            variable(name, signatureIndex)
        }

        override fun variable(signatureIndex: Int) {
            val name = signature?.get(signatureIndex)?.variableName ?: TODO("Add a name")
            variable(name, signatureIndex)
        }
    }
}
