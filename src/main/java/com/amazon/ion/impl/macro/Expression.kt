// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl._Private_Utils.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

enum class ExpressionKind {
    // Instructions
    /* 00 */ Nop,
    /* 01 */ Placeholder,
    /* 02 */ ContinueExpansion,
    /* 03 */ EndOfExpansion,
    // Data model expressions
    /* 04 */ AnnotationsUnused,
    /* 05 */ FieldName,
    // Still data model expressions, but more specifically also data model values
    /* 06 */ NullValue,
    /* 07 */ BoolValue,
    // TODO: Should we merge these two?
    /* 08 */ LongIntValue,
    /* 09 */ BigIntValue,
    /* 10 */ FloatValue,
    /* 11 */ DecimalValue,
    /* 12 */ TimestampValue,
    /* 13 */ StringValue,
    /* 14 */ SymbolValue,
    /* 15 */ BlobValue,
    /* 16 */ ClobValue,
    // Still data model expressions, but also HasStartAndEnd
    /* 17 */ ListValue,
    /* 18 */ SExpValue,
    /* 19 */ StructValue,
    // Things to evaluate
    /* 20 */ EExpression,
    /* 21 */ MacroInvocation,
    /* 22 */ ExpressionGroup,
    // Not HasStartAndEnd
    /* 23 */ VariableRef,
    ;

    fun hasStartAndEnd(): Boolean = this.ordinal in 17..22
    fun isDataModelExpression(): Boolean = this.ordinal in 4..19
    fun isDataModelValue(): Boolean = this.ordinal in 6..19

    // TODO: Micro optimization: see which one of these is most efficient
    fun isTextValue() = this == StringValue || this == SymbolValue
    fun isIntValue() = this === LongIntValue || this === BigIntValue
    fun isLobValue() = ordinal == 15 || ordinal == 16

    fun isDataModelContainer(): Boolean = this == ListValue || this == StructValue || this == SExpValue
    fun isInvokableExpression(): Boolean = this.ordinal in 20..21
    fun isTemplateBodyExpression(): Boolean = this.ordinal.let { it in 4..19 || it in 21..23  }
    fun isEExpressionBody(): Boolean = this.ordinal.let { it in 4..20 || it == 22  }
    fun isExpansionOutput(): Boolean = this.ordinal in 3..19
    fun isExpansionOutputOrContinue(): Boolean = this.ordinal in 2..19

    fun toIonType(): IonType? {
        return when (this) {
            BoolValue -> IonType.BOOL
            LongIntValue,
            BigIntValue -> IonType.INT
            FloatValue -> IonType.FLOAT
            DecimalValue -> IonType.DECIMAL
            TimestampValue -> IonType.TIMESTAMP
            StringValue -> IonType.STRING
            SymbolValue -> IonType.SYMBOL
            BlobValue -> IonType.BLOB
            ClobValue -> IonType.CLOB
            ListValue -> IonType.LIST
            SExpValue -> IonType.SEXP
            StructValue -> IonType.STRUCT
            else -> null
        }
    }
}

/**
 * There should be
 *   - public factory methods that protect the invariants
 *   - internal-only `init` methods
 *   - internal-only default constructor
 *   - private setters (although I am less certain about this. There might be a use case to make them internal.)
 */
