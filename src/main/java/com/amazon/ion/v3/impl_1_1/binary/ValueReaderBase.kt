package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import com.amazon.ion.v3.unused.*
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_MACROS
import com.amazon.ion.v3.visitor.ApplicationReaderDriver.Companion.ION_1_1_SYSTEM_SYMBOLS
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

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
        this.opcode = TID_UNSET
        if (opcode == TID_ON_FIELD_NAME.toInt()) {
            // TODO: Move this to the StructReader if we can.
            val r = (this as StructReader)
            val sid = r.fieldNameSid()
            if (sid < 0) r.fieldName()
            this.opcode = TID_AFTER_FIELD_NAME
            return
        }
        val currentPosition = source.position()
        val length = IdMappings.LENGTH_FOR_OPCODE_CALCULATOR[opcode].calculate(source, macroTable, currentPosition)
        source.position(currentPosition + length)
    }

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
        if (opcode == 0xFA || opcode and 0xF0 == 0xA0) {
            // Inline text
            val scratchBuffer = pool.scratchBuffer
            scratchBuffer.limit(position + length)
            scratchBuffer.position(position)
            source.position(position + length)
            return StandardCharsets.UTF_8.decode(scratchBuffer).toString()
            // return pool.utf8Decoder.decode(scratchBuffer, length)
        } else if (opcode == 0xEE) {
            // System SID
            val sid = IntHelper.readFixedUInt(source, 1)
            return SystemSymbols_1_1[sid]?.text
        } else if (opcode == 0xE1) {
            // 1 byte SID
            val sid = IntHelper.readFixedUInt(source, 1)
            return symbolTable[sid]
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
        val sid = if (opcode == 0xFA || opcode and 0xF0 == 0xA0) {
            // Inline text
            -1
        } else when (opcode) {
            // System SID
            0xEE -> -1
            // 1 byte SID
            0xE1 -> source.get().toInt() and 0xFF
            // 2 byte SID with bias
            0xE2 -> (source.getShort().toInt() and 0xFFFF) + 256
            // FlexUInt SID with bias
            0xE3 -> IntHelper.readFlexUInt(source) + 65792
            else -> throw IonException("Not positioned on a symbol")
        }
        if (sid >= 0) {
            this.opcode = TID_UNSET
        }
        return sid
    }

    override fun clobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun blobValue(): ByteBuffer {
        TODO("Not yet implemented")
    }

    override fun listValue(): ListReader {
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

    override fun sexpValue(): SexpReader {
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

    override fun structValue(): StructReader {
        val opcode = opcode.toInt()
        val length = IdMappings.length(opcode, source)
        if (length < 0) {
            // Delimited container
            val start = source.position()
            this.opcode = TID_UNSET
            return pool.getDelimitedStruct(start, this, symbolTable, macroTable)
        } else {
            // Length prefixed container
            val start = source.position()
            source.position(start + length)
            this.opcode = TID_UNSET
            return pool.getStruct(start, length, symbolTable, macroTable)
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    private fun macroValue(opcode: Int): MacroV2 {
        when (opcode shr 4) {
            0x0, 0x1, 0x2, 0x3 -> {
                if (opcode >= macroTable.size) {
                    println(source)
                    println("Macro Table size: " + macroTable.size)
                }
                return macroTable[opcode]
            }
            0x4 -> {
                // Opcode with 12-bit address and bias
                val bias = (opcode and 0xF) * 256 + 64
                val unbiasedId = source.get().toInt() and 0xFF
                val id: Int = unbiasedId + bias
                if (id >= macroTable.size) {
                    println(source)
                    println("Macro Table size: " + macroTable.size)
                }
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
                    throw IonException("Not positioned on an E-Expression: ${opcode.toByte().toHexString()} @ ${source.position()}")
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

    final override fun macroInvocation(): MacroInvocation {
        val opcode = opcode.toInt()
        val macro = macroValue(opcode)
        this.opcode = TID_UNSET
        // We read the length in case it is there, but we don't actually need to use it.
        // Until we implement lazy reading of the args.
        IdMappings.length(opcode, source)

        // TODO: Make this lazy.
        val argumentBytecode = IntList()
        val currentPosition = source.position()
        val constants = mutableListOf<Any?>()
        val argLength = readEExpArgsAsByteCode(source, source.position(), argumentBytecode, constants, macro, macroTable)
        source.position(currentPosition + argLength)
        val args = ArgumentBytecode(
            argumentBytecode.toArray(),
            constantPool = constants.toTypedArray(),
            source = pool.newSlice(0),
            signature = macro.signature,
        )

        return MacroInvocation(
            macro,
            { args },
            { startMacroEvaluation(macro, args, pool, symbolTable, macroTable) },
            {
                object : Iterator<ValueReader> {
                    private var i = 0
                    private val n = args.signature.size
                    private val theseArgs = args

                    override fun hasNext(): Boolean = i < n

                    override fun next(): ValueReader {
                        val bytecode = theseArgs.getArgument(i++)
                        // TODO: Make this read a slice of the args rather than needing to perform an array copy.
                        return pool.getSequence(ArgumentBytecode.NO_ARGS, bytecode, 0, theseArgs.constantPool(), symbolTable, macroTable)
                    }
                }
            }
        )
    }

    private val scratch = IntList(32)

    private fun parseArgs(macro: MacroV2): ArgumentBytecode {
        val signature = macro.signature
        var presenceBitsPosition = source.position()
        val firstArgPosition = presenceBitsPosition + macro.numPresenceBytesRequired
        source.position(firstArgPosition)

        var presenceByteOffset = 8
        var presenceByte = 0

        var i = 0

        val bytecode = scratch
        bytecode.clear()

        val arguments = Array<IntArray>(signature.size) { ArgumentBytecode.EMPTY_ARG }

        while (i < signature.size) {
            var presence = -1
            val parameter = signature[i]
            if (parameter.iCardinality != 1) {
                // Read a value from the presence bitmap
                // But we might need to "refill" our presence byte first
                if (presenceByteOffset > 7) {
                    presenceByte = source.get(presenceBitsPosition++).toInt() and 0xFF
                    presenceByteOffset = 0
                }
                presence = (presenceByte ushr presenceByteOffset) and 0b11
                presenceByteOffset += 2
            } else {
                // Or set it to the implied "1" value
                presence = 1
            }

            when (presence) {
                0 -> {
                    // Do nothing. All the arg slots are pre-initialized to "EMPTY"
                }
                1 -> {
                    // TODO: Support for tagless types.
                    val tokenType = this.nextToken()
                    readArgumentValue(tokenType, bytecode)
                    bytecode.add(MacroBytecode.END_OF_ARGUMENT_SUBSTITUTION.opToInstruction())
                    arguments[i] = bytecode.toArray()
                    bytecode.clear()
                }
                else -> {

                    // Expression group length
                    val length = IntHelper.readFlexUInt(source)
                    val position = source.position()
                    // TODO: Check if we're on a tagless parameter.
                    //       For now, we'll assume that they are all tagged.

                    if (length == 0) {
                        // Delimited expression group.
                        val start = source.position()
                        val l = IdMappings.LENGTH_FOR_OPCODE_CALCULATOR[0xF1].calculate(source, macroTable, position)
                        source.position(position + l)
                        pool.getDelimitedSequence(start, this, symbolTable, macroTable).use { group ->
                            var tokenType = group.nextToken()
                            while (tokenType != TokenTypeConst.END) {
                                group.readArgumentValue(tokenType, bytecode)
                                tokenType = group.nextToken()
                            }
                            bytecode.add(MacroBytecode.END_OF_ARGUMENT_SUBSTITUTION.opToInstruction())
                            arguments[i] = bytecode.toArray()
                            bytecode.clear()
                        }

                    } else {
                        pool.getList(position, length, symbolTable, macroTable).use {
                            var tokenType = nextToken()
                            while (tokenType != TokenTypeConst.END) {
                                readArgumentValue(tokenType, bytecode)
                                tokenType = nextToken()
                            }
                            bytecode.add(MacroBytecode.END_OF_ARGUMENT_SUBSTITUTION.opToInstruction())
                            arguments[i] = bytecode.toArray()
                            bytecode.clear()
                        }
                        source.position(position + length)
                    }
                }
            }

            i++
        }

        return ArgumentBytecode(
            bytecode = bytecode.toArray(),
            source = source,
            constantPool = emptyArray(),
            signature = signature,
        )
    }

    internal fun readArgumentValue(tokenType: Int, bytecode: IntList) {
//        println(source.position())
        // TODO: Lazy parsing of values.
//        println("Reading ${TokenTypeConst(tokenType)} argument at position ${source.position() - 1}")
        when (tokenType) {
            TokenTypeConst.NULL -> {
                val nullType = this.nullValue()
                if (nullType == IonType.NULL) {
                    bytecode.add(MacroBytecode.OP_NULL_NULL.opToInstruction())
                } else {
                    bytecode.add(MacroBytecode.OP_NULL_TYPED.opToInstruction(nullType.ordinal))
                }
            }
            TokenTypeConst.BOOL -> {
                val bool = if (this.booleanValue()) 1 else 0
                bytecode.add(MacroBytecode.OP_BOOL.opToInstruction(bool))
            }
            TokenTypeConst.INT -> {
                // TODO: Support BigIntegers
                val longValue = this.longValue()
                bytecode.add(MacroBytecode.OP_INLINE_LONG.opToInstruction())
                bytecode.add((longValue shr 32).toInt())
                bytecode.add(longValue.toInt())
            }
            TokenTypeConst.FLOAT -> {
                bytecode.add(MacroBytecode.OP_INLINE_DOUBLE.opToInstruction())
                val doubleBits = this.doubleValue().toRawBits()
                bytecode.add(doubleBits.toInt())
                bytecode.add((doubleBits shr 32).toInt())
            }
            TokenTypeConst.DECIMAL -> {
                val start = source.position()
                skip()
                val end = source.position()
                bytecode.add(MacroBytecode.OP_REF_DECIMAL.opToInstruction(end - start))
                bytecode.add(start)
            }
            TokenTypeConst.TIMESTAMP -> {
                val opcode = this.opcode.toInt()
                // this.opcode = TID_UNSET
                val start = source.position()
                skip()
                if (opcode == 0xF8) {
                    val end = source.position()
                    bytecode.add(MacroBytecode.OP_REF_TIMESTAMP_LONG.opToInstruction(end - start))
                } else {
                    bytecode.add(MacroBytecode.OP_REF_TIMESTAMP_SHORT.opToInstruction(opcode))
                }
                bytecode.add(start)
            }
            TokenTypeConst.STRING -> {
                val opcode = opcode.toInt()
                val length = if (opcode == 0xF9) {
                    IntHelper.readFlexUInt(source)
                } else {
                    opcode and 0xF
                }
                val position = source.position()
                source.position(position + length)
                bytecode.add(MacroBytecode.OP_REF_STRING.opToInstruction(length))
                bytecode.add(position)
            }
            TokenTypeConst.SYMBOL -> {
                val opcode = opcode.toInt()
                when (opcode) {
                    Opcode.SYSTEM_SYMBOL -> {
                        bytecode.add(MacroBytecode.OP_SYSTEM_SYMBOL_SID.opToInstruction(source.get().toInt()))
                    }
                    Opcode.SYMBOL_VALUE_SID_U8 -> {
                        bytecode.add(MacroBytecode.OP_SYMBOL_SID.opToInstruction(source.get().toInt()))
                    }
                    Opcode.SYMBOL_VALUE_SID_U16 -> {
                        bytecode.add(MacroBytecode.OP_SYMBOL_SID.opToInstruction(256 + source.getShort().toInt()))
                    }
                    Opcode.SYMBOL_VALUE_SID_FLEXUINT -> {
                        bytecode.add(MacroBytecode.OP_SYMBOL_SID.opToInstruction(65792 + IntHelper.readFlexUInt(source)))
                    }
                    Opcode.VARIABLE_LENGTH_INLINE_SYMBOL -> {
                        val length = IntHelper.readFlexUInt(source)
                        val start = source.position()
                        source.position(start + length)
                        bytecode.add(MacroBytecode.OP_REF_SYMBOL_TEXT.opToInstruction(length))
                        bytecode.add(start)
                    }
                    else -> {
                        val length = opcode and 0xF
                        val start = source.position()
                        source.position(start + length)
                        bytecode.add(MacroBytecode.OP_REF_SYMBOL_TEXT.opToInstruction(length))
                        bytecode.add(start)
                    }
                }
            }
            TokenTypeConst.BLOB -> TODO()
            TokenTypeConst.CLOB -> TODO()
            TokenTypeConst.LIST -> {
                val opcode = opcode.toInt()
                this.opcode = TID_UNSET
                if (opcode == 0xF1) {
                    val containerStartIndex = bytecode.size()
                    bytecode.add(MacroBytecode.UNSET)
                    val bytecodeStart = containerStartIndex + 1
                    pool.getDelimitedSequence(source.position(), this, symbolTable, macroTable).use { list ->
                        while (true) {
                            val nextToken = list.nextToken()
                            if (nextToken == TokenTypeConst.END) break
                            list.readArgumentValue(nextToken, bytecode)
                        }
                    }
                    bytecode.add(MacroBytecode.OP_CONTAINER_END.opToInstruction())
                    val end = bytecode.size()
                    bytecode[containerStartIndex] = MacroBytecode.OP_LIST_START.opToInstruction(end - bytecodeStart)
                } else {
                    val start: Int = source.position()
                    val calculator = IdMappings.LENGTH_FOR_OPCODE_CALCULATOR[opcode]
                    val length = calculator.calculate(source, macroTable, start)
                    source.position(start + length)
                    bytecode.add(MacroBytecode.OP_REF_LIST.opToInstruction(length))
                    bytecode.add(start)
                }
            }
            TokenTypeConst.SEXP -> {
                val opcode = opcode.toInt()
                this.opcode = TID_UNSET
                if (opcode == 0xF2) {
                    val containerStartIndex = bytecode.size()
                    bytecode.add(MacroBytecode.UNSET)
                    val bytecodeStart = containerStartIndex + 1
                    pool.getDelimitedSequence(source.position(), this, symbolTable, macroTable).use { list ->
                        while (true) {
                            val nextToken = list.nextToken()
                            if (nextToken == TokenTypeConst.END) break
                            list.readArgumentValue(nextToken, bytecode)
                        }
                    }
                    bytecode.add(MacroBytecode.OP_CONTAINER_END.opToInstruction())
                    val end = bytecode.size()
                    bytecode[containerStartIndex] = MacroBytecode.OP_SEXP_START.opToInstruction(end - bytecodeStart)
                } else {
                    val start: Int = source.position()
                    val calculator = IdMappings.LENGTH_FOR_OPCODE_CALCULATOR[opcode]
                    val length = calculator.calculate(source, macroTable, start)
                    source.position(start + length)
                    bytecode.add(MacroBytecode.OP_REF_SEXP.opToInstruction(length))
                    bytecode.add(start)
                }
            }
            TokenTypeConst.STRUCT -> {
                val opcode = opcode.toInt()
                this.opcode = TID_UNSET

                if (opcode == 0xF3) {
                    val containerStartIndex = bytecode.size()
                    bytecode.add(MacroBytecode.UNSET)
                    val bytecodeStart = containerStartIndex + 1
                    pool.getDelimitedStruct(source.position(), this, symbolTable, macroTable).use { list ->
                        while (true) {
                            val nextToken = list.nextToken()
                            if (nextToken == TokenTypeConst.END) break
                            list.readArgumentValue(nextToken, bytecode)
                        }
                    }
                    bytecode.add(MacroBytecode.OP_CONTAINER_END.opToInstruction())
                    val end = bytecode.size()
                    bytecode[containerStartIndex] = MacroBytecode.OP_STRUCT_START.opToInstruction(end - bytecodeStart)
                } else {
                    val start: Int = source.position()
                    val calculator = IdMappings.LENGTH_FOR_OPCODE_CALCULATOR[opcode]
                    val length = calculator.calculate(source, macroTable, start)
                    source.position(start + length)
                    bytecode.add(MacroBytecode.OP_REF_SID_STRUCT.opToInstruction(length))
                    bytecode.add(start)
                }
//                val start: Int = source.position()
//                val calculator = IdMappings.LENGTH_FOR_OPCODE_CALCULATOR[opcode]
//                val length = calculator.calculate(source, macroTable, start)
//                val bytecodeOperation: Int = if (opcode == 0xF3) MacroBytecode.OP_REF_FLEXSYM_STRUCT else MacroBytecode.OP_REF_SID_STRUCT
//                source.position(start + length)
//                bytecode.add(bytecodeOperation.opToInstruction(length))
//                bytecode.add(start)
            }
            TokenTypeConst.EXPRESSION_GROUP -> TODO("Should be unreachable")
            TokenTypeConst.MACRO_INVOCATION -> {
                // TODO: Consider flattening this macro inline?
                val macro = macroValue(opcode.toInt())

                val args = parseArgs(macro)
                // val macroInvocation = macroInvocation()

                if (macro.body != null) {
                    // Inline the template body into the macro argument.
                    templateExpressionToBytecode(macro.body, bytecode, mutableListOf()) { parameterIndex, destination ->
                        val bytes = args.getArgument(parameterIndex)
                        destination.addAll(bytes)
                    }
                } else {
                    macro.signature.forEachIndexed { index, parameter ->
                        val arg = args.getArgument(index)
                        generateBytecodeContainer(
                            MacroBytecode.OP_START_ARGUMENT_VALUE,
                            MacroBytecode.OP_END_ARGUMENT_VALUE,
                            bytecode
                        ) {
                            bytecode.addAll(arg)
                        }
                    }
                    if (macro.systemAddress != SystemMacro.DEFAULT_ADDRESS) {
//                        val cpIndex = constants.size
//                        constants.add(macro)
//                        bytecode.add(MacroBytecode.OP_INVOKE_MACRO.opToInstruction(cpIndex))
                        TODO()
                    } else {
                        bytecode.add(MacroBytecode.OP_INVOKE_SYS_MACRO.opToInstruction(macro.systemAddress))
                    }
                }
            }
            TokenTypeConst.ANNOTATIONS -> {
                val anns = annotations().toStringArray()
//                if (anns.size == 1) {
//                    val ann = anns[0]
//                    val cpIndex = constants.size
//                    constants.add(ann)
//                    bytecode.add(MacroBytecode.OP_CP_ONE_ANNOTATION.opToInstruction(cpIndex))
//
//                } else {
//                    bytecode.add(MacroBytecode.OP_CP_N_ANNOTATIONS.opToInstruction(anns.size))
//                    for (ann in anns) {
//                        val cpIndex = constants.size
//                        constants.add(ann)
//                        bytecode.add(cpIndex)
//                    }
//                }
                TODO()
            }
            TokenTypeConst.FIELD_NAME -> {
                this as StructReader
                val fieldNameSid = fieldNameSid()
                if (fieldNameSid < 0) {
                    val start = source.position()
                    FlexSymHelper.skipFlexSym(source)
                    val end = source.position()
                    bytecode.add(MacroBytecode.OP_REF_FIELD_NAME_TEXT.opToInstruction(end - start))
                    bytecode.add(start)
                } else {
                    bytecode.add(MacroBytecode.OP_FIELD_NAME_SID.opToInstruction(fieldNameSid))
                }
            }
            else -> TODO("${TokenTypeConst(tokenType)} at ~${source.position()}")
        }
    }

    final override fun annotations(): AnnotationIterator {
        val opcode = opcode.toInt()
        this.opcode = TID_AFTER_ANNOTATION
        val length = AnnotationIteratorImpl.calculateLength(opcode, source)
        val start = source.position()
        source.position(start + length)
        return pool.getAnnotations(opcode, start, length, symbolTable)
    }

    override fun timestampValue(): Timestamp {
        val opcode = opcode.toInt()
        this.opcode = TID_UNSET
        return TimestampHelper.readTimestamp(opcode, source)
    }

    override fun doubleValue(): Double {
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

