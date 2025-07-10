package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.*
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.instructionToOp
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import javax.crypto.Mac

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
    var constantPool: Array<Any?> = arrayOf()
    @JvmField
    var arguments: ArgumentReader = NoneReader
    @JvmField
    var isArgumentOwner: Boolean = false

    @JvmField
    var i = 0

    @JvmField
    var instruction: Int = INSTRUCTION_NOT_SET


    companion object {
        const val INSTRUCTION_NOT_SET = MacroBytecode.UNSET shl 24
    }

    fun init(
        bytecode: IntArray,
        constantPool: Array<Any?>,
        arguments: ArgumentReader,
        isArgumentOwner: Boolean,
     ) {
        this.bytecode = bytecode
        this.constantPool = constantPool
        this.arguments = arguments
        this.isArgumentOwner = isArgumentOwner
        instruction = INSTRUCTION_NOT_SET
        i = 0
        reinitState()
    }

    protected open fun reinitState() {}

    override fun nextToken(): Int {
        instruction = bytecode[i++]
        when (instruction) {
            MacroBytecode.OP_START_ARGUMENT_VALUE -> TODO()
            MacroBytecode.OP_ARGUMENT_REF_TYPE -> TODO()
        }
        return currentToken()
    }

    final override fun close() {
        if (isArgumentOwner) {
            arguments.close()
        }
        returnToPool()
    }

    protected abstract fun returnToPool()

    // TODO: Make sure that this also returns END when at the end of the input.
    override fun currentToken(): Int {
        val op = instruction ushr 24
        when (op) {
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
            MacroBytecode.OP_EMPTY_SYMBOL -> return TokenTypeConst.SYMBOL
            MacroBytecode.OP_CP_BLOB -> return TokenTypeConst.BLOB
            MacroBytecode.OP_CP_CLOB -> return TokenTypeConst.CLOB
            MacroBytecode.OP_LIST_START -> return TokenTypeConst.LIST
            MacroBytecode.OP_SEXP_START -> return TokenTypeConst.SEXP
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
            // TODO: Arguments?
            else -> TODO("Op: ${op.toString(16)}")
        }
    }

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
            MacroBytecode.OP_EMPTY_SYMBOL -> return IonType.SYMBOL
            MacroBytecode.OP_CP_BLOB -> return IonType.BLOB
            MacroBytecode.OP_CP_CLOB -> return IonType.CLOB
            MacroBytecode.OP_LIST_START -> return IonType.LIST
            MacroBytecode.OP_SEXP_START -> return IonType.SEXP
            MacroBytecode.OP_STRUCT_START -> return IonType.STRUCT

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

    override fun expressionGroup(): SequenceReader {
        TODO("Should we still expose this?")
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
                val msb = bytecode[i++].toLong() and 0xFFFFFFFF
                val lsb = bytecode[i++].toLong() and 0xFFFFFFFF
                Double.fromBits((msb shl 32) or lsb)
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
                return constantPool[constantPoolIndex] as String?
            }
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
    }

    override fun symbolValueSid(): Int {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_SYMBOL -> return -1
            else -> TODO("Op: ${instruction.instructionToOp()}")
        }
    }

    override fun lookupSid(sid: Int): String? = arguments.lookupSid(sid)

    override fun timestampValue(): Timestamp {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_TIMESTAMP -> {
                val constantPoolIndex = (instruction and 0xFFFFFF)
                instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as Timestamp
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
            else -> TODO("Op: $op")
        }
    }

    override fun getIonVersion(): Short = 0x0101

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")
    override fun seekTo(position: Int) = TODO("This method only applies to readers that support IVMs.")
    override fun position(): Int  = TODO("This method only applies to readers that support IVMs.")
}
