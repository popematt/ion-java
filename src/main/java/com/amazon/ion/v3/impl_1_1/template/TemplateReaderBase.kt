package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.*
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.bytecodeToString
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.instructionToOp
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

abstract class TemplateReaderBase(
    @JvmField
    val pool: TemplateResourcePool,
    // Available modules? No. That's already resolved in the compiler.
    // Macro table? No. That's already resolved in the compiler.
): ValueReader, TemplateReader {

    // TODO:
    //   Switch this to use the bytecode from the macro definition. See if we get a perf improvement.
    //   Try to convert E-Exp args to bytecode as well.
    //   Also try inlining macro invocations as well.

    @JvmField
    var bytecode: IntArray = IntArray(0)
    @JvmField
    var i = 0
    @JvmField
    var constantPool: Array<Any?> = arrayOf()

    private var bytecode1: IntArray? = null
    private var i1 = -1
    private var constantPool1: Array<Any?>? = null

    private var argumentValueIndices: MutableList<Int> = mutableListOf()

    @JvmField
    var arguments: ArgumentBytecode = ArgumentBytecode.NO_ARGS

    @JvmField
    var instruction: Int = INSTRUCTION_NOT_SET


    companion object {
        const val INSTRUCTION_NOT_SET = MacroBytecode.UNSET shl 24
    }

    fun init(
        bytecode: IntArray,
        constantPool: Array<Any?>,
        arguments: ArgumentBytecode,
        isArgumentOwner: Boolean,
     ) {
        this.bytecode = bytecode
        this.constantPool = constantPool
        this.arguments = arguments
        instruction = INSTRUCTION_NOT_SET
        i = 0
    }

    override fun nextToken(): Int {
        var instruction = INSTRUCTION_NOT_SET
        while (instruction == INSTRUCTION_NOT_SET) {
            instruction = bytecode[i++]
            val op = instruction ushr 24
            when (op) {
                MacroBytecode.OP_START_ARGUMENT_VALUE -> {
                    argumentValueIndices.add(i - 1)
                }
                MacroBytecode.OP_ARGUMENT_REF_TYPE -> {
                    if (bytecode1 != null) throw IllegalStateException("Need to push more than one set of variables.")
                    bytecode1 = bytecode
                    i1 = i
                    constantPool1 = constantPool
                    bytecode = arguments.getArgument(instruction and 0xFFFFFF)
                    i = 0
                    constantPool = arguments.constantPool()
                    instruction = INSTRUCTION_NOT_SET
                }
                MacroBytecode.END_OF_ARGUMENT_SUBSTITUTION -> {
                    bytecode = bytecode1!!
                    bytecode1 = null
                    constantPool = constantPool1!!
                    i = i1
                    instruction = INSTRUCTION_NOT_SET
                }
            }
        }
        this.instruction = instruction
        return tokenType(instruction ushr 24).also {
            if (it == TokenTypeConst.UNSET) {
                throw Exception("TokenTypeConst == UNSET; instruction: ${instruction.toString(16)}; i=$i, i1=$i1")
            }
        }
    }

    private fun _nextToken(): Int {
        var instruction = INSTRUCTION_NOT_SET
        while (instruction == INSTRUCTION_NOT_SET) {
            instruction = bytecode[i++]
            val op = instruction ushr 24
            when (op) {
                MacroBytecode.OP_START_ARGUMENT_VALUE -> {
                    argumentValueIndices.add(i - 1)
                }
                MacroBytecode.OP_ARGUMENT_REF_TYPE -> {
                    if (bytecode1 != null) throw IllegalStateException("Need to push more than one set of variables.")
                    bytecode1 = bytecode
                    i1 = i
                    constantPool1 = constantPool
                    bytecode = arguments.getArgument(instruction and 0xFFFFFF)
                    i = 0
                    constantPool = arguments.constantPool()
                    instruction = INSTRUCTION_NOT_SET
                }
                MacroBytecode.END_OF_ARGUMENT_SUBSTITUTION -> {
                    bytecode = bytecode1!!
                    bytecode1 = null
                    constantPool = constantPool1!!
                    i = i1
                    instruction = INSTRUCTION_NOT_SET
                }
            }
        }
        this.instruction = instruction
        return tokenType(instruction ushr 24).also {
            if (it == TokenTypeConst.UNSET) {
                throw Exception("TokenTypeConst == UNSET; instruction: ${instruction.toString(16)}; i=$i, i1=$i1")
            }
        }
    }

    final override fun close() {
        returnToPool()
    }

    protected abstract fun returnToPool()


    @OptIn(ExperimentalStdlibApi::class)
    private fun tokenType(operation: Int): Int {
        // TODO: This method is >10% of all.
        //       See if we can align the MacroBytecode and the TokenTypeConst values so that
        //       We can perform a simple arithmatic operation instead of a jump table.
        when (operation) {
            // TODO: Reference instructions
            MacroBytecode.OP_NULL_NULL,
            MacroBytecode.OP_NULL_TYPED -> return TokenTypeConst.NULL
            MacroBytecode.OP_BOOL -> return TokenTypeConst.BOOL
            MacroBytecode.OP_SMALL_INT,
            MacroBytecode.OP_INLINE_INT,
            MacroBytecode.OP_INLINE_LONG,
            MacroBytecode.OP_CP_BIG_INT -> return TokenTypeConst.INT
            MacroBytecode.OP_INLINE_DOUBLE -> return TokenTypeConst.FLOAT
            MacroBytecode.OP_CP_DECIMAL,
            MacroBytecode.OP_DECIMAL_ZERO -> return TokenTypeConst.DECIMAL
            MacroBytecode.OP_CP_TIMESTAMP -> return TokenTypeConst.TIMESTAMP
            MacroBytecode.OP_CP_STRING,
            MacroBytecode.OP_EMPTY_STRING -> return TokenTypeConst.STRING
            MacroBytecode.OP_CP_SYMBOL,
            MacroBytecode.OP_CP_SYMBOL_ID,
            MacroBytecode.OP_UNKNOWN_SYMBOL -> return TokenTypeConst.SYMBOL
            MacroBytecode.OP_CP_BLOB -> return TokenTypeConst.BLOB
            MacroBytecode.OP_CP_CLOB -> return TokenTypeConst.CLOB
            MacroBytecode.OP_REF_LIST,
            MacroBytecode.OP_LIST_START -> return TokenTypeConst.LIST
            MacroBytecode.OP_REF_SEXP,
            MacroBytecode.OP_SEXP_START -> return TokenTypeConst.SEXP
            MacroBytecode.OP_REF_SID_STRUCT,
            MacroBytecode.OP_REF_FLEXSYM_STRUCT,
            MacroBytecode.OP_STRUCT_START -> return TokenTypeConst.STRUCT
            MacroBytecode.OP_INVOKE_MACRO -> return TokenTypeConst.MACRO_INVOCATION
            MacroBytecode.OP_CP_FIELD_NAME,
            MacroBytecode.OP_FIELD_NAME_SID -> return TokenTypeConst.FIELD_NAME
            MacroBytecode.OP_CP_ONE_ANNOTATION,
            MacroBytecode.OP_CP_N_ANNOTATIONS -> return TokenTypeConst.ANNOTATIONS
            MacroBytecode.OP_LIST_END,
            MacroBytecode.OP_SEXP_END,
            MacroBytecode.OP_STRUCT_END,
            MacroBytecode.EOF -> return TokenTypeConst.END
            MacroBytecode.UNSET -> return TokenTypeConst.UNSET
            MacroBytecode.OP_REF_MACRO_INVOCATION -> return TokenTypeConst.MACRO_INVOCATION
            MacroBytecode.OP_START_ARGUMENT_VALUE -> return TokenTypeConst.EXPRESSION_GROUP
            MacroBytecode.OP_END_ARGUMENT_VALUE -> return TokenTypeConst.END
            // TODO: Arguments?
            else -> TODO("Op: ${operation.toHexString()}")
        }
    }

    // TODO: Make sure that this also returns END when at the end of the input.
    // TODO: See if we can eliminate this from the API.
    override fun currentToken(): Int {
        val op = instruction ushr 24
        return tokenType(op)
    }

    // TODO: See if we can eliminate this from the API.
    override fun isTokenSet(): Boolean = currentToken() != TokenTypeConst.UNSET

    override fun ionType(): IonType? {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_NULL_NULL -> return IonType.NULL
            MacroBytecode.OP_NULL_TYPED -> return IonType.entries[(instruction and 0xFF)]
            MacroBytecode.OP_BOOL -> return IonType.BOOL
            MacroBytecode.OP_SMALL_INT,
            MacroBytecode.OP_INLINE_INT,
            MacroBytecode.OP_INLINE_LONG,
            MacroBytecode.OP_CP_BIG_INT -> return IonType.INT
            MacroBytecode.OP_INLINE_DOUBLE -> return IonType.FLOAT
            MacroBytecode.OP_CP_DECIMAL,
            MacroBytecode.OP_DECIMAL_ZERO -> return IonType.DECIMAL
            MacroBytecode.OP_CP_TIMESTAMP -> return IonType.TIMESTAMP
            MacroBytecode.OP_CP_STRING,
            MacroBytecode.OP_EMPTY_STRING -> return IonType.STRING
            MacroBytecode.OP_CP_SYMBOL,
            MacroBytecode.OP_CP_SYMBOL_ID,
            MacroBytecode.OP_UNKNOWN_SYMBOL -> return IonType.SYMBOL
            MacroBytecode.OP_CP_BLOB -> return IonType.BLOB
            MacroBytecode.OP_CP_CLOB -> return IonType.CLOB
            MacroBytecode.OP_LIST_START -> return IonType.LIST
            MacroBytecode.OP_SEXP_START -> return IonType.SEXP
            MacroBytecode.OP_STRUCT_START -> return IonType.STRUCT

            MacroBytecode.OP_REF_MACRO_INVOCATION,

            MacroBytecode.UNSET,
            MacroBytecode.EOF,
            MacroBytecode.OP_INVOKE_MACRO -> return null

            else -> TODO()
        }
    }

    override fun valueSize(): Int {
        return when (instruction.instructionToOp()) {
            MacroBytecode.OP_SMALL_INT -> 2
            MacroBytecode.OP_INLINE_INT -> 4
            MacroBytecode.OP_INLINE_LONG -> 8
            MacroBytecode.OP_CP_BIG_INT -> 9
            MacroBytecode.OP_CP_BLOB,
            MacroBytecode.OP_CP_CLOB -> {
                (constantPool[instruction and 0xFFFFFF] as ByteBuffer).remaining()
            }
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
    }

    override fun skip() {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_INLINE_INT -> i++
            MacroBytecode.OP_INLINE_DOUBLE,
            MacroBytecode.OP_INLINE_LONG -> i += 2
            MacroBytecode.OP_LIST_START,
            MacroBytecode.OP_SEXP_START,
            MacroBytecode.OP_STRUCT_START -> {
                val length = instruction and 0xFFFFFF
                i+= length
            }
            MacroBytecode.EOF,
            MacroBytecode.OP_LIST_END,
            MacroBytecode.OP_SEXP_END,
            MacroBytecode.OP_STRUCT_END -> {
                throw UnsupportedOperationException("Cannot skip past end.")
            }
        }
        instruction = INSTRUCTION_NOT_SET
    }

    override fun macroValue(): MacroV2 {
        val constantPoolIndex = (instruction and 0xFFFFFF)
        // instruction = MacroBytecode.NONE.opToInstruction()
        return constantPool[constantPoolIndex] as MacroV2
    }

    override fun macroArguments(signature: Array<Macro.Parameter>): ArgumentReader {
        if (signature.isEmpty()) {
            instruction = INSTRUCTION_NOT_SET
            return NoneReader
        }
        TODO()
    }

    override fun macroArgumentsNew(signature: Array<Macro.Parameter>): ArgumentBytecode {
        instruction = INSTRUCTION_NOT_SET
        val cpIndex = bytecode[i++]
        return constantPool[cpIndex] as ArgumentBytecode
    }

    class TemplateMacroArgs(
        private val arguments: Array<IntArray>,
        private val constantPool: Array<Any?>
    ): ArgumentBytecode {
        override fun constantPool(): Array<Any?> {
            return constantPool
        }

        override fun getArgument(parameterIndex: Int): IntArray {
            return arguments[parameterIndex]
        }

        override fun getList(start: Int, length: Int): ListReader {
            TODO("Not yet implemented")
        }

        override fun getSexp(start: Int, length: Int): SexpReader {
            TODO("Not yet implemented")
        }

        override fun getStruct(start: Int, length: Int, flexsymMode: Boolean): StructReader {
            TODO("Not yet implemented")
        }

        override fun getMacro(macroAddress: Int): MacroV2 {
            TODO("Not yet implemented")
        }

        override fun getSymbol(sid: Int): String? {
            TODO("Not yet implemented")
        }
    }

    override fun expressionGroup(): SequenceReader {
        return pool.getSequence(arguments, bytecode, i, constantPool)
    }

    override fun annotations(): AnnotationIterator {
        val annotations = when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_N_ANNOTATIONS -> {
                val constantPoolIndex = (instruction and 0xFFFFFF)
                val annotations = arrayOfNulls<String?>(1)
                annotations[0] = constantPool[constantPoolIndex] as String?
                annotations
            }
            MacroBytecode.OP_CP_ONE_ANNOTATION -> {
                val nAnnotations = (instruction and 0xFFFFFF)
                Array(nAnnotations) { constantPool[bytecode[i++]] as String? }
            }
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
        instruction = INSTRUCTION_NOT_SET
        return pool.getAnnotations(annotations)
    }

    override fun nullValue(): IonType {
        val op = instruction.instructionToOp()
        instruction = INSTRUCTION_NOT_SET
        return when (op) {
            MacroBytecode.OP_NULL_NULL -> IonType.NULL
            MacroBytecode.OP_NULL_TYPED -> IonType.entries[(instruction and 0xFF)]
            else -> TODO("Not a null value: $op")
        }
    }

    override fun booleanValue(): Boolean {
        val bool = (instruction and 1) == 1
        instruction = INSTRUCTION_NOT_SET
        return bool
    }

    override fun longValue(): Long {
        val op = instruction.instructionToOp()
        val long = when (op) {
            MacroBytecode.OP_SMALL_INT -> {
                instruction.toShort().toLong()
            }
            MacroBytecode.OP_INLINE_INT -> {
                bytecode[i++].toLong()
            }
            MacroBytecode.OP_INLINE_LONG -> {
                val msb = bytecode[i++].toLong()
                val lsb = bytecode[i++].toLong()
                (msb shl 32) or lsb
            }
            else -> TODO("Op: $op")
        }
        instruction = INSTRUCTION_NOT_SET
        return long
    }

    override fun doubleValue(): Double {
        val op = instruction.instructionToOp()
        val double = when (op) {
            MacroBytecode.OP_INLINE_DOUBLE -> {
                val lsb = bytecode[i++].toLong() and 0xFFFFFFFF
                val msb = bytecode[i++].toLong() and 0xFFFFFFFF
                Double.fromBits((msb shl 32) or lsb)
                // val double = bytecode.unsafeGetDoubleAt(i)
//                val msb = bytecode[i++]
//                val lsb = bytecode[i++]
//                print(intArrayOf(msb, lsb).contentToString())
//                println(" -> " + intArrayOf(msb, lsb).unsafeGetDoubleAt(0))
            }
            else -> TODO("Op: $op")
        }
        instruction = INSTRUCTION_NOT_SET
        return double
    }

    override fun decimalValue(): Decimal {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_DECIMAL -> {
                val constantPoolIndex = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as Decimal
            }
            else -> TODO("Op: $op")
        }
    }

    override fun stringValue(): String {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_STRING -> {
                val constantPoolIndex = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as String
            }
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
    }

    override fun symbolValue(): String? {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_SYMBOL -> {
                val constantPoolIndex = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
//                val text = bytecode.unsafeGetReferenceAt<String>(i)
//                if (text != null) {
//                    i += 2
//                    return text
//                }
                return constantPool[constantPoolIndex] as String
            }
            MacroBytecode.OP_UNKNOWN_SYMBOL -> {
                instruction = INSTRUCTION_NOT_SET
                return null
            }
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
    }

    override fun symbolValueSid(): Int {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_SYMBOL -> return -1
            MacroBytecode.OP_UNKNOWN_SYMBOL -> {
                instruction = INSTRUCTION_NOT_SET
                return 0
            }
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
    }

    override fun lookupSid(sid: Int): String? = arguments.getSymbol(sid)

    override fun timestampValue(): Timestamp {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_TIMESTAMP -> {
                val constantPoolIndex = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                try {
                    return constantPool[constantPoolIndex] as Timestamp
                } catch (t: Throwable) {
                    println("Constant pool: ${constantPool.contentToString()}")
                    println("CP Index: $constantPoolIndex")
                    throw t
                }
            }
            else -> TODO("Op: $op")
        }
    }


    override fun clobValue(): ByteBuffer {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_CLOB -> {
                val constantPoolIndex = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as ByteBuffer
            }
            else -> TODO("Op: $op")
        }
    }

    override fun blobValue(): ByteBuffer {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_BLOB -> {
                val constantPoolIndex = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as ByteBuffer
            }
            else -> TODO("Op: $op")
        }
    }

    override fun listValue(): ListReader {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_LIST_START -> {
                val length = (instruction and 0xFFFFFF)
                val start = i
                i += length
                instruction = INSTRUCTION_NOT_SET
                return pool.getSequence(arguments, bytecode, start, constantPool)
            }
            MacroBytecode.OP_REF_LIST -> {
                val length = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                val start = bytecode[i++]
                return arguments.getList(start, length)
            }
            else -> TODO("Op: $op")
        }
    }

    override fun sexpValue(): SexpReader {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_SEXP_START -> {
                val length = (instruction and 0xFFFFFF)
                val start = i
                i += length
                instruction = INSTRUCTION_NOT_SET
                return pool.getSequence(arguments, bytecode, start, constantPool)
            }
            MacroBytecode.OP_REF_SEXP -> {
                val length = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                val start = bytecode[i++]
                return arguments.getSexp(start, length)
            }
            else -> TODO("Op: $op")
        }
    }

    override fun structValue(): StructReader {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_STRUCT_START -> {
                val length = (instruction and 0xFFFFFF)
                val start = i
                i += length
                instruction = INSTRUCTION_NOT_SET
                return pool.getStruct(arguments, bytecode, constantPool, start)
            }
            MacroBytecode.OP_REF_FLEXSYM_STRUCT -> {
                val length = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                val start = bytecode[i++]
                return arguments.getStruct(start, length, flexsymMode = true)
            }
            MacroBytecode.OP_REF_SID_STRUCT -> {
                val length = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                val start = bytecode[i++]
                return arguments.getStruct(start, length, flexsymMode = false)
            }
            else -> TODO("Op: $op")
        }
    }

    override fun getIonVersion(): Short = 0x0101

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")
    override fun seekTo(position: Int) = TODO("This method only applies to readers that support IVMs.")
    override fun position(): Int  = TODO("This method only applies to readers that support IVMs.")
}
