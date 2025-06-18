// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.Ion_1_1_Constants.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.Expression.*
import com.amazon.ion.util.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.ion_reader.*
import java.nio.ByteBuffer

/**
 * [FlatMacroCompiler] wraps an Ion reader. When directed to do so, it will take over advancing and getting values from the
 * reader in order to read one [MacroV2].
 */
internal class FlatMacroCompiler(
    private val getMacro: (MacroRef) -> MacroV2?,
    private val reader: IonReader
) {

    /** The name of the macro that was read. Returns `null` if no macro name is available. */
    var macroName: String? = null
        private set // Only mutable internally

    private lateinit var signature: Array<Macro.Parameter>

    /**
     * Compiles a template macro definition from the reader. Caller is responsible for positioning the reader at—but not
     * stepped into—the macro template s-expression.
     */
    fun compileMacro(): MacroV2 {
        macroName = null
        val expressions = ArrayList<TemplateBodyExpressionModel>()

        confirm(reader.type == IonType.SEXP) { "macro compilation expects a sexp starting with the keyword `macro`" }
        reader.confirmNoAnnotations("a macro definition sexp")
        reader.readContainer {
            reader.next()
            confirm(reader.type == IonType.SYMBOL && reader.stringValue() == "macro") { "macro compilation expects a sexp starting with the keyword `macro`" }

            reader.nextAndCheckType(IonType.SYMBOL, IonType.NULL, "macro name")

            reader.confirmNoAnnotations("macro name")
            if (reader.type != IonType.NULL) {
                macroName = reader.stringValue().also { confirm(isIdentifierSymbol(it!!)) { "invalid macro name: '$it'" } }

//                println("Compiling `$macroName`")
            }
            reader.nextAndCheckType(IonType.SEXP, "macro signature")
            reader.confirmNoAnnotations("macro signature")
            signature = reader.readSignature()
            confirm(reader.next() != null) { "Macro definition is missing a template body expression." }
            reader.compileTemplateBodyExpression(expressions, readLiterally = false, ParentType.TopLevel)
            confirm(reader.next() == null) { "Unexpected ${reader.type} after template body expression." }
        }
        return MacroV2(signature, expressions.toTypedArray())
//            .also { println(it.writeMacroDefinitionToIonString(macroName)) }
    }

    /**
     * Reads the macro signature, populating parameters in [signature].
     * Caller is responsible for making sure that the reader is positioned on (but not stepped into) the signature sexp.
     */
    private fun IonReader.readSignature(): Array<Macro.Parameter> {
        val signature = mutableListOf<Macro.Parameter>()
        var pendingParameter: Macro.Parameter? = null

        forEachInContainer {
            if (type != IonType.SYMBOL) throw IonException("parameter must be a symbol; found ${type}")

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
            val parameterEncoding = when (annotations.size) {
                0 -> Macro.ParameterEncoding.Tagged
                1 -> {
                    val encodingText = annotations[0].text
                    val encoding = Macro.ParameterEncoding.entries.singleOrNull { it.ionTextName == encodingText }
                    if (encoding == null) {
                        // TODO: Check for macro-shaped parameter encodings, and only if it's still null, we throw.
                        throw IonException("unsupported parameter encoding $annotations")
                    }
                    encoding
                }
                2 -> TODO("Qualified references for macro-shaped parameters")
                else -> throw IonException("unsupported parameter encoding $annotations")
            }
            confirm(isIdentifierSymbol(symbolText)) { "invalid parameter name: '$symbolText'" }
            confirm(signature.none { it.variableName == symbolText }) { "redeclaration of parameter '$symbolText'" }
            pendingParameter = Macro.Parameter(symbolText, parameterEncoding, Macro.ParameterCardinality.ExactlyOne)
        }
        // If we have a pending parameter than hasn't been added to the signature, add it here.
        if (pendingParameter != null) signature.add(pendingParameter!!)

        return signature.toTypedArray()
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
    private fun IonReader.compileTemplateBodyExpression(destination: MutableList<TemplateBodyExpressionModel>, readLiterally: Boolean, parentType: ParentType) {
        val annotations = typeAnnotations
        if (annotations.isNotEmpty()) {
            destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.ANNOTATIONS, 0, annotations = annotations))
        }

        if (isInStruct) {
            destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.FIELD_NAME, 0, fieldName = fieldName))
        }


        if (isNullValue()) {
            destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.NULL, 0, value = type))
        } else when (type) {
            IonType.BOOL -> destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.BOOL, 0, primitiveValue = if (booleanValue()) 1 else 0))
            IonType.INT -> {
                when (integerSize!!) {
                    IntegerSize.INT,
                    IntegerSize.LONG -> {
                        destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.INT, 0, primitiveValue = longValue()))
                    }
                    IntegerSize.BIG_INTEGER -> {
                        destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.INT, 0, value = bigIntegerValue()))
                    }
                }
            }
            IonType.FLOAT -> destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.FLOAT, 0, primitiveValue = doubleValue().toRawBits()))
            IonType.DECIMAL -> destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.DECIMAL, 0, value = decimalValue()))
            IonType.TIMESTAMP -> destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.TIMESTAMP, 0, value = timestampValue()))
            IonType.STRING -> destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.STRING, 0, value = stringValue()))
            IonType.BLOB -> destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.BLOB, 0, value = ByteBuffer.wrap(newBytes())))
            IonType.CLOB -> destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.CLOB, 0, value = ByteBuffer.wrap(newBytes())))
            IonType.SYMBOL -> destination.add(TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.SYMBOL, 0, value = symbolValue().text))
            IonType.LIST -> compileList(destination, readLiterally)
            IonType.SEXP -> compileSExpression(parentType, destination, annotations, readLiterally)
            IonType.STRUCT -> compileStruct(destination, readLiterally)
            // IonType.NULL, IonType.DATAGRAM, null
            else -> throw IllegalStateException("Found $type; this should be unreachable.")
        }
    }

    /**
     * Compiles a struct in a macro template.
     * When calling, the reader should be positioned at the struct, but not stepped into it.
     * If this function returns normally, it will be stepped out of the struct.
     * Caller will need to call [IonReader.next] to get the next value.
     */
    private fun IonReader.compileStruct(destination: MutableList<TemplateBodyExpressionModel>, readLiterally: Boolean) {
        val startExpression = TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.STRUCT, -1, value = null)
        destination.add(startExpression)
        val start = destination.size
        forEachInContainer {
            compileTemplateBodyExpression(destination, readLiterally, ParentType.Struct)
        }
        val end = destination.size
        startExpression.length = end - start
    }

    /**
     * Compiles a list or sexp in a macro template.
     * When calling, the reader should be positioned at the sequence, but not stepped into it.
     * If this function returns normally, it will be stepped out of the sequence.
     * Caller will need to call [IonReader.next] to get the next value.
     */
    private fun IonReader.compileList(destination: MutableList<TemplateBodyExpressionModel>, readLiterally: Boolean) {
        val startExpression = TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.LIST, -1, value = null)
        destination.add(startExpression)
        val start = destination.size
        forEachInContainer {
            compileTemplateBodyExpression(destination, readLiterally, ParentType.List)
        }
        val end = destination.size
        startExpression.length = end - start
    }

    enum class ParentType {
        List,
        SExp,
        Struct,
        ExprGroup,
        MacroInvocation,
        TopLevel
    }

    /**
     * Compiles an unclassified S-Expression in a template body expression.
     * When calling, the reader should be positioned at the sexp, but not stepped into it.
     * If this function returns normally, it will be stepped out of the sexp.
     * Caller will need to call [IonReader.next] to get the next value.
     */
    private fun IonReader.compileSExpression(parentType: ParentType, destination: MutableList<TemplateBodyExpressionModel>, annotations: Array<String?>, readLiterally: Boolean) {
        val startExpression = TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.SEXP, -1, value = null)
        destination.add(startExpression)
        val start = destination.size
        stepIn()
        if (next() != null) {
            if (type == IonType.SYMBOL && !readLiterally) {
                when (stringValue()) {
                    TDL_VARIABLE_EXPANSION_SIGIL -> {
                        confirm(annotations.isEmpty()) { "Variable expansion may not be annotated" }
                        confirmNoAnnotations("Variable expansion operator")
                        compileVariableExpansion(startExpression)
                        return
                    }

                    TDL_EXPRESSION_GROUP_SIGIL -> {
                        confirm(annotations.isEmpty()) { "Expression group may not be annotated" }
                        confirmNoAnnotations("Expression group operator")
                        compileExpressionTail(destination, false, ParentType.ExprGroup)
                        startExpression.expressionKind = TemplateBodyExpressionModel.Kind.EXPRESSION_GROUP
                        startExpression.length = destination.size - start
                        return
                    }

                    TDL_MACRO_INVOCATION_SIGIL -> {
                        confirm(annotations.isEmpty()) { "Macro invocation may not be annotated" }
                        confirmNoAnnotations("Macro invocation operator")
                        next()
                        val macroRef = readMacroReference()

                        // TODO: Come up with a consistent strategy for handling special forms.
                        if (macroRef.hasName()) when (macroRef.name) {
                            // TODO: Compile a "LITERAL" expression that holds a `ValueReaderBase` that can lazily parse whatever is in here.
                            SystemSymbols_1_1.LITERAL.text -> {
                                // Drop the placeholder.
                                destination.removeLastOrNull()
                                forEachRemaining { compileTemplateBodyExpression(destination, readLiterally = true, parentType) }
                                stepOut()
                                return
                            }
                        }

                        val macro = getMacro(macroRef)
                            ?: SystemMacro.getMacroOrSpecialForm(macroRef)
                            ?: throw IonException("Unrecognized macro: $macroRef")


                        // TODO: coalesce these branch conditions
                        if (macro.body == null) {
                            when (macro.systemAddress) {
                                SystemMacro.META_ADDRESS -> {
                                    // Discard the arguments
                                    // TODO: Make this cheaper by skipping rather than compiling.
                                    compileExpressionTail(mutableListOf(), true, ParentType.MacroInvocation)
                                    if (parentType == ParentType.MacroInvocation) {
                                        // Replace with empty expression group.
                                        startExpression.length = 0
                                        startExpression.expressionKind =
                                            TemplateBodyExpressionModel.Kind.EXPRESSION_GROUP
                                    } else {
                                        // Just remove it entirely
                                        destination.removeLast()
                                    }
                                }
                                SystemMacro.NONE_ADDRESS -> {
                                    if (parentType == ParentType.MacroInvocation) {
                                        // Replace with empty expression group.
                                        startExpression.length = 0
                                        startExpression.expressionKind =
                                            TemplateBodyExpressionModel.Kind.EXPRESSION_GROUP
                                    } else {
                                        // Just remove it entirely
                                        destination.removeLast()
                                    }
                                }
                                SystemMacro.VALUES_ADDRESS -> {
                                    if (parentType == ParentType.MacroInvocation) {
                                        compileExpressionTail(destination, false, parentType)
                                        startExpression.expressionKind = TemplateBodyExpressionModel.Kind.EXPRESSION_GROUP
                                        startExpression.length = destination.size - start
                                    } else {
                                        destination.removeLast()
                                        compileExpressionTail(destination, false, parentType)
                                    }

                                }
                                SystemMacro.DEFAULT_ADDRESS -> {
                                    val args: MutableList<TemplateBodyExpressionModel> = mutableListOf()
                                    // TODO: Capture rest args in an expression group
                                    compileExpressionTail(args, readLiterally = false, ParentType.MacroInvocation)
                                    val arg0 = args[0]
                                    when (arg0.expressionKind) {
                                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 -> {
                                            destination.removeLast()
                                            // Copy in this arg, discard the other one
                                            for (i in 0..arg0.length) {
                                                destination.add(args[i])
                                            }
                                        }
                                        TemplateBodyExpressionModel.Kind.EXPRESSION_GROUP -> {
                                            if (arg0.length == 0) {
                                                destination.removeLast()
                                                // Discard this arg, copy in the other one
                                                for (i in 1 until args.size) {
                                                    destination.add(args[i])
                                                }
                                            } else {
                                                // Copy in a full invocation
                                                destination.addAll(args)
                                                startExpression.expressionKind = TemplateBodyExpressionModel.Kind.INVOCATION
                                                startExpression.length = destination.size - start
                                                startExpression.value = macro
                                            }
                                        }
                                        else -> {
                                            // Do a full invocation
                                            destination.addAll(args)
                                            startExpression.expressionKind = TemplateBodyExpressionModel.Kind.INVOCATION
                                            startExpression.length = destination.size - start
                                            startExpression.value = macro
                                        }


                                    }
                                }
                                // Non-inlineable system macros
                                else -> {
                                    compileExpressionTail(destination, false, ParentType.MacroInvocation)
                                    startExpression.expressionKind = TemplateBodyExpressionModel.Kind.INVOCATION
                                    startExpression.length = destination.size - start
                                    startExpression.value = macro
                                }
                            }
//                            val argumentPositions = calculateArgumentPositions(macro.signature, destination, start, destination.size)
//                            startExpression.indices = argumentPositions
                        } else {
                            destination.removeLast()
//                            println("Inlining: $macroRef")
                            compileInlineMacroInvocation(destination, macro)
                        }
                        return
                    }
                }
            }
            // Compile the value we're already positioned on before compiling the rest of the s-expression
            compileTemplateBodyExpression(destination, readLiterally, ParentType.SExp)
        }
        compileExpressionTail(destination, readLiterally, ParentType.SExp)
        startExpression.expressionKind = TemplateBodyExpressionModel.Kind.SEXP
        startExpression.length = destination.size - start
    }

    private fun IonReader.compileInlineMacroInvocation(destination: MutableList<TemplateBodyExpressionModel>, macro: MacroV2) {
        // Copy the macro inline.
        // Steps:
        //  - Need to read the arguments into a map
        //  - Copy the body into this one
        //  - Replace variables with the arguments

//        println("Inlining macro $macro")
//        println("Reading arguments...")
        // Read the args to the macro invocation
        val args: MutableList<TemplateBodyExpressionModel> = mutableListOf()
        // TODO: Capture rest args in an expression group
        compileExpressionTail(args, readLiterally = false, ParentType.MacroInvocation)
//        println("  " + args)
        // Build a lookup for the arguments
        val argumentIndices = IntArray(macro.signature.size)
        var position = 0
        for (i in 0 until macro.signature.size) {
            if (position < args.size) {
                argumentIndices[i] = position
                val currentExpression = args[position++]
                position += currentExpression.length
            } else {
                argumentIndices[i] = -1
            }
        }
//        println("  " + argumentIndices.contentToString())
        // Copy to body into this macro body
//        println("Copying macro body")
        val body = macro.body!!
        copyInlinedContent(0, body.size, body, destination, args.toTypedArray(), argumentIndices)
    }

    // Visible for testing
    internal fun copyInlinedContent(
        start: Int,
        endExclusive: Int,
        source: Array<TemplateBodyExpressionModel>,
        destination: MutableList<TemplateBodyExpressionModel>,
        args: Array<TemplateBodyExpressionModel>,
        argIndices: IntArray,
        indent: String = "",
    ) {
        var i = start
        while (i < endExclusive) {
            val next = source[i++]
            if (next.expressionKind == TemplateBodyExpressionModel.Kind.VARIABLE && argIndices.isNotEmpty()) {
//                println("${indent}injecting variable: $next")
                copyArgValueInline(next.value as Int, destination, args, argIndices, indent)

            } else if (next.length == 0) {
//                println("${indent}Copying value: $next")
                destination.add(next)

            } else {
                // Macro invocations are handled here, same as any container. Since a macro can only depend on macros
                // that were compiled earlier, the nested invocation that we're currently inlining has no more nestable
                // invocations.
                val startExpression = next.copy()
//                println("${indent}Copying container value: $next")
                destination.add(startExpression)
                val destinationContainerStart = destination.size
                copyInlinedContent(i, i + startExpression.length, source, destination, args, argIndices, "$indent  ")
                val destinationContainerEnd = destination.size
                startExpression.length = destinationContainerEnd - destinationContainerStart
            }
            i += next.length
        }
    }

    fun copyArgValueInline(
        signatureIndex: Int,
        destination: MutableList<TemplateBodyExpressionModel>,
        args: Array<TemplateBodyExpressionModel>,
        argIndices: IntArray,
        indent: String = "",
    ) {
        val argIndex = argIndices[signatureIndex]
        if (argIndex < 0) {
            val absentArg = TemplateBodyExpressionModel(TemplateBodyExpressionModel.Kind.EXPRESSION_GROUP, 0)
            destination.add(absentArg)
        } else {
            val argumentExpression = args[argIndex]
            if (argumentExpression.length == 0) {
//                println("${indent}Copying argument: $argumentExpression")
                destination.add(argumentExpression)
            } else {
//                println("${indent}Copying container argument: $argumentExpression")
                destination.add(argumentExpression.copy())
                val argumentDestinationStart = destination.size
                val argumentSourceContentStart = argIndex + 1
                copyInlinedContent(argumentSourceContentStart, argumentSourceContentStart + argumentExpression.length, args, destination, emptyArray(), intArrayOf(), indent + "  ")
                val argumentDestinationEnd = destination.size
                argumentExpression.length = argumentDestinationEnd - argumentDestinationStart
            }
        }
    }


    internal fun copyInlineContainer() {

    }


    /**
     * Must be positioned on the (expected) macro reference.
     */
    private fun IonReader.readMacroReference(): MacroRef {
        val annotations = getTypeAnnotationSymbols()
        val moduleName = when (annotations.size) {
            0 -> null
            1 -> annotations[0].text
            else -> throw IonException("macro name may only be qualified by one module name")
        }
        return when (type) {
            IonType.SYMBOL -> MacroRef.byName(moduleName, stringValue())
            IonType.INT -> {
                val id = intValue()
                if (id < 0) throw IonException("Macro ID must be non-negative: $id")
                MacroRef.byId(moduleName, id)
            }
            else -> throw IonException("macro invocation must start with an id (int) or identifier (symbol); found ${type ?: "nothing"}\"")
        }
    }

    private fun IonReader.compileVariableExpansion(placeholder: TemplateBodyExpressionModel) {
        next()
        confirm(type == IonType.SYMBOL) { "Variable names must be symbols" }
        val name = stringValue()
        confirmNoAnnotations("on variable reference '$name'")
        val index = signature.indexOfFirst { it.variableName == name }
        confirm(index >= 0) { "variable '$name' is not recognized" }
        placeholder.value = index
        placeholder.length = 0
        placeholder.expressionKind = TemplateBodyExpressionModel.Kind.VARIABLE
        confirm(next() == null) { "Variable expansion should contain only the variable name." }
        stepOut()
    }

    private fun IonReader.compileExpressionTail(destination: MutableList<TemplateBodyExpressionModel>, readLiterally: Boolean, thisType: ParentType): Int {
        forEachRemaining {
            compileTemplateBodyExpression(destination, readLiterally, thisType)
        }
        val seqEnd = destination.size
        stepOut()
        return seqEnd
    }

    // Helper functions

    /** Utility method for checking that annotations are empty or a single array with the given annotations */
    private fun List<SymbolToken>.isEmptyOr(text: String): Boolean = isEmpty() || (size == 1 && this[0].assumeText() == text)

    /** Throws [IonException] if any annotations are on the current value in this [IonReader]. */
    private fun IonReader.confirmNoAnnotations(location: String) {
        confirm(this.typeAnnotations.isEmpty()) { "found annotations on $location" }
    }

    /** Moves to the next type and throw [IonException] if it is not the `expected` [IonType]. */
    private fun IonReader.nextAndCheckType(expected: IonType, location: String) {
        confirm(next() == expected) { "$location must be a $expected; found ${type ?: "nothing"}" }
    }

    /** Moves to the next type and throw [IonException] if it is not the `expected` [IonType]. */
    private fun IonReader.nextAndCheckType(expected0: IonType, expected1: IonType, location: String) {
        next()
        confirm(type == expected0 || type == expected1) { "$location must be a $expected0 or $expected1; found ${type ?: "nothing"}" }
    }

    /** Steps into a container, executes [block], and steps out. */
    private inline fun IonReader.readContainer(block: () -> Unit) { stepIn(); block(); stepOut() }

    /** Executes [block] for each remaining value at the current reader depth. */
    private inline fun IonReader.forEachRemaining(block: (IonType) -> Unit) { while (next() != null) { block(type) } }

    /** Steps into a container, executes [block] for each value at that reader depth, and steps out. */
    private inline fun IonReader.forEachInContainer(block: (IonType) -> Unit) = readContainer { forEachRemaining(block) }
}
