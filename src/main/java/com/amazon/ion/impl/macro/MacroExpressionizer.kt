package com.amazon.ion.impl.macro

import com.amazon.ion.*
import com.amazon.ion.IonType.*
import com.amazon.ion.impl.macro.Macro.ParameterEncoding.*
import com.amazon.ion.util.confirm
import java.util.*

/**
 * Purpose is to read from IonReader of some sort and construct [Expression]s.
 *
 * Expansion Algorithm.
 *
 * There are two sources of expressions. The template macro definitions, and the macro arguments.
 * The macro expander merges those.
 *
 * In order to avoid having to seek/skip around, we eagerly read all macro arguments into [Expression]s.
 * We also need to eagerly read any nested macro arguments because macro invocations aren't
 * length prefixed, so we can't skip past them.
 *
 * 1. Read macro id
 * 2. get corresponding macro signature
 * 3. Read macro args
 *     1. Recurse/loop to 1 if a macro is one of the args.
 *
 */
class MacroExpressionizer(
    private val reader: IonReader,
    private val macroLookup: java.util.function.Function<MacroRef, Macro>
){
    // TODO: We need these added to a reader in some way.
    private fun IonReader.isMacro(): Boolean = TODO()
    private fun IonReader.isExpressionGroup(): Boolean = TODO()

    // TODO: Consider replacing with an array
    private var expressions: MutableList<EncodingExpression> = mutableListOf()

    /**
     * Caller is responsible for ensuring that the reader is positioned at a macro invocation, but not stepped into
     * the macro.
     */
    fun readMacroExpression() {
        confirm(reader.typeAnnotationSymbols.isEmpty()) { "E-Expressions may not be annotated" }
        reader.stepIn()
        val macroRef = when (reader.next()) {
            // TODO: See if this is correct. Maybe we need to have separate methods for reading macro ids
            SYMBOL -> MacroRef.ByName(reader.stringValue())
            INT -> MacroRef.ById(reader.longValue())
            else -> throw IonException("macro invocation must start with an id (int) or identifier (symbol); found ${reader.type ?: "nothing"}\"")
        }
        val macro = macroLookup.apply(macroRef)
        val signature = macro.signature
        val placeholderIndex = expressions.size
        expressions.add(Expression.Placeholder)

        for (parameter in signature) {
            val nextType = reader.next()
            if (nextType == null && !reader.isMacro()) {
                // End of container?

                // If so, let's check that all required parameters are present.
                break
            }
            if (reader.isExpressionGroup()) {
                // TODO: make sure this function can handle expression groups
                reader.forEachInContainer {
                    when (parameter.type) {
                        Tagged -> {
                            // Easy case, parameter must have a type.
                            readTaggedValue()
                        }
                        else -> {
                            TODO()
                        }
                        // TODO: Other types
                    }
                }
            } else {
                when (parameter.type) {
                    Tagged -> {
                        // Easy case, parameter must have a type.
                        readTaggedValue()
                    }
                    else -> {
                        TODO()
                    }
                    // TODO: Other types
                }
            }
        }

        val endInclusive = expressions.size - 1
        expressions[placeholderIndex] = Expression.EExpression(macroRef, placeholderIndex, endInclusive)
    }

    /**
     * Caller must ensure that the reader is positioned on the value.
     * Does not read expression groups.
     */
    private fun readTaggedValue() {
        // TODO: Could typeAnnotations ever be an array of nulls?
        // NOTE: `toList()` does not allocate for an empty list.
        val annotations = reader.typeAnnotationSymbols.toList()

        if (reader.isNullValue) {
            expressions.add(Expression.NullValue(annotations, reader.type))
        } else when (reader.type) {
            BOOL -> expressions.add(Expression.BoolValue(annotations, reader.booleanValue()))
            INT -> expressions.add(
                when (reader.integerSize!!) {
                    IntegerSize.INT,
                    IntegerSize.LONG -> Expression.LongIntValue(annotations, reader.longValue())
                    IntegerSize.BIG_INTEGER -> Expression.BigIntValue(annotations, reader.bigIntegerValue())
                }
            )
            FLOAT -> expressions.add(Expression.FloatValue(annotations, reader.doubleValue()))
            DECIMAL -> expressions.add(Expression.DecimalValue(annotations, reader.decimalValue()))
            TIMESTAMP -> expressions.add(
                Expression.TimestampValue(
                    annotations,
                    reader.timestampValue()
                )
            )
            STRING -> expressions.add(Expression.StringValue(annotations, reader.stringValue()))
            BLOB -> expressions.add(Expression.BlobValue(annotations, reader.newBytes()))
            CLOB -> expressions.add(Expression.ClobValue(annotations, reader.newBytes()))
            SYMBOL -> expressions.add(Expression.SymbolValue(annotations, reader.symbolValue()))
            LIST -> readListExpression(annotations)
            SEXP -> readSexpExpression(annotations)
            STRUCT -> readStructExpression(annotations)
            NULL, DATAGRAM -> TODO("Unreachable")
            null -> {
                // Must be a macro invocation
                confirm(reader.isMacro()) { "Illegal macro argument. Expecting a tagged type, but found something else?" }
                readMacroExpression()
            }
        }

    }

    private fun readStructExpression(annotations: List<SymbolToken>) {
        val start = expressions.size
        expressions.add(Expression.Placeholder)
        val templateStructIndex = mutableMapOf<String, ArrayList<Int>>()
        reader.forEachInContainer {
            expressions.add(Expression.FieldName(fieldNameSymbol))
            fieldNameSymbol.text?.let {
                val valueIndex = expressions.size
                // Default is an array list with capacity of 1, since the most common case is that a field name occurs once.
                templateStructIndex.getOrPut(it) { ArrayList(1) } += valueIndex
            }
            readTaggedValue()
        }
        val end = expressions.lastIndex
        expressions[start] = Expression.StructValue(annotations, start, end, templateStructIndex)
    }

    private fun readListExpression(annotations: List<SymbolToken>) {
        val seqStart = expressions.size
        expressions.add(Expression.Placeholder)
        reader.forEachInContainer { readTaggedValue() }
        val seqEnd = expressions.lastIndex
        expressions[seqStart] = Expression.ListValue(annotations, seqStart, seqEnd)
    }

    private fun readSexpExpression(annotations: List<SymbolToken>) {
        val seqStart = expressions.size
        expressions.add(Expression.Placeholder)
        reader.forEachInContainer { readTaggedValue() }
        val seqEnd = expressions.lastIndex
        expressions[seqStart] = Expression.ListValue(annotations, seqStart, seqEnd)
    }

    // Helper functions

    /** Utility method for checking that annotations are empty or a single array with the given annotations */
    private fun Array<String>.isEmptyOr(text: String): Boolean = isEmpty() || (size == 1 && this[0] == text)

    /** Throws [IonException] if any annotations are on the current value in this [IonReader]. */
    private fun IonReader.confirmNoAnnotations(location: String) {
        confirm(typeAnnotations.isEmpty()) { "found annotations on $location" }
    }

    /** Moves to the next type and throw [IonException] if it is not the `expected` [IonType]. */
    private fun IonReader.nextAndCheckType(expected: IonType, location: String) {
        confirm(next() == expected) { "$location must be a $expected; found ${type ?: "nothing"}" }
    }

    /** Steps into a container, executes [block], and steps out. */
    private inline fun IonReader.readContainer(block: IonReader.() -> Unit) { stepIn(); block(); stepOut() }

    /** Executes [block] for each remaining value at the current reader depth. */
    // TODO: This probably needs to be updated so that it doesn't step out when it encounters a macro
    private inline fun IonReader.forEachRemaining(block: IonReader.(IonType) -> Unit) { while (next() != null) { block(type) } }

    /** Steps into a container, executes [block] for each value at that reader depth, and steps out. */
    private inline fun IonReader.forEachInContainer(block: IonReader.(IonType) -> Unit) = readContainer { forEachRemaining(block) }
}
