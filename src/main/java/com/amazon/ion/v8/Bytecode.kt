package com.amazon.ion.v8

import com.amazon.ion.v8.Bytecode.DataFormatters.AS_HEX_BYTE
import com.amazon.ion.v8.Bytecode.DataFormatters.AS_HEX_INT
import com.amazon.ion.v8.Bytecode.DataFormatters.AS_INT
import com.amazon.ion.v8.Bytecode.DataFormatters.BYTECODE_LENGTH
import com.amazon.ion.v8.Bytecode.DataFormatters.CHAR
import com.amazon.ion.v8.Bytecode.DataFormatters.CP_INDEX
import com.amazon.ion.v8.Bytecode.DataFormatters.NO_DATA
import com.amazon.ion.v8.Bytecode.DataFormatters.REF_LENGTH
import com.amazon.ion.v8.Bytecode.DataFormatters.SID
import com.amazon.ion.v8.Bytecode.DataFormatters.SRC_INDEX

/**
 * ```text
 * Instructions are formatted here as  OPERATION(data)[operands]
 *
 * E.g. Boolean true could be this:
 *
 *     Operation | Data
 *      00000001   00000000 00000000 00000001
 *
 *
 *
 *
 * TODO: Currently, the 0x00 operation is reserved for "unset", but consider using it for Ion Binary opcodes instead.
 *       That might allow us to unify the macro reader and the raw binary reader. Will it improve performance? I'm not sure.
 */
object Bytecode {

    // NOTE about ref opcodes
    // It seems that data locality is one of the most important concerns for performance.
    // So, for fixed-sized scalars, it will probably be cheaper to eagerly materialize them to be able to
    // put them inline.


    // TODO: Standardize the values of the instruction suffixes
    //       Suffixes:
    //         * SID = ?  (symbol, fieldname, annotation)
    //         * REF = ?  (fieldname, annotation, int, decimal, timestamp, string, symbol, blob, clob, list, sexp, struct)
    //         * CP = ?   (fieldname, annotation, int, decimal, timestamp, string, symbol, blob, clob)
    //         * INLINE_VALUE = ?
    //         * NULL = 7 (all types)

    const val TOKEN_TYPE_SHIFT_AMOUNT = 3
    const val OPERATION_SHIFT_AMOUNT = 24
    const val DATA_MASK = 0xFFFFFF

    /** If DATA is 0, this should raise an error. All other values of DATA may be used for state tracking by reader implementations.  */
    const val UNSET = 0x00

