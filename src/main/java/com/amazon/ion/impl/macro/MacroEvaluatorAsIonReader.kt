// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl._Private_Utils.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

/**
 * This class is an example of how we might wrap the macro evaluator's [Expression] model, adapting it to an [IonReader].
 *
 * TODO:
 *   - Consider merging this with [MacroEvaluator].
 *   - Error handling is inconsistent with other [IonReader] implementations
 *   - Testing
 */
class MacroEvaluatorAsIonReader(
    private val evaluator: MacroEvaluator,
) : IonReader {

    private class ContainerInfo {
        @JvmField var currentFieldName: ExpressionA? = null
        @JvmField var container: ExpressionA? = null
    }
    private val containerStack = _Private_RecyclingStack(8) { ContainerInfo() }

    private var currentFieldName: ExpressionA? = null
    private var currentValueExpression: ExpressionA? = null

    private var queuedFieldName: ExpressionA? = null
    private var queuedValueExpression: ExpressionA? = null

    private fun queueNext() {
        queuedValueExpression = null
        while (queuedValueExpression == null) {
            val nextCandidate = evaluator.expandNext()
            when (nextCandidate?.kind) {
                null -> {
                    // Field name is cleared when reaching the end of the struct (or when being replaced with a new field name)
                    queuedFieldName = null
                    return
                }
                // TODO: Should we assert that there are no queued annotations when we encounter a field name?
                ExpressionKind.FieldName -> queuedFieldName = nextCandidate
                else -> queuedValueExpression = nextCandidate
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun hasNext(): Boolean {
        if (queuedValueExpression == null) queueNext()
        return queuedValueExpression != null
    }

    override fun next(): IonType? {
        if (!hasNext()) {
            currentValueExpression = null
            return null
        }
        currentValueExpression = queuedValueExpression
        currentFieldName = queuedFieldName
        queuedValueExpression = null
        return getType()
    }

    /**
     * Transcodes the e-expression argument expressions provided to this MacroEvaluator
     * without evaluation.
     * @param writer the writer to which the expressions will be transcoded.
     */
    fun transcodeArgumentsTo(writer: MacroAwareIonWriter) {
        var index = 0
        val arguments: List<ExpressionA> = evaluator.getArguments()
        val numberOfContainerEndsAtExpressionIndex = IntArray(arguments.size + 1)

        currentFieldName = null // Field names are written only via FieldName expressions

        while (index < arguments.size) {
            for (i in 0 until numberOfContainerEndsAtExpressionIndex[index]) {
                writer.stepOut()
            }
            val argument = arguments[index]
            when (argument.kind) {
                ExpressionKind.FieldName -> writer.setFieldNameSymbol(argument.dataAsSymbolToken())
                ExpressionKind.EExpression -> {
                    writer.startMacro(argument.value as Macro)
                    numberOfContainerEndsAtExpressionIndex[argument.endExclusive]++
                }
                ExpressionKind.ExpressionGroup -> {
                    writer.startExpressionGroup()
                    numberOfContainerEndsAtExpressionIndex[argument.endExclusive]++
                }
                else -> {
                    if (argument.kind.isDataModelContainer()) {
                        if (hasAnnotations()) {
                            writer.setTypeAnnotationSymbols(*typeAnnotationSymbols!!)
                        }
                        writer.stepIn(argument.ionType)
                        numberOfContainerEndsAtExpressionIndex[argument.endExclusive]++
                    } else if (argument.kind.isDataModelValue()) {
                        if (hasAnnotations()) {
                            writer.setTypeAnnotationSymbols(*typeAnnotationSymbols!!)
                        }
                        currentValueExpression = argument
                        writer.writeValue(this)
                    } else {
                        throw IllegalStateException("Unexpected branch")
                    }
                }
            }
            index++
        }
        for (i in 0 until numberOfContainerEndsAtExpressionIndex[index]) {
            writer.stepOut()
        }
    }

    override fun stepIn() {
        // This is essentially a no-op for Lists and SExps
        containerStack.peek()?.currentFieldName = this.currentFieldName

        val containerToStepInto = currentValueExpression
        evaluator.stepIn()
        containerStack.push {
            it.container = containerToStepInto
            it.currentFieldName = null
        }
        currentFieldName = null
        currentValueExpression = null
        queuedFieldName = null
        queuedValueExpression = null
    }

    override fun stepOut() {
        evaluator.stepOut()
        containerStack.pop()
        // This is essentially a no-op for Lists and SExps
        currentFieldName = containerStack.peek()?.currentFieldName
        currentValueExpression = null // Must call `next()` to get the next value
        queuedFieldName = null
        queuedValueExpression = null
    }

    override fun close() { /* Nothing to do (yet) */ }
    override fun <T : Any?> asFacet(facetType: Class<T>?): Nothing? = null
    override fun getDepth(): Int = containerStack.size()
    override fun getSymbolTable(): SymbolTable? = null

    override fun getType(): IonType? = currentValueExpression?.ionType

    fun hasAnnotations(): Boolean = currentValueExpression != null && currentValueExpression!!.annotations.isNotEmpty()

    override fun getTypeAnnotations(): Array<String>? = currentValueExpression?.annotations?.let { Array(it.size) { i -> it[i].assumeText() } }
    override fun getTypeAnnotationSymbols(): Array<SymbolToken>? = currentValueExpression?.annotations?.toTypedArray()

    private class SymbolTokenAsStringIterator(val tokens: List<SymbolToken>) : MutableIterator<String> {

        var index = 0

        override fun hasNext(): Boolean {
            return index < tokens.size
        }

        override fun next(): String {
            if (index >= tokens.size) {
                throw NoSuchElementException()
            }
            return tokens[index++].assumeText()
        }

        override fun remove() {
            throw UnsupportedOperationException("This iterator does not support removal")
        }
    }

    override fun iterateTypeAnnotations(): MutableIterator<String> {
        return if (currentValueExpression?.annotations?.isNotEmpty() == true) {
            SymbolTokenAsStringIterator(currentValueExpression!!.annotations)
        } else {
            Collections.emptyIterator()
        }
    }

    override fun isInStruct(): Boolean = containerStack.peek()?.container?.ionType == IonType.STRUCT

    override fun getFieldId(): Int = currentFieldName?.value?.let { it as? SymbolToken }?.sid ?: 0
    override fun getFieldName(): String? = currentFieldName?.value?.let {
        when (it) {
            is SymbolToken -> it.text
            else -> it as String?
        }
    }
    override fun getFieldNameSymbol(): SymbolToken? = currentFieldName?.value?.let {
        when (it) {
            is String -> newSymbolToken(it)
            else -> it as SymbolToken?
        }
    }
    override fun isNullValue(): Boolean = currentValueExpression!!.kind == ExpressionKind.NullValue
    override fun booleanValue(): Boolean = currentValueExpression!!.value as Boolean

    override fun getIntegerSize(): IntegerSize {
        // TODO: Make this more efficient, more precise
        val intData = currentValueExpression?.value
        return when (intData) {
            is Long -> if (intData.toInt().toLong() == intData) {
                IntegerSize.INT
            } else {
                IntegerSize.LONG
            }
            is BigInteger -> IntegerSize.BIG_INTEGER
            else -> throw IonException("Expected a non-null int value; found ${currentValueExpression?.ionType}")
        }
    }

    /** TODO: Throw on data loss */
    override fun intValue(): Int = longValue().toInt()

    override fun longValue(): Long {
        val intData = currentValueExpression?.value
        return when (intData) {
            is Long -> intData
            is BigInteger -> intData.longValueExact()
            else -> throw IonException("Expected a non-null int value; found ${currentValueExpression?.ionType}")
        }
    }

    override fun bigIntegerValue(): BigInteger {
        val intData = currentValueExpression?.value
        return when (intData) {
            is Long -> intData.toBigInteger()
            is BigInteger -> intData
            else -> throw IonException("Expected a non-null int value; found ${currentValueExpression?.ionType}")
        }
    }

    override fun doubleValue(): Double = currentValueExpression?.value as Double
    override fun bigDecimalValue(): BigDecimal = currentValueExpression?.value as BigDecimal
    override fun decimalValue(): Decimal = Decimal.valueOf(bigDecimalValue())
    override fun timestampValue(): Timestamp = currentValueExpression?.value as Timestamp
    override fun dateValue(): Date = timestampValue().dateValue()
    override fun stringValue(): String = currentValueExpression?.value.let {
        when (it) {
            is String -> it
            is SymbolToken -> it.assumeText()
            null -> it!!
            else -> throw IonException("Expected a text value; found ${currentValueExpression?.ionType}")
        }
    }
    override fun symbolValue(): SymbolToken {
        if (currentValueExpression?.kind != ExpressionKind.SymbolValue) {
            throw IonException("Expected a symbol value; found ${currentValueExpression?.ionType}")
        }
        return when (val symbol = currentValueExpression?.value) {
            is String -> newSymbolToken(symbol)
            else -> symbol as SymbolToken
        }
    }
    override fun byteSize(): Int = (currentValueExpression?.value as ByteArray).size
    override fun newBytes(): ByteArray = (currentValueExpression?.value as ByteArray).copyOf()

    override fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int {
        TODO("Not yet implemented")
    }
}
