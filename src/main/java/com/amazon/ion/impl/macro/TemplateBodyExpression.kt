// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl.macro

import com.amazon.ion.*
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Marker interface for things that may be the output from the macro evaluator.
 */
sealed interface ResolvedExpression: Expression

/**
 * Marker interface representing E-Expressions.
 */
sealed interface EncodingExpression: Expression

/**
 * Marker interface representing expressions in the body of a template.
 *
 * A template body is compiled into a list of expressions, without nesting, for ease and efficiency of evaluating
 * e-expressions. Because of this, the container types do not have other values nested in them; rather they contain a
 * range that indicates which of the following expressions are part of that container.
 */
sealed interface TemplateBodyExpression: Expression

/**
 * In-memory expression model.
 *
 * We cannot use [`IonValue`](com.amazon.ion.IonValue) for this because `IonValue` requires references to parent
 * containers and to an IonSystem which makes it impractical for reading and writing macros definitions. Furthermore,
 * there is information we need to capture that cannot be expressed in the IonValue model, such as macro invocations
 * and variable references.
 */
sealed interface Expression {
    sealed interface HasStartAndEnd: Expression {
        val startInclusive: Int
        val endInclusive: Int
    }
    sealed interface Container: HasStartAndEnd

    val type: IonType?
        get() = null

    val annotations: List<SymbolToken>?
        get() = null

    // TODO: Special Forms (if_void, for, ...)? -- no, those should be implemented as system macros.

    /**
     * A temporary placeholder that is used only while a macro is partially compiled.
     */
    object Placeholder : TemplateBodyExpression, EncodingExpression

    /**
     * A group of expressions that form the argument for one macro parameter.
     *
     * TODO: Should we include the parameter name for ease of debugging?
     *
     * @property startInclusive the index of the first expression of the expression group (i.e. this instance)
     * @property endInclusive the index of the last expression contained in the expression group
     */
    data class ExpressionGroup(override val startInclusive: Int, override val endInclusive: Int) : EncodingExpression, TemplateBodyExpression, HasStartAndEnd

    // Scalars
    data class NullValue(override val annotations: List<SymbolToken> = emptyList(), override val type: IonType) : TemplateBodyExpression, EncodingExpression, ResolvedExpression

    data class BoolValue(override val annotations: List<SymbolToken> = emptyList(), val value: Boolean) : TemplateBodyExpression, EncodingExpression, ResolvedExpression {
        override val type: IonType get() = IonType.BOOL
    }

    sealed interface IntValue

    data class LongIntValue(override val annotations: List<SymbolToken> = emptyList(), val value: Long) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, IntValue {
        override val type: IonType get() = IonType.INT
    }

    data class BigIntValue(override val annotations: List<SymbolToken> = emptyList(), val value: BigInteger) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, IntValue {
        override val type: IonType get() = IonType.INT
    }

    data class FloatValue(override val annotations: List<SymbolToken> = emptyList(), val value: Double) : TemplateBodyExpression, EncodingExpression, ResolvedExpression {
        override val type: IonType get() = IonType.FLOAT
    }

    data class DecimalValue(override val annotations: List<SymbolToken> = emptyList(), val value: BigDecimal) : TemplateBodyExpression, EncodingExpression, ResolvedExpression {
        override val type: IonType get() = IonType.DECIMAL
    }

    data class TimestampValue(override val annotations: List<SymbolToken> = emptyList(), val value: Timestamp) : TemplateBodyExpression, EncodingExpression, ResolvedExpression {
        override val type: IonType get() = IonType.TIMESTAMP
    }

    sealed interface TextValue {
        val stringValue: String
    }

    data class StringValue(override val annotations: List<SymbolToken> = emptyList(), val value: String) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, TextValue {
        override val type: IonType get() = IonType.STRING
        override val stringValue: String get() = value
    }

    data class SymbolValue(override val annotations: List<SymbolToken> = emptyList(), val value: SymbolToken) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, TextValue {
        override val type: IonType get() = IonType.SYMBOL
        override val stringValue: String get() = value.assumeText()
    }

    sealed interface LobValue {
        val value: ByteArray
    }

