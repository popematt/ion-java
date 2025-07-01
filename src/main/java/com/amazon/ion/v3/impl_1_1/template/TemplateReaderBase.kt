package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.*
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

abstract class TemplateReaderBase(
    @JvmField
    val pool: TemplateResourcePool,
    // TODO: consider flattening this
    @JvmField
    var source: Array<TemplateBodyExpressionModel>,
    @JvmField
    var arguments: ArgumentReader,
    @JvmField
    var isArgumentOwner: Boolean,
    // TODO: Can we manage the expansion limit through the template resource pool?
    @JvmField
    var expansionLimit: Int = 1_000_000,
    // Available modules? No. That's already resolved in the compiler.
    // Macro table? No. That's already resolved in the compiler.
): ValueReader, TemplateReader {

    @JvmField
    var i = 0
    @JvmField
    var currentExpression: TemplateBodyExpressionModel? = null
    @JvmField
    var endExclusive: Int = source.size

    /**
     * Helps to simulate encountering the field name, annotations, and value as separate tokens.
     * Invariants:
     */
    @JvmField
    var state: Byte = State.READY
    protected object State {
        val READY: Byte = 0
        val SEEN_FIELD_NAME: Byte = 1
        val SEEN_ANNOTATIONS: Byte = 2
        val SEEN_VALUE: Byte = 3
    }

    fun init(
        source: Array<TemplateBodyExpressionModel>,
        arguments: ArgumentReader,
        isArgumentOwner: Boolean,
     ) {
        this.isArgumentOwner = isArgumentOwner
        this.source = source
        this.endExclusive = source.size
        this.arguments = arguments
        i = 0
        currentExpression = null
        state = State.READY
        reinitState()
    }

    protected open fun reinitState() {}

    protected inline fun <U> consumeCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (TemplateBodyExpressionModel) -> U, ): U {
        val expr = currentExpression
        currentExpression = null
        return when (expr!!.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> argMethod(arguments)
            else -> bodyMethod(expr)
        }
    }

    protected inline fun <U> inspectCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (TemplateBodyExpressionModel) -> U, ): U {
        val expr = currentExpression
        return when (expr!!.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> argMethod(arguments)
            else -> bodyMethod(expr)
        }
    }

    override fun nextToken(): Int {
        if (i >= endExclusive) {
            return TokenTypeConst.END
        }
        val expr = this.source[i++]
        this.currentExpression = expr
        // this.i += expr.length

        if (expr.expressionKind == TokenTypeConst.VARIABLE_REF) {
            val args = this.arguments
            val v = expr.primitiveValue.toInt()
            return args.seekToArgument(v)
        }

        return expr.expressionKind
    }

    final override fun close() {
        if (isArgumentOwner) {
            arguments.close()
        }
        returnToPool()
    }

    protected abstract fun returnToPool()

    // TODO: Make sure that this also returns END when at the end of the input.
    override fun currentToken(): Int {
        val expr = currentExpression ?: return TokenTypeConst.UNSET

        return if (expr.expressionKind == TokenTypeConst.VARIABLE_REF) {
            arguments.currentToken()
        } else {
            expr.expressionKind
        }
    }

    override fun isTokenSet(): Boolean = currentToken() != TokenTypeConst.UNSET

    // TODO: This will probably fail when the current token is unset.
    override fun ionType(): IonType? = inspectCurrentExpression(ArgumentReader::ionType) {
        if (it.expressionKind == TokenTypeConst.NULL) {
            it.value as IonType
        } else {
            IonType.entries[it.expressionKind]
        }
    }

    override fun valueSize(): Int = inspectCurrentExpression(ArgumentReader::valueSize) {
        // This is mostly only used for Ints... and Lobs?
        return@inspectCurrentExpression when (val value = currentExpression?.value) {
            is Long -> 8
            is BigInteger -> 9
            is ByteBuffer -> value.remaining()
            else -> -1
        }
    }

    override fun skip() {
        currentExpression = null
    }

    override fun macroValue(): MacroV2 = inspectCurrentExpression(ArgumentReader::macroValue) { it.value as MacroV2 }

    override fun macroArguments(signature: Array<Macro.Parameter>): ArgumentReader {
        if (signature.isEmpty()) {
            return NoneReader
        }

        val expr = currentExpression!!
        currentExpression = null
        return when (expr.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> arguments.macroArguments(signature)
            else -> {
                pool.getArguments(arguments, signature, expr.additionalValue as Array<TemplateBodyExpressionModel>)
            }
        }
    }

    override fun expressionGroup(): SequenceReader = consumeCurrentExpression(ArgumentReader::expressionGroup) {
        pool.getSequence(arguments, it.value as Array<TemplateBodyExpressionModel>)
    }

    override fun nullValue(): IonType = consumeCurrentExpression(ArgumentReader::nullValue) { it.value as IonType }
    override fun booleanValue(): Boolean = consumeCurrentExpression(ArgumentReader::booleanValue) { it.primitiveValue > 0 }
    // TODO: Consider checking to make sure that we have a long, rather than a big integer.
    override fun longValue(): Long = consumeCurrentExpression(ArgumentReader::longValue) { it.primitiveValue }
    override fun stringValue(): String = consumeCurrentExpression(ArgumentReader::stringValue) { it.value as String }
    override fun symbolValue(): String? = consumeCurrentExpression(ArgumentReader::symbolValue) { it.value as String? }

    override fun symbolValueSid(): Int {
        val sid = inspectCurrentExpression(
            { it.symbolValueSid() },
            // The text should have been resolved when the macro was compiled. Even if there is a SID,
            // it doesn't necessarily correspond to the current symbol table.
            { -1 }
        )
        if (sid >= 0) {
            currentExpression = null
        }
        return sid
    }

    override fun lookupSid(sid: Int): String? = arguments.lookupSid(sid)

    override fun timestampValue(): Timestamp = consumeCurrentExpression(ArgumentReader::timestampValue) { it.value as Timestamp }
    override fun clobValue(): ByteBuffer = consumeCurrentExpression(ArgumentReader::clobValue) { it.value as ByteBuffer }
    override fun blobValue(): ByteBuffer = consumeCurrentExpression(ArgumentReader::blobValue) { it.value as ByteBuffer }

    override fun listValue(): ListReader = consumeCurrentExpression(ArgumentReader::listValue) {
        pool.getSequence(arguments, it.value as Array<TemplateBodyExpressionModel>)
    }

    override fun sexpValue(): SexpReader = consumeCurrentExpression(ArgumentReader::sexpValue) {
        pool.getSequence(arguments, it.value as Array<TemplateBodyExpressionModel>)
    }

    override fun structValue(): StructReader {
        val expr = currentExpression
        currentExpression = null
        return when (expr!!.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> arguments.structValue()
            else -> {
                pool.getStruct(arguments, expr.value as Array<TemplateBodyExpressionModel>)
            }
        }
    }

    override fun annotations(): AnnotationIterator = inspectCurrentExpression(ArgumentReader::annotations) {
        expr -> pool.getAnnotations(expr.annotations)
    }

    override fun doubleValue(): Double = consumeCurrentExpression(ArgumentReader::doubleValue) { Double.fromBits(it.primitiveValue) }

    override fun decimalValue(): Decimal = Decimal.valueOf(consumeCurrentExpression(ArgumentReader::decimalValue) { it.value as BigDecimal })

    override fun getIonVersion(): Short = 0x0101

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")
    override fun seekTo(position: Int) = TODO("This method only applies to readers that support IVMs.")
    override fun position(): Int  = TODO("This method only applies to readers that support IVMs.")
}
