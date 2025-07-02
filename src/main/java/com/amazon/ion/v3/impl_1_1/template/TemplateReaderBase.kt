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
     @JvmField
    var source: Array<TemplateBodyExpressionModel>,
    @JvmField
    var tokens: IntArray,
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
    var currentToken: Int = TokenTypeConst.UNSET
    @JvmField
    var endExclusive: Int = source.size

    fun init(
        source: Array<TemplateBodyExpressionModel>,
        tokens: IntArray,
        arguments: ArgumentReader,
        isArgumentOwner: Boolean,
     ) {
        this.source = source
        this.tokens = tokens
        this.endExclusive = source.size
        this.arguments = arguments
        this.isArgumentOwner = isArgumentOwner
        i = 0
//        currentExpression = null
        reinitState()
    }

    protected open fun reinitState() {}

    protected inline fun <U> consumeCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (TemplateBodyExpressionModel) -> U, ): U {
        val expr = currentExpression
//        currentExpression = null
        return when (expr!!.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> argMethod(arguments)
            else -> bodyMethod(expr)
        }
    }

    protected inline fun <U> inspectCurrentExpression(argMethod: (ArgumentReader) -> U, bodyMethod: (TemplateBodyExpressionModel) -> U, ): U {
        val expr = currentExpression!!
        return when (expr.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> argMethod(arguments)
            else -> bodyMethod(expr)
        }
    }

    override fun nextToken(): Int {
        val tokens = this.tokens
        if (i >= tokens.size) {
            return TokenTypeConst.END
        }

        currentToken = tokens[i++]

        when (currentToken) {
            TokenTypeConst.END -> {
                return currentToken
            }
            TokenTypeConst.VARIABLE_REF -> {
                val expr = this.source[i - 1]
                val v = expr.primitiveValue.toInt()
                currentExpression = expr
                val args = this.arguments
                currentToken = args.seekToArgument(v)
                return currentToken
            }
            else -> {
                val expr = this.source[i - 1]
                currentExpression = expr
                return currentToken
            }
        }
    }

    final override fun close() {
        if (isArgumentOwner) {
            arguments.close()
        }
        returnToPool()
    }

    protected abstract fun returnToPool()

    // TODO: Make sure that this also returns END when at the end of the input.
    override fun currentToken(): Int = currentToken

    override fun isTokenSet(): Boolean = currentToken() != TokenTypeConst.UNSET

    // TODO: This will probably fail when the current token is unset.
    override fun ionType(): IonType? = inspectCurrentExpression(ArgumentReader::ionType) {
        if (it.expressionKind == TokenTypeConst.NULL) {
            it.valueObject as IonType
        } else {
            IonType.entries[it.expressionKind]
        }
    }

    override fun valueSize(): Int = inspectCurrentExpression(ArgumentReader::valueSize) {
        // This is mostly only used for Ints... and Lobs?
        return@inspectCurrentExpression when (val value = currentExpression?.valueObject) {
            is Long -> 8
            is BigInteger -> 9
            is ByteBuffer -> value.remaining()
            else -> -1
        }
    }

    override fun skip() {
//        currentExpression = null
    }

    override fun macroValue(): MacroV2 = inspectCurrentExpression(ArgumentReader::macroValue) { it.valueObject as MacroV2 }

    override fun macroArguments(signature: Array<Macro.Parameter>): ArgumentReader {
        if (signature.isEmpty()) {
            return NoneReader
        }

        val expr = currentExpression!!
//        currentExpression = null
        return when (expr.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> arguments.macroArguments(signature)
            else -> {
                pool.getArguments(arguments, signature, expr.childExpressions, expr.childTokens)
            }
        }
    }

    override fun expressionGroup(): SequenceReader = consumeCurrentExpression(ArgumentReader::expressionGroup) {
        pool.getSequence(arguments, it.childExpressions, it.childTokens)
    }

    override fun nullValue(): IonType = consumeCurrentExpression(ArgumentReader::nullValue) { it.valueObject as IonType }
    override fun booleanValue(): Boolean = consumeCurrentExpression(ArgumentReader::booleanValue) { it.primitiveValue > 0 }
    // TODO: Consider checking to make sure that we have a long, rather than a big integer.
    override fun longValue(): Long = consumeCurrentExpression(ArgumentReader::longValue) { it.primitiveValue }
    override fun stringValue(): String = consumeCurrentExpression(ArgumentReader::stringValue) { it.valueObject as String }
    override fun symbolValue(): String? = consumeCurrentExpression(ArgumentReader::symbolValue) { it.valueObject as String? }

    override fun symbolValueSid(): Int {
        val sid = inspectCurrentExpression(
            { it.symbolValueSid() },
            // The text should have been resolved when the macro was compiled. Even if there is a SID,
            // it doesn't necessarily correspond to the current symbol table.
            { -1 }
        )
        if (sid >= 0) {
//            currentExpression = null
        }
        return sid
    }

    override fun lookupSid(sid: Int): String? = arguments.lookupSid(sid)

    override fun timestampValue(): Timestamp = consumeCurrentExpression(ArgumentReader::timestampValue) { it.valueObject as Timestamp }
    override fun clobValue(): ByteBuffer = consumeCurrentExpression(ArgumentReader::clobValue) { it.valueObject as ByteBuffer }
    override fun blobValue(): ByteBuffer = consumeCurrentExpression(ArgumentReader::blobValue) { it.valueObject as ByteBuffer }

    override fun listValue(): ListReader = consumeCurrentExpression(ArgumentReader::listValue) {
        pool.getSequence(arguments, it.childExpressions, it.childTokens)
    }

    override fun sexpValue(): SexpReader = consumeCurrentExpression(ArgumentReader::sexpValue) {
        pool.getSequence(arguments, it.childExpressions, it.childTokens)
    }

    override fun structValue(): StructReader {
        val expr = currentExpression
//        currentExpression = null
        return when (expr!!.expressionKind) {
            TokenTypeConst.VARIABLE_REF -> arguments.structValue()
            else -> {
                pool.getStruct(arguments, expr.childExpressions, expr.childTokens)
            }
        }
    }

    override fun annotations(): AnnotationIterator = inspectCurrentExpression(ArgumentReader::annotations) {
        expr -> pool.getAnnotations(expr.annotations)
    }

    override fun doubleValue(): Double = consumeCurrentExpression(ArgumentReader::doubleValue) { Double.fromBits(it.primitiveValue) }

    override fun decimalValue(): Decimal = Decimal.valueOf(consumeCurrentExpression(ArgumentReader::decimalValue) { it.valueObject as BigDecimal })

    override fun getIonVersion(): Short = 0x0101

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")
    override fun seekTo(position: Int) = TODO("This method only applies to readers that support IVMs.")
    override fun position(): Int  = TODO("This method only applies to readers that support IVMs.")
}
