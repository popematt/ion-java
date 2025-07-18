package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.*

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

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    operator fun invoke(value: Int): String {
        return when (value shr 24) {
            0x00 -> ""
            0x01 -> "OP_NULL_NULL"
            0x02 -> "OP_NULL_TYPED"
            0x03 -> "OP_BOOL"
            0x04 -> "OP_SMALL_INT"
            0x05 -> "OP_INLINE_INT"
            0x06 -> "OP_INLINE_LONG"
            0x07 -> "OP_INLINE_DOUBLE"
            0x08 -> "OP_DECIMAL_ZERO"
            0x09 -> "OP_EMPTY_STRING"
            0x0a -> "OP_UNKNOWN_SYMBOL"
            0x0b -> "OP_CP_BIG_INT"
            0x0c -> "OP_CP_DECIMAL"
            0x0d -> "OP_CP_TIMESTAMP"
            0x0e -> "OP_CP_STRING"
            0x0f -> "OP_CP_SYMBOL"
            0x11 -> "OP_CP_BLOB"
            0x12 -> "OP_CP_CLOB"
            0x13 -> "OP_LIST_START"
            0x14 -> "OP_LIST_END"
            0x15 -> "OP_SEXP_START"
            0x16 -> "OP_SEXP_END"
            0x17 -> "OP_STRUCT_START"
            0x18 -> "OP_STRUCT_END"
            0x19 -> "OP_FIELD_NAME_SID"
            0x1A -> "OP_CP_FIELD_NAME"
            0x1B -> "OP_ONE_ANNOTATION_SID"
            0x1C -> "OP_CP_ONE_ANNOTATION"
            0x1D -> "OP_N_ANNOTATION_SID"
            0x1F -> "OP_CP_N_ANNOTATIONS"
            0x21 -> "OP_START_ARGUMENT_VALUE"
            0x22 -> "OP_END_ARGUMENT_VALUE"
            0x23 -> "OP_ARGUMENT_REF_TYPE"
            0x24 -> "OP_INVOKE_MACRO"
            0x25 -> "OP_INVOKE_SYS_MACRO"
            0x26 -> "EOF"
            0x27 -> "END_OF_ARGUMENT_SUBSTITUTION"
            0x30 -> "OP_REF_INT"
            0x31 -> "OP_REF_FLOAT"
            0x32 -> "OP_REF_DECIMAL"
            0x33 -> "OP_REF_TIMESTAMP_SHORT"
            0x34 -> "OP_REF_TIMESTAMP_LONG"
            0x35 -> "OP_REF_LIST"
            0x36 -> "OP_REF_SEXP"
            0x37 -> "OP_REF_SID_STRUCT"
            0x38 -> "OP_REF_FLEXSYM_STRUCT"
            0x3A -> "OP_REF_MACRO_INVOCATION"
            else -> ""
        } + "(${value.toHexString()})"
    }

    // TODO: Compact the op numbers so that we can have an efficient jump table.

    private const val TOKEN_TYPE_OFFSET = 2

    /** If DATA is 0, this should raise an error. All other values of DATA may be used for state tracking by reader implementations.  */
    const val UNSET = 0x00

    /** No DATA */
    const val OP_NULL_NULL = TokenTypeConst.NULL shl TOKEN_TYPE_OFFSET
    /** DATA is the IonType ordinal */
    const val OP_NULL_TYPED = (TokenTypeConst.NULL shl TOKEN_TYPE_OFFSET) + 1
    /** DATA is 0 for false; 1 for true */
    const val OP_BOOL = TokenTypeConst.BOOL shl TOKEN_TYPE_OFFSET
    /** DATA is a 16-bit signed int */
    const val OP_SMALL_INT = TokenTypeConst.INT shl TOKEN_TYPE_OFFSET
    /** OPERAND is 32-bit signed int */
    const val OP_INLINE_INT = (TokenTypeConst.INT shl TOKEN_TYPE_OFFSET) + 1
    /** OPERAND is 64-bit signed int. `(op0 shl 32) or op1` */
    const val OP_INLINE_LONG = (TokenTypeConst.INT shl TOKEN_TYPE_OFFSET) + 2
