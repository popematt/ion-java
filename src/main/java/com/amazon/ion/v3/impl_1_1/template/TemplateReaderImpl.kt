package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.OPERATION_SHIFT_AMOUNT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.TOKEN_TYPE_SHIFT_AMOUNT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.instructionToOp
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

open class TemplateReaderImpl internal constructor(
    val pool: ResourcePool,
    // Available modules? No. That's already resolved in the compiler.
    // Macro table? Yes. That's already resolved in the compiler, but we might encounter
    // an e-expression that needs to lazily read in one of the arguments.
): ValueReader, SexpReader, ListReader, SequenceReader, TemplateReader {

    @JvmField
    var isStruct: Boolean = false

    @JvmField
    var bytecode: IntArray = IntArray(0)
     @JvmField
    var i = 0
//        set(value) {
//            if (value == 19) {
//                val e = Exception()
//                e.fillInStackTrace()
//                val x = e.stackTraceToString().lines()
//                println(x)
//            }
//            field = value
//        }
    @JvmField
    var constantPool: Array<Any?> = arrayOf()

    private var argStackSize: Byte = 0
    private var argumentValueIndicesStack = IntArray(INITIAL_STACK_SIZE)

    // TODO: See if we can get rid of these stacks.
    private var stackHeight: Byte = 0
    private var bytecodeStack: Array<IntArray?> = arrayOfNulls(INITIAL_STACK_SIZE)
    private var iStack = IntArray(INITIAL_STACK_SIZE) { -1 }
    private var constantPoolStack: Array<Array<Any?>?> = arrayOfNulls(INITIAL_STACK_SIZE)

    @JvmField
    var arguments: ArgumentBytecode = ArgumentBytecode.NO_ARGS

    @JvmField
    var instruction: Int = INSTRUCTION_NOT_SET

    @JvmField
    internal var source: ByteBuffer = ByteBuffer.allocate(0)
    @JvmField
    internal var symbolTable: Array<String?> = arrayOf(null)
    @JvmField
    internal var macroTable: Array<MacroV2> = arrayOf()

    companion object {
        const val INSTRUCTION_NOT_SET = MacroBytecode.UNSET shl 24

        const val INSTRUCTION_END = MacroBytecode.EOF shl 24

        private const val INITIAL_STACK_SIZE = 32
    }

    fun init(
        source: ByteBuffer,
        bytecode: IntArray,
        constantPool: Array<Any?>,
        arguments: ArgumentBytecode,
     ) {
        this.source = source
        this.bytecode = bytecode
        this.constantPool = constantPool
        this.arguments = arguments
        instruction = INSTRUCTION_NOT_SET
        i = 0

//        println("Starting at $i")
//        MacroBytecode.debugString(bytecode)
//        println(bytecode.bytecodeToString())
//        arguments.iterator().forEach { println(it.bytecodeToString()) }
    }

    fun initTables(symbolTable: Array<String?>, macroTable: Array<MacroV2>) {
        this.symbolTable = symbolTable
        this.macroTable = macroTable
    }

    internal fun rewind() {
        instruction = INSTRUCTION_NOT_SET
        i = 0
    }

    override fun nextToken(): Int {
//        println(javaClass.simpleName + "@" + Integer.toHexString(System.identityHashCode(this)) + " -> ")
        if (instruction == INSTRUCTION_END) {
            return TokenTypeConst.END
        }
        var instruction = INSTRUCTION_NOT_SET
        var i = this.i
        while (instruction == INSTRUCTION_NOT_SET) {
//            print("Reading @ $i...")
            instruction = bytecode[i++]
//            println(" ${MacroBytecode(instruction)}")
            val op = instruction ushr 24
            when (op) {
                MacroBytecode.OP_START_ARGUMENT_VALUE -> {
                    // Put args positions on stack for template macro invocations to use in evaluation
                    argumentValueIndicesStack[(argStackSize++).toInt()] = i - 1
                    i += instruction and MacroBytecode.DATA_MASK
                    instruction = INSTRUCTION_NOT_SET
                    continue
                }

                MacroBytecode.OP_PARAMETER -> {
//                    println("Stepping in to parameter value: ${MacroBytecode(instruction)}")
                    if (stackHeight.toInt() == iStack.size) {
                        TODO("Grow the stacks")
                    }
                    val s = (stackHeight++).toInt()
                    bytecodeStack[s] = bytecode
                    iStack[s] = i
                    constantPoolStack[s] = constantPool
                    bytecode = arguments.getArgument(instruction and MacroBytecode.DATA_MASK)
                    i = 0
                    constantPool = arguments.constantPool()
                    instruction = INSTRUCTION_NOT_SET
                }

                MacroBytecode.OP_END_ARGUMENT_VALUE,
                MacroBytecode.OP_RETURN -> {
                    if (stackHeight.toInt() == 0) {
                        instruction = MacroBytecode.EOF.opToInstruction()
                    } else {
//                        println("Stepping out of parameter value: ${MacroBytecode(instruction)}")
                        val s = (--stackHeight).toInt()
                        bytecode = bytecodeStack[s]!!
                        constantPool = constantPoolStack[s]!!
                        i = iStack[s]
                        instruction = INSTRUCTION_NOT_SET
                    }
                }

                MacroBytecode.OP_INVOKE_SYS_MACRO -> when (instruction and 0xFF) {
                    SystemMacro.DEFAULT_ADDRESS -> {
                        val secondArgStartIndex = argumentValueIndicesStack[(--argStackSize).toInt()]
                        val firstArgStartIndex = argumentValueIndicesStack[(--argStackSize).toInt()]
                        val currentI = i
                        if (stackHeight.toInt() == iStack.size) {
                            TODO("Grow the stacks")
                        }
                        val s = (stackHeight++).toInt()
                        bytecodeStack[s] = bytecode
                        iStack[s] = currentI
                        constantPoolStack[s] = constantPool

                        if (firstArgStartIndex == secondArgStartIndex - 2) {
                            // Empty expression group
                            i = secondArgStartIndex + 1
                            instruction = INSTRUCTION_NOT_SET
                            continue
                        }

                        val firstArg = pool.getSequence(arguments, bytecode, firstArgStartIndex + 1, constantPool, symbolTable, macroTable)

                        i = if (firstArg.nextToken() != TokenTypeConst.END) {
                            firstArgStartIndex + 1
                        } else {
                            secondArgStartIndex + 1
                        }
                        firstArg.close()
                        instruction = INSTRUCTION_NOT_SET
                    }
                    else -> TODO("System Macro Instruction not supported ${MacroBytecode(instruction)}")
                }
            }
        }
        this.instruction = instruction
        this.i = i
        return (instruction ushr (OPERATION_SHIFT_AMOUNT + TOKEN_TYPE_SHIFT_AMOUNT))
            .also {
//                println("Returning ${TokenTypeConst(it)} (${MacroBytecode(instruction)}), i=${this.i}")
            if (it == TokenTypeConst.UNSET) {
                MacroBytecode.debugString(bytecode)
                (0 until stackHeight).reversed().forEach {
                    println("---- Stack Entry $it ----")
                    MacroBytecode.debugString(bytecodeStack[i]!!)
                }

                throw Exception("TokenTypeConst == UNSET; ${MacroBytecode(instruction)} instruction: ${instruction.toString(16)}; i=$i; \nMaybe the instruction should have been ${MacroBytecode(instruction.opToInstruction())}")
            }
        }
    }

    override fun close() {
        pool.returnSequence(this)
    }

    // TODO: Make sure that this also returns END when at the end of the input.
    // TODO: See if we can eliminate this from the API.
    override fun currentToken(): Int {
        return instruction ushr (OPERATION_SHIFT_AMOUNT + TOKEN_TYPE_SHIFT_AMOUNT)
    }

    /**
     * This is only used in [StreamReaderAsIonReader] to make sure to skip a value when `next()` is called because it
     * has a different contract than [ValueReader].
     */
    override fun isTokenSet(): Boolean = currentToken() != TokenTypeConst.UNSET

    override fun ionType(): IonType? {
        val instruction = instruction
        return when (instruction ushr (TOKEN_TYPE_SHIFT_AMOUNT + OPERATION_SHIFT_AMOUNT)) {
            TokenTypeConst.NULL -> {
                if (instruction and 0x01000000 == 0) {
                    IonType.NULL
                } else {
                    IonType.entries[instruction and 0xFF]
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
        return when (instruction.instructionToOp()) {
            MacroBytecode.OP_SMALL_INT -> 2
            MacroBytecode.OP_INLINE_INT -> 4
            MacroBytecode.OP_INLINE_LONG -> 8
            MacroBytecode.OP_CP_BIG_INT -> 9
            MacroBytecode.OP_CP_BLOB,
            MacroBytecode.OP_CP_CLOB -> {
                val cpindex = instruction and MacroBytecode.DATA_MASK
                (constantPool[cpindex] as ByteBuffer).remaining()
            }
            else -> incorrectUsage(object{}.javaClass)
        }
    }

    private fun incorrectUsage(any: Any): Nothing {
        throw IncorrectUsageException("Cannot read a ${any.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
    }

    override fun skip() {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_LIST_START,
            MacroBytecode.OP_SEXP_START,
            MacroBytecode.OP_STRUCT_START -> {
                val length = instruction and MacroBytecode.DATA_MASK
                i += length
            }
            MacroBytecode.EOF,
            MacroBytecode.OP_CONTAINER_END -> {
                throw UnsupportedOperationException("Cannot skip past end.")
            }
            else -> {
                i += MacroBytecode.Operations[op]!!.numberOfOperands
            }
        }
        instruction = INSTRUCTION_NOT_SET
    }

    override fun macroInvocation(): MacroInvocation {
        val instruction = instruction
         val cpIndex = (instruction and MacroBytecode.DATA_MASK)
        val op = instruction.instructionToOp()
        this.instruction = INSTRUCTION_NOT_SET
        when (op) {
            // This should be unreachable if we have flattened all the macro invocations.
            MacroBytecode.OP_INVOKE_MACRO -> {
                val macro = constantPool[cpIndex] as MacroV2
                // TODO: Ensure that there are no elided args in the bytecode.
                val sig = macro.signature
                /**
                 * TODO:
                 *
                 * ### Option 1:
                 *
                 * Append individual args to the environment stack, and record the offset for the current stack frame.
                 * When appending args to the stack, adjust any variable indices. Now, all variables in the args
                 * point to the correct location. Any variables in the callee must use the offset to calculate the
                 * correct argument position in the environment stack.
                 *
                 * Pros: No array copying, Decent-ish data locality
                 *
                 * Cons: Requires pre-parsing the arguments (each time we use them), lots of bookkeeping(?)
                 *
                 * ```
                 * val environment = Array<IntArray>()
                 * ```
                 *
                 * ### Option 2:
                 *
                 * When building the arguments, read them and resolve any variables in them by copying the
                 * resolved values into the arguments.
                 *
                 * Environment is always flat because we only need one level of arguments (all prior levels are inlined already).
                 *
                 * Pros: Very good data locality
                 *
                 * Cons: A lot of array copying, requires re-reading the argument bytecode (each time we use them)
                 *
                 * ### Option 3:
                 *
                 * Environment object that has the current args and a reference to the parent environment
                 *
                 * ```
                 * class Environment(
                 *     val arguments: Array<IntArray>,
                 *     val argumentConstantPool: Array<Any>,
                 *     val parent: Environment? = null,
                 * )
                 * ```
                 *
                 * Pros: No preprocessing required
                 *
                 * Cons: Terrible data locality and indirection
                 *
                 * ### Option 4:
                 *
                 * All args are in a single array. The environment is a stack of these arrays. When encountering
                 * a variable, it is possible to skip-scan to the correct argument in the next entry down the stack.
                 *
                 * ```
                 * val environment = Array<IntArray>
                 * ```
                 *
                 *
                 * Pros: decent data locality, no pre-processing of args required.
                 *
                 * Cons: some indirection
                 *
                 * TODO: Also consider:
                 *
                 * How are the constant pools getting dealt with? (Note that this problem goes away if
                 * we can use `Unsafe` because we can put an object reference inline)
                 * Any time we need to copy data, we also need to copy the constants somehow.
                 * If we append the constant pool to an existing constant pool, then we need to go and adjust constant
                 * pool indices accordingly, which could be tedious and/or expensive, although no worse than updating
                 * variable indices. (Or, we need to track a constant pool offset and adjust accordingly.)
                 * If we don't append the constants to an existing constant pool, then we need to have a stack of
                 * constant pools that we can reference.
                 *
                 * Unfortunately, `Unsafe.getObject()` is incredibly slow. How else can we maintain a single constant pool?
                 * We could have a HashMap<Long, Any?> that holds the value, where the `Long` is the address, but is that
                 * going to be too expensive? The nice thing is that it can be a shared instance since we don't need to
                 * worry about creating new copies for every macro invocation. It would need to have some sort of lifetime
                 * in order to avoid memory leaks, but the lifetime could just be the life of the reader.
                 *
                 * Actually, we could use a bespoke map, based on IdentityHashMap that uses the memory address as the key.
                 * That would mean we don't have to box the keys, and we have a stable identifier in the bytecode that
                 * doesn't ever have to be rewritten if the data is moved to a new array.
                 *
                 * Alternately, we could use an array of objects, and then use `Unsafe` to read integers from it. That
                 * might be faster, although slightly more complicated because we have to ensure that our opcodes work
                 * with the alignment of the object array.
                 *
                 */
                val args = Array(sig.size) { ArgumentBytecode.EMPTY_ARG }

                var j = sig.size
                while (j-- > 0) {
                    val argJIndex = argumentValueIndicesStack[(--argStackSize).toInt()]
                    val argJInstruction = bytecode[argJIndex]
                    val argJLength = argJInstruction and MacroBytecode.DATA_MASK
                    val argJ = IntArray(argJLength)
                    System.arraycopy(bytecode, argJIndex + 1, argJ, 0, argJLength)
                    argJ[argJLength - 1] = MacroBytecode.END_OF_ARGUMENT_SUBSTITUTION.opToInstruction()
                    args[j] = argJ
                }

                TODO("OP_INVOKE_MACRO")
            }
            MacroBytecode.OP_CP_MACRO_INVOCATION -> {
                val invocation = (constantPool[cpIndex] as MacroInvocation)
                return invocation
            }
            else -> throw IllegalStateException("Not positioned on macro: ${MacroBytecode(instruction)}")
        }
    }

    override fun expressionGroup(): SequenceReader {
        return pool.getSequence(arguments, bytecode, i, constantPool, symbolTable, macroTable)
    }

    override fun annotations(): AnnotationIterator {
        val annotations = mutableListOf<String?>()
        val sids = IntList()
        val bytecode = bytecode
        var instruction = instruction
        var i = i
        while(true) {
            when (instruction.instructionToOp()) {
                MacroBytecode.OP_CP_ONE_ANNOTATION -> {
                    val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                    annotations += constantPool[constantPoolIndex] as String?
                    sids.add(-1)
                }
                MacroBytecode.OP_ANN_SYSTEM_SID -> {
                    val sid = instruction and MacroBytecode.DATA_MASK
                    if (sid == 0) {
                        annotations += null
                        sids.add(0)
                    } else {
                        annotations += SystemSymbols_1_1[sid]!!.text
                        sids.add(-1)
                    }
                }
                MacroBytecode.OP_ONE_ANNOTATION_SID -> {
                    val sid = (instruction and MacroBytecode.DATA_MASK)
                    annotations += symbolTable[sid]
                    sids.add(sid)
                }
                MacroBytecode.OP_REF_ONE_FLEXSYM_ANNOTATION -> {
                    val length = instruction and MacroBytecode.DATA_MASK
                    val position = bytecode[i++]
                    annotations += readTextRef(position, length)
                }
                else -> {
                    if (annotations.isNotEmpty()) break
                    throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
                }
            }
            instruction = bytecode[i++]
        }

        annotations.forEachIndexed { index, s ->
            if (sids[index] == -1 && s == null) TODO()
        }


        this.i = i - 1
        this.instruction = INSTRUCTION_NOT_SET
        return pool.getAnnotations(annotations.toTypedArray())
    }

    override fun nullValue(): IonType {
        val instruction = this.instruction
        this.instruction = INSTRUCTION_NOT_SET
        val op = instruction.instructionToOp()
        return when (op) {
            MacroBytecode.OP_NULL_NULL -> IonType.NULL
            MacroBytecode.OP_NULL_TYPED -> IonType.entries[(instruction and 0xFF)]
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun booleanValue(): Boolean {
        val bool = (instruction and 1) == 1
        instruction = INSTRUCTION_NOT_SET
        return bool
    }

    override fun longValue(): Long {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        val long = when (op) {
            MacroBytecode.OP_SMALL_INT -> {
                (instruction and MacroBytecode.DATA_MASK).toShort().toLong()
            }
            MacroBytecode.OP_INLINE_INT -> {
                bytecode[i++].toLong()
            }
            MacroBytecode.OP_INLINE_LONG -> {
                val msb = bytecode[i++].toLong()
                val lsb = bytecode[i++].toLong()
                (msb shl 32) or lsb
            }
            MacroBytecode.OP_CP_BIG_INT -> {
                val cpIndex = (instruction and MacroBytecode.DATA_MASK)
                val bi = constantPool[cpIndex] as BigInteger
                bi.longValueExact()
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
        this.instruction = INSTRUCTION_NOT_SET
        return long
    }

    override fun doubleValue(): Double {
        val op = instruction.instructionToOp()
        val bytecode = bytecode
        val double = when (op) {
            MacroBytecode.OP_INLINE_DOUBLE -> {
                val lsb = bytecode[i++].toLong() and 0xFFFFFFFF
                val msb = bytecode[i++].toLong() and 0xFFFFFFFF
                Double.fromBits((msb shl 32) or lsb)
            }
            MacroBytecode.OP_INLINE_FLOAT -> {
                Float.fromBits(bytecode[i++]).toDouble()
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
        instruction = INSTRUCTION_NOT_SET
        return double
    }

    override fun decimalValue(): Decimal {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_DECIMAL -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                instruction = INSTRUCTION_NOT_SET
                return Decimal.valueOf(constantPool[constantPoolIndex] as BigDecimal)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun stringValue(): String {
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_STRING -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as String
            }
            MacroBytecode.OP_REF_STRING -> {
                val length = instruction and MacroBytecode.DATA_MASK
                val position = bytecode[i++]
                return readTextRef(position, length)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun symbolValue(): String? {
        val instruction = this.instruction
//        println("Reading symbol text from ${MacroBytecode(instruction)}; i=$i")
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_CP_SYMBOL_TEXT -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                this.instruction = INSTRUCTION_NOT_SET
                if (constantPoolIndex >= constantPool.size) {
                    println("Hello")
                }
                return constantPool[constantPoolIndex] as String?
            }
            MacroBytecode.OP_SYMBOL_SID -> {
                this.instruction = INSTRUCTION_NOT_SET
                return lookupSid(instruction and MacroBytecode.DATA_MASK)
            }
            MacroBytecode.OP_REF_SYMBOL_TEXT -> {
                val length = instruction and MacroBytecode.DATA_MASK
                this.instruction = INSTRUCTION_NOT_SET
                val position = bytecode[i++]
                return readTextRef(position, length)
                // .also { println("... \"$it\"\n; i=$i") }
            }
            MacroBytecode.OP_SYSTEM_SYMBOL_SID -> {
                this.instruction = INSTRUCTION_NOT_SET
                return SystemSymbols_1_1[instruction and MacroBytecode.DATA_MASK]!!.text
            }
            MacroBytecode.OP_SYMBOL_CHAR -> {
                this.instruction = INSTRUCTION_NOT_SET
                val char = (instruction and MacroBytecode.DATA_MASK).toChar()
                return "$char"
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun symbolValueSid(): Int {
        val instruction = this.instruction
//        println("Reading symbol sid from ${MacroBytecode(instruction)}")
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_SYSTEM_SYMBOL_SID -> {
                val systemSid = (instruction and MacroBytecode.DATA_MASK)
                if (systemSid == 0) {
                    this.instruction = INSTRUCTION_NOT_SET
                    return 0
                } else {
                    return -1
                }
            }
            MacroBytecode.OP_SYMBOL_CHAR,
            MacroBytecode.OP_REF_SYMBOL_TEXT,
            MacroBytecode.OP_CP_SYMBOL_TEXT -> return -1
            MacroBytecode.OP_SYMBOL_SID -> {
                this.instruction = INSTRUCTION_NOT_SET
                return instruction and MacroBytecode.DATA_MASK
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun lookupSid(sid: Int): String? = symbolTable[sid]

    override fun timestampValue(): Timestamp {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_TIMESTAMP -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                this.instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as Timestamp
            }
            MacroBytecode.OP_REF_TIMESTAMP_SHORT -> {
                this.instruction = INSTRUCTION_NOT_SET
                val opcode = instruction and MacroBytecode.DATA_MASK
                val position = bytecode[i++]
                return TimestampHelper.readTimestampAt(opcode, source, position)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }


    override fun clobValue(): ByteBuffer {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_CLOB -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as ByteBuffer
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun blobValue(): ByteBuffer {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_CP_BLOB -> {
                val constantPoolIndex = (instruction and MacroBytecode.DATA_MASK)
                instruction = INSTRUCTION_NOT_SET
                return constantPool[constantPoolIndex] as ByteBuffer
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun listValue(): ListReader {
        val instruction = this.instruction
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_LIST_START -> {
                val length = (instruction and MacroBytecode.DATA_MASK)
                val start = i
                i += length
                this.instruction = INSTRUCTION_NOT_SET
                return pool.getSequence(arguments, bytecode, start, constantPool, symbolTable, macroTable)
            }
            MacroBytecode.OP_REF_LIST -> {
                val length = (instruction and MacroBytecode.DATA_MASK)
                this.instruction = INSTRUCTION_NOT_SET
                val start = bytecode[i++]
                return pool.getList(start, length, symbolTable, macroTable)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun sexpValue(): SexpReader {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_SEXP_START -> {
                val length = (instruction and MacroBytecode.DATA_MASK)
                val start = i
                i += length
                instruction = INSTRUCTION_NOT_SET
                return pool.getSequence(arguments, bytecode, start, constantPool, symbolTable, macroTable)
            }
            MacroBytecode.OP_REF_SEXP -> {
                val length = (instruction and MacroBytecode.DATA_MASK)
                instruction = INSTRUCTION_NOT_SET
                val start = bytecode[i++]
                return pool.getPrefixedSexp(start, length, symbolTable, macroTable)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun structValue(): StructReader {
        val op = instruction.instructionToOp()
        when (op) {
            MacroBytecode.OP_STRUCT_START -> {
                val length = (instruction and MacroBytecode.DATA_MASK)
                val start = i
                i += length
                instruction = INSTRUCTION_NOT_SET
                return pool.getStruct(arguments, bytecode, start, constantPool, symbolTable, macroTable)
            }
            MacroBytecode.OP_REF_SID_STRUCT -> {
                val length = (instruction and MacroBytecode.DATA_MASK)
                instruction = INSTRUCTION_NOT_SET
                val start = bytecode[i++]
                return pool.getStruct(start, length, symbolTable, macroTable)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    protected fun _fieldName(): String? {
        val instruction = instruction
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_FIELD_NAME_SID -> {
                this.instruction = INSTRUCTION_NOT_SET
                val sid = instruction and MacroBytecode.DATA_MASK
                return symbolTable[sid]
            }
            MacroBytecode.OP_CP_FIELD_NAME -> {
                val constantPoolIndex = instruction and MacroBytecode.DATA_MASK
                this.instruction = INSTRUCTION_NOT_SET
                val fieldName = constantPool[constantPoolIndex] as String?
                return fieldName
            }
            MacroBytecode.OP_FIELD_NAME_SYSTEM_SID -> {
                this.instruction = INSTRUCTION_NOT_SET
                val sid = instruction and MacroBytecode.DATA_MASK
                return SystemSymbols_1_1[sid]?.text
            }
            MacroBytecode.OP_REF_FIELD_NAME_TEXT -> {
                val length = instruction and MacroBytecode.DATA_MASK
                val position = bytecode[i++]
                this.instruction = INSTRUCTION_NOT_SET
                return readTextRef(position, length)
            }
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    protected fun _fieldNameSid(): Int {
        val instruction = instruction
        when (instruction.instructionToOp()) {
            MacroBytecode.OP_FIELD_NAME_SID -> {
                this.instruction = INSTRUCTION_NOT_SET
                return instruction and MacroBytecode.DATA_MASK
            }
            MacroBytecode.OP_FIELD_NAME_SYSTEM_SID,
            MacroBytecode.OP_REF_FIELD_NAME_TEXT,
            MacroBytecode.OP_CP_FIELD_NAME -> return -1
            else -> throw IncorrectUsageException("Cannot read a ${object {}.javaClass.enclosingMethod.name} from instruction ${MacroBytecode(instruction)}")
        }
    }

    override fun getIonVersion(): Short = 0x0101

    override fun ivm(): Short = throw IonException("IVM is not supported by this reader")
    override fun seekTo(position: Int) = throw UnsupportedOperationException("This method only applies to readers that support IVMs.")
    override fun position(): Int  = throw UnsupportedOperationException("This method only applies to readers that support IVMs.")

    private fun readTextRef(position: Int, length: Int): String {
        val scratchBuffer = pool.scratchBuffer
        scratchBuffer.limit(position + length)
        scratchBuffer.position(position)
        return StandardCharsets.UTF_8.decode(scratchBuffer).toString()
    }
}
