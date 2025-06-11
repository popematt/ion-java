package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.*
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.sign

abstract class TemplateReaderBase(
    @JvmField
    val pool: TemplateResourcePool,
    // TODO: consider flattening this
    @JvmField
    var info: TemplateResourcePool.TemplateInvocationInfo,
    @JvmField
    var startInclusive: Int,
    @JvmField
    var endExclusive: Int,
    @JvmField
    var isArgumentOwner: Boolean,
    // TODO: Can we manage the expansion limit through the template resource pool?
    @JvmField
    var expansionLimit: Int = 1_000_000,
    // Available modules? No. That's already resolved in the compiler.
    // Macro table? No. That's already resolved in the compiler.
): ValueReader, TemplateReader {

    @JvmField
    var i = startInclusive
    @JvmField
    var currentExpression: Expression? = null

    private var source = info.source

    fun init(
        templateInvocationInfo: TemplateResourcePool.TemplateInvocationInfo,
        startInclusive: Int,
        endExclusive: Int,
        isArgumentOwner: Boolean,
     ) {
        this.info = templateInvocationInfo
        this.startInclusive = startInclusive
        this.endExclusive = endExclusive
        this.isArgumentOwner = isArgumentOwner
        this.source = templateInvocationInfo.source
        i = startInclusive
        currentExpression = null
//        id = UUID.randomUUID().toString().take(8)
        reinitState()
    }

    protected open fun reinitState() {}

    protected fun <T: Expression, U> consumeCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (T) -> U, ): U {
        val expr = currentExpression
        currentExpression = null
        return when (expr!!.tokenType) {
            TokenTypeConst.VARIABLE_REF -> argMethod(info.arguments)
            else -> bodyMethod(expr as T)
//            else -> throw IonException("Not positioned on a ${T::class.simpleName}")
        }
    }

    protected fun <T: Expression, U> inspectCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (T) -> U, ): U {
        // Try class check
        val expr = currentExpression
        return when (expr!!.tokenType) {
            TokenTypeConst.VARIABLE_REF -> argMethod(info.arguments)
            else -> bodyMethod(expr as T)
//            else -> throw IonException("Not positioned on a ${T::class.simpleName}")
        }
    }

    override fun nextToken(): Int {
        var i = this.i
        if (i >= endExclusive) {
            return TokenTypeConst.END
        }
        var expr: Expression? = this.currentExpression
        if (expr == null) {
            expr = this.source[i]
            i++
            if (expr is Expression.HasStartAndEnd) i = expr.endExclusive
            this.i = i
            this.currentExpression = expr
        }
        if (expr is Expression.VariableRef) {
//            val args = this.info.arguments
//            args.seekToBeforeArgument(expr.signatureIndex)
//            return args.nextToken()
            return resolveVariableTokenType(expr.signatureIndex)
        }

        return expr.tokenType
    }

    private fun resolveVariableTokenType(v: Int): Int {

        val args = this.info.arguments
        args.seekToBeforeArgument(v)
        return args.nextToken()
    }

    final override fun close() {
        if (isArgumentOwner) {
            info.arguments.close()
        }
         returnToPool()
    }

    protected abstract fun returnToPool()

    // TODO: Make sure that this also returns END when at the end of the input.
    override fun currentToken(): Int {
        val expr = currentExpression ?: return TokenTypeConst.UNSET

        return if (expr is Expression.VariableRef) {
            info.arguments.currentToken()
        } else {
            expr.tokenType
        }
    }

    override fun isTokenSet(): Boolean = currentToken() != TokenTypeConst.UNSET

    // TODO: This will probably fail when the current token is unset.
    override fun ionType(): IonType? = inspectCurrentExpression(ArgumentReader::ionType, Expression.DataModelValue::type)

    override fun valueSize(): Int = inspectCurrentExpression<Expression.DataModelValue, _>(ArgumentReader::valueSize) {
        // This is mostly only used for Ints... and Lobs?
        return@inspectCurrentExpression when (val expr = currentExpression) {
            is Expression.LongIntValue -> 8
            is Expression.BigIntValue -> 9
            is Expression.LobValue -> expr.value.size
            else -> -1
        }
    }

    override fun skip() {
        currentExpression = null
    }

    override fun macroValue(): Macro = inspectCurrentExpression(ArgumentReader::macroValue, Expression.InvokableExpression::macro)

    override fun macroArguments(signature: List<Macro.Parameter>): ArgumentReader {
        val expr = currentExpression!!
        currentExpression = null
        return when (expr.tokenType) {
            TokenTypeConst.VARIABLE_REF -> info.arguments.macroArguments(signature)
            else -> {
                expr as Expression.InvokableExpression
                // FIXME: 10% of all
                pool.getArguments(info, signature, expr.startInclusive, expr.endExclusive)
            }
        }
    }

    override fun expressionGroup(): SequenceReader = consumeCurrentExpression(ArgumentReader::expressionGroup) {
            expr: Expression.ExpressionGroup -> pool.getSequence(info, expr.startInclusive, expr.endExclusive)
    }

    override fun nullValue(): IonType = consumeCurrentExpression(ArgumentReader::nullValue, Expression.NullValue::type)
    override fun booleanValue(): Boolean = consumeCurrentExpression(ArgumentReader::booleanValue, Expression.BoolValue::value)
    override fun longValue(): Long = consumeCurrentExpression(ArgumentReader::longValue, Expression.LongIntValue::value)
    override fun stringValue(): String = consumeCurrentExpression(ArgumentReader::stringValue, Expression.StringValue::value)

    override fun symbolValue(): String? = consumeCurrentExpression(
        { it.symbolValue() },
        { expr: Expression.SymbolValue -> expr.value.text }
    )

    override fun symbolValueSid(): Int {
        val sid = inspectCurrentExpression(
            { it.symbolValueSid() },
            // The text should have been resolved when the macro was compiled. Even if there is a SID,
            // it doesn't necessarily correspond to the current symbol table.
            { expr: Expression.SymbolValue -> -1 }
        )
        if (sid >= 0) {
            currentExpression = null
        }
        return sid
    }

    override fun lookupSid(sid: Int): String? = info.arguments.lookupSid(sid)

    override fun timestampValue(): Timestamp = consumeCurrentExpression(ArgumentReader::timestampValue, Expression.TimestampValue::value)

    // TODO: Make the lob values use a ByteBuffer?
    override fun clobValue(): ByteBuffer = consumeCurrentExpression(ArgumentReader::clobValue) {
        expr: Expression.ClobValue -> ByteBuffer.wrap(expr.value)
    }

    override fun blobValue(): ByteBuffer = consumeCurrentExpression(ArgumentReader::blobValue) {
            expr: Expression.BlobValue -> ByteBuffer.wrap(expr.value)
    }

    override fun listValue(): ListReader = consumeCurrentExpression(ArgumentReader::listValue) {
        expr: Expression.ListValue -> pool.getSequence(info, expr.startInclusive, expr.endExclusive)
    }

    override fun sexpValue(): SexpReader = consumeCurrentExpression(ArgumentReader::sexpValue) {
        expr: Expression.SExpValue -> pool.getSequence(info, expr.startInclusive, expr.endExclusive)
    }

    override fun structValue(): StructReader = consumeCurrentExpression(ArgumentReader::structValue) {
            expr: Expression.StructValue -> pool.getStruct(info, expr.startInclusive, expr.endExclusive)
    }

    override fun annotations(): AnnotationIterator = inspectCurrentExpression(ArgumentReader::annotations) {
        expr: Expression.DataModelValue -> pool.getAnnotations(expr.annotations)
    }

    override fun doubleValue(): Double = consumeCurrentExpression(ArgumentReader::doubleValue, Expression.FloatValue::value)
    override fun decimalValue(): Decimal = Decimal.valueOf(consumeCurrentExpression(ArgumentReader::decimalValue, Expression.DecimalValue::value))


    override fun getIonVersion(): Short = 0x0101

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")
    override fun seekTo(position: Int) = TODO("This method only applies to readers that support IVMs.")
    override fun position(): Int  = TODO("This method only applies to readers that support IVMs.")
}