    // We must override hashcode and equals in the lob types because `value` is a `byte[]`
    data class BlobValue(override val annotations: List<SymbolToken> = emptyList(), override val value: ByteArray) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, LobValue {
        override val type: IonType get() = IonType.BLOB
        override fun hashCode(): Int = annotations.hashCode() * 31 + value.contentHashCode()
        override fun equals(other: Any?): Boolean = this === other || other is BlobValue && annotations == other.annotations && (value === other.value || value.contentEquals(other.value))
    }

    data class ClobValue(override val annotations: List<SymbolToken> = emptyList(), override val value: ByteArray) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, LobValue {
        override val type: IonType get() = IonType.CLOB
        override fun hashCode(): Int = annotations.hashCode() * 31 + value.contentHashCode()
        override fun equals(other: Any?): Boolean = this === other || other is ClobValue && annotations == other.annotations && (value === other.value || value.contentEquals(other.value))
    }

    /**
     * An Ion List that could contain variables or macro invocations.
     *
     * @property startInclusive the index of the first expression of the list (i.e. this instance)
     * @property endInclusive the index of the last expression contained in the list
     */
    data class ListValue(override val annotations: List<SymbolToken> = emptyList(), override val startInclusive: Int, override val endInclusive: Int) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, Container {
        override val type: IonType get() = IonType.LIST
    }

    /**
     * An Ion SExp that could contain variables or macro invocations.
     */
    data class SExpValue(override val annotations: List<SymbolToken> = emptyList(), override val startInclusive: Int, override val endInclusive: Int) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, Container {
        override val type: IonType get() = IonType.SEXP
    }

    /**
     * An Ion Struct that could contain variables or macro invocations.
     */
    data class StructValue(override val annotations: List<SymbolToken> = emptyList(), override val startInclusive: Int, override val endInclusive: Int, val templateStructIndex: Map<String, List<Int>>) : TemplateBodyExpression, EncodingExpression, ResolvedExpression, Container {
        override val type: IonType get() = IonType.STRUCT
    }

    data class FieldName(val value: SymbolToken) : TemplateBodyExpression, EncodingExpression, ResolvedExpression

    /**
     * A reference to a variable that needs to be expanded.
     */
    data class VariableReference(val signatureIndex: Int) : TemplateBodyExpression

    /**
     * A macro invocation that needs to be expanded.
     */
    data class MacroInvocation(val address: MacroRef, override val startInclusive: Int, override val endInclusive: Int) : TemplateBodyExpression, HasStartAndEnd

    /**
     * An e-expression that needs to be expanded.
     */
    data class EExpression(val address: MacroRef, override val startInclusive: Int, override val endInclusive: Int) : EncodingExpression, HasStartAndEnd
}

/**
 * TODO: Do we need something like this so that we can lazily access the bytes from the source buffer?
 * TODO: This is untested.
 */
class ByteArraySlice(private val bytes: ByteArray, private val startInclusive: Int, val size: Int): Iterable<Byte> {

    fun toByteArray(): ByteArray {
        val byteArray = ByteArray(size)
        copyInto(byteArray, 0)
        return byteArray
    }

    fun copyInto(buffer: ByteArray, position: Int, len: Int = size): Int {
        val length = minOf(len, size, buffer.size - position)
        bytes.copyInto(buffer, position, startInclusive, length)
        return length
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArraySlice) return false
        if (size != other.size) return false
        if (bytes === other.bytes && startInclusive == other.startInclusive) return true
        return (0 until size).any { i -> bytes[i + startInclusive] != other.bytes[i + other.startInclusive] }
    }

    override fun hashCode(): Int {
        var result = 1
        for (i in 0 until size) result = 31 * result + bytes[i]
        return result
    }

    override fun iterator(): ByteIterator = Iter(bytes, startInclusive, startInclusive + size)

    private class Iter(private val bytes: ByteArray, startInclusive: Int, private val endExclusive: Int): ByteIterator() {
        private var i = startInclusive
        override fun hasNext(): Boolean = i < endExclusive
        override fun nextByte(): Byte = bytes[i++]
    }
}
