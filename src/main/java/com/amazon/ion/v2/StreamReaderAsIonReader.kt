package com.amazon.ion.v2

import com.amazon.ion.Decimal
import com.amazon.ion.IntegerSize
import com.amazon.ion.IonException
import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.SymbolTable
import com.amazon.ion.SymbolToken
import com.amazon.ion.Timestamp
import com.amazon.ion.impl._Private_Utils
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date

class StreamReaderAsIonReader(valueReader: ValueReader? = null): IonReader {
    init { valueReader?.let(::initWith) }

    private inline fun <reified T> Array<T?>.grow(): Array<T?> {
        val newSize = this.size * 2
        val newArray = arrayOfNulls<T>(newSize)
        this.copyInto(newArray)
        return newArray
    }

    private fun IntArray.grow(): IntArray {
        val newSize = this.size * 2
        val newArray = IntArray(newSize)
        this.copyInto(newArray)
        return newArray
    }

    private fun pushContainer(reader: ValueReader) {
        if (stackSize >= stack.size) {
            stack = stack.grow()
        }
        stack[stackSize++] = reader
    }

    private var stack = arrayOfNulls<ValueReader>(32)
    private var stackSize: Int = 0

    private var type: IonType? = null
    private lateinit var reader: ValueReader
    private var fieldName: String? = null
    private var fieldNameSid: Int = -1

    private var annotations: Array<String?> = arrayOfNulls(8)
    private var annotationsSids = IntArray(8) { -1 }
    private var annotationsSize = 0

    internal fun initWith(reader: ValueReader) {
        stackSize = 0
        this.reader = reader
        reset()
    }

    private fun storeAnnotations() {
        val annotationsIterator = reader.annotations()
        var annotationCount = 0
        while (annotationsIterator.hasNext()) {
            if (annotationCount >= annotations.size) {
                annotations = annotations.grow()
                annotationsSids = annotationsSids.grow()
            }
            annotationsIterator.next()
            annotationsSids[annotationCount] = annotationsIterator.getSid()
            annotations[annotationCount] = annotationsIterator.getText()
            annotationCount++
        }
        annotationsSize = annotationCount
    }

    private fun reset() {
        type = null
        fieldName = null
        fieldNameSid = -1
        annotationsSize = 0
    }

    override fun close() {
        reader.close()
        stack.iterator().forEach { it?.close() }
        stackSize = 0
    }

    override fun <T : Any?> asFacet(facetType: Class<T>?): T {
        TODO("Not yet implemented")
    }

    override fun hasNext(): Boolean = TODO("Deprecated and unsupported")

    override fun next(): IonType? {
        reset()
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
                storeAnnotations()
                null
            }
            TokenTypeConst.FIELD_NAME -> {
                fieldNameSid = (reader as StructReader).fieldNameSid()
                if (fieldNameSid < 0) {
                    // Inline field name text
                    fieldName = (reader as StructReader).fieldName()
                } else {
                    fieldName = reader.lookupSid(fieldNameSid)
                }
                null
            }
            TokenTypeConst.END -> return null
            TokenTypeConst.IVM -> {
                TODO("Switch versions, if needed.")
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
        pushContainer(reader)
        reader = child
        type = null
        fieldName = null
        fieldNameSid = -1
    }

    override fun stepOut() {
        if (stackSize == 0) {
            throw IonException("Nothing to step out of.")
        }
        val parent = stack[--stackSize]!!
        reader.close()
        reader = parent
    }

    override fun getDepth(): Int = stack.size

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

    override fun getTypeAnnotations(): Array<String?> = annotations.copyOf(annotationsSize)

    override fun getTypeAnnotationSymbols(): Array<SymbolToken> {
        return Array<SymbolToken>(annotations.size) { i ->
            _Private_Utils.newSymbolToken(annotations[i], annotationsSids[i]) as SymbolToken
        }
    }

    override fun iterateTypeAnnotations(): Iterator<String?> = annotationIterator.also { it.reset() }

    private val annotationIterator: TypeAnnotationsIterator = TypeAnnotationsIterator(this)

    class TypeAnnotationsIterator(val reader: StreamReaderAsIonReader) : Iterator<String?> {
        fun reset() {
            i = 0
        }

        var i = 0
        override fun hasNext(): Boolean = i < reader.annotationsSize

        override fun next(): String? {
            if (!hasNext()) throw NoSuchElementException()
            return reader.annotations[i++]
        }
    }


    override fun getFieldId(): Int = fieldNameSid

    override fun getFieldName(): String? = fieldName

    override fun getFieldNameSymbol(): SymbolToken = _Private_Utils.newSymbolToken(fieldName, fieldNameSid)

    override fun isNullValue(): Boolean = reader.currentToken() == TokenTypeConst.NULL

    override fun isInStruct(): Boolean = reader is StructReader

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
