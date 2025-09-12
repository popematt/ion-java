package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.v8.Bytecode.OPERATION_SHIFT_AMOUNT
import com.amazon.ion.v8.Bytecode.TOKEN_TYPE_SHIFT_AMOUNT
import com.amazon.ion.v8.Bytecode.instructionToOp
import com.amazon.ion.v8.Bytecode.opToInstruction
import com.amazon.ion.v8.BytecodeIonReader.AnnotationHelper.EMPTY_ANNOTATIONS
import com.amazon.ion.v8.BytecodeIonReader.AnnotationHelper.EMPTY_ITERATOR
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.*


/**
 * An IonReader implementation that converts top-level values to MacroBytecode before reading.
 *
 * This allows it to hold large top-level values in memory since we don't necessarily need to retain the entire source data.
 *
 * To make this generic over multiple types of input (ByteArray, InputStream, etc.) we only need to be able to
 * - read some scalar references (for blobs, clobs, text, timestamps, decimals) from the input
 * - refill the bytecode from the input
 *
 * TODO: Push the encoding context handling down to the binary-to-bytecode handler so that we can try to get better performance.
 * TODO: See if we can implement an Ion 1.0 binary-to-bytecode handler so that this can handle both Ion versions.
 */
class BytecodeIonReader(
    private val source: ByteArray,
): IonReader {
    // TODO: Move `source` and `sourceI` into a separate abstraction, if possible.
    private var sourceI = 0

    private var bytecodeI = 0

    private var minorVersion: Byte = 1

    // TODO: Implement proper encoding context management that supports multiple modules.
    class ModuleManager {
        class Module {
            // TODO
        }
        val availableModules = mutableMapOf<String, Module>()
        val activeModules = mutableListOf<Module>()
    }

    class Context {
        // TODO: Consolidate some of the macro-related encoding context.
        @JvmField
        internal var macroTable: Array<MacroV8> = emptyArray()
        @JvmField
        internal var macroTableBytecode = IntList()
        @JvmField
        internal var macroBytecodeOffsets = IntList()

        @JvmField
        internal var currentSymbolTable = UnsafeStringList().apply { SYSTEM_SYMBOLS.forEach { add(it) } }

        /* The unused symbol table, kept so that we can clear and re-use it later instead of re-allocating. */
        @JvmField
        internal var availableSymbolTable = UnsafeStringList().apply { SYSTEM_SYMBOLS.forEach { add(it) } }
    }

    @JvmField internal val context = Context()

    @JvmField internal var symbolTable: Array<String?> = context.currentSymbolTable.unsafeGetArray()


    // TODO: Make operations in BytecodeIonReader access the underlying array instead.
    @JvmField internal var bytecodeIntList = IntList().also {
        it.add(Bytecode.REFILL.opToInstruction())
    }
    @JvmField internal var bytecode = bytecodeIntList.unsafeGetArray()

    @JvmField internal var constantPool = UnsafeObjectList<Any?>(256)
    @JvmField internal var firstLocalConstant = 0

    private var instruction = 0

    // Always set to the index after the current `instruction`
    private var fieldNameIndex = -1
    private var annotationsIndex = -1
    private var annotationCount: Byte = 0

    private var isInStruct = false
    private var isNextAlreadyLoaded = false

    private val containerStack = _Private_RecyclingStack(10) { ContainerInfo() }

    data class ContainerInfo(
        @JvmField var isInStruct: Boolean = false,
        @JvmField var bytecodeI: Int = -1,
    )

    companion object {
        const val INSTRUCTION_NOT_SET = 0

        private val SYSTEM_SYMBOLS = arrayOf(
            null,
            "\$ion",
            "\$ion_1_0",
            "\$ion_symbol_table",
            "name",
            "version",
            "imports",
            "symbols",
            "max_id",
            "\$ion_shared_symbol_table",
        )

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
            IonType.CLOB,

            IonType.BLOB,
            IonType.LIST,
            IonType.SEXP,
            IonType.STRUCT,
            // Pad with extra nulls so that we don't get ArrayIndexOutOfBoundsException with some of the non-data model token types.
            null,
            null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null,
        )
    }

    override fun close() {}

    private fun refillBytecode(): IntArray {
        // Fill 1 top-level value, or until the bytecode buffer is full, whichever comes first.

        val source = source
        var i = sourceI

        val bytecodeIntList = bytecodeIntList
        bytecodeIntList.clear()
        val constantPool = constantPool
        constantPool.truncate(firstLocalConstant)
        val context = context

        i += compileTopLevel(source, i, bytecodeIntList, constantPool, context.macroTableBytecode.unsafeGetArray(), context.macroBytecodeOffsets.unsafeGetArray(), symbolTable, source.size)
        sourceI = i

        val bytecodeArray = bytecodeIntList.unsafeGetArray()
        this.bytecode = bytecodeArray

//        Bytecode.debugString(bytecodeArray)
//        println()

        return bytecodeArray
    }

    override fun hasNext(): Boolean {
        val result = next() != null
        isNextAlreadyLoaded = true
        return result
    }

    override fun next(): IonType? {
        if (isNextAlreadyLoaded) {
            isNextAlreadyLoaded = false
            return type
        }

        var bytecode = bytecode
        var i = bytecodeI
        var instruction = instruction
        var op = instruction.instructionToOp()

        var tokenType: Int

        var annotationStart = 0 // Stores the start of the annotations, offset by 1 (for bit-twiddling reasons).
        var annotationFlag = -1 // -1 if not set, 0 if set. Used to avoid branches in the annotation case.
        var annotationCount = 0

        val N_OPERANDS = Bytecode.Operations.N_OPERANDS

        do {
            // Move to the next instruction.
            val length = instruction and Bytecode.DATA_MASK
            // TODO: This line is 7% of all. Can we improve it? Maybe, if we can encode the operands into the variant somehow.
            val operandsToSkip = N_OPERANDS[op]
            // Figure out how to make this branchless
            i += if (operandsToSkip.toInt() == -1) length else operandsToSkip.toInt()



            instruction = bytecode[i++]
            op = instruction.instructionToOp()

            tokenType = (instruction ushr (OPERATION_SHIFT_AMOUNT + TOKEN_TYPE_SHIFT_AMOUNT))

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
                    bytecode = refillBytecode()
                    i = 0
                }
                TokenTypeConst.SYSTEM_VALUE -> {
                    i += handleSystemValue(instruction, bytecode, i)
                }
                TokenTypeConst.EOF -> {

                    break
                }
                else -> {
                    TODO("${TokenTypeConst(tokenType)} at ${i - 1}")
                }
            }
        } while (true)

        this.bytecodeI = i
        this.instruction = instruction
        this.annotationsIndex = annotationStart - 1
        this.annotationCount = annotationCount.toByte()

        return getType(instruction)
    }

    private fun handleIvm(instruction: Int) {
        val minorVersion = instruction and Bytecode.DATA_MASK
        // TODO: Reset active modules, etc.
        when (minorVersion) {
            0 -> {
                this.minorVersion = 0
//                this.symbolTable = EncodingContext.ION_1_0_SYMBOL_TABLE
                this.context.macroTable = emptyArray()
                TODO()
            }
            1 -> {
                this.minorVersion = 1
                this.symbolTable = SYSTEM_SYMBOLS
                this.context.currentSymbolTable.truncate(10)
                this.context.macroTable = emptyArray()
            }
        }
    }

    // TODO: Push this into the bytecode compiler
    private fun handleSystemValue(instruction: Int, bytecode: IntArray, position: Int): Int {
        // TODO: We could probably make this more efficient, or re-use some of the existing code.
        var i = position
        when (instruction.instructionToOp()) {
            Bytecode.DIRECTIVE_SET_SYMBOLS -> {
                val context = context
                val newSymbols = context.availableSymbolTable
                newSymbols.truncate(10)
                // newSymbols.add(null)
                val constantPool = constantPool
                while (true) {
                    val instruction0 = bytecode[i++]
                    val s = when (instruction0.instructionToOp()) {
                        Bytecode.OP_CP_SYMBOL_TEXT,
                        Bytecode.OP_CP_STRING -> constantPool[instruction0 and Bytecode.DATA_MASK] as String?
                        Bytecode.OP_REF_SYMBOL_TEXT,
                        Bytecode.OP_REF_STRING -> readText(position = bytecode[i++], length = instruction0 and Bytecode.DATA_MASK, source)
                        Bytecode.OP_SYMBOL_CHAR -> (instruction0 and Bytecode.DATA_MASK).toChar().toString()
                        Bytecode.OP_SYMBOL_SID -> symbolTable[instruction0 and Bytecode.DATA_MASK]
                        Bytecode.OP_NULL_SYMBOL,
                        Bytecode.OP_NULL_STRING -> null
                        Bytecode.OP_CONTAINER_END -> break
                        else -> throw IonException("Not a valid symbol declaration")
                    }
                    newSymbols.add(s)
                }
                context.availableSymbolTable = context.currentSymbolTable
                context.currentSymbolTable = newSymbols
                this.symbolTable = newSymbols.unsafeGetArray()
            }
            Bytecode.DIRECTIVE_ADD_SYMBOLS -> {
                val newSymbols = context.currentSymbolTable
                val constantPool = constantPool
                while (true) {
                    val instruction0 = bytecode[i++]
                    val s = when (instruction0.instructionToOp()) {
                        Bytecode.OP_CP_SYMBOL_TEXT,
                        Bytecode.OP_CP_STRING -> constantPool[instruction0 and Bytecode.DATA_MASK] as String?
                        Bytecode.OP_REF_SYMBOL_TEXT,
                        Bytecode.OP_REF_STRING -> readText(position = bytecode[i++], length = instruction0 and Bytecode.DATA_MASK, source)
                        Bytecode.OP_SYMBOL_CHAR -> (instruction0 and Bytecode.DATA_MASK).toChar().toString()
                        Bytecode.OP_SYMBOL_SID -> symbolTable[instruction0 and Bytecode.DATA_MASK]
                        Bytecode.OP_NULL_SYMBOL,
                        Bytecode.OP_NULL_STRING -> null
                        Bytecode.OP_CONTAINER_END -> break
                        else -> throw IonException("Not a valid symbol declaration")
                    }
                    newSymbols.add(s)
                }

                this.symbolTable = newSymbols.unsafeGetArray()
            }
            Bytecode.DIRECTIVE_SET_MACROS -> {
                val newMacroNames = UnsafeObjectList<String?>()
                val newMacroBytecode = IntList()
                val newMacroOffsets = IntList()
                newMacroOffsets.add(0)

//                Bytecode.debugString(bytecode)
                // TODO: We need some way to "garbage collect" from the constant pool.
                while (true) {
                    val instruction0 = bytecode[i++]
                    when (instruction0.instructionToOp()) {
                        Bytecode.OP_CONTAINER_END -> break
                        Bytecode.OP_SEXP_START -> {
                            val sexpLength = instruction0 and Bytecode.DATA_MASK
                            val end = i + sexpLength
                            val nameInstruction = bytecode[i++]
                            val name = when (nameInstruction.instructionToOp()) {
                                Bytecode.OP_CP_SYMBOL_TEXT,
                                Bytecode.OP_CP_STRING -> constantPool[nameInstruction and Bytecode.DATA_MASK] as String?
                                Bytecode.OP_REF_SYMBOL_TEXT,
                                Bytecode.OP_REF_STRING -> readText(position = bytecode[i++], length = nameInstruction and Bytecode.DATA_MASK, source)
                                Bytecode.OP_SYMBOL_CHAR -> (nameInstruction and Bytecode.DATA_MASK).toChar().toString()
                                // Bytecode.OP_SYMBOL_SYSTEM_SID -> EncodingContextManager.ION_1_1_SYSTEM_SYMBOLS_AS_SYMBOL_TABLE[nameInstruction and Bytecode.DATA_MASK]
                                Bytecode.OP_SYMBOL_SID -> symbolTable[nameInstruction and Bytecode.DATA_MASK]
                                Bytecode.OP_NULL_NULL,
                                Bytecode.OP_NULL_SYMBOL,
                                Bytecode.OP_NULL_STRING -> null
                                else -> throw IonException("Not valid instruction for macro name: ${Bytecode(nameInstruction)}")
                            }
                            newMacroNames.add(name)
                            newMacroBytecode.addSlice(bytecode, i, end - i - 1)
                            newMacroBytecode.add(Bytecode.EOF.opToInstruction())
                            i = end
                            newMacroOffsets.add(newMacroBytecode.size())
                        }
                        else -> TODO("Not supported in macro table: ${Bytecode(instruction0)}")
                    }
                }
                this.firstLocalConstant = constantPool.size
                val context = this.context
                context.macroTableBytecode = newMacroBytecode
                context.macroBytecodeOffsets = newMacroOffsets

//                Bytecode.debugString(macroTableBytecode.unsafeGetArray(), constantPool = constantPool.toArray())
            }
            Bytecode.DIRECTIVE_ADD_MACROS -> {
                val newMacroNames = UnsafeObjectList<String?>()
                val context = this.context
                val newMacroBytecode = context.macroTableBytecode
                val newMacroOffsets = context.macroBytecodeOffsets

                while (true) {
                    val instruction0 = bytecode[i++]
                    when (instruction0.instructionToOp()) {
                        Bytecode.OP_CONTAINER_END -> break
                        Bytecode.OP_SEXP_START -> {
                            val sexpLength = instruction0 and Bytecode.DATA_MASK
                            val end = i + sexpLength
                            val nameInstruction = bytecode[i++]
                            val name = when (nameInstruction.instructionToOp()) {
                                Bytecode.OP_CP_SYMBOL_TEXT,
                                Bytecode.OP_CP_STRING -> constantPool[nameInstruction and Bytecode.DATA_MASK] as String?
                                Bytecode.OP_REF_SYMBOL_TEXT,
                                Bytecode.OP_REF_STRING -> readText(position = bytecode[i++], length = nameInstruction and Bytecode.DATA_MASK, source)
                                Bytecode.OP_SYMBOL_CHAR -> (nameInstruction and Bytecode.DATA_MASK).toChar().toString()
                                Bytecode.OP_SYMBOL_SID -> symbolTable[nameInstruction and Bytecode.DATA_MASK]
                                Bytecode.OP_NULL_NULL,
                                Bytecode.OP_NULL_SYMBOL,
                                Bytecode.OP_NULL_STRING -> null
                                else -> throw IonException("Not valid instruction for macro name: $nameInstruction")
                            }
                            newMacroNames.add(name)
                            newMacroBytecode.addSlice(bytecode, i, end - i)
                            newMacroBytecode.add(Bytecode.EOF.instructionToOp())
                            i = end
                            newMacroOffsets.add(newMacroBytecode.size())
                        }
                        else -> TODO("Not supported in macro table: ${Bytecode(instruction0)}")
                    }
                }
                this.firstLocalConstant = constantPool.size
            }
            else -> TODO()
        }
        return i - position
    }

    override fun getType(): IonType? = getType(instruction)

    fun getType(instruction: Int): IonType? {
        return TOKEN_TO_ION_TYPES[instruction ushr (OPERATION_SHIFT_AMOUNT + TOKEN_TYPE_SHIFT_AMOUNT)]
    }

    override fun stepIn() {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        when (op) {
            Bytecode.OP_LIST_START -> {
                val length = instruction and Bytecode.DATA_MASK
                containerStack.push {
                    it.bytecodeI = bytecodeI + length
                    it.isInStruct = isInStruct
                }
                this.isInStruct = false
            }
            Bytecode.OP_SEXP_START -> {
                val length = instruction and Bytecode.DATA_MASK
                containerStack.push {
                    it.bytecodeI = bytecodeI + length
                    it.isInStruct = isInStruct
                }
                this.isInStruct = false
            }
            Bytecode.OP_STRUCT_START -> {
                val length = instruction and Bytecode.DATA_MASK
                containerStack.push {
                    it.bytecodeI = bytecodeI + length
                    it.isInStruct = isInStruct
                }
                this.isInStruct = true
            }
            else -> throw IonException("Not positioned on a container")
        }

        this.instruction = INSTRUCTION_NOT_SET
    }

    override fun stepOut() {

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
            Bytecode.OP_SMALL_INT -> IntegerSize.INT
            Bytecode.OP_INLINE_INT -> IntegerSize.INT
            Bytecode.OP_INLINE_LONG -> IntegerSize.LONG
            Bytecode.OP_CP_BIG_INT -> IntegerSize.BIG_INTEGER
            else -> throw IonException("Not positioned on an int value")
        }
    }

    object AnnotationHelper {
        @JvmStatic
        val EMPTY_ANNOTATIONS: Array<String?> = emptyArray()
        @JvmStatic
        val EMPTY_ITERATOR: Iterator<String?> = object: Iterator<String?> {
            override fun hasNext(): Boolean = false
            override fun next(): String? = throw NoSuchElementException()
        }
    }

    override fun getTypeAnnotations(): Array<String?> {
        val nAnnotations = annotationCount.toInt()
        if (nAnnotations == 0) {
            return EMPTY_ANNOTATIONS
        }
        val result = arrayOfNulls<String>(nAnnotations)
        var p = annotationsIndex
        val bytecode = this.bytecode
        for (i in 0 until nAnnotations) {
            val instruction = bytecode[p++]
            result[i] = when (instruction.instructionToOp()) {
                Bytecode.OP_CP_ANNOTATION -> constantPool[instruction and Bytecode.DATA_MASK] as String?
                Bytecode.OP_ANNOTATION_SID -> symbolTable[instruction and Bytecode.DATA_MASK]
                Bytecode.OP_REF_ANNOTATION -> {
                    val position = bytecode[p++]
                    val length = instruction and Bytecode.DATA_MASK
                    readText(position, length, source)
                }
                else -> throw IllegalStateException("annotationsIndex does not point to an annotation; was ${Bytecode(instruction)}")
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
            val instruction = bytecode[p++]
            when (instruction.instructionToOp()) {
                Bytecode.OP_CP_ANNOTATION -> {
                    val text = constantPool[instruction and Bytecode.DATA_MASK] as String?
                    createSymbolToken(text, -1)
                }
                Bytecode.OP_ANNOTATION_SID -> {
                    val sid = instruction and Bytecode.DATA_MASK
                    val text = symbolTable[sid]
                    createSymbolToken(text, sid)
                }
                Bytecode.OP_REF_ANNOTATION -> {
                    val position = bytecode[p++]
                    val length = instruction and Bytecode.DATA_MASK
                    val text = readText(position, length, source)
                    createSymbolToken(text, -1)
                }
                else -> throw IllegalStateException("Annotation index does not point to an annotation; was ${Bytecode(instruction)}")
            }
        }
        return result
    }

    // TODO: We could make this lazy.
    override fun iterateTypeAnnotations(): Iterator<String?> = if (annotationCount.toInt() == 0) EMPTY_ITERATOR else typeAnnotations.iterator()

    override fun getFieldId(): Int {
        val fieldName = fieldNameIndex
        if (fieldName < 0) return -1
        val fieldInstruction = bytecode[fieldName]
        return when (fieldInstruction.instructionToOp()) {
            Bytecode.OP_CP_FIELD_NAME -> -1
            Bytecode.OP_FIELD_NAME_SID -> fieldInstruction and Bytecode.DATA_MASK
            Bytecode.OP_REF_FIELD_NAME_TEXT -> -1
            else -> throw IllegalStateException("Field name index does not point to a field name; was ${Bytecode(fieldInstruction)}")
        }
    }

    override fun getFieldName(): String? {
        val fieldName = fieldNameIndex
        if (fieldName < 0) return null
        val fieldInstruction = bytecode[fieldName]
        return when (fieldInstruction.instructionToOp()) {
            Bytecode.OP_CP_FIELD_NAME -> constantPool[fieldInstruction and Bytecode.DATA_MASK] as String?
            Bytecode.OP_FIELD_NAME_SID -> symbolTable[fieldInstruction and Bytecode.DATA_MASK]
            Bytecode.OP_REF_FIELD_NAME_TEXT -> {
                val position = bytecode[fieldName + 1]
                val length = fieldInstruction and Bytecode.DATA_MASK
                readText(position, length, source)
            }
            else -> throw IllegalStateException("Field name index does not point to a field name; was ${Bytecode(fieldInstruction)}")
        }
    }

    override fun getFieldNameSymbol(): SymbolToken? {
        val fieldName = fieldNameIndex
        if (fieldName < 0) return null
        val fieldInstruction = bytecode[fieldName]
        return when (fieldInstruction.instructionToOp()) {
            Bytecode.OP_CP_FIELD_NAME -> {
                val text = constantPool[fieldInstruction and Bytecode.DATA_MASK] as String?
                createSymbolToken(text, -1)
            }
            Bytecode.OP_FIELD_NAME_SID -> {
                val sid = fieldInstruction and Bytecode.DATA_MASK
                val text = symbolTable[sid]
                createSymbolToken(text, sid)
            }
            Bytecode.OP_REF_FIELD_NAME_TEXT -> {
                val position = bytecode[fieldName + 1]
                val length = fieldInstruction and Bytecode.DATA_MASK
                val text = readText(position, length, source)
                createSymbolToken(text, -1)
            }
            else -> throw IllegalStateException("Field name index does not point to a field name; was ${Bytecode(fieldInstruction)}")
        }
    }


    private fun readText(position: Int, length: Int, source: ByteArray): String {
        // TODO: try the decoder pool, as in IonContinuableCoreBinary
        return String(source, position, length, StandardCharsets.UTF_8)
    }

    override fun isNullValue(): Boolean {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        return 7 == (op and 7)
    }

    override fun booleanValue(): Boolean {
        val instruction = this.instruction
        if (instruction.instructionToOp() != Bytecode.OP_BOOL) {
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
            Bytecode.OP_SMALL_INT -> {
                (instruction and Bytecode.DATA_MASK).toShort().toLong()
            }
            Bytecode.OP_INLINE_INT -> {
                bytecode[i].toLong()
            }
            Bytecode.OP_INLINE_LONG -> {
                val msb = bytecode[i++].toLong()
                val lsb = bytecode[i].toLong()
                (msb shl 32) or lsb
            }
            Bytecode.OP_CP_BIG_INT -> {
                val cpIndex = (instruction and Bytecode.DATA_MASK)
                val bi = constantPool[cpIndex] as BigInteger
                bi.longValueExact()
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${Bytecode(instruction)}")
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
            Bytecode.OP_INLINE_DOUBLE -> {
                val lsb = bytecode[i++].toLong() and 0xFFFFFFFF
                val msb = bytecode[i].toLong() and 0xFFFFFFFF
                Double.fromBits((msb shl 32) or lsb)
            }
            Bytecode.OP_INLINE_FLOAT -> {
                Float.fromBits(bytecode[i]).toDouble()
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${Bytecode(instruction)}")
        }
        return double
    }

    override fun bigDecimalValue(): BigDecimal = decimalValue()

    override fun decimalValue(): Decimal {
        val op = instruction.instructionToOp()
        when (op) {
            Bytecode.OP_REF_DECIMAL -> {
                val i = bytecodeI
                val length = instruction and Bytecode.DATA_MASK
                val position = bytecode[i]
                return DecimalHelper.readDecimal(source, position, length)
            }
            Bytecode.OP_CP_DECIMAL -> {
                val constantPoolIndex = (instruction and Bytecode.DATA_MASK)
                return Decimal.valueOf(constantPool[constantPoolIndex] as BigDecimal)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${Bytecode(instruction)}")
        }
    }

    override fun dateValue(): Date = timestampValue().dateValue()

    override fun timestampValue(): Timestamp {
        val i = bytecodeI
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        when (op) {
            Bytecode.OP_CP_TIMESTAMP -> {
                val constantPoolIndex = (instruction and Bytecode.DATA_MASK)
                return constantPool[constantPoolIndex] as Timestamp
            }
            Bytecode.OP_REF_TIMESTAMP_SHORT -> {
                val opcode = instruction and Bytecode.DATA_MASK
                val position = bytecode[i]
                return TimestampHelper.readShortTimestampAt(opcode, source, position)
            }
            Bytecode.OP_REF_TIMESTAMP_LONG -> {
                val length = instruction and Bytecode.DATA_MASK
                val position = bytecode[i]
                return TimestampHelper.readLongTimestampAt(source, position, length)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${Bytecode(instruction)}")
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    override fun stringValue(): String? {
        val i = bytecodeI
        return when (instruction.instructionToOp()) {
            Bytecode.OP_NULL_STRING,
            Bytecode.OP_NULL_SYMBOL -> null
            Bytecode.OP_CP_STRING -> {
                val constantPoolIndex = (instruction and Bytecode.DATA_MASK)
                constantPool[constantPoolIndex] as String
            }
            Bytecode.OP_CP_SYMBOL_TEXT -> {
                constantPool[instruction and Bytecode.DATA_MASK] as String?
            }
            Bytecode.OP_REF_STRING -> {
                val length = instruction and Bytecode.DATA_MASK
                val position = bytecode[i]
                String(source, position, length, Charsets.UTF_8)
            }
            Bytecode.OP_REF_SYMBOL_TEXT -> {
                val position = bytecode[i]
                val length = instruction and Bytecode.DATA_MASK
                readText(position, length, source)
            }
            Bytecode.OP_SYMBOL_CHAR -> {
                (instruction and Bytecode.DATA_MASK).toChar().toString()
            }
            Bytecode.OP_SYMBOL_SID -> {
                val sid = instruction and Bytecode.DATA_MASK
                symbolTable[sid]
            }

            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${Bytecode(instruction)}")
        }
    }


    override fun symbolValue(): SymbolToken? {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        val i = bytecodeI
        return when (op) {
            Bytecode.OP_NULL_SYMBOL -> null
            Bytecode.OP_CP_SYMBOL_TEXT -> {
                val text = constantPool[instruction and Bytecode.DATA_MASK] as String?
                _Private_Utils.newSymbolToken(text)
            }
            Bytecode.OP_REF_SYMBOL_TEXT -> {
                val position = bytecode[i]
                val length = instruction and Bytecode.DATA_MASK
                val text = readText(position, length, source)
                _Private_Utils.newSymbolToken(text)
            }
            Bytecode.OP_SYMBOL_CHAR -> {
                val c = (instruction and Bytecode.DATA_MASK).toChar()
                _Private_Utils.newSymbolToken(c.toString())
            }
            Bytecode.OP_SYMBOL_SID -> {
                val sid = instruction and Bytecode.DATA_MASK
                val text = symbolTable[sid]
                createSymbolToken(text, sid)
            }
            else -> throw IonException("Not positioned on a symbol")
        }
    }


    override fun getSymbolTable(): SymbolTable = ArrayBackedLstSnapshot(context.currentSymbolTable.toArray())

    override fun byteSize(): Int {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        return when (op) {
            Bytecode.OP_CP_BLOB,
            Bytecode.OP_CP_CLOB -> {
                val slice = constantPool[instruction and Bytecode.DATA_MASK] as ByteArraySlice
                slice.length
            }
            Bytecode.OP_REF_BLOB,
            Bytecode.OP_REF_CLOB -> {
                val length = instruction and Bytecode.DATA_MASK
                length
            }
            else -> throw IonException("Not positioned on a lob value")
        }
    }

    override fun newBytes(): ByteArray {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        val i = bytecodeI
        return when (op) {
            Bytecode.OP_CP_BLOB,
            Bytecode.OP_CP_CLOB -> {
                val slice = constantPool[instruction and Bytecode.DATA_MASK] as ByteArraySlice
                slice.newByteArray()
            }
            Bytecode.OP_REF_BLOB,
            Bytecode.OP_REF_CLOB -> {
                val length = instruction and Bytecode.DATA_MASK
                val position = bytecode[i]
                source.copyOfRange(position, position + length)
            }
            else -> throw IonException("Not positioned on a lob value")
        }
    }

    override fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int = TODO("Not yet implemented")
    override fun <T : Any?> asFacet(facetType: Class<T>?): T = TODO("Not yet implemented")
}