class ExpressionA internal constructor() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExpressionA
        return kind == other.kind &&
                startInclusive == other.startInclusive &&
                endExclusive == other.endExclusive &&
                annotations == other.annotations &&
                if (value is ByteArray && other.value is ByteArray) {
                    (value as ByteArray).contentEquals(other.value as ByteArray)
                } else {
                    value == other.value
                }
    }

    override fun hashCode(): Int {
        return Objects.hash(kind, startInclusive, endExclusive, annotations, if (value is ByteArray) (value as ByteArray).contentHashCode() else value)
    }

    override fun toString(): String = "$kind($startInclusive, $endExclusive, annotations=$annotations, data=${if (value is ByteArray) (value as ByteArray).contentToString() else value})"

    var kind: ExpressionKind = ExpressionKind.Placeholder
        private set

    /**
     * Only set if `kind.hasStartAndEnd()` is `true`.
     */
    var startInclusive: Int = -1
        private set

    /**
     * Only set if `kind.hasStartAndEnd()` is `true`.
     */
    var endExclusive: Int = -1
        private set

    /**
     * Placeholder, Continue, End, ExpressionGroup, List, Struct, Sexp -- null
     * VariableRef -- Int (signature index)
     * FieldName -- SymbolToken (or String?)
     * Annotations -- Array<String/SymbolToken>
     * EExpression, MacroInvocation -- Macro
     *
     * The only type that is unnecessarily boxed because of this is Double.
     * The integer values can be smuggled in the start/end fields if necessary.
     */
    @JvmField
    var value: Any? = null

    @JvmField
    var annotations: List<SymbolToken> = emptyList()

    // TODO: For lazy expressions, add something like a marker or checkpoint field.
    //       If the marker field is set, then the data field is filled lazily (for scalars),
    //       or the start and end fields are unset (for containers).

    val ionType: IonType?
        get() {
            return when (kind) {
                ExpressionKind.NullValue -> value as IonType
                else -> kind.toIonType()
            }
        }

    internal fun dataAsInt() = value.let {
        when (it) {
            is Int -> it
            is Long -> it.toInt()
            is BigInteger -> it.intValueExact()
            else -> throw IonException("Expected an integer value; found $kind")
        }
    }

    internal fun dataAsLong() = value.let {
        when (it) {
            is Int -> it.toLong()
            is Long -> it
            is BigInteger -> it.longValueExact()
            else -> throw IonException("Expected an integer value; found $kind")
        }
    }

    internal fun dataAsBigInt() = value.let {
        when (it) {
            is Int -> it.toBigInteger()
            is Long -> it.toBigInteger()
            is BigInteger -> it
            else -> throw IonException("Expected an integer value; found $kind")
        }
    }

    internal fun dataAsSymbolToken() = value.let {
        when (it) {
            is String -> newSymbolToken(it)
            is SymbolToken -> it
            else -> throw IonException("Expected an text value; found $kind")
        }
    }

    internal fun dataAsString() = value.let {
        when (it) {
            is String -> it
            is SymbolToken -> it.assumeText()
            else -> throw IonException("Expected an text value; found $kind")
        }
    }

    internal fun dataAsSymbolTokenList() = value.let {
        it as MutableList<*>
        it.map { text ->
            if (text is String) {
                newSymbolToken(text)
            } else {
                text as SymbolToken
            }
        }
    }

    internal fun dataAsStringList() = value.let {
        it as MutableList<*>
        it.map { text ->
            if (text is SymbolToken) {
                text.assumeText()
            } else {
                text as String
            }
        }
    }

    internal fun initFieldName(name: String) {
        kind = ExpressionKind.FieldName
        value = name
    }

    internal fun initFieldName(name: SymbolToken) {
        kind = ExpressionKind.FieldName
        value = name
    }

    /** Should be List<String> or List<SymbolToken> or List<String|SymbolToken> */
    fun withAnnotations(annotations: List<SymbolToken>): ExpressionA = apply {
        // if (annotations.isEmpty()) { throw IllegalStateException("Don't add an empty annotations expression") }
        // kind = if (annotations.isNotEmpty()) ExpressionKind.Annotations else ExpressionKind.ContinueExpansion
        this.annotations = annotations
    }

    internal fun initNull(type: IonType) {
        kind = ExpressionKind.NullValue
        value = type
    }

    internal fun initBool(value: Boolean) {
        kind = ExpressionKind.BoolValue
        this.value = value
    }

    internal fun initInt(value: Long) {
        kind = ExpressionKind.LongIntValue
        // TODO: Can we get a perf improvement by co-opting startInclusive so that we don't need to box the Long value?
        this.value = value
    }

    internal fun initInt(value: BigInteger) {
        kind = ExpressionKind.BigIntValue
        this.value = value
    }

    internal fun initFloat(value: Double) {
        kind = ExpressionKind.FloatValue
        this.value = value
    }

    internal fun initDecimal(value: BigDecimal) {
        kind = ExpressionKind.DecimalValue
        this.value = value
    }

    internal fun initTimestamp(value: Timestamp) {
        kind = ExpressionKind.TimestampValue
        this.value = value
    }

    internal fun initString(value: String) {
        kind = ExpressionKind.StringValue
        this.value = value
    }

    internal fun initSymbol(value: SymbolToken) {
        kind = ExpressionKind.SymbolValue
        this.value = value
    }

    internal fun initSymbol(value: String) {
        kind = ExpressionKind.SymbolValue
        this.value = value
    }

    internal fun initBlob(value: ByteArray) {
        kind = ExpressionKind.BlobValue
        this.value = value
    }

    internal fun initClob(value: ByteArray) {
        kind = ExpressionKind.ClobValue
        this.value = value
    }

    internal fun initList(startInclusive: Int, endExclusive: Int) {
        kind = ExpressionKind.ListValue
        this.startInclusive = startInclusive
        this.endExclusive = endExclusive
    }

    internal fun initSexp(startInclusive: Int, endExclusive: Int) {
        kind = ExpressionKind.SExpValue
        this.startInclusive = startInclusive
        this.endExclusive = endExclusive
    }

    internal fun initStruct(startInclusive: Int, endExclusive: Int) {
        kind = ExpressionKind.StructValue
        this.startInclusive = startInclusive
        this.endExclusive = endExclusive
    }

    internal fun initEExpression(macro: Macro, startInclusive: Int, endExclusive: Int) {
        kind = ExpressionKind.EExpression
        this.startInclusive = startInclusive
        this.endExclusive = endExclusive
        value = macro
    }

    internal fun initMacroInvocation(macro: Macro, startInclusive: Int, endExclusive: Int) {
        kind = ExpressionKind.MacroInvocation
        this.startInclusive = startInclusive
        this.endExclusive = endExclusive
        value = macro
    }

    internal fun initVariableRef(signatureIndex: Int) {
        kind = ExpressionKind.VariableRef
        // TODO: Can we get a perf improvement by co-opting startInclusive so that we don't need to box the Int value?
        value = signatureIndex
    }

    internal fun initExpressionGroup(startInclusive: Int, endExclusive: Int) {
        kind = ExpressionKind.ExpressionGroup
        this.startInclusive = startInclusive
        this.endExclusive = endExclusive
    }

    companion object {
        @JvmStatic
        internal val END_OF_EXPANSION = ExpressionA().also { it.kind = ExpressionKind.EndOfExpansion }
        @JvmStatic
        internal val CONTINUE_EXPANSION = ExpressionA().also { it.kind = ExpressionKind.ContinueExpansion }

        @JvmStatic fun newFieldName(name: String) = ExpressionA().also { it.initFieldName(name) }
        @JvmStatic fun newFieldName(name: SymbolToken) = ExpressionA().also { it.initFieldName(name) }
        /** Should be List<String> or List<SymbolToken> or List<String|SymbolToken> */
        // @JvmStatic fun newAnnotations(annotations: List<Any>) = ExpressionA().also { it.initAnnotations(annotations) }
        @JvmStatic fun newNull(ionType: IonType) = ExpressionA().also { it.initNull(ionType) }
        @JvmStatic fun newBool(value: Boolean) = ExpressionA().also { it.initBool(value) }
        @JvmStatic fun newInt(value: Long) = ExpressionA().also { it.initInt(value) }
        @JvmStatic fun newInt(value: BigInteger) = ExpressionA().also { it.initInt(value) }
        @JvmStatic fun newFloat(value: Double) = ExpressionA().also { it.initFloat(value) }
        @JvmStatic fun newDecimal(value: BigDecimal) = ExpressionA().also { it.initDecimal(value) }
        @JvmStatic fun newTimestamp(value: Timestamp) = ExpressionA().also { it.initTimestamp(value) }
        @JvmStatic fun newSymbol(value: SymbolToken) = ExpressionA().also { it.initSymbol(value) }
        @JvmStatic fun newSymbol(value: String) = ExpressionA().also { it.initSymbol(value) }
        @JvmStatic fun newString(value: String) = ExpressionA().also { it.initString(value) }
        @JvmStatic fun newBlob(value: ByteArray) = ExpressionA().also { it.initBlob(value) }
        @JvmStatic fun newClob(value: ByteArray) = ExpressionA().also { it.initClob(value) }
        @JvmStatic fun newList(startInclusive: Int, endExclusive: Int) = ExpressionA().also { it.initList(startInclusive, endExclusive) }
        @JvmStatic fun newSexp(startInclusive: Int, endExclusive: Int) = ExpressionA().also { it.initSexp(startInclusive, endExclusive) }
        @JvmStatic fun newStruct(startInclusive: Int, endExclusive: Int) = ExpressionA().also { it.initStruct(startInclusive, endExclusive) }
        @JvmStatic internal fun newEExpression(macro: Macro, startInclusive: Int, endExclusive: Int) = ExpressionA().also { it.initEExpression(macro, startInclusive, endExclusive) }
        @JvmStatic fun newMacroInvocation(macro: Macro, startInclusive: Int, endExclusive: Int) = ExpressionA().also { it.initMacroInvocation(macro, startInclusive, endExclusive) }
        @JvmStatic fun newExpressionGroup(startInclusive: Int, endExclusive: Int) = ExpressionA().also { it.initExpressionGroup(startInclusive, endExclusive) }
        @JvmStatic fun newVariableRef(signatureIndex: Int) = ExpressionA().also { it.initVariableRef(signatureIndex) }
    }
}

