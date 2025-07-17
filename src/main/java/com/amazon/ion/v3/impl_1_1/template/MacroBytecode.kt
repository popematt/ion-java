package com.amazon.ion.v3.impl_1_1.template

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

    // OP_STRUCT_START(17000015)
    // OP_CP_FIELD_NAME(1a000000)
    // OP_ARGUMENT_REF_TYPE(23000000)
    // OP_CP_FIELD_NAME(1a000001)
    // OP_ARGUMENT_REF_TYPE(23000001)
    // OP_CP_FIELD_NAME(1a000002)
    // OP_START_ARGUMENT_VALUE(21000002)
    // OP_CP_SYMBOL(0f000003)
    // OP_END_ARGUMENT_VALUE(22000000)
    // OP_START_ARGUMENT_VALUE(21000002)
    // OP_CP_SYMBOL(0f000004)
    // OP_END_ARGUMENT_VALUE(22000000)
    // OP_INVOKE_MACRO(24000005)
    // OP_CP_FIELD_NAME(1a000006)
    // OP_START_ARGUMENT_VALUE(21000002)
    // OP_ARGUMENT_REF_TYPE(23000002)
    // OP_END_ARGUMENT_VALUE(22000000)
    // OP_START_ARGUMENT_VALUE(21000002)
    // OP_SMALL_INT(04000001)
    // OP_END_ARGUMENT_VALUE(22000000)
    // OP_INVOKE_MACRO(24000007)
    // OP_STRUCT_END(18000000)
    // EOF(26000000)

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


    /** If DATA is 0, this should raise an error. All other values of DATA may be used for state tracking by reader implementations.  */
    const val UNSET = 0x00

    /** No DATA */
    const val OP_NULL_NULL = 0x01
    /** DATA is the IonType ordinal */
    const val OP_NULL_TYPED = 0x02
    /** DATA is 0 for false; 1 for true */
    const val OP_BOOL = 0x03
    /** DATA is a 16-bit signed int */
    const val OP_SMALL_INT = 0x04
    /** OPERAND is 32-bit signed int */
    const val OP_INLINE_INT = 0x05
    /** OPERAND is 64-bit signed int. `(op0 shl 32) or op1` */
    const val OP_INLINE_LONG = 0x06
//    /** DATA is a 16-bit float */
//    const val OP_FLOAT_16 = 0x06

    // TODO: Inline float?
    /** OPERAND is 64-bit float. `Double.fromBits((op0 shl 32) or op1)` */
    const val OP_INLINE_DOUBLE = 0x07

    /** DATA is a 16-bit signed int representing the precision */
    const val OP_DECIMAL_ZERO = 0x08
    const val OP_EMPTY_STRING = 0x09
    const val OP_UNKNOWN_SYMBOL = 0x0A


    // Materialized (pooled) scalars

    /** DATA is u24 index into constant pool */
    const val OP_CP_BIG_INT = 0x0B
    const val OP_CP_DECIMAL = 0x0C
    const val OP_CP_TIMESTAMP = 0x0D
    const val OP_CP_STRING = 0x0E
    const val OP_CP_SYMBOL = 0x0F
    const val OP_CP_BLOB = 0x11
    const val OP_CP_CLOB = 0x12

    // Data model containers
    // They are both length prefixed and delimited.
    const val OP_LIST_START = 0x13
    const val OP_LIST_END = 0x14
    const val OP_SEXP_START = 0x15
    const val OP_SEXP_END = 0x16
    const val OP_STRUCT_START = 0x17
    const val OP_STRUCT_END = 0x18


    // Metadata
    /** DATA is the SID */
    const val OP_FIELD_NAME_SID = 0x19
    const val OP_CP_FIELD_NAME = 0x1A
    const val OP_UNKNOWN_FIELD_NAME = 0x44
    const val OP_ONE_ANNOTATION_SID = 0x1B
    /** DATA is constant pool index */
    const val OP_CP_ONE_ANNOTATION = 0x1C
    const val OP_N_ANNOTATION_SID = 0x1D
    /** DATA is n; followed by n operands which are constant pool indexes */
    const val OP_CP_N_ANNOTATIONS = 0x1F

    // Macros
    /**
     * DATA is the number of bytecodes until the end of the argument, including the closing delimiter.
     */
    const val OP_START_ARGUMENT_VALUE = 0x21
    const val OP_END_ARGUMENT_VALUE = 0x22

    /** DATA is the index into the calling context's argument pool */
    const val OP_ARGUMENT_REF_TYPE = 0x23

    // Must be preceded by an acceptable number of ARGUMENT_VALUE
    /** DATA is a constant pool index to the `Macro` instance */
    const val OP_INVOKE_MACRO = 0x24

    /**
     * TODO: Special instructions for system macros that should be hard coded
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     */
    const val OP_INVOKE_SYS_MACRO = 0x25




    // TODO: See if we can coalesce the different "End" and "EOF" instructions.
    const val EOF = 0x26

    // This is a "soft" end. It doesn't signal the end of a container or macro, but rather that the template reader
    // should switch back to the original source.
    const val END_OF_ARGUMENT_SUBSTITUTION = 0x27

    // NOTE about ref opcodes
    // It seems that data locality is one of the most important concerns for performance.
    // So, for many scalars, it will be cheaper to eagerly materialize them to be able to
    // put them inline. Particularly for fixed-sized values.

    /** DATA is length; OPERAND is start_index */
    const val OP_REF_INT = 0x30
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_DECIMAL = 0x32
    /** DATA is opcode, length is implicit; OPERAND is start_index */
    const val OP_REF_TIMESTAMP_SHORT = 0x33
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_TIMESTAMP_LONG = 0x34

    /** DATA is length; OPERAND is start_index */
    const val OP_REF_LIST = 0x35
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_SEXP = 0x36
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_SID_STRUCT = 0x37
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_FLEXSYM_STRUCT = 0x38

    /** DATA is constant pool index to MacroInvocation instance */
    const val OP_CP_MACRO_INVOCATION = 0x3A

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

    @OptIn(ExperimentalStdlibApi::class)
    @JvmStatic
    fun IntArray.bytecodeToString(): String {
        return this.joinToString(" ", prefix = "<< ", postfix = " >>") { MacroBytecode(it) }
    }
}
