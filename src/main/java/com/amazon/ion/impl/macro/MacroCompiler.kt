// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.Expression.*
import com.amazon.ion.util.confirm
import java.math.BigDecimal
import java.math.BigInteger

/**
 * [MacroCompiler] wraps an Ion reader. When directed to do so, it will take over advancing and getting values from the
 * reader in order to read one [TemplateMacro].
 */
abstract class MacroCompiler(
    private val getMacro: (MacroRef) -> Macro?,
) {

    /** The name of the macro that was read. Returns `null` if no macro name is available. */
    var macroName: String? = null
        private set // Only mutable internally

    private val signature: MutableList<Macro.Parameter> = mutableListOf()
    private val expressions: MutableList<TemplateBodyExpression> = mutableListOf()

    /**
     * Compiles a template macro definition from the reader. Caller is responsible for positioning the reader at—but not
     * stepped into—the macro template s-expression.
     */
    fun compileMacro(): TemplateMacro {
        macroName = null
        signature.clear()
        expressions.clear()

        confirm(encodingType() == IonType.SEXP) { "macro compilation expects a sexp starting with the keyword `macro`" }
        confirmNoAnnotations("a macro definition sexp")
        readContainer {
            nextValue()
            confirm(encodingType() == IonType.SYMBOL && stringValue() == "macro") { "macro compilation expects a sexp starting with the keyword `macro`" }

            nextAndCheckType(IonType.SYMBOL, IonType.NULL, "macro name")
            confirmNoAnnotations("macro name")
            if (encodingType() != IonType.NULL) {
                macroName = stringValue().also { confirm(isIdentifierSymbol(it)) { "invalid macro name: '$it'" } }
            }
            nextAndCheckType(IonType.SEXP, "macro signature")
            confirmNoAnnotations("macro signature")
            readSignature()
            confirm(nextValue()) { "Macro definition is missing a template body expression." }
            compileTemplateBodyExpression(isQuoted = false)
            confirm(!nextValue()) { "Unexpected ${encodingType()} after template body expression." }
        }
        return TemplateMacro(signature.toList(), expressions.toList())
    }

    /**
     * Reads the macro signature, populating parameters in [signature].
     * Caller is responsible for making sure that the reader is positioned on (but not stepped into) the signature sexp.
     */
    private fun readSignature() {
        var pendingParameter: Macro.Parameter? = null

        forEachInContainer {
            if (encodingType() != IonType.SYMBOL) throw IonException("parameter must be a symbol; found ${encodingType()}")

            val symbolText = stringValue()

            val cardinality = Macro.ParameterCardinality.fromSigil(symbolText)

            if (cardinality != null) {
                confirmNoAnnotations("cardinality sigil")
                // The symbol is a cardinality modifier
                if (pendingParameter == null) {
                    throw IonException("Found an orphaned cardinality in macro signature")
                } else {
                    signature.add(pendingParameter!!.copy(cardinality = cardinality))
                    pendingParameter = null
                    return@forEachInContainer
                }
            }

            // If we have a pending parameter, add it to the signature before we read the next parameter
            if (pendingParameter != null) signature.add(pendingParameter!!)

            // Read the next parameter name
            val annotations = getTypeAnnotationSymbols()
            confirm(annotations.isEmptyOr(Macro.ParameterEncoding.Tagged.ionTextName)) { "unsupported parameter encoding $annotations" }
            confirm(isIdentifierSymbol(symbolText)) { "invalid parameter name: '$symbolText'" }
            confirm(signature.none { it.variableName == symbolText }) { "redeclaration of parameter '$symbolText'" }
            pendingParameter = Macro.Parameter(symbolText, Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)
        }
        // If we have a pending parameter than hasn't been added to the signature, add it here.
        if (pendingParameter != null) signature.add(pendingParameter!!)
    }

    private fun isIdentifierSymbol(symbol: String): Boolean {
        if (symbol.isEmpty()) return false

        // If the symbol's text matches an Ion keyword, it's not an identifier symbol.
        // Eg, the symbol 'false' must be quoted and is not an identifier symbol.
        if (_Private_IonTextAppender.isIdentifierKeyword(symbol)) return false

        if (!_Private_IonTextAppender.isIdentifierStart(symbol[0].code)) return false

        return symbol.all { c -> _Private_IonTextAppender.isIdentifierPart(c.code) }
    }

    /**
     * Compiles the current value on the reader into a [TemplateBodyExpression] and adds it to [expressions].
     * Caller is responsible for ensuring that the reader is positioned on a value.
     *
     * If called when the reader is not positioned on any value, throws [IllegalStateException].
     */
    private fun compileTemplateBodyExpression(isQuoted: Boolean) {
        // NOTE: `toList()` does not allocate for an empty list.
        val annotations: List<SymbolToken> = getTypeAnnotationSymbols()

        if (isNullValue()) {
            expressions.add(NullValue(annotations, encodingType()!!))
        } else when (encodingType()) {
            IonType.BOOL -> expressions.add(BoolValue(annotations, booleanValue()))
            IonType.INT -> expressions.add(
                when (integerSize()!!) {
                    IntegerSize.INT,
                    IntegerSize.LONG -> LongIntValue(annotations, longValue())
                    IntegerSize.BIG_INTEGER -> BigIntValue(annotations, bigIntegerValue())
                }
            )
            IonType.FLOAT -> expressions.add(FloatValue(annotations, doubleValue()))
            IonType.DECIMAL -> expressions.add(DecimalValue(annotations, decimalValue()))
            IonType.TIMESTAMP -> expressions.add(TimestampValue(annotations, timestampValue()))
            IonType.STRING -> expressions.add(StringValue(annotations, stringValue()))
            IonType.BLOB -> expressions.add(BlobValue(annotations, newBytes()))
            IonType.CLOB -> expressions.add(ClobValue(annotations, newBytes()))
            IonType.SYMBOL -> {
                if (isQuoted) {
                    expressions.add(SymbolValue(annotations, symbolValue()))
                } else {
                    val name = stringValue()
                    confirmNoAnnotations("on variable reference '$name'")
                    val index = signature.indexOfFirst { it.variableName == name }
                    confirm(index >= 0) { "variable '$name' is not recognized" }
                    expressions.add(VariableRef(index))
                }
            }
            IonType.LIST -> compileSequence(isQuoted) { start, end -> ListValue(annotations, start, end) }
            IonType.SEXP -> {
                if (isQuoted) {
                    compileSequence(isQuoted = true) { start, end -> SExpValue(annotations, start, end) }
                } else {
                    confirmNoAnnotations(location = "a macro invocation")
                    compileMacroInvocation()
                }
            }
            IonType.STRUCT -> compileStruct(annotations, isQuoted)
            // IonType.NULL, IonType.DATAGRAM, null
            else -> throw IllegalStateException("Found ${encodingType()}; this should be unreachable.")
        }
    }

    /**
     * Compiles a struct in a macro template.
     * When calling, the reader should be positioned at the struct, but not stepped into it.
     * If this function returns normally, it will be stepped out of the struct.
     * Caller will need to call [IonReader.next] to get the next value.
     */
    private fun compileStruct(annotations: List<SymbolToken>, isQuoted: Boolean) {
        val start = expressions.size
        expressions.add(Placeholder)
        val templateStructIndex = mutableMapOf<String, ArrayList<Int>>()
        forEachInContainer {
            val fieldName: SymbolToken = fieldNameSymbol()
            expressions.add(FieldName(fieldName))
            fieldName.text?.let {
                val valueIndex = expressions.size
                // Default is an array list with capacity of 1, since the most common case is that a field name occurs once.
                templateStructIndex.getOrPut(it) { ArrayList(1) } += valueIndex
            }
            compileTemplateBodyExpression(isQuoted)
        }
        val end = expressions.size
        expressions[start] = StructValue(annotations, start, end, templateStructIndex)
    }

    /**
     * Compiles a list or sexp in a macro template.
     * When calling, the reader should be positioned at the sequence, but not stepped into it.
     * If this function returns normally, it will be stepped out of the sequence.
     * Caller will need to call [IonReader.next] to get the next value.
     */
    private inline fun compileSequence(isQuoted: Boolean, newTemplateBodySequence: (Int, Int) -> TemplateBodyExpression) {
        val seqStart = expressions.size
        expressions.add(Placeholder)
        forEachInContainer { compileTemplateBodyExpression(isQuoted) }
        val seqEnd = expressions.size
        expressions[seqStart] = newTemplateBodySequence(seqStart, seqEnd)
    }

    /**
     * Compiles a macro invocation in a macro template.
     * When calling, the reader should be positioned at the sexp, but not stepped into it.
     * If this function returns normally, it will be stepped out of the sexp.
     * Caller will need to call [IonReader.next] to get the next value.
     */
    private fun compileMacroInvocation() {
        stepIntoContainer()
        nextValue()
        val macroRef = when (encodingType()) {
            IonType.SYMBOL -> {
                val macroName = stringValue()
                // TODO: Come up with a consistent strategy for handling special forms.
                when (macroName) {
                    "literal" -> {
                        // It's the "literal" special form; skip compiling a macro invocation and just treat all contents as literals
                        forEachRemaining { compileTemplateBodyExpression(isQuoted = true) }
                        stepOutOfContainer()
                        return
                    }
                    ";" -> {
                        val macroStart = expressions.size
                        expressions.add(Placeholder)
                        forEachRemaining { compileTemplateBodyExpression(isQuoted = false) }
                        val macroEnd = expressions.size
                        expressions[macroStart] = ExpressionGroup(macroStart, macroEnd)
                        stepOutOfContainer()
                        return
                    }
                    else -> MacroRef.ByName(macroName)
                }
            }
            IonType.INT -> MacroRef.ById(intValue())
            else -> throw IonException("macro invocation must start with an id (int) or identifier (symbol); found ${encodingType() ?: "nothing"}\"")
        }

        val macro = getMacro(macroRef) ?: throw IonException("Unrecognized macro: $macroRef")

        val macroStart = expressions.size
        expressions.add(Placeholder)
        forEachRemaining { compileTemplateBodyExpression(isQuoted = false) }
        val macroEnd = expressions.size
        expressions[macroStart] = MacroInvocation(macro, macroStart, macroEnd)

        stepOutOfContainer()
    }

    // Helper functions

    /** Utility method for checking that annotations are empty or a single array with the given annotations */
    private fun List<SymbolToken>.isEmptyOr(text: String): Boolean = isEmpty() || (size == 1 && this[0].assumeText() == text)

    /** Throws [IonException] if any annotations are on the current value in this [IonReader]. */
    private fun confirmNoAnnotations(location: String) {
        confirm(!hasAnnotations()) { "found annotations on $location" }
    }

    /** Moves to the next type and throw [IonException] if it is not the `expected` [IonType]. */
    private fun nextAndCheckType(expected: IonType, location: String) {
        confirm(nextValue() && encodingType() == expected) { "$location must be a $expected; found ${encodingType() ?: "nothing"}" }
    }

    /** Moves to the next type and throw [IonException] if it is not the `expected` [IonType]. */
    private fun nextAndCheckType(expected0: IonType, expected1: IonType, location: String) {
        confirm(nextValue() && (encodingType() == expected0 || encodingType() == expected1)) { "$location must be a $expected0 or $expected1; found ${encodingType() ?: "nothing"}" }
    }

    /** Steps into a container, executes [block], and steps out. */
    private inline fun readContainer(block: () -> Unit) { stepIntoContainer(); block(); stepOutOfContainer() }

    /** Executes [block] for each remaining value at the current reader depth. */
    private inline fun forEachRemaining(block: (IonType) -> Unit) { while (nextValue()) { block(encodingType()!!) } }

    /** Steps into a container, executes [block] for each value at that reader depth, and steps out. */
    private inline fun forEachInContainer(block: (IonType) -> Unit) = readContainer { forEachRemaining(block) }

    protected abstract fun hasAnnotations(): Boolean
    protected abstract fun fieldNameSymbol(): SymbolToken
    protected abstract fun encodingType(): IonType?

    /** Returns true if positioned on a value; false if at container or stream end. */
    protected abstract fun nextValue(): Boolean
    protected abstract fun stringValue(): String
    protected abstract fun intValue(): Int
    protected abstract fun decimalValue(): BigDecimal
    protected abstract fun doubleValue(): Double
    protected abstract fun stepIntoContainer()
    protected abstract fun stepOutOfContainer()
    protected abstract fun getTypeAnnotationSymbols(): List<SymbolToken>
    protected abstract fun integerSize(): IntegerSize?
    protected abstract fun booleanValue(): Boolean
    protected abstract fun isNullValue(): Boolean
    protected abstract fun longValue(): Long
    protected abstract fun bigIntegerValue(): BigInteger
    protected abstract fun timestampValue(): Timestamp
    protected abstract fun newBytes(): ByteArray
    protected abstract fun symbolValue(): SymbolToken
}