/**
 * In-memory expression model.
 *
 * We cannot use [`IonValue`](com.amazon.ion.IonValue) for this because `IonValue` requires references to parent
 * containers and to an IonSystem which makes it impractical for reading and writing macros definitions. Furthermore,
 * there is information we need to capture that cannot be expressed in the IonValue model, such as macro invocations
 * and variable references.
 *
 * Template bodies are compiled into a list of expressions, without nesting, for ease and efficiency of evaluating
 * e-expressions. Because of this, the container types do not have other values nested in them; rather they contain a
 * range that indicates which of the following expressions are part of that container.
 *
 * TODO: Consider creating an enum or integer-based expression type id so that we can `switch` efficiently on it.
 */
sealed interface ExpressionB {

    /** Interface for expressions that "contain" other expressions */
    sealed interface HasStartAndEnd : ExpressionB {
        /**
         * The position of this expression in its containing list.
         * Child expressions (if any) start at `selfIndex + 1`.
         */
        val selfIndex: Int
        /**
         * The index of the first child expression (if any).
         * Always equal to `selfIndex + 1`.
         */
        val startInclusive: Int get() = selfIndex + 1
        /**
         * The exclusive end of the child expressions (if any).
         * If there are no child expressions, will be equal to [startInclusive].
         */
        val endExclusive: Int
    }

