package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.*
import com.amazon.ion.impl.macro.Expression
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.*
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

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
    var currentExpression: TemplateBodyExpressionModel? = null
    @JvmField
    var childStartIndex = -1
    @JvmField
    var source = info.source

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


    init {
//        debugInit()
    }

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
        childStartIndex = -1
        state = State.READY
//        id = UUID.randomUUID().toString().take(8)
        reinitState()
//        debugInit()
    }

    private fun debugInit() {
        println("Source: ${source.joinToString("    \n", postfix = "]", prefix = "[") { "$it" }}")
        println("startInclusive: $startInclusive")
        println("endExclusive: $endExclusive")
        println("isArgumentOwner: $isArgumentOwner")
    }

    protected open fun reinitState() {}

    protected fun <U> consumeCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (TemplateBodyExpressionModel) -> U, ): U {
        val expr = currentExpression
        currentExpression = null
        return when (expr!!.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> argMethod(info.arguments)
            else -> bodyMethod(expr)
        }
    }

    protected fun <U> inspectCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (TemplateBodyExpressionModel) -> U, ): U {
        val expr = currentExpression
        return when (expr!!.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> argMethod(info.arguments)
            else -> bodyMethod(expr)
        }
    }

    override fun nextToken(): Int {
        if (i >= endExclusive) {
            return TokenTypeConst.END
        }
        val expr = this.source[i++]
        this.childStartIndex = i
        this.currentExpression = expr
        this.i += expr.length

        if (expr.expressionKind == TokenTypeConst.VARIABLE_REF) {
            return resolveVariableTokenType(expr.value as Int)
        }

        return expr.expressionKind
    }

    protected fun resolveVariableTokenType(v: Int): Int {
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

        return if (expr.expressionKind == TokenTypeConst.VARIABLE_REF) {
            info.arguments.currentToken()
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
        val expr = currentExpression!!
        currentExpression = null
        return when (expr.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> info.arguments.macroArguments(signature)
            else -> {
                // FIXME: 10% of all
                pool.getArguments(info, signature, childStartIndex, childStartIndex + expr.length)
            }
        }
    }

    override fun expressionGroup(): SequenceReader = consumeCurrentExpression(ArgumentReader::expressionGroup) {
        val start = childStartIndex
        pool.getSequence(info, start, start + it.length)
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

    override fun lookupSid(sid: Int): String? = info.arguments.lookupSid(sid)

    override fun timestampValue(): Timestamp = consumeCurrentExpression(ArgumentReader::timestampValue) { it.value as Timestamp }
    override fun clobValue(): ByteBuffer = consumeCurrentExpression(ArgumentReader::clobValue) { it.value as ByteBuffer }
    override fun blobValue(): ByteBuffer = consumeCurrentExpression(ArgumentReader::blobValue) { it.value as ByteBuffer }

    override fun listValue(): ListReader = consumeCurrentExpression(ArgumentReader::listValue) {
        val start = childStartIndex
        pool.getSequence(info, start, start + it.length)
    }

    override fun sexpValue(): SexpReader = consumeCurrentExpression(ArgumentReader::sexpValue) {
        val start = childStartIndex
        pool.getSequence(info, start, start + it.length)
    }

    override fun structValue(): StructReader = consumeCurrentExpression(ArgumentReader::structValue) {
        val start = childStartIndex
        pool.getStruct(info, start, start + it.length)
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
