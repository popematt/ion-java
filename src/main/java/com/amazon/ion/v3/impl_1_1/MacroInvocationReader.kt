package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.Expression.*
import com.amazon.ion.v3.*
import java.nio.ByteBuffer

class MacroInvocationReader(
    val pool: TemplateResourcePool,
    // TODO: consider flattening this
    var info: TemplateResourcePool.TemplateInvocationInfo,
    var startInclusive: Int,
    var endExclusive: Int,
    // TODO: Can we manage the expansion limit through the template resource pool?
    var expansionLimit: Int = 1_000_000,
    // Available modules? No. That's already resolved in the compiler.
    // Macro table? No. That's already resolved in the compiler.
): ValueReader, SequenceReader, TemplateReader {

    var i = 0
    var currentExpression: Expression? = null

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
        if (currentExpression == null) {
            val expr = info.source[i++]
            if (expr is HasStartAndEnd) i = expr.endExclusive
            currentExpression = expr
        }
        return currentExpression!!.tokenType
    }

    // TODO: Make sure that this also returns END when at the end of the input.
    override fun currentToken(): Int = currentExpression?.tokenType ?: TokenTypeConst.UNSET

    override fun isTokenSet(): Boolean = currentToken() != TokenTypeConst.UNSET

    override fun ionType(): IonType? = (currentExpression as? DataModelValue)?.type

    override fun valueSize(): Int {
        // This is mostly only used for Ints.
        when (currentExpression) {
            is LongIntValue -> return 8
            is BigIntValue -> return 9
            else -> return -1
        }
    }

    override fun skip() {
        currentExpression = null
    }

    override fun variableValue(): ValueReader {
        val expr = takeCurrentExpression<VariableRef>()
        return pool.getVariable(info.arguments, expr.signatureIndex)
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

    override fun nullValue(): IonType = takeCurrentExpression<NullValue>().type
    override fun booleanValue(): Boolean = takeCurrentExpression<BoolValue>().value
    override fun longValue(): Long = takeCurrentExpression<LongIntValue>().value
    override fun stringValue(): String = takeCurrentExpression<StringValue>().value
    override fun symbolValue(): String? = takeCurrentExpression<SymbolValue>().value.text

    override fun symbolValueSid(): Int {
//        val expr = takeCurrentExpression<SymbolValue>()
//        if (expr.value.sid < 0) {
//            currentExpression = expr
//            return -1
//        } else {
//            return expr.value.sid
//        }
        return -1
    }

    override fun lookupSid(sid: Int): String? {
        TODO("Macro evaluator has only resolved symbol text")
        // return pool.symbolTable[sid]
    }

    override fun timestampValue(): Timestamp = takeCurrentExpression<TimestampValue>().value

    // TODO: Make the lob values use a ByteBuffer?
    override fun clobValue(): ByteBuffer = ByteBuffer.wrap(takeCurrentExpression<ClobValue>().value)
    override fun blobValue(): ByteBuffer = ByteBuffer.wrap(takeCurrentExpression<BlobValue>().value)

    override fun listValue(): ListReader {
        val expr = takeCurrentExpression<ListValue>()
        return pool.getSequence(info, expr.startInclusive, expr.endExclusive)
    }

    override fun sexpValue(): SexpReader {
        val expr = takeCurrentExpression<SExpValue>()
        return pool.getSequence(info, expr.startInclusive, expr.endExclusive)
    }

    override fun structValue(): StructReader {
        val expr = takeCurrentExpression<StructValue>()
        return pool.getStruct(info, expr.startInclusive, expr.endExclusive)
    }

    override fun macroValue(): Macro {
        val expr = (currentExpression as MacroInvocation)
        val arguments = pool.arguments.removeLastOrNull()
            ?.apply { init(info, expr.startInclusive, expr.endExclusive) }
            ?: TemplateArgumentReaderImpl(pool, info, expr.startInclusive, expr.endExclusive)

        return expr.macro
    }

    override fun eexpArgs(signature: List<Macro.Parameter>): ArgumentReader {
        return super.eexpArgs(signature)
    }

    override fun annotations(): AnnotationIterator {
        TODO("Annotations should be their own expression type.")
    }

    override fun doubleValue(): Double = takeCurrentExpression<FloatValue>().value
    override fun decimalValue(): Decimal = Decimal.valueOf(takeCurrentExpression<DecimalValue>().value)

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")

    override fun getIonVersion(): Short = 0x0101

    override fun seekTo(position: Int) = TODO("This method only applies to raw readers.")
    override fun position(): Int  = TODO("This method only applies to raw readers.")

    override fun close() {
        pool.invocations.add(this)
    }
}