    /** Marker interface representing expressions that can be present in E-Expressions. */
    sealed interface EExpressionBodyExpression : ExpressionB

    /** Marker interface representing expressions in the body of a template. */
    sealed interface TemplateBodyExpression : ExpressionB

    /**
     * Marker interface for things that are part of the Ion data model.
     * These expressions are the only ones that may be the output from the macro evaluator.
     * All [DataModelExpression]s are also valid to use as [TemplateBodyExpression]s and [EExpressionBodyExpression]s.
     */
    sealed interface DataModelExpression : ExpressionB, EExpressionBodyExpression, TemplateBodyExpression, ExpansionOutputExpression

    /** Output of a macro expansion (internal to the macro evaluator) */
    sealed interface ExpansionOutputExpressionOrContinue
    /** Output of the macro evaluator */
    sealed interface ExpansionOutputExpression : ExpansionOutputExpressionOrContinue

    /**
     * Indicates to the macro evaluator that the current expansion did not produce a value this time, but it may
     * produce more expressions. The macro evaluator should request another expression from that macro.
     */
    data object ContinueExpansion : ExpansionOutputExpressionOrContinue

    /** Signals the end of an expansion in the macro evaluator. */
    data object EndOfExpansion : ExpansionOutputExpression

    /**
     * Interface for expressions that are _values_ in the Ion data model.
     */
    sealed interface DataModelValue : DataModelExpression {
        val annotations: List<SymbolToken>
        val type: IonType

        fun withAnnotations(annotations: List<SymbolToken>): DataModelValue
    }

    /** Expressions that represent Ion container types */
    sealed interface DataModelContainer : HasStartAndEnd, DataModelValue

    /**
     * A temporary placeholder that is used only while a macro or e-expression is partially compiled.
     *
     * TODO: See if we can get rid of this by e.g. using nulls during macro compilation.
     */
    object Placeholder : TemplateBodyExpression, EExpressionBodyExpression

    /**
     * A group of expressions that form the argument for one macro parameter.
     *
     * TODO: Should we include the parameter name for ease of debugging?
     *       We'll hold off for now and see how the macro evaluator shakes out.
     *
     * @property selfIndex the index of the first expression of the expression group (i.e. this instance)
     * @property endExclusive the index of the last expression contained in the expression group
     */
    data class ExpressionGroup(override val selfIndex: Int, override val endExclusive: Int) : EExpressionBodyExpression, TemplateBodyExpression, HasStartAndEnd

