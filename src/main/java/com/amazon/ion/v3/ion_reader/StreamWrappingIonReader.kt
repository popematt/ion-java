package com.amazon.ion.v3.ion_reader

import com.amazon.ion.Decimal
import com.amazon.ion.IntegerSize
import com.amazon.ion.IonException
import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.SymbolTable
import com.amazon.ion.SymbolToken
import com.amazon.ion.Timestamp
import com.amazon.ion.impl._Private_Utils
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.template.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date

/**
 * A wrapper for [StreamReader] that implements [IonReader].
 * This implementation does not handle top-level values.
 */
class StreamWrappingIonReader: IonReader {

    private lateinit var templateReaderPool: TemplateResourcePool
    private lateinit var reader: ValueReader

    private var type: IonType? = null
    private var fieldName: String? = null
    private var fieldNameSid: Int = -1

    private val annotationState = Annotations()

    private val readerManager = ReaderManager()


    fun init(reader: ValueReader, templateReaderPool: TemplateResourcePool) {
        readerManager.pushReader(reader)
        this.reader = reader
        this.templateReaderPool = templateReaderPool
        reset()
    }

    fun init(reader: ValueReader) {
        readerManager.pushReader(reader)
        this.reader = reader
        reset()
    }

    private fun reset() {
        type = null
        fieldName = null
        fieldNameSid = -1
        annotationState.annotationsSize = 0
    }

    override fun close() {
        readerManager.close()
    }

    override fun <T : Any?> asFacet(facetType: Class<T>?): T {
        TODO("Not yet implemented")
    }

    override fun hasNext(): Boolean = TODO("Deprecated and unsupported")

    override fun next(): IonType? {
        reset()
        if (reader.currentToken() != TokenTypeConst.UNSET) {
            reader.skip()
        }
        return _next()
    }


    private tailrec fun _next(): IonType? {
        val token = reader.nextToken()

        type = when (token) {
            TokenTypeConst.NULL -> reader.ionType()
            TokenTypeConst.BOOL -> IonType.BOOL
            TokenTypeConst.INT -> IonType.INT
            TokenTypeConst.FLOAT -> IonType.FLOAT
            TokenTypeConst.DECIMAL -> IonType.DECIMAL
            TokenTypeConst.TIMESTAMP -> IonType.TIMESTAMP
            TokenTypeConst.STRING -> IonType.STRING
            TokenTypeConst.SYMBOL -> IonType.SYMBOL
            TokenTypeConst.CLOB -> IonType.CLOB
            TokenTypeConst.BLOB -> IonType.BLOB
            TokenTypeConst.LIST -> IonType.LIST
            TokenTypeConst.SEXP -> IonType.SEXP
            TokenTypeConst.STRUCT -> IonType.STRUCT
            TokenTypeConst.ANNOTATIONS -> {
                reader.annotations().use { annotationState.storeAnnotations(it) }
                null
            }
            TokenTypeConst.FIELD_NAME -> {
                fieldNameSid = (reader as StructReader).fieldNameSid()
                if (fieldNameSid < 0) {
                    fieldName = (reader as StructReader).fieldName()
                } else {
                    fieldName = reader.lookupSid(fieldNameSid)
                }
                null
            }
            TokenTypeConst.END -> {
                if (readerManager.isTopAContainer()) {
                    return null
                } else if (readerManager.readerDepth == 1) {
                    return null
                } else {
                    reader = readerManager.popReader()!!
                    null
                }
            }
            TokenTypeConst.MACRO_INVOCATION -> {
                val macro = reader.macroValue()
                val args = reader.macroArguments(macro.signature)
                val eexp = templateReaderPool.startEvaluation(macro, args)
                readerManager.pushReader(eexp)
                reader = eexp
                type = null
                null
            }
            TokenTypeConst.VARIABLE_REF -> {
//                val variableReader = (reader as TemplateReader).deprecated_variableValue()
//                readerManager.pushReader(variableReader)
//                reader = variableReader
                null
            }
            else -> TODO("Unreachable: ${TokenTypeConst(token)}")
        }
        if (type != null) {
            return type
        }
        return _next()
    }

    override fun stepIn() {
        val child = when (reader.currentToken()) {
            TokenTypeConst.NULL -> throw IonException("Cannot step into a null value")
            TokenTypeConst.LIST -> reader.listValue()
            TokenTypeConst.SEXP -> reader.sexpValue()
            TokenTypeConst.STRUCT -> reader.structValue()
            else -> throw IonException("Cannot step in unless positioned on a container")
        }
        readerManager.pushContainer(child)
        reader = child
        type = null
        fieldName = null
        fieldNameSid = -1
    }

    override fun stepOut() {
        reader = readerManager.popContainer()
    }

    override fun getDepth(): Int = readerManager.containerDepth

    override fun getSymbolTable(): SymbolTable {
        TODO()
    }

    override fun getType(): IonType? = type

    override fun getIntegerSize(): IntegerSize {
        val size = reader.valueSize()
        return when {
            size < 0 -> IntegerSize.BIG_INTEGER
            size <= 4 -> IntegerSize.INT
            size <= 8 -> IntegerSize.LONG
            else -> IntegerSize.BIG_INTEGER
        }
    }

    override fun getTypeAnnotations(): Array<String?> = annotationState.getTypeAnnotations()
    override fun getTypeAnnotationSymbols(): Array<SymbolToken> = annotationState.getTypeAnnotationSymbols()
    override fun iterateTypeAnnotations(): Iterator<String?> = annotationState.iterateTypeAnnotations()

    override fun getFieldId(): Int = fieldNameSid

    override fun getFieldName(): String? = fieldName

    override fun getFieldNameSymbol(): SymbolToken = _Private_Utils.newSymbolToken(fieldName, fieldNameSid)

    override fun isNullValue(): Boolean = reader.currentToken() == TokenTypeConst.NULL

    override fun isInStruct(): Boolean {
        return readerManager.isInStruct
    }

    override fun booleanValue(): Boolean = reader.booleanValue()
    override fun intValue(): Int = reader.longValue().toInt()
    override fun longValue(): Long = reader.longValue()
    // TODO: Support real big integers
    override fun bigIntegerValue(): BigInteger = reader.longValue().toBigInteger()
    override fun doubleValue(): Double = reader.doubleValue()
    override fun bigDecimalValue(): BigDecimal = reader.decimalValue()
    override fun decimalValue(): Decimal = reader.decimalValue()
    override fun dateValue(): Date = timestampValue().dateValue()
    override fun timestampValue(): Timestamp = reader.timestampValue()
    override fun stringValue(): String? {
        return when (reader.currentToken()) {
            TokenTypeConst.NULL -> null
            TokenTypeConst.STRING -> reader.stringValue()
            TokenTypeConst.SYMBOL -> reader.symbolValue()
            else -> throw IonException("Reader is not positioned on a string")
        }
    }
    override fun symbolValue(): SymbolToken {
        val sid = reader.symbolValueSid()
        val text = if (sid < 0) {
            reader.symbolValue()
        } else {
            reader.lookupSid(sid)
        }
        return _Private_Utils.newSymbolToken(text, sid)
    }

    override fun byteSize(): Int = TODO("lobs not implemented")
    override fun newBytes(): ByteArray = TODO("lobs not implemented")
    override fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int  = TODO("lobs not implemented")
}
