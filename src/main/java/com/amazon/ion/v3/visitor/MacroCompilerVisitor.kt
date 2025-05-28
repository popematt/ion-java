package com.amazon.ion.v3.visitor

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.util.*
import com.amazon.ion.util.confirm
import com.amazon.ion.v3.*
import java.math.BigInteger
import java.nio.ByteBuffer

class MacroCompilerVisitor : VisitingReaderCallbackBase() {
    var name: String? = null
        private set

    private val signature: MutableList<Macro.Parameter> = mutableListOf()
    private val body: MutableList<Expression.TemplateBodyExpression> = mutableListOf()

    val macro: Macro
        get() = TemplateMacro(signature.toList(), body.toList())

    private var state = INIT

    private val signatureReader = SignatureReader(signature)
    private val bodyReader = BodyVisitor(body)

    fun reset() {
        name = null
        signature.clear()
        body.clear()
        state = INIT
    }

    companion object {
        const val INIT = 0
        const val AFTER_MACRO_KW = 1
        const val AFTER_NAME = 2
        const val AFTER_SIGNATURE = 3
        const val AFTER_BODY = 4
    }

    override fun onValue(type: TokenType): VisitingReaderCallback {
        when (state) {
            INIT -> {
                if (type == TokenType.SYMBOL) {
                    return this
                } else {
                    throw IonException("Expected the keyword 'macro'; found an $type")
                }
            }
            AFTER_MACRO_KW -> {
                if (type == TokenType.SYMBOL) {
                    return this
                } else if (type == TokenType.NULL) {
                    // TODO: Remove this branch, I think.
                } else if (type == TokenType.SEXP) {
                    state = AFTER_NAME
                    return signatureReader
                } else {
                    throw IonException("Expected the keyword 'macro' to be followed by the macro name or signature.")
                }
            }
            AFTER_NAME -> {
                if (type == TokenType.SEXP) {
                    return signatureReader
                } else {
                    throw IonException("Expected to find macro signature. Instead found a $type")
                }
            }
            AFTER_SIGNATURE -> return bodyReader
            AFTER_BODY -> throw IonException("There should be no values after the template body.")
        }
        throw IonException("Unexpected value in macro definition: $type")
    }

    override fun onSymbol(value: String?, sid: Int) {
        if (state == INIT) {
            // TODO: Check for 'macro' keyword
            state = AFTER_MACRO_KW
        } else if (state == AFTER_MACRO_KW) {
            name = value ?: throw IonException("When macro name is present it must have known text")
            state = AFTER_NAME
        } else {
            throw IonException()
        }
    }

    class SignatureReader(val signature: MutableList<Macro.Parameter>): VisitingReaderCallbackBase() {
        var pendingParameter: Macro.Parameter? = null
        var annotations: Array<String?> = EMPTY_ARRAY

        companion object {
            private val EMPTY_ARRAY = emptyArray<String?>()
        }

        override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback? {
            this.annotations = annotations.toStringArray()
            return this
        }

        override fun onValue(type: TokenType): VisitingReaderCallback? {
            if (type != TokenType.SYMBOL) {
                throw IonException("expected macro parameter definition, found $type")
            }
            return this
        }