    // Scalars
    data class NullValue(override val annotations: List<SymbolToken> = emptyList(), override val type: IonType) : DataModelValue {
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    data class BoolValue(override val annotations: List<SymbolToken> = emptyList(), val value: Boolean) : DataModelValue {
        override val type: IonType get() = IonType.BOOL
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    sealed interface IntValue : DataModelValue {
        val bigIntegerValue: BigInteger
        val longValue: Long
    }

    data class LongIntValue(override val annotations: List<SymbolToken> = emptyList(), val value: Long) : IntValue {
        override val type: IonType get() = IonType.INT
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
        override val bigIntegerValue: BigInteger get() = BigInteger.valueOf(value)
        override val longValue: Long get() = value
    }

    data class BigIntValue(override val annotations: List<SymbolToken> = emptyList(), val value: BigInteger) : IntValue {
        override val type: IonType get() = IonType.INT
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
        override val bigIntegerValue: BigInteger get() = value
        override val longValue: Long get() = value.longValueExact()
    }

    data class FloatValue(override val annotations: List<SymbolToken> = emptyList(), val value: Double) : DataModelValue {
        override val type: IonType get() = IonType.FLOAT
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    data class DecimalValue(override val annotations: List<SymbolToken> = emptyList(), val value: BigDecimal) : DataModelValue {
        override val type: IonType get() = IonType.DECIMAL
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    data class TimestampValue(override val annotations: List<SymbolToken> = emptyList(), val value: Timestamp) : DataModelValue {
        override val type: IonType get() = IonType.TIMESTAMP
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    sealed interface TextValue : DataModelValue {
        val stringValue: String
    }

    data class StringValue(override val annotations: List<SymbolToken> = emptyList(), val value: String) : TextValue {
        override val type: IonType get() = IonType.STRING
        override val stringValue: String get() = value
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    data class SymbolValue(override val annotations: List<SymbolToken> = emptyList(), val value: SymbolToken) : TextValue {
        override val type: IonType get() = IonType.SYMBOL
        override val stringValue: String get() = value.assumeText()
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    sealed interface LobValue : DataModelValue {
        // TODO: Consider replacing this with a ByteArray "View" that is backed by the original
        //       data source to avoid eagerly copying data.
        val value: ByteArray
    }

    // We must override hashcode and equals in the lob types because `value` is a `byte[]`
    data class BlobValue(override val annotations: List<SymbolToken> = emptyList(), override val value: ByteArray) : LobValue {
        override val type: IonType get() = IonType.BLOB
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
        override fun hashCode(): Int = annotations.hashCode() * 31 + value.contentHashCode()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BlobValue) return false
            if (other.annotations != this.annotations) return false
            return value === other.value || value.contentEquals(other.value)
        }
    }

    data class ClobValue(override val annotations: List<SymbolToken> = emptyList(), override val value: ByteArray) : LobValue {
        override val type: IonType get() = IonType.CLOB
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
        override fun hashCode(): Int = annotations.hashCode() * 31 + value.contentHashCode()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ClobValue) return false
            if (other.annotations != this.annotations) return false
            return value === other.value || value.contentEquals(other.value)
        }
    }

    /**
     * An Ion List that could contain variables or macro invocations.
     *
     * @property selfIndex the index of the first expression of the list (i.e. this instance)
     * @property endExclusive the index of the last expression contained in the list
     */
    data class ListValue(
        override val annotations: List<SymbolToken> = emptyList(),
        override val selfIndex: Int,
        override val endExclusive: Int
    ) : DataModelContainer {
        override val type: IonType get() = IonType.LIST
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    /**
     * An Ion SExp that could contain variables or macro invocations.
     */
    data class SExpValue(
        override val annotations: List<SymbolToken> = emptyList(),
        override val selfIndex: Int,
        override val endExclusive: Int
    ) : DataModelContainer {
        override val type: IonType get() = IonType.SEXP
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    /**
     * An Ion Struct that could contain variables or macro invocations.
     */
    data class StructValue(
        override val annotations: List<SymbolToken> = emptyList(),
        override val selfIndex: Int,
        override val endExclusive: Int,
        val templateStructIndex: Map<String, List<Int>>
    ) : DataModelContainer {
        override val type: IonType get() = IonType.STRUCT
        override fun withAnnotations(annotations: List<SymbolToken>) = copy(annotations = annotations)
    }

    data class FieldName(val value: SymbolToken) : DataModelExpression

    /**
     * A reference to a variable that needs to be expanded.
     */
    data class VariableRef(val signatureIndex: Int) : TemplateBodyExpression

    sealed interface InvokableExpression : HasStartAndEnd, ExpressionB {
        val macro: Macro
    }

    /**
     * A macro invocation that needs to be expanded.
     */
    data class MacroInvocation(
        override val macro: Macro,
        override val selfIndex: Int,
        override val endExclusive: Int
    ) : TemplateBodyExpression, HasStartAndEnd, InvokableExpression

    /**
     * An e-expression that needs to be expanded.
     */
    data class EExpression(
        override val macro: Macro,
        override val selfIndex: Int,
        override val endExclusive: Int
    ) : EExpressionBodyExpression, HasStartAndEnd, InvokableExpression
}
