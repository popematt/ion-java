package com.amazon.ion.v3.ion_reader_b

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.*
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.OPERATION_SHIFT_AMOUNT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.TOKEN_TYPE_SHIFT_AMOUNT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.instructionToOp
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import com.amazon.ion.v3.impl_1_1.template.TemplateReaderImpl.Companion.INSTRUCTION_NOT_SET
import com.amazon.ion.v3.ion_reader.*
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.ArrayList


/**
 * An IonReader implementation that converts top-level values to MacroBytecode before reading.
 *
 * This allows it to hold large top-level values in memory since we don't necessarily need to retain the entire source data.
 *
 * To make this generic over multiple types of input (ByteArray, InputStream, etc.) we only need to be able to
 * - read some scalar references (for blobs, clobs, text, timestamps, decimals) from the input
 * - refill the bytecode from the input
 *
 */
class BytecodeIonReader(
    private val source: ByteArray,
    private val additionalMacros: List<MacroV2> = emptyList(),
): IonReader {
    private var sourceI = 0

    private var minorVersion: Byte = 0

    @JvmField
    internal var symbolTable: Array<String?> = arrayOf(null)

    @JvmField
    internal var macroTable: Array<MacroV2> = emptyArray()

    private val bytecodeIntList = IntList(2048).also { it.add(MacroBytecode.REFILL.opToInstruction()) }
    /** Only contains bytecode for user (maybe?) values */
    private var bytecode = bytecodeIntList.unsafeGetArray()

    // TODO: Consider using an array.
    private var constantPool = ArrayList<Any?>(256)

    private var instruction = 0

    // Always set to the index after the current `instruction`
    private var bytecodeI = 0
    private var fieldNameIndex = -1
    private var annotationsIndex = -1
    private var annotationCount: Byte = 0

    private var isInStruct = false

    private var isNextAlreadyLoaded = false

    private val containerStack = _Private_RecyclingStack(10) { ContainerInfo() }

    private val encodingContext = EncodingContext(StreamWrappingIonReader())


    init {
        encodingContext.ivm()
        symbolTable = EncodingContext.ION_1_1_DEFAULT_SYMBOL_TABLE
        macroTable = encodingContext.buildActiveMacroTable(additionalMacros)
    }

    override fun close() {

    }

    private fun refillBytecode(): IntArray {
        // Fill 1 top-level value, or until the bytecode buffer is full, whichever comes first.

        val source = source
        var i = sourceI

//        println("Refilling bytecode, starting at $i")

        val bytecode = bytecodeIntList
        bytecode.clear()
        val constantPool = constantPool
        constantPool.clear()

        // read op code
        val opcode = source[i++].toInt() and 0xFF
        i += compileValue(opcode, bytecode, constantPool, source, i, macroTable)
        sourceI = i

        // TODO: "Can be refilled?"
        if (i < source.size) {
            bytecode.add(MacroBytecode.REFILL.opToInstruction())
        } else {
            bytecode.add(MacroBytecode.EOF.opToInstruction())
        }


        val bytecodeArray = bytecode.unsafeGetArray()
        this.bytecode = bytecodeArray

//        println("\nREFILLED...")
//        MacroBytecode.debugString(bytecodeArray)
//        println()

        return bytecodeArray
    }


    override fun hasNext(): Boolean {
        val result = next() != null
        isNextAlreadyLoaded = true
        return result
    }

    override fun next(): IonType? {
        // See if we can get rid of this branch.
        if (isNextAlreadyLoaded) {
            isNextAlreadyLoaded = false
            return type
        }

        var bytecode = bytecode
        var i = bytecodeI
        var instruction = instruction
        var op = instruction.instructionToOp()

        var annotationStart = 0 // Stores the start of the annotations, offset by 1 (for bit-twiddling reasons).
        var annotationFlag = -1 // -1 if not set, 0 if set. Used to avoid branches in the annotation case.
        var annotationCount = 0

        var tokenType: Int

        do {
            // Move to the next instruction.
            val length = instruction and MacroBytecode.DATA_MASK
            val operandsToSkip = MacroBytecode.Operations.N_OPERANDS[op]
            i += if (operandsToSkip.toInt() == -1) length else operandsToSkip.toInt()
            instruction = bytecode[i++]
            op = instruction.instructionToOp()

            tokenType = (instruction ushr (OPERATION_SHIFT_AMOUNT + TOKEN_TYPE_SHIFT_AMOUNT))

//            println("oO? ${MacroBytecode(instruction)}")

            when (tokenType) {
                // MacroBytecode doesn't have a nop with length, so it's fine to skip just one.
                TokenTypeConst.NOP -> fieldNameIndex = -1
                TokenTypeConst.END, // Container end OR end of input
                TokenTypeConst.NULL,
                TokenTypeConst.BOOL,
                TokenTypeConst.INT,
                TokenTypeConst.FLOAT,
                TokenTypeConst.DECIMAL,
                TokenTypeConst.TIMESTAMP,
                TokenTypeConst.STRING,
                TokenTypeConst.SYMBOL,
                TokenTypeConst.BLOB,
                TokenTypeConst.CLOB,
                TokenTypeConst.LIST,
                TokenTypeConst.SEXP,
                TokenTypeConst.STRUCT -> break
                TokenTypeConst.FIELD_NAME -> fieldNameIndex = i - 1
                TokenTypeConst.ANNOTATIONS -> {
                    annotationStart = (annotationStart or (i and annotationFlag))
                    annotationCount++
                    annotationFlag = 0
                }
                TokenTypeConst.IVM -> handleIvm(instruction)
                TokenTypeConst.REFILL -> {
                    // TODO: This will break if we refill between a field name and value or between an annotation and value.
                    //       So we should avoid letting that happen.
                    bytecode = refillBytecode()
                    i = 0
                }
                TokenTypeConst.SYSTEM_VALUE -> {
                    i += handleSystemValue(instruction, bytecode, i)
                }
                else -> {
                    MacroBytecode.debugString(bytecode)
//                    println("Container ends stack:")
//                    containerStack.iterator().forEach {
//                        println("    $it")
//                    }
                    TODO("${TokenTypeConst(tokenType)} at ${i - 1}")
                }
            }
        } while (true)


        this.bytecodeI = i
        this.instruction = instruction
        this.annotationsIndex = annotationStart - 1
        this.annotationCount = annotationCount.toByte()

        val isDirective = annotationStart > 0 && depth == 0 && (tokenType == TokenTypeConst.STRUCT || tokenType == TokenTypeConst.SEXP) && isPositionedOnDirective(tokenType)

        if (isDirective) {
            TODO("Handle directives")
        }
//        println("                        -> ${MacroBytecode(instruction)}")
        return getType(instruction)
    }

    private fun isPositionedOnDirective(tokenType: Int): Boolean {
        val bytecode = bytecode
        val annotations = annotationsIndex
        val annotationInstruction = bytecode[annotations]

        val firstAnnotation = when (annotationInstruction.instructionToOp()) {
            MacroBytecode.OP_CP_ONE_ANNOTATION -> {
                val constantPoolIndex = (annotationInstruction and MacroBytecode.DATA_MASK)
                constantPool[constantPoolIndex] as String?
            }
            MacroBytecode.OP_ANNOTATION_SYSTEM_SID -> {
                val sid = annotationInstruction and MacroBytecode.DATA_MASK
                if (sid == 0) {
                    null
                } else {
                    SystemSymbols_1_1[sid]!!.text
                }
            }
            MacroBytecode.OP_ANNOTATION_SID -> {
                val sid = (annotationInstruction and MacroBytecode.DATA_MASK)
                symbolTable[sid]
            }
            MacroBytecode.OP_REF_ONE_FLEXSYM_ANNOTATION -> {
                val length = annotationInstruction and MacroBytecode.DATA_MASK
                val position = bytecode[annotations + 1]
                readText(position, length, source)
            }
            else -> TODO()
        }

        return when (tokenType) {
            TokenTypeConst.STRUCT -> firstAnnotation == "\$ion_symbol_table"
            TokenTypeConst.SEXP -> firstAnnotation == "\$ion"
            else -> false
        }


    }


    private fun handleIvm(instruction: Int) {
        val minorVersion = instruction and MacroBytecode.DATA_MASK
        encodingContext.ivm()
        when (minorVersion) {
            0 -> {
                this.symbolTable = EncodingContext.ION_1_0_SYMBOL_TABLE
                this.macroTable = emptyArray()
                TODO()
            }
            1 -> {
                this.symbolTable = EncodingContext.ION_1_1_DEFAULT_SYMBOL_TABLE
                this.macroTable = encodingContext.buildActiveMacroTable(additionalMacros)
            }
        }
    }

    private fun handleSystemValue(instruction: Int, bytecode: IntArray, position: Int): Int {
        var i = position
        // This might not be applicable for "use"
        val argStartInstruction = bytecode[i++]
        when (instruction.instructionToOp()) {
            MacroBytecode.DIRECTIVE_SYSTEM_MACRO -> when (instruction and MacroBytecode.DATA_MASK) {
                SystemMacro.SET_SYMBOLS_ADDRESS -> {
                    // There's an expression group of symbol texts
                    // But this is bytecode, so there's no presence bits to read.
                    // Instead, there's an OP_ARG_START instruction, that we will skip.
                    val newSymbols = ArrayList<String?>()
                    newSymbols.add(null)
                    while (true) {
                        val instruction0 = bytecode[i++]
                        val s = when (instruction0.instructionToOp()) {
                            MacroBytecode.OP_CP_SYMBOL_TEXT,
                            MacroBytecode.OP_CP_STRING -> constantPool[instruction0 and MacroBytecode.DATA_MASK] as String?
                            MacroBytecode.OP_REF_SYMBOL_TEXT,
                            MacroBytecode.OP_REF_STRING -> readText(position = bytecode[i++], length = instruction0 and MacroBytecode.DATA_MASK, source)
                            MacroBytecode.OP_SYMBOL_CHAR -> (instruction0 and MacroBytecode.DATA_MASK).toChar().toString()
                            MacroBytecode.OP_SYMBOL_SYSTEM_SID -> EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[instruction0 and MacroBytecode.DATA_MASK]
                            MacroBytecode.OP_SYMBOL_SID -> symbolTable[instruction0 and MacroBytecode.DATA_MASK]
                            MacroBytecode.OP_NULL_SYMBOL,
                            MacroBytecode.OP_NULL_STRING -> null
                            MacroBytecode.OP_CONTAINER_END -> break
                            MacroBytecode.OP_END_ARGUMENT_VALUE,
                            MacroBytecode.OP_RETURN -> break
                            else -> throw IonException("Not a valid symbol declaration")
                        }
                        newSymbols.add(s)
                    }
                    this.symbolTable = newSymbols.toTypedArray()
                }
                SystemMacro.ADD_SYMBOLS_ADDRESS -> {
                    // There's an expression group of symbol texts
                    // But this is bytecode, so there's no presence bits to read.

                    val newSymbols = ArrayList<String?>()
                    while (true) {
                        val instruction0 = bytecode[i++]
                        val s = when (instruction0.instructionToOp()) {
                            MacroBytecode.OP_CP_SYMBOL_TEXT,
                            MacroBytecode.OP_CP_STRING -> constantPool[instruction0 and MacroBytecode.DATA_MASK] as String?
                            MacroBytecode.OP_REF_SYMBOL_TEXT,
                            MacroBytecode.OP_REF_STRING -> readText(position = bytecode[i++], length = instruction0 and MacroBytecode.DATA_MASK, source)
                            MacroBytecode.OP_SYMBOL_CHAR -> (instruction0 and MacroBytecode.DATA_MASK).toChar().toString()
                            MacroBytecode.OP_SYMBOL_SYSTEM_SID -> EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[instruction0 and MacroBytecode.DATA_MASK]
                            MacroBytecode.OP_SYMBOL_SID -> symbolTable[instruction0 and MacroBytecode.DATA_MASK]
                            MacroBytecode.OP_NULL_SYMBOL,
                            MacroBytecode.OP_NULL_STRING -> null
                            MacroBytecode.OP_CONTAINER_END -> break
                            MacroBytecode.OP_END_ARGUMENT_VALUE,
                            MacroBytecode.OP_RETURN -> break
                            else -> throw IonException("Not a valid symbol declaration")
                        }
                        newSymbols.add(s)
                    }

                    val symbolTable = symbolTable
                    val newSymbolTable = arrayOfNulls<String>(symbolTable.size + newSymbols.size)
                    symbolTable.copyInto(newSymbolTable)
                    newSymbols.toTypedArray().copyInto(newSymbolTable, symbolTable.size)
                    this.symbolTable = newSymbolTable
                }
                SystemMacro.SET_MACROS_ADDRESS -> {
                    val buffer = ByteBuffer.wrap(source)
                    val r = TemplateReaderImpl(ResourcePool(buffer))
                    r.initTables(symbolTable, macroTable)
                    r.init(buffer, bytecode, constantPool.toTypedArray(), ArgumentBytecode.NO_ARGS)
                    r.i = 2
                    try {
                        encodingContext.addOrSetMacros(r, false)
                    } catch (t: Throwable) {
                        MacroBytecode.debugString(bytecode)
                        throw t
                    }
                    macroTable = encodingContext.buildActiveMacroTable(additionalMacros)
                    i += argStartInstruction and MacroBytecode.DATA_MASK
                }
                SystemMacro.ADD_MACROS_ADDRESS -> {
                    val buffer = ByteBuffer.wrap(source)
                    val r = TemplateReaderImpl(ResourcePool(buffer))
                    r.initTables(symbolTable, macroTable)
                    r.init(buffer, bytecode, constantPool.toTypedArray(), ArgumentBytecode.NO_ARGS)
                    r.i = 2
                    encodingContext.addOrSetMacros(r, true)
                    macroTable = encodingContext.buildActiveMacroTable(additionalMacros)
                    i += argStartInstruction and MacroBytecode.DATA_MASK
                }
            }
            else -> TODO()
        }
        return i - position
    }



    override fun getType(): IonType? = getType(instruction)

    companion object {
        @JvmField
        val TOKEN_TO_ION_TYPES = arrayOf(
            null,
            IonType.NULL,
            IonType.BOOL,
            IonType.INT,
            IonType.FLOAT,

            IonType.DECIMAL,
            IonType.TIMESTAMP,
            IonType.STRING,
            IonType.SYMBOL,
            IonType.BLOB,

            IonType.CLOB,
            IonType.LIST,
            IonType.SEXP,
            IonType.STRUCT,
            null,

            null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null,
        )
    }

    fun getType(instruction: Int): IonType? {
        return TOKEN_TO_ION_TYPES[instruction ushr (OPERATION_SHIFT_AMOUNT + TOKEN_TYPE_SHIFT_AMOUNT)]

        // TODO: Consider having type-specific nulls in macro-bytecode so that all the branches can be eliminated here
        //       and replaced with a direct array lookup.
//        return when (instruction ushr (OPERATION_SHIFT_AMOUNT + TOKEN_TYPE_SHIFT_AMOUNT)) {
//            TokenTypeConst.NULL -> {
//                if (instruction and 0x01000000 == 0) {
//                    IonType.NULL
//                } else {
//                    IonType.entries[instruction and 0xFF]
//                }
//            }
//            TokenTypeConst.BOOL -> IonType.BOOL
//            TokenTypeConst.INT -> IonType.INT
//            TokenTypeConst.FLOAT -> IonType.FLOAT
//            TokenTypeConst.DECIMAL -> IonType.DECIMAL
//            TokenTypeConst.TIMESTAMP -> IonType.TIMESTAMP
//            TokenTypeConst.SYMBOL -> IonType.SYMBOL
//            TokenTypeConst.STRING -> IonType.STRING
//            TokenTypeConst.CLOB -> IonType.CLOB
//            TokenTypeConst.BLOB -> IonType.BLOB
//            TokenTypeConst.LIST -> IonType.LIST
//            TokenTypeConst.SEXP -> IonType.SEXP
//            TokenTypeConst.STRUCT -> IonType.STRUCT
//            else -> null
//        }
    }

    data class ContainerInfo(
        @JvmField var isInStruct: Boolean = false,
        @JvmField var bytecodeI: Int = -1,
    )

    override fun stepIn() {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_LIST_START -> {
                val length = instruction and MacroBytecode.DATA_MASK
                containerStack.push {
                    it.bytecodeI = bytecodeI + length
                    it.isInStruct = isInStruct
                }
                this.isInStruct = false
            }
            MacroBytecode.OP_SEXP_START -> {
                val length = instruction and MacroBytecode.DATA_MASK
                containerStack.push {
                    it.bytecodeI = bytecodeI + length
                    it.isInStruct = isInStruct
                }
                this.isInStruct = false
            }
            MacroBytecode.OP_STRUCT_START -> {
                val length = instruction and MacroBytecode.DATA_MASK
                containerStack.push {
                    it.bytecodeI = bytecodeI + length
                    it.isInStruct = isInStruct
                }
                this.isInStruct = true
            }
            MacroBytecode.OP_REF_LIST,
            MacroBytecode.OP_REF_SEXP,
            MacroBytecode.OP_REF_SID_STRUCT -> TODO()
            else -> throw IonException("Not positioned on a container")
        }
//        println("Stepped into ${MacroBytecode(instruction)}")
//        containerStack.iterator().forEach {
//            println("        $it")
//        }

        this.instruction = INSTRUCTION_NOT_SET
    }

    override fun stepOut() {
//        println("Stepping out. Container ends stack:")
//        containerStack.iterator().forEach {
//            println("    $it")
//        }

        val top = containerStack.pop() ?: throw IonException("Nothing to step out of.")
        this.bytecodeI = top.bytecodeI
        this.isInStruct = top.isInStruct
        this.instruction = INSTRUCTION_NOT_SET
        this.fieldNameIndex = -1
    }

    override fun isInStruct(): Boolean = isInStruct

    override fun getDepth(): Int = containerStack.size()

    override fun getIntegerSize(): IntegerSize {
        return when (instruction.instructionToOp()) {
            MacroBytecode.OP_SMALL_INT -> IntegerSize.INT
            MacroBytecode.OP_INLINE_INT -> IntegerSize.INT
            MacroBytecode.OP_INLINE_LONG -> IntegerSize.LONG
            MacroBytecode.OP_CP_BIG_INT -> IntegerSize.BIG_INTEGER
            else -> throw IonException("Not positioned on an int value")
        }
    }

    override fun getTypeAnnotations(): Array<String?> {
        val nAnnotations = annotationCount.toInt()
        val result = arrayOfNulls<String>(nAnnotations)
        var p = annotationsIndex
        val bytecode = this.bytecode
        for (i in 0 until nAnnotations) {
            val instruction = bytecode[p]
            result[i] = when (instruction.instructionToOp()) {
                MacroBytecode.OP_CP_ONE_ANNOTATION -> constantPool[instruction and MacroBytecode.DATA_MASK] as String?
                MacroBytecode.OP_ANNOTATION_SID -> symbolTable[instruction and MacroBytecode.DATA_MASK]
                MacroBytecode.OP_ANNOTATION_SYSTEM_SID -> EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[instruction and MacroBytecode.DATA_MASK]
                MacroBytecode.OP_REF_ONE_FLEXSYM_ANNOTATION -> {
                    val position = bytecode[p++]
                    val length = instruction and MacroBytecode.DATA_MASK
                    readText(position, length, source)
                }
                else -> throw IllegalStateException("Field name index does not point to a field name; was ${MacroBytecode(instruction)}")
            }
        }
        return result
    }

    private fun createSymbolToken(text: String?, sid: Int): SymbolToken = _Private_Utils.newSymbolToken(text, sid)

    override fun getTypeAnnotationSymbols(): Array<SymbolToken> {

        val nAnnotations = annotationCount.toInt()
        var p = annotationsIndex
        val bytecode = this.bytecode
        val result = Array(nAnnotations) { i ->
            val instruction = bytecode[p]
            when (instruction.instructionToOp()) {
                MacroBytecode.OP_CP_ONE_ANNOTATION -> {
                    val text = constantPool[instruction and MacroBytecode.DATA_MASK] as String?
                    createSymbolToken(text, -1)
                }
                MacroBytecode.OP_ANNOTATION_SID -> {
                    val sid = instruction and MacroBytecode.DATA_MASK
                    val text = symbolTable[sid]
                    createSymbolToken(text, sid)
                }
                MacroBytecode.OP_ANNOTATION_SYSTEM_SID -> {
                    val text = EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[instruction and MacroBytecode.DATA_MASK]
                    createSymbolToken(text, -1)
                }
                MacroBytecode.OP_REF_ONE_FLEXSYM_ANNOTATION -> {
                    val position = bytecode[p++]
                    val length = instruction and MacroBytecode.DATA_MASK
                    val text = readText(position, length, source)
                    createSymbolToken(text, -1)
                }
                else -> throw IllegalStateException("Annotation index does not point to an annotation; was ${MacroBytecode(instruction)}")
            }
        }
        return result
    }

    // TODO: We could make this lazy.
    override fun iterateTypeAnnotations(): Iterator<String?> = typeAnnotations.iterator()

    override fun getFieldId(): Int {
        val fieldName = fieldNameIndex
        if (fieldName < 0) return -1
        val fieldInstruction = bytecode[fieldName]
        return when (fieldInstruction.instructionToOp()) {
            MacroBytecode.OP_CP_FIELD_NAME -> -1
            MacroBytecode.OP_FIELD_NAME_SID -> fieldInstruction and MacroBytecode.DATA_MASK
            MacroBytecode.OP_FIELD_NAME_SYSTEM_SID -> -1
            MacroBytecode.OP_REF_FIELD_NAME_TEXT -> -1
            else -> throw IllegalStateException("Field name index does not point to a field name; was ${MacroBytecode(fieldInstruction)}")
        }
    }

    override fun getFieldName(): String? {
        val fieldName = fieldNameIndex
        if (fieldName < 0) return null
        val fieldInstruction = bytecode[fieldName]
        return when (fieldInstruction.instructionToOp()) {
            MacroBytecode.OP_CP_FIELD_NAME -> constantPool[fieldInstruction and MacroBytecode.DATA_MASK] as String?
            MacroBytecode.OP_FIELD_NAME_SID -> symbolTable[fieldInstruction and MacroBytecode.DATA_MASK]
            MacroBytecode.OP_FIELD_NAME_SYSTEM_SID -> EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[fieldInstruction and MacroBytecode.DATA_MASK]
            MacroBytecode.OP_REF_FIELD_NAME_TEXT -> {
                val position = bytecode[fieldName + 1]
                val length = fieldInstruction and MacroBytecode.DATA_MASK
                readText(position, length, source)
            }
            else -> throw IllegalStateException("Field name index does not point to a field name; was ${MacroBytecode(fieldInstruction)}")
        }
    }

    override fun getFieldNameSymbol(): SymbolToken? {
        val fieldName = fieldNameIndex
        if (fieldName < 0) return null
        val fieldInstruction = bytecode[fieldName]
        return when (fieldInstruction.instructionToOp()) {
            MacroBytecode.OP_CP_FIELD_NAME -> {
                val text = constantPool[fieldInstruction and MacroBytecode.DATA_MASK] as String?
                createSymbolToken(text, -1)
            }
            MacroBytecode.OP_FIELD_NAME_SID -> {
                val sid = fieldInstruction and MacroBytecode.DATA_MASK
                val text = symbolTable[sid]
                createSymbolToken(text, sid)
            }
            MacroBytecode.OP_FIELD_NAME_SYSTEM_SID -> {
                val text = EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[fieldInstruction and MacroBytecode.DATA_MASK]
                createSymbolToken(text, -1)
            }
            MacroBytecode.OP_REF_FIELD_NAME_TEXT -> {
                val position = bytecode[fieldName + 1]
                val length = fieldInstruction and MacroBytecode.DATA_MASK
                val text = readText(position, length, source)
                createSymbolToken(text, -1)
            }
            else -> throw IllegalStateException("Field name index does not point to a field name; was ${MacroBytecode(fieldInstruction)}")
        }
    }


    private fun readText(position: Int, length: Int, source: ByteArray): String {
        // TODO: try the decoder pool, as in IonContinuableCoreBinary
        return String(source, position, length, StandardCharsets.UTF_8)
    }

    override fun isNullValue(): Boolean {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        return op == op and 7
    }

    override fun booleanValue(): Boolean {
        val instruction = this.instruction
        if (instruction.instructionToOp() != MacroBytecode.OP_BOOL) {
            throw IonException("Not positioned on a boolean")
        }
        val bool = (instruction and 1) == 1
        return bool
    }

    override fun intValue(): Int = longValue().toInt()

    override fun longValue(): Long {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        var i = bytecodeI
        val long = when (op) {
            MacroBytecode.OP_SMALL_INT -> {
                (instruction and MacroBytecode.DATA_MASK).toShort().toLong()
            }
            MacroBytecode.OP_INLINE_INT -> {
                bytecode[i].toLong()
            }
            MacroBytecode.OP_INLINE_LONG -> {
                val msb = bytecode[i++].toLong()
                val lsb = bytecode[i].toLong()
                (msb shl 32) or lsb
            }
            MacroBytecode.OP_CP_BIG_INT -> {
                val cpIndex = (instruction and MacroBytecode.DATA_MASK)
                val bi = constantPool[cpIndex] as BigInteger
                bi.longValueExact()
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
        return long
    }

    override fun bigIntegerValue(): BigInteger {
        TODO("Not yet implemented")
    }

    override fun doubleValue(): Double {
        var i = bytecodeI
        val op = instruction.instructionToOp()
        val bytecode = bytecode
        val double = when (op) {
            MacroBytecode.OP_INLINE_DOUBLE -> {
                val lsb = bytecode[i++].toLong() and 0xFFFFFFFF
                val msb = bytecode[i].toLong() and 0xFFFFFFFF
                Double.fromBits((msb shl 32) or lsb)
            }
            MacroBytecode.OP_INLINE_FLOAT -> {
                Float.fromBits(bytecode[i]).toDouble()
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
        return double
    }

    override fun bigDecimalValue(): BigDecimal = decimalValue()

    override fun decimalValue(): Decimal {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_REF_DECIMAL -> {
                TODO("Decimal Refs")
            }
            MacroBytecode.OP_CP_DECIMAL -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                return Decimal.valueOf(constantPool[constantPoolIndex] as BigDecimal)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun dateValue(): Date = timestampValue().dateValue()

    override fun timestampValue(): Timestamp {
        val i = bytecodeI
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_TIMESTAMP -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                return constantPool[constantPoolIndex] as Timestamp
            }
            MacroBytecode.OP_REF_TIMESTAMP_SHORT -> {
                val opcode = instruction and MacroBytecode.DATA_MASK
                val position = bytecode[i]
                return TimestampByteArrayHelper.readShortTimestampAt(opcode, source, position)
            }
            MacroBytecode.OP_REF_TIMESTAMP_LONG -> {
                val length = instruction and MacroBytecode.DATA_MASK
                val position = bytecode[i]
                return TimestampByteArrayHelper.readLongTimestampAt(source, position, length)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }


    override fun stringValue(): String? {
        val i = bytecodeI
        return when (instruction.instructionToOp()) {
            MacroBytecode.OP_NULL_TYPED -> null
            MacroBytecode.OP_CP_STRING -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                constantPool[constantPoolIndex] as String
            }
            MacroBytecode.OP_REF_STRING -> {
                val length = instruction and MacroBytecode.DATA_MASK
                val position = bytecode[i]
                String(source, position, length, Charsets.UTF_8)
            }
            MacroBytecode.OP_CP_SYMBOL_TEXT -> {
                constantPool[instruction and MacroBytecode.DATA_MASK] as String?
            }
            MacroBytecode.OP_REF_SYMBOL_TEXT -> {
                val position = bytecode[i + 1]
                val length = instruction and MacroBytecode.DATA_MASK
                readText(position, length, source)
            }
            MacroBytecode.OP_SYMBOL_CHAR -> {
                (instruction and MacroBytecode.DATA_MASK).toChar().toString()
            }
            MacroBytecode.OP_SYMBOL_SYSTEM_SID -> {
                EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[instruction and MacroBytecode.DATA_MASK]
            }
            MacroBytecode.OP_SYMBOL_SID -> {
                val sid = instruction and MacroBytecode.DATA_MASK
                symbolTable[sid]
            }

            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }


    override fun symbolValue(): SymbolToken? {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        val i = bytecodeI
        return when (op) {
            MacroBytecode.OP_NULL_TYPED -> null
            MacroBytecode.OP_CP_SYMBOL_TEXT -> {
                val text = constantPool[instruction and MacroBytecode.DATA_MASK] as String?
                _Private_Utils.newSymbolToken(text)
            }
            MacroBytecode.OP_REF_SYMBOL_TEXT -> {
                val position = bytecode[i + 1]
                val length = instruction and MacroBytecode.DATA_MASK
                val text = readText(position, length, source)
                _Private_Utils.newSymbolToken(text)
            }
            MacroBytecode.OP_SYMBOL_CHAR -> {
                val c = (instruction and MacroBytecode.DATA_MASK).toChar()
                _Private_Utils.newSymbolToken(c.toString())
            }
            MacroBytecode.OP_SYMBOL_SYSTEM_SID -> {
                val text = EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[instruction and MacroBytecode.DATA_MASK]
                _Private_Utils.newSymbolToken(text)
            }
            MacroBytecode.OP_SYMBOL_SID -> {
                val sid = instruction and MacroBytecode.DATA_MASK
                val text = symbolTable[sid]
                createSymbolToken(text, sid)
            }
            else -> throw IonException("Not positioned on a symbol")
        }
    }


    override fun getSymbolTable(): SymbolTable = ArrayBackedLstSnapshot(symbolTable.copyOf())

    override fun byteSize(): Int = TODO("Not yet implemented")
    override fun newBytes(): ByteArray = TODO("Not yet implemented")
    override fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int = TODO("Not yet implemented")
    override fun <T : Any?> asFacet(facetType: Class<T>?): T = TODO("Not yet implemented")
}