    const val OP_NULL_NULL = (TokenTypeConst.NULL shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_BOOL = (TokenTypeConst.BOOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_INT = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_FLOAT = (TokenTypeConst.FLOAT shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_DECIMAL = (TokenTypeConst.DECIMAL shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_TIMESTAMP = (TokenTypeConst.TIMESTAMP shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_STRING = (TokenTypeConst.STRING shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_SYMBOL = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_BLOB = (TokenTypeConst.BLOB shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_CLOB = (TokenTypeConst.CLOB shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_LIST = (TokenTypeConst.LIST shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_SEXP = (TokenTypeConst.SEXP shl TOKEN_TYPE_SHIFT_AMOUNT) + 7
    const val OP_NULL_STRUCT = (TokenTypeConst.STRUCT shl TOKEN_TYPE_SHIFT_AMOUNT) + 7

    /** DATA is 0 for false; 1 for true */
    const val OP_BOOL = TokenTypeConst.BOOL shl TOKEN_TYPE_SHIFT_AMOUNT

    /** DATA is a 16-bit signed int */
    const val OP_SMALL_INT = TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT
    /** OPERAND is 32-bit signed int */
    const val OP_INLINE_INT = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** OPERAND is 64-bit signed int. `(op0 shl 32) or op1` */
    const val OP_INLINE_LONG = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    /** DATA is u24 index into constant pool */
    const val OP_CP_BIG_INT = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 3
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_INT = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 4

    /** OPERAND is 32-bit float. `Int.fromBits(operand)` */
    const val OP_INLINE_FLOAT = (TokenTypeConst.FLOAT shl TOKEN_TYPE_SHIFT_AMOUNT)
    /** OPERAND is 64-bit float. `Double.fromBits((op0 shl 32) or op1)` */
    const val OP_INLINE_DOUBLE = (TokenTypeConst.FLOAT shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    /** DATA is u24 index into constant pool */
    const val OP_CP_DECIMAL = (TokenTypeConst.DECIMAL shl TOKEN_TYPE_SHIFT_AMOUNT)
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_DECIMAL = (TokenTypeConst.DECIMAL shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    /** DATA is u24 index into constant pool */
    const val OP_CP_TIMESTAMP = (TokenTypeConst.TIMESTAMP shl TOKEN_TYPE_SHIFT_AMOUNT)
    /** DATA is opcode, length is implicit; OPERAND is start_index */
    const val OP_REF_TIMESTAMP_SHORT = (TokenTypeConst.TIMESTAMP shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_TIMESTAMP_LONG = (TokenTypeConst.TIMESTAMP shl TOKEN_TYPE_SHIFT_AMOUNT) + 2

    /** DATA is u24 index into constant pool */
    const val OP_CP_STRING = (TokenTypeConst.STRING shl TOKEN_TYPE_SHIFT_AMOUNT)
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_STRING = (TokenTypeConst.STRING shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    /** DATA is u24 index into constant pool */
    const val OP_CP_SYMBOL_TEXT = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT)
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_SYMBOL_TEXT = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** DATA is SID as u24 */
    const val OP_SYMBOL_SID = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    /** DATA is 1 ascii character; TODO: or up to 3 bytes of utf8? */
    const val OP_SYMBOL_CHAR = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 3
    /** DATA is SID as u24 */
    const val OP_SYMBOL_SYSTEM_SID = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 4

    /** DATA is u24 index into constant pool */
    const val OP_CP_BLOB = (TokenTypeConst.BLOB shl TOKEN_TYPE_SHIFT_AMOUNT)
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_BLOB = (TokenTypeConst.BLOB shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    /** DATA is u24 index into constant pool */
    const val OP_CP_CLOB = (TokenTypeConst.CLOB shl TOKEN_TYPE_SHIFT_AMOUNT)
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_CLOB = (TokenTypeConst.CLOB shl TOKEN_TYPE_SHIFT_AMOUNT) + 1


    // Data model containers
    // They are both length prefixed and delimited.
    const val OP_LIST_START = TokenTypeConst.LIST shl TOKEN_TYPE_SHIFT_AMOUNT
    const val OP_SEXP_START = TokenTypeConst.SEXP shl TOKEN_TYPE_SHIFT_AMOUNT
    const val OP_STRUCT_START = TokenTypeConst.STRUCT shl TOKEN_TYPE_SHIFT_AMOUNT


    /** DATA is length; OPERAND is start_index */
    const val OP_REF_LIST = (TokenTypeConst.LIST shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_SEXP = (TokenTypeConst.SEXP shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_SID_STRUCT = (TokenTypeConst.STRUCT shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    // Metadata
    /** DATA is u24 constant pool index */
    const val OP_CP_FIELD_NAME = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_SHIFT_AMOUNT)
    /** DATA is length, OPERAND is start_index */
    const val OP_REF_FIELD_NAME_TEXT = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** DATA is the SID */
    const val OP_FIELD_NAME_SID = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_SHIFT_AMOUNT) + 2

    /** DATA is constant pool index */
    const val OP_CP_ANNOTATION = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 0
    /**
     * DATA is length; OPERAND is start_index
     * If length is 0, the OPERAND is a system symbol ID.
     */
    const val OP_REF_ANNOTATION = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** DATA is SID as u24 */
    const val OP_ANNOTATION_SID = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 2

    // Macros
    /** DATA is the length of the default value. */
    const val OP_PARAMETER = TokenTypeConst.VARIABLE_REF shl TOKEN_TYPE_SHIFT_AMOUNT

    /** DATA is tagless value's opcode. */
    const val OP_TAGLESS_PARAMETER = (TokenTypeConst.VARIABLE_REF shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    /** DATA is macro id. */
    const val OP_MACRO_SHAPED_PARAMETER = (TokenTypeConst.VARIABLE_REF shl TOKEN_TYPE_SHIFT_AMOUNT) + 2

    // Directives:
    // We should try to use the DATA for something useful, if possible, especially since these cannot be skipped.
    const val DIRECTIVE_SET_SYMBOLS = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val DIRECTIVE_ADD_SYMBOLS = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val DIRECTIVE_SET_MACROS = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    const val DIRECTIVE_ADD_MACROS = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 3
    const val DIRECTIVE_USE = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 4
    const val DIRECTIVE_MODULE = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 5
    const val DIRECTIVE_ENCODING = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 6

    const val ABSENT_ARGUMENT = (TokenTypeConst.ABSENT_ARGUMENT shl TOKEN_TYPE_SHIFT_AMOUNT)

    const val EOF = TokenTypeConst.EOF shl TOKEN_TYPE_SHIFT_AMOUNT
    const val OP_CONTAINER_END = TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT

    const val IVM = (TokenTypeConst.IVM shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val REFILL = (TokenTypeConst.REFILL shl TOKEN_TYPE_SHIFT_AMOUNT)






    @JvmStatic
    fun renderForDebugging(bytecode: IntArray): Array<String> {
        val sb = StringBuilder()
        debugString(bytecode, sb::append, useIndent = true, useNumbers = false)
        return sb.toString().trim().split("\n").toTypedArray()
    }

    @JvmStatic
    fun toDebugString(bytecode: IntArray, useIndent: Boolean = true, useNumbers: Boolean = true, lax: Boolean = false): String {
        val sb = StringBuilder()
        debugString(bytecode, sb::append, useIndent, useNumbers, lax = lax)
        return sb.toString()
    }

    @JvmStatic
    private fun line(n: Int): String {
        return "L$n".padEnd(6, ' ')
    }

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    fun debugString(bytecode: IntArray, write: (String) -> Unit = ::print, useIndent: Boolean = true, useNumbers: Boolean = true, constantPool: Array<Any?>? = null, lax: Boolean = false, symbolTable: Array<String?>? = null, start: Int = 0, end: Int = bytecode.size) {
        var indent = ""
        var i = start
        while (i < end) {
            if (useNumbers) write(line(i))
            val instruction = bytecode[i++]
            val operationInt = instruction ushr OPERATION_SHIFT_AMOUNT
            if (operationInt == 0) {
                write(indent)
                write("NOP ${instruction.toHexString()}\n")
                // continue
                return
            }

            val operation = Operations.entries.singleOrNull() { it.operation == operationInt }
//            operation ?: throw IllegalStateException("Unknown operation $operationInt at position ${i - 1}.")

            operation ?: if (!lax) {
                 throw IllegalStateException("Unknown operation $operationInt at position ${i - 1}.")
            } else {
                write(indent)
                write("UNKNOWN ")
                write(instruction.toHexString())
                write("\n")
                continue
            }
            if (useIndent) when (operationInt) {
                OP_CONTAINER_END -> indent = indent.dropLast(2)
            }
            write(indent)
            write(operation.name)
            write(operation.dataFormatter(instruction and DATA_MASK))

            if (constantPool != null && operation.dataFormatter == DataFormatters.CP_INDEX) {
                val cpIndex = instruction and DATA_MASK
                if (cpIndex >= constantPool.size) {
                    write("        ERROR: missing constant $cpIndex")
                } else {
                    write("        <${constantPool[cpIndex].toString().take(20)}>")
                }
            }

            if (symbolTable != null && operation.dataFormatter == DataFormatters.SID) {
                val sid = instruction and DATA_MASK
                if (sid == 0) {
                    write("        <$0>")

                } else if (sid >= symbolTable.size) {
                    write("        ERROR: symbol out of bounds $sid")
                } else {
                    write("        <${symbolTable[sid]?.take(20) ?: "$0"}>")
                }
            }


            repeat(operation.numberOfOperands) {
                write("\n")
                if (useNumbers) write(line(i))
                val operand = bytecode[i++]
                write("$indent  ")
                write(operation.operandFormatter(operand))
            }

            write("\n")
            if (useIndent) when (operationInt) {
                DIRECTIVE_SET_SYMBOLS,
                DIRECTIVE_ADD_SYMBOLS,
                DIRECTIVE_SET_MACROS,
                DIRECTIVE_ADD_MACROS,
                DIRECTIVE_USE,
                DIRECTIVE_MODULE,
                DIRECTIVE_ENCODING,
                OP_LIST_START,
                OP_SEXP_START,
                OP_STRUCT_START -> indent += ". "
            }

            if (operationInt == REFILL) break
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    operator fun invoke(value: Int): String {
        val op = Operations.entries.singleOrNull { it.operation == value.instructionToOp() }?.name ?: ""
        return "$op(${value.toHexString()})"
    }

    object DataFormatters {
        @OptIn(ExperimentalStdlibApi::class)
        val AS_HEX_INT = { data: Int -> " " + data.toHexString()}
        @OptIn(ExperimentalStdlibApi::class)
        val AS_HEX_BYTE = { data: Int -> " " + data.toUByte().toHexString()}
        val AS_INT = { data: Int -> " $data" }
        val REF_LENGTH = { data: Int -> " $data" }
        val BYTECODE_LENGTH = { data: Int -> " $data" }
        val SRC_INDEX = { data: Int -> " $data" }
        val CP_INDEX = { data: Int -> " $data" }
        val SID = { data: Int -> " $data" }
        val CHAR = { data: Int -> " '${data.toChar()}'" }
        val NO_DATA = { _: Int -> "" }
    }

    /**
     * Information about the various bytecode operations. This is used exclusively (I think) for being able to dump the
     * bytecode in a human-readable format for debugging and for testing.
     */
    enum class Operations(val operation: Int, val dataFormatter: (Int) -> String, val numberOfOperands: Int = 0, val operandFormatter: (Int) -> String = { "" }) {
        NULL_NULL(OP_NULL_NULL, NO_DATA),
        NULL_BOOL(OP_NULL_BOOL, NO_DATA),
        NULL_INT(OP_NULL_INT, NO_DATA),
        NULL_FLOAT(OP_NULL_FLOAT, NO_DATA),
        NULL_DECIMAL(OP_NULL_DECIMAL, NO_DATA),
        NULL_TIMESTAMP(OP_NULL_TIMESTAMP, NO_DATA),
        NULL_STRING(OP_NULL_STRING, NO_DATA),
        NULL_SYMBOL(OP_NULL_SYMBOL, NO_DATA),
        NULL_BLOB(OP_NULL_BLOB, NO_DATA),
        NULL_CLOB(OP_NULL_CLOB, NO_DATA),
        NULL_LIST(OP_NULL_LIST, NO_DATA),
        NULL_SEXP(OP_NULL_SEXP, NO_DATA),
        NULL_STRUCT(OP_NULL_STRUCT, NO_DATA),

        BOOL(OP_BOOL, dataFormatter = { " " + (it == 1).toString() }),

        INT16(OP_SMALL_INT, { " " + (it.toShort()).toString() }),
        INT32(OP_INLINE_INT, NO_DATA, 1, AS_HEX_INT),
        INT64(OP_INLINE_LONG, NO_DATA, 2, AS_HEX_INT),
        INT_CNST(OP_CP_BIG_INT, CP_INDEX),
        INT_REF(OP_REF_INT, REF_LENGTH, 1, SRC_INDEX),
        FLOAT32(OP_INLINE_FLOAT, NO_DATA, 1, AS_HEX_INT),
        FLOAT64(OP_INLINE_DOUBLE, NO_DATA, 2, AS_HEX_INT),
        DEC_CNST(OP_CP_DECIMAL, AS_INT),
        DEC_REF(OP_REF_DECIMAL, REF_LENGTH, 1, SRC_INDEX),
        TS_CNST(OP_CP_TIMESTAMP, CP_INDEX),
        TSS_REF(OP_REF_TIMESTAMP_SHORT, AS_HEX_BYTE, 1, SRC_INDEX),
        TSL_REF(OP_REF_TIMESTAMP_LONG, REF_LENGTH, 1, SRC_INDEX),
        STR_CNST(OP_CP_STRING, CP_INDEX),
        STR_REF(OP_REF_STRING, REF_LENGTH, 1, SRC_INDEX),
        SYM_SYS(OP_SYMBOL_SYSTEM_SID, SID),
        SYM_CNST(OP_CP_SYMBOL_TEXT, CP_INDEX),
        SYM_REF(OP_REF_SYMBOL_TEXT, REF_LENGTH, 1, SRC_INDEX),
        SYM_SID(OP_SYMBOL_SID, SID),
        SYM_1_CHAR(OP_SYMBOL_CHAR, CHAR),
        BLOB_CNST(OP_CP_BLOB, CP_INDEX),
        BLOB_REF(OP_REF_BLOB, REF_LENGTH, 1, SRC_INDEX),
        CLOB_CNST(OP_CP_CLOB, CP_INDEX),
        CLOB_REF(OP_REF_CLOB, REF_LENGTH, 1, SRC_INDEX),
        LIST_START(OP_LIST_START, BYTECODE_LENGTH),
        SEXP_START(OP_SEXP_START, BYTECODE_LENGTH),
        STRUCT_START(OP_STRUCT_START, BYTECODE_LENGTH),
        LIST_REF(OP_REF_LIST, REF_LENGTH, 1, SRC_INDEX),
        SEXP_REF(OP_REF_SEXP, REF_LENGTH, 1, SRC_INDEX),
        STRUCT_REF(OP_REF_SID_STRUCT, REF_LENGTH, 1, SRC_INDEX),
        FNAME_SID(OP_FIELD_NAME_SID, SID),
        FNAME_CNST(OP_CP_FIELD_NAME, CP_INDEX),
        FNAME_REF(OP_REF_FIELD_NAME_TEXT, REF_LENGTH, 1, SRC_INDEX),
        ANN_CNST(OP_CP_ANNOTATION, CP_INDEX),
        ANN_SID(OP_ANNOTATION_SID, SID),
        ANN_REF(OP_REF_ANNOTATION, REF_LENGTH, 1, SRC_INDEX),
        PARAM(OP_PARAMETER, AS_INT),
        EOF(Bytecode.EOF, NO_DATA),
        CONTAINER_END(OP_CONTAINER_END, NO_DATA),

        REFILL(Bytecode.REFILL, NO_DATA),
        IVM(Bytecode.IVM, NO_DATA),

        SET_SYMBOLS(DIRECTIVE_SET_SYMBOLS, NO_DATA),
        ADD_SYMBOLS(DIRECTIVE_ADD_SYMBOLS, NO_DATA),
        SET_MACROS(DIRECTIVE_SET_MACROS, NO_DATA),
        ADD_MACROS(DIRECTIVE_ADD_MACROS, NO_DATA),
        USE(DIRECTIVE_USE, NO_DATA),
        MODULE(DIRECTIVE_MODULE, NO_DATA),
        ENCODING(DIRECTIVE_ENCODING, NO_DATA),
        ;

        companion object {

            @JvmField
            internal val OPERATIONS = Array<Operations?>(256) { i ->
                Operations.entries.singleOrNull { it.operation == i }
            }

            @JvmField
            internal val N_OPERANDS = ByteArray(256) { i ->
                val op = Operations.entries.singleOrNull { it.operation == i }
                if (op == null) {
                    0
                } else {
                    if (op.numberOfOperands == 0 && op.dataFormatter === DataFormatters.BYTECODE_LENGTH) {
                        -1
                    } else {
                        op.numberOfOperands
                    }
                }.toByte()
            }

            @JvmStatic
            operator fun get(i: Int): Operations? {
                return OPERATIONS[i]
            }

            @JvmStatic
            fun dump() {
//                Operations.entries
//                    .sortedBy { it.operation.toUByte() }
//                    .forEach {
//                        println("${it.operation.toByte().toHexString()} ${TokenTypeConst(it.operation ushr TOKEN_TYPE_SHIFT_AMOUNT)} ${it.name}")
//                    }
            }
        }

    }

    /** Converts a 2-hex digit operation constant into a full instruction int */
    @JvmStatic
    fun Int.opToInstruction(data: Int = 0): Int {
        return (this shl 24) or (data and 0xFFFFFF)
    }

    /** Extract the 1 byte operation from a 4-byte instruction */
    @JvmStatic
    fun Int.instructionToOp(): Int {
        return this ushr 24
    }
}