//    /** DATA is a 16-bit float */
//    const val OP_FLOAT_16 = 0x06

    // TODO: Inline float?
    /** OPERAND is 64-bit float. `Double.fromBits((op0 shl 32) or op1)` */
    const val OP_INLINE_DOUBLE = TokenTypeConst.FLOAT shl TOKEN_TYPE_OFFSET

    const val OP_UNKNOWN_SYMBOL = TokenTypeConst.SYMBOL shl TOKEN_TYPE_OFFSET


    // Materialized (pooled) scalars

    /** DATA is u24 index into constant pool */
    const val OP_CP_BIG_INT = (TokenTypeConst.INT shl TOKEN_TYPE_OFFSET) + 3
    const val OP_CP_DECIMAL = (TokenTypeConst.DECIMAL shl TOKEN_TYPE_OFFSET)
    const val OP_CP_TIMESTAMP = (TokenTypeConst.TIMESTAMP shl TOKEN_TYPE_OFFSET)
    const val OP_CP_STRING = (TokenTypeConst.STRING shl TOKEN_TYPE_OFFSET)
    const val OP_CP_SYMBOL = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_OFFSET) + 1
    const val OP_CP_BLOB = (TokenTypeConst.BLOB shl TOKEN_TYPE_OFFSET)
    const val OP_CP_CLOB = (TokenTypeConst.CLOB shl TOKEN_TYPE_OFFSET)

    // Data model containers
    // They are both length prefixed and delimited.
    const val OP_LIST_START = TokenTypeConst.LIST shl TOKEN_TYPE_OFFSET
    const val OP_SEXP_START = TokenTypeConst.SEXP shl TOKEN_TYPE_OFFSET
    const val OP_STRUCT_START = TokenTypeConst.STRUCT shl TOKEN_TYPE_OFFSET


    /** DATA is length; OPERAND is start_index */
    const val OP_REF_LIST = (TokenTypeConst.LIST shl TOKEN_TYPE_OFFSET) + 1
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_SEXP = (TokenTypeConst.SEXP shl TOKEN_TYPE_OFFSET) + 1
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_SID_STRUCT = (TokenTypeConst.STRUCT shl TOKEN_TYPE_OFFSET) + 1
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_FLEXSYM_STRUCT = (TokenTypeConst.STRUCT shl TOKEN_TYPE_OFFSET) + 2

    // Metadata
    /** DATA is the SID */
    const val OP_FIELD_NAME_SID = TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_OFFSET
    const val OP_CP_FIELD_NAME = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_OFFSET) + 1
    const val OP_UNKNOWN_FIELD_NAME = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_OFFSET) + 2
    const val OP_ONE_ANNOTATION_SID = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_OFFSET)
    /** DATA is constant pool index */
    const val OP_CP_ONE_ANNOTATION = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_OFFSET) + 1
    const val OP_N_ANNOTATION_SID = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_OFFSET) + 2
    /** DATA is n; followed by n operands which are constant pool indexes */
    const val OP_CP_N_ANNOTATIONS = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_OFFSET) + 3

    // Macros
    /**
     * DATA is the number of bytecodes until the end of the argument, including the closing delimiter.
     */
    const val OP_START_ARGUMENT_VALUE = TokenTypeConst.EXPRESSION_GROUP shl TOKEN_TYPE_OFFSET

    /** DATA is the index into the calling context's argument pool */
    const val OP_ARGUMENT_REF_TYPE = TokenTypeConst.VARIABLE_REF shl TOKEN_TYPE_OFFSET

    // Must be preceded by an acceptable number of ARGUMENT_VALUE
    /** DATA is a constant pool index to the `Macro` instance */
    const val OP_INVOKE_MACRO = TokenTypeConst.MACRO_INVOCATION shl TOKEN_TYPE_OFFSET

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
    const val OP_INVOKE_SYS_MACRO = (TokenTypeConst.MACRO_INVOCATION shl TOKEN_TYPE_OFFSET) + 1

    /** DATA is constant pool index to MacroInvocation instance */
    const val OP_CP_MACRO_INVOCATION = (TokenTypeConst.MACRO_INVOCATION shl TOKEN_TYPE_OFFSET) + 2

    // TODO: See if we can coalesce the different "End" and "EOF" instructions.
    const val EOF = TokenTypeConst.END shl TOKEN_TYPE_OFFSET

    const val OP_LIST_END = (TokenTypeConst.END shl TOKEN_TYPE_OFFSET) + 1
    const val OP_SEXP_END = (TokenTypeConst.END shl TOKEN_TYPE_OFFSET) + 1
    const val OP_STRUCT_END = (TokenTypeConst.END shl TOKEN_TYPE_OFFSET) + 1
    const val OP_END_ARGUMENT_VALUE = (TokenTypeConst.END shl TOKEN_TYPE_OFFSET) + 1
    const val OP_CONTAINER_END = (TokenTypeConst.END shl TOKEN_TYPE_OFFSET) + 1

    // This is a "soft" end. It doesn't signal the end of a container or macro, but rather that the template reader
    // should switch back to the original source.
    const val END_OF_ARGUMENT_SUBSTITUTION = (TokenTypeConst.END shl TOKEN_TYPE_OFFSET) + 2

    // NOTE about ref opcodes
    // It seems that data locality is one of the most important concerns for performance.
    // So, for many scalars, it will probably be cheaper to eagerly materialize them to be able to
    // put them inline. Particularly for fixed-sized values.

    /** DATA is length; OPERAND is start_index */
    const val OP_REF_INT = 0x30
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_DECIMAL = 0x32
    /** DATA is opcode, length is implicit; OPERAND is start_index */
    const val OP_REF_TIMESTAMP_SHORT = 0x33
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_TIMESTAMP_LONG = 0x34



    // TODO: REMAINING REF OPS


    /** Converts a 2-hex digit operation constant into a full instruction int */
    @JvmStatic
    fun Int.opToInstruction(data: Int = 0): Int {
        return (this shl 24) or data
    }

    /** Extract the 1 byte operation from a 4-byte instruction */
    @JvmStatic
    fun Int.instructionToOp(): Int {
        return this ushr 24
    }

    @JvmStatic
    fun IntArray.bytecodeToString(): String {
        return this.joinToString(" ", prefix = "<< ", postfix = " >>") { MacroBytecode(it) }
    }
}
