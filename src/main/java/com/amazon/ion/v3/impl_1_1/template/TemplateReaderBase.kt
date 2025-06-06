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
    val pool: TemplateResourcePool,
    // TODO: consider flattening this
    var info: TemplateResourcePool.TemplateInvocationInfo,
    var startInclusive: Int,
    var endExclusive: Int,
    var isArgumentOwner: Boolean,
    // TODO: Can we manage the expansion limit through the template resource pool?
    var expansionLimit: Int = 1_000_000,
    // Available modules? No. That's already resolved in the compiler.
    // Macro table? No. That's already resolved in the compiler.
): ValueReader, TemplateReader {

    var i = startInclusive
    var currentExpression: Expression? = null

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
        i = startInclusive
        currentExpression = null
//        id = UUID.randomUUID().toString().take(8)
        reinitState()
    }

    protected open fun reinitState() {}


    internal var id = "" //UUID.randomUUID().toString().take(8)



    protected inline fun <reified T: Expression, U> consumeCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (T) -> U, ): U {
        val result = inspectCurrentExpression(argMethod, bodyMethod)
        // if (currentExpression !is Expression.VariableRef) {
            currentExpression = null
        //}
        return result
    }

    protected inline fun <reified T: Expression, U> inspectCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (T) -> U, ): U {
        val expr = currentExpression
        return when (expr) {
            is Expression.VariableRef -> argMethod(info.arguments)
            is T -> bodyMethod(expr)
            else -> throw IonException("Not positioned on a ${T::class.simpleName}")
        }
    }

    override fun nextToken(): Int {
        var expr: Expression? = this.currentExpression
        if (i >= endExclusive) {
            return TokenTypeConst.END
        }
        if (expr == null) {
            expr = info.source[i++]
//            if (Debug.enabled) println("[$id ${this::class.simpleName}] At position ${i}, read expression $expr")
            if (expr is Expression.HasStartAndEnd) i = expr.endExclusive
            currentExpression = expr
        }
        if (expr is Expression.VariableRef) {
            val childArgId = when (val args = info.arguments) {
                is TemplateReaderBase -> args.id
                is EExpArgumentReaderImpl -> args.id
                else -> TODO()
            }
//            println("[$id] nextToken() seeking in ${childArgId} for child argument: ${expr.signatureIndex}")
//            println(info.signature)
//            println(info.arguments.signature)
//
//            if (info.arguments.signature.size <= expr.signatureIndex) {
//                println("Dumping Info: ($i)")
//                println(info.signature)
//                println(info.source.joinToString("\n  ", prefix = "  "))
//                println(info.arguments.signature)
//            }

            info.arguments.seekToBeforeArgument(expr.signatureIndex)
            return info.arguments.nextToken()
        }

        return expr.tokenType
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
        return when (val expr = currentExpression) {
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
        return consumeCurrentExpression(
            {
//                println("Getting arguments from variable: $signature")
                it.macroArguments(signature) },
            { expr : Expression.InvokableExpression ->
//                println("Getting arguments from template: ${signature}")
                pool.getArguments(info, signature, expr.startInclusive, expr.endExclusive) }
        )
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
