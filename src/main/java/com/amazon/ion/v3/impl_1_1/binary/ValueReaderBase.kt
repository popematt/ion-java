package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_MACROS
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_SYMBOLS
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Debug {
    var enabled = false
}

abstract class ValueReaderBase(
    @JvmField
    internal var source: ByteBuffer,
    @JvmField
    internal var pool: ResourcePool,
    @JvmField
    internal var symbolTable: Array<String?>,
    @JvmField
    internal var macroTable: Array<MacroV2>,
): ValueReader {

    init {
        source.order(ByteOrder.LITTLE_ENDIAN)
    }

    /**
     *
     * == Structs ==
     *
     * - UNSET -> ON_FIELD_NAME, END
     * - ON_FIELD_NAME -> AFTER_FIELD_NAME
     * - AFTER_FIELD_NAME -> AFTER_ANNOTATIONS, opcode
     * - AFTER_ANNOTATIONS -> opcode
     * - opcode -> UNSET
     *
     * == Sequences ==
     *
     * - UNSET -> END, AFTER_ANNOTATIONS, opcode
     * - AFTER_ANNOTATIONS -> opcode
     * - opcode -> UNSET
     */
    companion object {
        const val TID_UNSET: Short = -1
        const val NEEDS_DATA: Short = -2
        const val INVALID_DATA: Short = -3
        const val TID_AFTER_ANNOTATION: Short = -4
        const val TID_ON_FIELD_NAME: Short = -5
        const val TID_AFTER_FIELD_NAME: Short = -6
        const val TID_START: Short = -7
        const val TID_END: Short = -8
        const val TID_EXPRESSION_GROUP: Short = -9
        const val TID_EMPTY_ARGUMENT: Short = -10
    }

    /**
     * Either the current opcode/typeId (if positive) or some other indicator, if negative.
     */
    @JvmField
    internal var opcode: Short = TID_UNSET

//    internal var id = "" // UUID.randomUUID().toString().take(8)

    internal fun init(
        start: Int,
        length: Int,
    ) {
        source.limit(start + length)
        source.position(start)
        opcode = TID_UNSET
//        id = UUID.randomUUID().toString().take(8)
        resetState()
    }

    protected open fun resetState() {}

    // TODO: Consolidate with `init`
    internal fun initTables(
        symbolTable: Array<String?>,
        macroTable: Array<MacroV2>,
    ) {
        check(symbolTable[0] == null)
        this.symbolTable = symbolTable
        this.macroTable = macroTable
    }

    override fun nextToken(): Int {
        var type: Int
        do {
            if (!source.hasRemaining()) return TokenTypeConst.END

            // Check for the next token...
            val b = source.get()
//            if (Debug.enabled) println("[$id ${this::class.simpleName}] At position ${source.position() - 1}, read byte ${(b.toInt() and 0xFF).toString(16)}")
            opcode = (b.toInt() and 0xFF).toShort()
            type = type(opcode.toInt())
            when (opcode) {
                TID_UNSET -> continue
                TID_END -> return TokenTypeConst.END
                else -> break
            }
        } while (true)

        return type
    }

    //  Returns the TokenType constant.
    protected fun type(state: Int): Int {
        if (state < 0) {
            return when (state) {
                TID_END.toInt() -> TokenTypeConst.END
                TID_ON_FIELD_NAME.toInt() -> TokenTypeConst.FIELD_NAME
                TID_EXPRESSION_GROUP.toInt() -> TokenTypeConst.EXPRESSION_GROUP
                TID_EMPTY_ARGUMENT.toInt() -> TokenTypeConst.ABSENT_ARGUMENT
                else -> TokenTypeConst.UNSET
            }
        }
        return IdMappings.TOKEN_TYPE_FOR_OPCODE[state]
    }

    override fun currentToken(): Int = type(opcode.toInt())

    override fun isTokenSet(): Boolean = opcode != TID_UNSET

    override fun ionType(): IonType? {
        val opcode = opcode.toInt()
        return when (type(opcode)) {
            TokenTypeConst.NULL -> {
                if (opcode == 0xEA) {
                    IonType.NULL
                } else {
                    source.mark()
                    val type = when (source.get().toInt()) {
                        0x00 -> IonType.BOOL
                        0x01 -> IonType.INT
                        0x02 -> IonType.FLOAT
                        0x03 -> IonType.DECIMAL
                        0x04 -> IonType.TIMESTAMP
                        0x05 -> IonType.STRING
                        0x06 -> IonType.SYMBOL
                        0x07 -> IonType.BLOB
                        0x08 -> IonType.CLOB
                        0x09 -> IonType.LIST
                        0x0A -> IonType.SEXP
                        0x0B -> IonType.STRUCT
                        else -> throw IonException("Not a valid null value")
                    }
                    source.reset()
                    type
                }
            }
            TokenTypeConst.BOOL -> IonType.BOOL
            TokenTypeConst.INT -> IonType.INT
            TokenTypeConst.FLOAT -> IonType.FLOAT
            TokenTypeConst.DECIMAL -> IonType.DECIMAL
            TokenTypeConst.TIMESTAMP -> IonType.TIMESTAMP
            TokenTypeConst.SYMBOL -> IonType.SYMBOL
            TokenTypeConst.STRING -> IonType.STRING
            TokenTypeConst.CLOB -> IonType.CLOB
            TokenTypeConst.BLOB -> IonType.BLOB
            TokenTypeConst.LIST -> IonType.LIST
            TokenTypeConst.SEXP -> IonType.SEXP
            TokenTypeConst.STRUCT -> IonType.STRUCT
            else -> null
        }
    }

    override fun valueSize(): Int {
        return IdMappings.length(opcode.toInt())
    }

    override fun skip() {
        val opcode = opcode.toInt()
        if (this.opcode == TID_END) {
            TODO()
        }
        val length = IdMappings.length(opcode, source)
        if (length >= 0) {
            source.position(source.position() + length)
        } else {
            if (opcode == TID_ON_FIELD_NAME.toInt()) {
                // TODO: Move this to the StructReader if we can.
                val r = (this as StructReader)
                val sid = r.fieldNameSid()
                if (sid < 0) r.fieldName()
                return
            } else if (opcode < 0x60) {
                val macro = macroValue()
                val args = macroArguments(macro.signature)
                val end = args.calculateEndPosition()
                args.close()
                source.position(end)
            } else when (opcode) {
                // Symbol with FlexUInt SID
                0xE3 -> symbolValueSid()

                // TODO: make this better for delimited containers. Because of the way that `listValue()`
                //   et al are implemented for delimited containers, this does a double read of the delimited
                //   containers in order to skip it once. This problem is compounded when nested. Delimited
                //   containers that are nested 1 layer deep will be skipped with a double read for each read
                //   of the parent, so 4 reads to skip it once.
                // FIXME: 14% of all

                // Delimited List and sexp
                0xF1, 0xF2 -> {
                    val start = source.position()
                    val s = pool.getDelimitedSequence(start, this, symbolTable, macroTable)
                    while (s.nextToken() != TokenTypeConst.END) {
                        s.skip()
                    }
                    s.close()
                }
                // Opcode F3
                0xF3 -> structValue().use {
                    while (it.nextToken() != TokenTypeConst.END) {
//                        it.fieldName()
//                        it.nextToken()
                        it.skip()
                    }
                    source.position((it as ValueReaderBase).source.position())
                }
                0xE4, 0xE5, 0xE6,
                0xE7, 0xE8, 0xE9  -> annotations().close()
                // E-Expressions that do not have a length prefix.
                0xEF, 0xF4 -> {
                    val macro = macroValue()
                    // TODO: If we stop eagerly calculating the size of macro args, this may need to change.
                    macroArguments(macro.signature).close()
                }
                else -> {
                    TODO("Skipping an opcode: 0x${opcode.toString(16)}")
                }
            }
        }
        this.opcode = TID_UNSET
    }

//    override fun skip() {
//        val opcode = opcode.toInt()
//        if (this.opcode == TID_END) {
//            TODO()
//        }
//        val length = IdMappings.length(opcode, source)
//        if (length >= 0) {
//            source.position(source.position() + length)
//        } else {
//            when (val token = type(opcode)) {
//                TokenTypeConst.FIELD_NAME -> {
//                    // TODO: Move this to the StructReader if we can.
//                    val r = (this as StructReader)
//                    val sid = r.fieldNameSid()
//                    if (sid < 0) r.fieldName()
//                    return
//                }
//                TokenTypeConst.SYMBOL -> {
//                    if (symbolValueSid() < 0) {
//                        // Length prefixed symbols and system symbols are already covered, so this should be unreachable.
//                        TODO("Skipping Symbols for opcode ${opcode.toString(16)}")
//                    }
//                }
//                // TODO: make this better for delimited containers. Because of the way that `listValue()`
//                //   et al are implemented for delimited containers, this does a double read of the delimited
//                //   containers in order to skip it once. This problem is compounded when nested. Delimited
//                //   containers that are nested 1 layer deep will be skipped with a double read for each read
//                //   of the parent, so 4 reads to skip it once.
//                // FIXME: 14% of all
//                TokenTypeConst.LIST -> {
//                    listValue().use {  }
//                }
//                TokenTypeConst.SEXP -> sexpValue().use {  }
//                TokenTypeConst.STRUCT -> structValue().use {
//                    while (it.nextToken() != TokenTypeConst.END) {
////                        it.fieldName()
////                        it.nextToken()
//                        it.skip()
//                    }
//                    source.position((it as ValueReaderBase).source.position())
//                }
//                TokenTypeConst.ANNOTATIONS -> annotations().use {  }
//                TokenTypeConst.MACRO_INVOCATION -> {
//                    val macro = macroValue()
//                    val end = macroArguments(macro.signature).use { it.calculateEndPosition() }
//                    source.position(end)
//                }
//                else -> {
//                    TODO("Skipping a ${TokenTypeConst(token)} [opcode: 0x${opcode.toString(16)}]")
//                }
//            }
//        }
//        this.opcode = TID_UNSET
//    }

    override fun nullValue(): IonType {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        return if (opcode == 0xEA) {
            IonType.NULL
        } else if (opcode == 0xEB) {
            val type = source.get()
            when (type.toInt()) {
                0x00 -> IonType.BOOL
                0x01 -> IonType.INT
                0x02 -> IonType.FLOAT
                0x03 -> IonType.DECIMAL
                0x04 -> IonType.TIMESTAMP
                0x05 -> IonType.STRING
                0x06 -> IonType.SYMBOL
                0x07 -> IonType.BLOB
                0x08 -> IonType.CLOB
                0x09 -> IonType.LIST
                0x0A -> IonType.SEXP
                0x0B -> IonType.STRUCT
                else -> throw IonException("Not a valid null value")
            }
        } else {
            throw IonException("Not positioned on a null")
        }
    }

    override fun booleanValue(): Boolean {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        val bool = when (opcode) {
            0x6E -> true
            0x6F -> false
            else -> throw IonException("Not positioned on a boolean: ${opcode.toString(16)}")
        }
        return bool
    }

    override fun longValue(): Long {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        return when (opcode) {
            0x60 -> 0
            0x61 -> source.get().toLong()
            0x62 -> source.getShort().toLong()
            0x63 -> IntHelper.readFixedInt(source, 3)
            0x64 -> source.getInt().toLong()
            0x65 -> IntHelper.readFixedInt(source, 5)
            0x66 -> IntHelper.readFixedInt(source, 6)
            0x67 -> IntHelper.readFixedInt(source, 7)
            0x68 -> source.getLong()
            else -> if (opcode == 0xF6) {
                // Not really supported for Longs anyway.
                TODO("Variable length integers")
            } else {
                throw IonException("Not positioned on an int; found ${TokenTypeConst(opcode)} (opcode: $opcode)")
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun stringValue(): String {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        val length = if (opcode shr 4 == 9) {
            opcode and 0xF
        } else if (opcode == 0xF9) {
            IntHelper.readFlexUInt(source)
        } else {
            throw IonException("Not positioned on a string")
        }
        val position = source.position()
        // Why do we need scratchBuffer here?
        val scratchBuffer = pool.scratchBuffer
        scratchBuffer.limit(position + length)
        scratchBuffer.position(position)
        source.position(position + length)
        return pool.utf8Decoder.decode(scratchBuffer, length)
    }

    override fun lookupSid(sid: Int): String? {
        try {
            return symbolTable[sid]
        } catch (t: Throwable) {
            println("Failed SID Lookup for $sid")
            println("There are ${symbolTable.size} symbols: ${symbolTable.contentToString()}")
            throw t
        }
    }

    override fun symbolValue(): String? {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        val length = IdMappings.length(opcode, source)
        val position = source.position()
        if (opcode == 0xF9 || opcode and 0xF0 == 0xA0) {
            // Inline text
            val scratchBuffer = pool.scratchBuffer
            scratchBuffer.limit(position + length)
            scratchBuffer.position(position)
            source.position(position + length)
            return pool.utf8Decoder.decode(scratchBuffer, length)
        } else if (opcode == 0xEE) {
            // System SID
            val sid = IntHelper.readFixedUInt(source, 1)
            return SystemSymbols_1_1[sid]?.text
        } else if (opcode == 0xE1) {
            // 1 byte SID
            val sid = IntHelper.readFixedUInt(source, 1)
            return null
        } else if (opcode == 0xE2) {
            // 2 byte SID with bias
            val sid = IntHelper.readFixedUInt(source, 2) + 256
            return symbolTable[sid]
        } else if (opcode == 0xE3) {
            // FlexUInt SID with bias
            val sid = IntHelper.readFlexUInt(source) + 65792
            return symbolTable[sid]
        } else {
            throw IonException("Not positioned on a symbol")
        }
    }

    override fun symbolValueSid(): Int {
        val opcode = opcode.toInt()
        if (opcode == 0xF9 || opcode and 0xF0 == 0xA0) {
            // Inline text
            return -1
        } else if (opcode == 0xEE) {
            // System SID
            return -1
        } else if (opcode == 0xE1) {
            // 1 byte SID
            this.opcode = TID_UNSET
            val sid = IntHelper.readFixedUInt(source, 1)
            return sid
        } else if (opcode == 0xE2) {
            // 2 byte SID with bias
            this.opcode = TID_UNSET
            val sid = IntHelper.readFixedUInt(source, 2) + 256
            return sid
        } else if (opcode == 0xE3) {
            // FlexUInt SID with bias
            this.opcode = TID_UNSET
            val sid = IntHelper.readFlexUInt(source) + 65792
            return sid
        } else {
            throw IonException("Not positioned on a symbol")
        }
    }

    override fun clobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun blobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    final override fun listValue(): ListReader {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        val length = if (opcode shr 4 == 0xB) {
            opcode and 0xF
        } else if (opcode == 0xFB) {
            IntHelper.readFlexUInt(source)
        } else if (opcode == 0xF1) {
            val start = source.position()
            return pool.getDelimitedSequence(start, this, symbolTable, macroTable)
        } else {
            throw IonException("Not positioned on a list")
        }
        val start = source.position()
        source.position(start + length)
        return pool.getList(start, length, symbolTable, macroTable)
    }

    final override fun sexpValue(): SexpReader {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        val length = IdMappings.length(opcode.toInt(), source)
        if (length < 0) {
            // Delimited container
            val start = source.position()
            return pool.getDelimitedSexp(start, this, symbolTable, macroTable)
        } else {
            // Length prefixed container
            val start = source.position()
            source.position(start + length)
            return pool.getPrefixedSexp(start, length, symbolTable, macroTable)
        }
    }

    final override fun structValue(): StructReader {
        val opcode = opcode.toInt()
        val length = IdMappings.length(opcode, source)
        if (length < 0) {
            // Delimited container
            val start = source.position()
            this.opcode = TID_UNSET

//            val sacrificialReader = pool.getDelimitedStruct(start, this, symbolTable, macroTable)
//            while (sacrificialReader.nextToken() != TokenTypeConst.END) {
//                sacrificialReader.skip()
//            }
//
//            val endPosition = sacrificialReader.source.position()
//            sacrificialReader.close()
//            source.position(endPosition)

            return pool.getDelimitedStruct(start, this, symbolTable, macroTable)
        } else {
            // Length prefixed container
            val start = source.position()
            source.position(start + length)
            this.opcode = TID_UNSET
            return pool.getStruct(start, length, symbolTable, macroTable)
        }
    }


    final override fun macroValue(): MacroV2 {
        val opcode = opcode.toInt()
        when (opcode shr 4) {
            0x0, 0x1, 0x2, 0x3 -> return macroTable[opcode]
            0x4 -> {
                // Opcode with 12-bit address and bias
                val bias = (opcode and 0xF) * 256 + 64
                val unbiasedId = source.get().toInt() and 0xFF
                val id: Int = unbiasedId + bias
                return macroTable[id]
            }
            0x5 -> {
                // Opcode with 20-bit address and bias
                val bias = (opcode and 0xF) * 256 * 256 + 4160
                val unbiasedId = source.getShort().toInt() and 0xFFFF
                val id: Int = unbiasedId + bias
                return macroTable[id]
            }
            0xE -> {
                if (opcode == 0xEF) {
                    // System macro
                    val id = source.get().toInt()
                    return EncodingContextManager.ION_1_1_SYSTEM_MACROS[id]
                } else {
                    throw IonException("Not positioned on an E-Expression")
                }
            }
            0xF -> {
                if (opcode == 0xF4) {
                    // E-expression with FlexUInt macro address
                    return macroTable[IntHelper.readFlexUInt(source)]
                } else if (opcode == 0xF5) {
                    // E-expression with FlexUInt macro address followed by FlexUInt length prefix
                    val address = IntHelper.readFlexUInt(source)
                    return macroTable[address]
                } else {
                    throw IonException("Not positioned on an E-Expression")
                }
            }
            else -> throw IonException("Not positioned on an E-Expression")

        }
    }

    final override fun macroArguments(signature: Array<Macro.Parameter>): EExpArgumentReaderImpl {
        // TODO: Add a state value representing "start of arguments", and check for it here
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        val length = IdMappings.length(opcode, source)
        val position = source.position()
        val reader = if (length < 0) {
            val maxPossibleLength = source.limit() - position
            val args = pool.getEExpArgs(position, maxPossibleLength, signature, symbolTable, macroTable)
            // TODO: See if we can avoid eagerly calculating the length of the arguments
            source.position(args.calculateEndPosition())
            args
        } else {
            source.position(position + length)
            pool.getEExpArgs(position, length, signature, symbolTable, macroTable)
        }
        return reader
    }

    final override fun annotations(): AnnotationIterator {
        val opcode = opcode.toInt()
        this.opcode = TID_AFTER_ANNOTATION
        val length = AnnotationIteratorImpl.calculateLength(opcode, source)
        val start = source.position()
        source.position(start + length)
        return pool.getAnnotations(opcode, start, length, symbolTable)
    }

    final override fun timestampValue(): Timestamp {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        return TimestampHelper.readTimestamp(opcode, source)
    }

    final override fun doubleValue(): Double {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        return when (opcode) {
            0x6A -> 0.0
            0x6B -> TODO()
            0x6C -> source.getFloat().toDouble()
            0x6D -> source.getDouble()
            else -> throw IonException("Not positioned on a float")
        }
    }

    override fun decimalValue(): Decimal {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        TODO("Not yet implemented")
    }

    final override fun getIonVersion(): Short = 0x0101

    final override fun ivm(): Short {
        if (opcode.toInt() != 0xE0) throw IonException("Not positioned on an IVM")
        opcode = TID_UNSET
        val version = source.getShort()
        // TODO: Check the last byte of the IVM to make sure it is well formed.
        source.get()

        symbolTable = ION_1_1_SYSTEM_SYMBOLS
        macroTable = ION_1_1_SYSTEM_MACROS

        return version
    }

    final override fun seekTo(position: Int) {
        source.position(position)
    }
    final override fun position(): Int = source.position()

    override fun expressionGroup(): SequenceReader {
        TODO("Not yet implemented")
    }
}

