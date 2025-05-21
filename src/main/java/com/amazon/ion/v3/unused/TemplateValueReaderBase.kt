package com.amazon.ion.v3.unused

import com.amazon.ion.Decimal
import com.amazon.ion.IonException
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.ListReader
import com.amazon.ion.v3.SequenceReader
import com.amazon.ion.v3.SexpReader
import com.amazon.ion.v3.StructReader
import com.amazon.ion.v3.TokenTypeConst
import com.amazon.ion.v3.ValueReader
import com.amazon.ion.v3.impl_1_1.template.TemplateResourcePool
import java.nio.ByteBuffer

/**
 * This attempts to abstract the variable resolution away from consumers so that it's
 * all handled within the reader. This appears to be a bit of a dead-end approach because
 * of the complexity involved with a variable resolving to more than one Token—specifically
 * because of annotations.
 *
 * If we make it so that `FIELD_NAME` and `ANNOTATIONS` are not separate tokens, then
 * this becomes much easier in many ways, and it simplifies other edge cases, such
 * as a field name followed by a `NOP`.
 */
abstract class TemplateValueReaderBase(
    val pool: TemplateResourcePool,
    // TODO: consider flattening this
    var info: TemplateResourcePool.TemplateInvocationInfo,
    var startInclusive: Int,
    var endExclusive: Int,
    // TODO: Can we manage the expansion limit through the template resource pool?
    var expansionLimit: Int = 1_000_000,
    // Available modules? No. That's already resolved in the compiler.
    // Macro table? No. That's already resolved in the compiler.
): ValueReader, SequenceReader {

    var i = 0
    var currentExpression: Expression? = null
    var currentArgumentIndex: Int = -1

    fun init(
        templateInvocationInfo: TemplateResourcePool.TemplateInvocationInfo,
        startInclusive: Int,
        endExclusive: Int,
    ) {
        this.info = templateInvocationInfo
        this.startInclusive = startInclusive
        this.endExclusive = endExclusive
        i = 0
        currentExpression = null
    }

    override fun nextToken(): Int {
        if (i >= endExclusive) {
            return TokenTypeConst.END
        }
        if (currentExpression == null && currentArgumentIndex < 0) {
            val expr = info.source[i++]
            if (expr is Expression.HasStartAndEnd) i = expr.endExclusive
            currentExpression = expr
        }
        if (currentExpression is Expression.VariableRef) {
            currentArgumentIndex = takeCurrentExpression<Expression.VariableRef>().signatureIndex
            val token = info.arguments.seekToArgument(currentArgumentIndex)

            if (token == TokenTypeConst.ANNOTATIONS) {
                TODO("slurp the annotations up into an AnnotationsExpression.")
            } else {
                // Set up the value to be read?
            }
            return token
        } else {
            return currentExpression!!.tokenType
        }
    }

    // TODO: Make sure that this also returns END when at the end of the input.
    override fun currentToken(): Int = currentExpression?.tokenType ?: TokenTypeConst.UNSET
    override fun ionType(): IonType? = (currentExpression as? Expression.DataModelValue)?.type

    override fun valueSize(): Int {
        // This is mostly only used for Ints.
        when (currentExpression) {
            is Expression.LongIntValue -> return 8
            is Expression.BigIntValue -> return 9
            else -> return -1
        }
    }

    override fun skip() {
        currentExpression = null
    }

    fun variableValue(): ValueReader {
        val expr = takeCurrentExpression<Expression.VariableRef>()
        info.arguments.seekToArgument(expr.signatureIndex)
        info.arguments.seekTo(info.arguments.position() - 1)
        return info.arguments
    }

    private inline fun <reified T: Expression> takeCurrentExpression(): T {
        val expr = currentExpression
        currentExpression = null
        if (expr is T) {
            return expr
        } else {
            throw IonException("Not positioned on a ${T::class.simpleName}")
        }
    }

    override fun nullValue(): IonType = takeCurrentExpression<Expression.NullValue>().type
    override fun booleanValue(): Boolean = takeCurrentExpression<Expression.BoolValue>().value
    override fun longValue(): Long = takeCurrentExpression<Expression.LongIntValue>().value
    override fun stringValue(): String = takeCurrentExpression<Expression.StringValue>().value
    override fun symbolValue(): String? = takeCurrentExpression<Expression.SymbolValue>().value.text

    override fun symbolValueSid(): Int {
        val expr = takeCurrentExpression<Expression.SymbolValue>()
        if (expr.value.sid < 0) {
            currentExpression = expr
            return -1
        } else {
            return expr.value.sid
        }
    }

    override fun lookupSid(sid: Int): String? {
        TODO("Macro evaluator has only resolved symbol text")
    }

    override fun timestampValue(): Timestamp = takeCurrentExpression<Expression.TimestampValue>().value

    // TODO: Make the lob values use a ByteBuffer?
    override fun clobValue(): ByteBuffer = ByteBuffer.wrap(takeCurrentExpression<Expression.ClobValue>().value)
    override fun blobValue(): ByteBuffer = ByteBuffer.wrap(takeCurrentExpression<Expression.BlobValue>().value)

    override fun listValue(): ListReader {
        val expr = takeCurrentExpression<Expression.ListValue>()
        return pool.getSequence(info, expr.startInclusive, expr.endExclusive)
    }

    override fun sexpValue(): SexpReader {
        val expr = takeCurrentExpression<Expression.SExpValue>()
        return pool.getSequence(info, expr.startInclusive, expr.endExclusive)
    }

    override fun structValue(): StructReader {
        val expr = takeCurrentExpression<Expression.StructValue>()
        return pool.getStruct(info, expr.startInclusive, expr.endExclusive)
    }

    override fun annotations(): AnnotationIterator {
        // Actually, we just need synergy between `Expression` and `TokenType`.
        TODO("Annotations should be their own expression type.")
    }

    override fun doubleValue(): Double = takeCurrentExpression<Expression.FloatValue>().value
    override fun decimalValue(): Decimal = Decimal.valueOf(takeCurrentExpression<Expression.DecimalValue>().value)

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")

    override fun getIonVersion(): Short = 0x0101

    override fun seekTo(position: Int) = TODO("This method only applies to raw readers.")
    override fun position(): Int  = TODO("This method only applies to raw readers.")
}
