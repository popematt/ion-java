package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.IonType
import com.amazon.ion.v3.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.AS_HEX_BYTE
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.AS_HEX_INT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.AS_INT
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.CHAR
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.CP_INDEX
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.LENGTH
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.NO_DATA
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.SID
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.DataFormatters.SRC_INDEX

/**
 * ```text
 * Instructions are formatted here as  OPERATION(data)[operands]
 * When a value does not require the full 24 bits, it is "right" aligned.
 *
 * E.g. Boolean true could be this:
 *
 *     Operation | Data
 *      00000001   00000000 00000000 00000001
 */
object MacroBytecode {

    @JvmStatic
    fun renderForDebugging(bytecode: IntArray): Array<String> {
        val sb = StringBuilder()
        debugString(bytecode, sb::append, useIndent = false, useNumbers = false)
        return sb.toString().split("\n").toTypedArray()
    }

    @JvmStatic
    fun toDebugString(bytecode: IntArray): String {
        val sb = StringBuilder()
        debugString(bytecode, sb::append)
        return sb.toString()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    private fun line(n: Int): String {
        return "L$n".padEnd(6, ' ')
    }

    @JvmStatic
    fun debugString(bytecode: IntArray, write: (String) -> Unit = ::print, useIndent: Boolean = true, useNumbers: Boolean = true) {
        var indent = ""
        var i = 0
        while (i < bytecode.size) {
            write(line(i))
            val instruction = bytecode[i++]
            val operationInt = instruction ushr OPERATION_SHIFT_AMOUNT
            if (operationInt == 0) {
                write(indent)
                write("NOP\n")
                continue
            }

            val operation = Operations.entries.singleOrNull() { it.operation == operationInt }

            operation ?: throw IllegalStateException("Unknown operation $operationInt at position ${i-1}.")

            if (useIndent) when (operationInt) {
                OP_CONTAINER_END,
                OP_END_ARGUMENT_VALUE -> indent = indent.dropLast(2)
            }
            write(indent)
            write(operation.name)
            write(" ")
            write(operation.dataFormatter(instruction and DATA_MASK))

            repeat(operation.numberOfOperands) {
                write("\n${line(i)}$indent  ")
                write(operation.operandFormatter(bytecode[i++]))
            }

            write("\n")
            if (useIndent) when (operationInt) {
                OP_LIST_START,
                OP_SEXP_START,
                OP_STRUCT_START,
                OP_START_ARGUMENT_VALUE -> indent += ". "
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    operator fun invoke(value: Int): String {
        val op = Operations.entries.singleOrNull { it.operation == value.instructionToOp() }?.name ?: ""
        return "$op(${value.toHexString()})"
    }

    const val TOKEN_TYPE_SHIFT_AMOUNT = 3
    const val OPERATION_SHIFT_AMOUNT = 24

    const val DATA_MASK = 0xFFFFFF

    /** If DATA is 0, this should raise an error. All other values of DATA may be used for state tracking by reader implementations.  */
    const val UNSET = 0x00

    object DataFormatters {
        @OptIn(ExperimentalStdlibApi::class)
        val AS_HEX_INT = { data: Int -> data.toHexString()}
        @OptIn(ExperimentalStdlibApi::class)
        val AS_HEX_BYTE = { data: Int -> data.toUByte().toHexString()}
        val AS_INT = { data: Int -> data.toString() }
        val LENGTH = { data: Int -> data.toString() }
        val SRC_INDEX = { data: Int -> data.toString() }
        val CP_INDEX = { data: Int -> data.toString() }
        val SID = { data: Int -> data.toString() }
        val CHAR = { data: Int -> "'${data.toChar()}'" }
        val NO_DATA = { _: Int -> "" }
    }

    // TokenTypes: NUL, BOO, INT, FLT, DCM, TIS, STI, SYM, BLB, CLB, LIS, SXP, STU, ANN, FN, ARG, MAC,
    // "actions": INL, REF, CON, SRT, END, SID, SYS,
    enum class Operations(val operation: Int, val dataFormatter: (Int) -> String, val numberOfOperands: Int = 0, val operandFormatter: (Int) -> String = { "" }) {
        NULL(OP_NULL_NULL, dataFormatter = NO_DATA),
        NULLTYPE(OP_NULL_TYPED, dataFormatter = { IonType.entries[it].toString().lowercase() }),
        BOOL(OP_BOOL, dataFormatter = { (it == 1).toString() }),
        INT16(OP_SMALL_INT, { (it.toShort()).toString() }),
        INT32(OP_INLINE_INT, NO_DATA, 1, AS_HEX_INT),
        INT64(OP_INLINE_LONG, NO_DATA, 2, AS_HEX_INT),
        INT_CNST(OP_CP_BIG_INT, CP_INDEX),
        INT_REF(OP_REF_INT, LENGTH, 1, SRC_INDEX),
        FLOAT32(OP_INLINE_FLOAT, NO_DATA, 1, AS_HEX_INT),
        FLOAT64(OP_INLINE_DOUBLE, NO_DATA, 2, AS_HEX_INT),
        DEC_CNST(OP_CP_DECIMAL, AS_INT),
        DEC_REF(OP_REF_DECIMAL, LENGTH, 1, SRC_INDEX),
        TS_CNST(OP_CP_TIMESTAMP, CP_INDEX),
        TSS_REF(OP_REF_TIMESTAMP_SHORT, AS_HEX_BYTE, 1, SRC_INDEX),
        TSL_REF(OP_REF_TIMESTAMP_LONG, LENGTH, 1, SRC_INDEX),
        STR_CNST(OP_CP_STRING, CP_INDEX),
        STR_REF(OP_REF_STRING, LENGTH, 1, SRC_INDEX),
        SYM_SYS(OP_SYSTEM_SYMBOL_SID, SID),
        SYM_CNST(OP_CP_SYMBOL_TEXT, CP_INDEX),
        SYM_REF(OP_REF_SYMBOL_TEXT, LENGTH, 1, SRC_INDEX),
        SYM_SID(OP_SYMBOL_SID, SID),
        SYM_1_CHAR(OP_SYMBOL_CHAR, CHAR),
        BLOB_CNST(OP_CP_BLOB, CP_INDEX),
        BLOB_REF(OP_REF_BLOB, LENGTH, 1, SRC_INDEX),
        CLOB_CNST(OP_CP_CLOB, CP_INDEX),
        CLOB_REF(OP_REF_CLOB, LENGTH, 1, SRC_INDEX),
        LIST_START(OP_LIST_START, LENGTH),
        SEXP_START(OP_SEXP_START, LENGTH),
        STRUCT_START(OP_STRUCT_START, LENGTH),
        LIST_REF(OP_REF_LIST, LENGTH, 1, SRC_INDEX),
        SEXP_REF(OP_REF_SEXP, LENGTH, 1, SRC_INDEX),
        STRUCT_REF(OP_REF_SID_STRUCT, LENGTH, 1, SRC_INDEX),
        FNAME_SID(OP_FIELD_NAME_SID, SID),
        FNAME_CNST(OP_CP_FIELD_NAME, CP_INDEX),
        FNAME_SYS(OP_FIELD_NAME_SYSTEM_SID, SID),
        FNAME_REF(OP_REF_FIELD_NAME_TEXT, LENGTH, 1, SRC_INDEX),
        ANN_CNST(OP_CP_ONE_ANNOTATION, CP_INDEX),
        ANN_SID(OP_ONE_ANNOTATION_SID, SID),
        ANN_REF(OP_REF_ONE_FLEXSYM_ANNOTATION, LENGTH, 1, SRC_INDEX),
        ANN_SYS(OP_ANN_SYSTEM_SID, SID),
        ARG_START(OP_START_ARGUMENT_VALUE, LENGTH),
        PARAM(OP_PARAMETER, AS_INT),
        MAC_CNST(OP_INVOKE_MACRO, CP_INDEX),
        // TODO? An instruction denoting that an inline macro invocation is about to start.
        MAC_START(999, LENGTH),
        MAC_SYS(OP_INVOKE_SYS_MACRO, AS_HEX_BYTE),
        CP_MACRO_INVOCATION(OP_CP_MACRO_INVOCATION, CP_INDEX),
        EOF(MacroBytecode.EOF, NO_DATA),
        CONTAINER_END(OP_CONTAINER_END, NO_DATA),
        ARG_END(OP_END_ARGUMENT_VALUE, NO_DATA),
        RETURN(OP_RETURN, NO_DATA),

        ;

        @OptIn(ExperimentalStdlibApi::class)
        companion object {

            @JvmStatic
            private val OPERATIONS = Array<Operations?>(256) { i ->
                Operations.entries.singleOrNull { it.operation == i }
            }

            @JvmStatic
            operator fun get(i: Int): Operations? {
                return OPERATIONS[i]
            }

            init {
//                Operations.entries
//                    .sortedBy { it.operation.toUByte() }
//                    .forEach {
//                        println("${it.operation.toByte().toHexString()} ${TokenTypeConst(it.operation ushr TOKEN_TYPE_SHIFT_AMOUNT)} ${it.name}")
//                    }
            }

        }
    }


    /** No DATA */
    const val OP_NULL_NULL = TokenTypeConst.NULL shl TOKEN_TYPE_SHIFT_AMOUNT
    /** DATA is the IonType ordinal */
    const val OP_NULL_TYPED = (TokenTypeConst.NULL shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
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

    /** DATA is SID as u24 */
    const val OP_SYSTEM_SYMBOL_SID = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT)
    // const val OP_UNKNOWN_SYMBOL = TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT
    /** DATA is u24 index into constant pool */
    const val OP_CP_SYMBOL_TEXT = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_SYMBOL_TEXT = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    /** DATA is SID as u24 */
    const val OP_SYMBOL_SID = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 3
    /** DATA is 1 ascii character; TODO: or up to 3 bytes of utf8? */
    const val OP_SYMBOL_CHAR = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 4

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
    /** DATA is u8 system SID */
    const val OP_FIELD_NAME_SYSTEM_SID = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_SHIFT_AMOUNT) + 3

    /** DATA is constant pool index */
    const val OP_CP_ONE_ANNOTATION = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 0
    /**
     * DATA is length; OPERAND is start_index
     * If length is 0, the OPERAND is a system symbol ID.
     */
    const val OP_REF_ONE_FLEXSYM_ANNOTATION = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** DATA is SID as u24 */
    const val OP_ONE_ANNOTATION_SID = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    /** DATA is system sid */
    const val OP_ANN_SYSTEM_SID = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 3

    // TODO: Could we just chain multiple "one" annotation opcodes?
    /** DATA is u8 indicating whether this is SIDs, CP Indexes, or FlexSyms and u16 indicating the number of annotations; followed by n operands */
//    const val OP_N_ANNOTATIONS = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 3

    // Macros
    /**
     * DATA is the number of bytecodes until the end of the argument, including the closing delimiter.
     */
    const val OP_START_ARGUMENT_VALUE = TokenTypeConst.EXPRESSION_GROUP shl TOKEN_TYPE_SHIFT_AMOUNT

    /** DATA is the index into the calling context's argument pool */
    const val OP_PARAMETER = TokenTypeConst.VARIABLE_REF shl TOKEN_TYPE_SHIFT_AMOUNT

    // Must be preceded by an acceptable number of ARGUMENT_VALUE
    /** DATA is a constant pool index to the `Macro` instance */
    const val OP_INVOKE_MACRO = TokenTypeConst.MACRO_INVOCATION shl TOKEN_TYPE_SHIFT_AMOUNT

    /**
     * TODO: Special instructions for system macros that should be hard coded
     *
     * | Macro          | Must be hardcoded | Return # | return type |
     * |:---------------|-------------------|----------|-------------|
     * | none           | N                 | 0        | -           |
     * | values         | N                 | *        | *           |
     * | default        | Y                 | *        | *           |
     * | meta           | N                 | 0        | -           |
     * | repeat         | Y                 | *        | *           |
     * | flatten        | Y                 | *        | *           |
     * | delta          | Y                 | *        | int         |
     * | sum            | Y                 | 1        | int         |
     * | annotate       | y                 | 1 value  | *           |
     * | make_string    | y                 | 1        | string      |
     * | make_symbol    | y                 | 1        | symbol      |
     * | make_decimal   | y                 | 1        | decimal     |
     * | make_timestamp | y                 | 1        | timestamp   |
     * | make_blob      | y                 | 1        | blob        |
     * | make_list      | n                 | 1        | list        |
     * | make_sexp      | n                 | 1        | sexp        |
     * | make_field     | y                 | 1        | struct      |
     * | make_struct    | y                 | 1        | struct      |
     * | parse_ion      | y                 | *        | *           |
     * | set_symbols    | n                 | 0 or 1   | directive   |
     * | add_symbols    | n                 | 0 or 1   | directive   |
     * | set_macros     | n                 | 0 or 1   | directive   |
     * | add_macros     | n                 | 0 or 1   | directive   |
     * | use            | n                 | 0 or 1   | directive   |
     *
     */
    const val OP_INVOKE_SYS_MACRO = (TokenTypeConst.MACRO_INVOCATION shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    /** DATA is constant pool index to MacroInvocation instance */
    const val OP_CP_MACRO_INVOCATION = (TokenTypeConst.MACRO_INVOCATION shl TOKEN_TYPE_SHIFT_AMOUNT) + 2

    // TODO: See if we can coalesce the different "End" and "EOF" instructions.
    const val EOF = TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT

    const val OP_LIST_END = (TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_SEXP_END = (TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_STRUCT_END = (TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_CONTAINER_END = (TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    // This is a "soft" end. It doesn't signal the end of a container or macro, but rather that the template reader
    // should switch back to the original source.
    const val OP_END_ARGUMENT_VALUE = (TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    const val END_OF_ARGUMENT_SUBSTITUTION = (TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT) + 2

    const val OP_RETURN = (TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT) + 3

    // NOTE about ref opcodes
    // It seems that data locality is one of the most important concerns for performance.
    // So, for many scalars, it will probably be cheaper to eagerly materialize them to be able to
    // put them inline. Particularly for fixed-sized values.


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
