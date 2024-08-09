package com.amazon.ion.impl.macro

import com.amazon.ion.Decimal
import com.amazon.ion.IntegerSize
import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.SymbolTable
import com.amazon.ion.SymbolToken
import com.amazon.ion.Timestamp
import com.amazon.ion.impl._Private_RecyclingStack
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

/**
 * This class wraps the macro evaluator's [Expression] model, adapting it to an [IonReader]
 *
 * TODO: Consider merging this with [MacroEvaluator]
 */
class MacroEvaluatorAsIonReader(
    private val evaluator: MacroEvaluator,
): IonReader {

    private class ContainerInfo {
        @JvmField var currentFieldName: Expression.FieldName? = null
        @JvmField var container: Expression.Container? = null
    }
    private val containerStack = _Private_RecyclingStack(8) { ContainerInfo() }

    private var currentFieldName: Expression.FieldName? = null
    private var currentExpression: Expression? = null

    private var queuedFieldName: Expression.FieldName? = null
    private var queuedExpression: Expression? = null

    private fun queueNext() {
        queuedExpression = null
        while (queuedExpression == null) {
            val nextCandidate = evaluator.expandNext() ?: return
            when (nextCandidate) {
                is Expression.FieldName -> queuedFieldName = nextCandidate
                else -> queuedExpression = nextCandidate
            }
        }
    }

    override fun hasNext(): Boolean {
        if (queuedExpression == null) queueNext()
        return queuedExpression != null
    }

    override fun next(): IonType? {
        if (!hasNext()) return null
        currentExpression = queuedExpression
        currentFieldName = queuedFieldName
        queuedExpression = null
        return getType()
    }

    override fun stepIn() {
        // This is essentially a no-op for Lists and SExps
        containerStack.peek().currentFieldName = this.currentFieldName

        val containerToStepInto = currentExpression
        evaluator.stepIn()
        containerStack.push {
            it.container = containerToStepInto as Expression.Container
            it.currentFieldName = null
        }
    }

    override fun stepOut() {
        evaluator.stepOut()
        containerStack.pop()
        // This is essentially a no-op for Lists and SExps
        currentFieldName = containerStack.peek().currentFieldName
    }

    override fun close() { TODO("Not yet implemented") }
    override fun <T : Any?> asFacet(facetType: Class<T>?): Nothing = TODO("Not supported")
    override fun getDepth(): Int = containerStack.size()
    override fun getSymbolTable(): SymbolTable = TODO("Not implemented in this abstraction")

    override fun getType(): IonType? = currentExpression?.type

    override fun getTypeAnnotations(): Array<String>? = currentExpression?.annotations?.let { Array(it.size) { i -> it[i].assumeText() } }
    override fun getTypeAnnotationSymbols(): Array<SymbolToken>? = currentExpression?.annotations?.toTypedArray()
    // TODO: Make this into an iterator that unwraps the SymbolTokens as it goes instead of allocating a new list
    override fun iterateTypeAnnotations(): MutableIterator<String>? = currentExpression?.annotations?.mapTo(mutableListOf()) { it.assumeText() }?.iterator()

    override fun isInStruct(): Boolean = TODO("Not yet implemented")

    override fun getFieldId(): Int = currentFieldName?.value?.sid ?: 0
    override fun getFieldName(): String? = currentFieldName?.value?.text
    override fun getFieldNameSymbol(): SymbolToken? = currentFieldName?.value

    override fun isNullValue(): Boolean = currentExpression is Expression.NullValue
    override fun booleanValue(): Boolean = (currentExpression as Expression.BoolValue).value

    override fun getIntegerSize(): IntegerSize {
        // TODO: Make this more efficient, more precise
        return when (val intExpression = currentExpression as Expression.IntValue) {
            is Expression.LongIntValue -> if (intExpression.value.toInt().toLong() == intExpression.value) {
                IntegerSize.INT
            } else {
                IntegerSize.LONG
            }
            is Expression.BigIntValue -> IntegerSize.BIG_INTEGER
        }
    }

    /** TODO: Throw on data loss */
    override fun intValue(): Int = longValue().toInt()

    override fun longValue(): Long = when (val intExpression = currentExpression as Expression.IntValue) {
        is Expression.LongIntValue -> intExpression.value
        is Expression.BigIntValue -> intExpression.value.longValueExact()
    }

    override fun bigIntegerValue(): BigInteger = when (val intExpression = currentExpression as Expression.IntValue) {
        is Expression.LongIntValue -> intExpression.value.toBigInteger()
        is Expression.BigIntValue -> intExpression.value
    }

    override fun doubleValue(): Double = (currentExpression as Expression.FloatValue).value
    override fun bigDecimalValue(): BigDecimal = (currentExpression as Expression.DecimalValue).value
    override fun decimalValue(): Decimal = Decimal.valueOf(bigDecimalValue())
    override fun timestampValue(): Timestamp = (currentExpression as Expression.TimestampValue).value
    override fun dateValue(): Date = timestampValue().dateValue()
    override fun stringValue(): String = (currentExpression as Expression.TextValue).stringValue
    override fun symbolValue(): SymbolToken = (currentExpression as Expression.SymbolValue).value
    override fun byteSize(): Int = (currentExpression as Expression.LobValue).value.size
    override fun newBytes(): ByteArray = (currentExpression as Expression.LobValue).value.copyOf()

    override fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int {
        TODO("Not yet implemented")
    }
}