        override fun onSymbol(value: String?, sid: Int) {

            if (value == null) throw IonException("Symbols in macro signature declarations must have know text.")

            // Is it cardinality?
            val cardinality = Macro.ParameterCardinality.fromSigil(value)
            if (cardinality != null) {
                if (annotations.isNotEmpty()) {
                    throw IonException("Cardinality may not have annotations")
                }
                if (pendingParameter == null) {
                    throw IonException("Found an orphaned cardinality in macro signature")
                } else {
                    signature.add(pendingParameter!!.copy(cardinality = cardinality))
                    pendingParameter = null
                    return
                }
            }

            // No? Then it's a parameter name.

            if (pendingParameter != null) signature.add(pendingParameter!!)
            val parameterEncoding = when (annotations.size) {
                0 -> Macro.ParameterEncoding.Tagged
                1 -> {
                    val encodingText = annotations[0]
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
            annotations = EMPTY_ARRAY
            confirm(isIdentifierSymbol(value)) { "invalid parameter name: '$value'" }
            confirm(signature.none { it.variableName == value }) { "redeclaration of parameter '$value'" }
            pendingParameter = Macro.Parameter(value, parameterEncoding, Macro.ParameterCardinality.ExactlyOne)
        }

        private fun isIdentifierSymbol(symbol: String): Boolean {
            if (symbol.isEmpty()) return false

            // If the symbol's text matches an Ion keyword, it's not an identifier symbol.
            // Eg, the symbol 'false' must be quoted and is not an identifier symbol.
            if (_Private_IonTextAppender.isIdentifierKeyword(symbol)) return false

            if (!_Private_IonTextAppender.isIdentifierStart(symbol[0].code)) return false

            return symbol.all { c -> _Private_IonTextAppender.isIdentifierPart(c.code) }
        }
    }

    class BodyVisitor(val body: MutableList<Expression.TemplateBodyExpression>) : VisitingReaderCallbackBase() {

        companion object {
            private val EMPTY_ARRAY = emptyArray<String?>()
        }

        private var annotations: Array<String?> = EMPTY_ARRAY
        private var pendingContainers = ArrayList<Int>()

        // TODO: Expressions should just contain strings because the current symbol table might not be in use by the
        //       time the macro is invoked.
        private fun annotationSymbols(): List<SymbolToken> {
            val a = annotations.map { _Private_Utils.newSymbolToken(it) }
            annotations = EMPTY_ARRAY
            return a
        }

        override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback {
            this.annotations = annotations.toStringArray()
            return this
        }

        override fun onField(fieldName: String?, fieldSid: Int): VisitingReaderCallback {
            body.add(Expression.FieldName(_Private_Utils.newSymbolToken(fieldName, fieldSid)))
            return this
        }

        override fun onListStart() {
            val startInclusive = body.size
            pendingContainers.add(startInclusive)
            body.add(Expression.ListValue(annotationSymbols(), startInclusive, -1))
        }

        override fun onListEnd() {
            val endExclusive = body.size
            val pendingContainerIndex = pendingContainers.removeLast()
            // TODO: Stop allocating here by mutating instead of copying
            body[pendingContainerIndex] = (body[pendingContainerIndex] as Expression.ListValue).copy(endExclusive = endExclusive)
        }

        override fun onSexpStart() {
            // TODO: Some way to peek ahead...
            val startInclusive = body.size
            pendingContainers.add(startInclusive)
            body.add(Expression.SExpValue(annotationSymbols(), startInclusive, -1))
        }

        override fun onClause(symbolText: String?, sid: Int): VisitingReaderCallback {
            when (symbolText) {
                "." -> {
                    TODO("Macros")
                }
                ".." -> {
                    TODO("Expression groups")
                }
                "%" -> {
                    TODO("variable")
                }
                else -> {
                    TODO()
                }
            }
        }

        override fun onSexpEnd() {
            val endExclusive = body.size
            val pendingContainerIndex = pendingContainers.removeLast()
            // TODO: Stop allocating here by mutating instead of copying
            body[pendingContainerIndex] = (body[pendingContainerIndex] as Expression.SExpValue).copy(endExclusive = endExclusive)
        }

        override fun onStructStart() {
            val startInclusive = body.size
            pendingContainers.add(startInclusive)
            body.add(Expression.StructValue(annotationSymbols(), startInclusive, -1, templateStructIndex = emptyMap()))
        }

        override fun onStructEnd() {
            val endExclusive = body.size
            val pendingContainerIndex = pendingContainers.removeLast()
            // TODO: Stop allocating here by mutating instead of copying
            body[pendingContainerIndex] = (body[pendingContainerIndex] as Expression.StructValue).copy(endExclusive = endExclusive)
        }

        override fun onNull(value: IonType) {
            body.add(Expression.NullValue(annotationSymbols(), value))
        }

        override fun onBoolean(value: Boolean) {
            body.add(Expression.BoolValue(annotationSymbols(), value))
        }

        override fun onLongInt(value: Long) {
            body.add(Expression.LongIntValue(annotationSymbols(), value))
        }

        override fun onBigInt(value: BigInteger) {
            body.add(Expression.BigIntValue(annotationSymbols(), value))
        }

        override fun onFloat(value: Double) {
            body.add(Expression.FloatValue(annotationSymbols(), value))
        }

        override fun onDecimal(value: Decimal) {
            body.add(Expression.DecimalValue(annotationSymbols(), value))
        }

        override fun onTimestamp(value: Timestamp) {
            body.add(Expression.TimestampValue(annotationSymbols(), value))
        }

        override fun onString(value: String) {
            body.add(Expression.StringValue(annotationSymbols(), value))
        }

        override fun onSymbol(value: String?, sid: Int) {
            body.add(Expression.SymbolValue(annotationSymbols(), _Private_Utils.newSymbolToken(value, sid)))
        }

        override fun onClob(value: ByteBuffer) {
            val len = value.limit() - value.position()
            val bytes = ByteArray(len)
            value.get(bytes)
            body.add(Expression.ClobValue(annotationSymbols(), bytes))
        }

        override fun onBlob(value: ByteBuffer) {
            val len = value.limit() - value.position()
            val bytes = ByteArray(len)
            value.get(bytes)
            body.add(Expression.BlobValue(annotationSymbols(), bytes))
        }
    }

    class TemplateBodyVisitor(val body: MutableList<Expression.TemplateBodyExpression>) : VisitingReaderCallbackBase() {

        companion object {
            private val EMPTY_ARRAY = emptyArray<String?>()
        }

        private var annotations: Array<String?> = EMPTY_ARRAY
        private var pendingContainers = ArrayList<Int>()

        // TODO: Expressions should just contain strings because the current symbol table might not be in use by the
        //       time the macro is invoked.
        private fun annotationSymbols(): List<SymbolToken> {
            val a = annotations.map { _Private_Utils.newSymbolToken(it) }
            annotations = EMPTY_ARRAY
            return a
        }

        override fun onValue(type: TokenType): VisitingReaderCallback? {
            if (type == TokenType.SEXP && annotations.isEmpty()) {
                // It might be a special form, variable, macro invocation, or expression group.
                return TemplateBodyVisitor(body)
            } else {
                return this
            }
        }

        override fun onAnnotation(annotations: AnnotationIterator): VisitingReaderCallback {
            this.annotations = annotations.toStringArray()
            return this
        }

        override fun onField(fieldName: String?, fieldSid: Int): VisitingReaderCallback {
            body.add(Expression.FieldName(_Private_Utils.newSymbolToken(fieldName, fieldSid)))
            return this
        }

        override fun onListStart() {
            val startInclusive = body.size
            pendingContainers.add(startInclusive)
            body.add(Expression.ListValue(annotationSymbols(), startInclusive, -1))
        }

        override fun onListEnd() {
            val endExclusive = body.size
            val pendingContainerIndex = pendingContainers.removeLast()
            // TODO: Stop allocating here by mutating instead of copying
            body[pendingContainerIndex] = (body[pendingContainerIndex] as Expression.ListValue).copy(endExclusive = endExclusive)
        }

        override fun onSexpStart() {
            // TODO: Some way to peek ahead...
            val startInclusive = body.size
            pendingContainers.add(startInclusive)
            body.add(Expression.SExpValue(annotationSymbols(), startInclusive, -1))
        }

        override fun onSexpEnd() {
            val endExclusive = body.size
            val pendingContainerIndex = pendingContainers.removeLast()
            // TODO: Stop allocating here by mutating instead of copying
            body[pendingContainerIndex] = (body[pendingContainerIndex] as Expression.SExpValue).copy(endExclusive = endExclusive)
        }

        override fun onStructStart() {
            val startInclusive = body.size
            pendingContainers.add(startInclusive)
            body.add(Expression.StructValue(annotationSymbols(), startInclusive, -1, templateStructIndex = emptyMap()))
        }

        override fun onStructEnd() {
            val endExclusive = body.size
            val pendingContainerIndex = pendingContainers.removeLast()
            // TODO: Stop allocating here by mutating instead of copying
            body[pendingContainerIndex] = (body[pendingContainerIndex] as Expression.StructValue).copy(endExclusive = endExclusive)
        }

        override fun onNull(value: IonType) {
            body.add(Expression.NullValue(annotationSymbols(), value))
        }

        override fun onBoolean(value: Boolean) {
            body.add(Expression.BoolValue(annotationSymbols(), value))
        }

        override fun onLongInt(value: Long) {
            body.add(Expression.LongIntValue(annotationSymbols(), value))
        }

        override fun onBigInt(value: BigInteger) {
            body.add(Expression.BigIntValue(annotationSymbols(), value))
        }

        override fun onFloat(value: Double) {
            body.add(Expression.FloatValue(annotationSymbols(), value))
        }

        override fun onDecimal(value: Decimal) {
            body.add(Expression.DecimalValue(annotationSymbols(), value))
        }

        override fun onTimestamp(value: Timestamp) {
            body.add(Expression.TimestampValue(annotationSymbols(), value))
        }

        override fun onString(value: String) {
            body.add(Expression.StringValue(annotationSymbols(), value))
        }

        open override fun onSymbol(value: String?, sid: Int) {
            body.add(Expression.SymbolValue(annotationSymbols(), _Private_Utils.newSymbolToken(value, sid)))
        }

        override fun onClob(value: ByteBuffer) {
            val len = value.limit() - value.position()
            val bytes = ByteArray(len)
            value.get(bytes)
            body.add(Expression.ClobValue(annotationSymbols(), bytes))
        }

        override fun onBlob(value: ByteBuffer) {
            val len = value.limit() - value.position()
            val bytes = ByteArray(len)
            value.get(bytes)
            body.add(Expression.BlobValue(annotationSymbols(), bytes))
        }
    }

}
