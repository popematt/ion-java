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
    const val OP_EMPTY_SYMBOL = 0x0A


    // Materialized (pooled) scalars

    /** DATA is u24 index into constant pool */
    const val OP_CP_BIG_INT = 0x0B
    const val OP_CP_DECIMAL = 0x0C
    const val OP_CP_TIMESTAMP = 0x0D
    const val OP_CP_STRING = 0x0E
    const val OP_CP_SYMBOL = 0x0F
    const val OP_CP_SYMBOL_ID = 0x10
    const val OP_CP_BLOB = 0x11
    const val OP_CP_CLOB = 0x12

    // Data model containers
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
    const val OP_ONE_ANNOTATION_SID = 0x1B
    /** DATA is constant pool index */
    const val OP_CP_ONE_ANNOTATION = 0x1C
    const val OP_N_ANNOTATION_SID = 0x1D
    /** DATA is n; followed by n operands which are constant pool indexes */
    const val OP_CP_N_ANNOTATIONS = 0x1F

    // Macros
    /**
     * Essentially, this can be used like a prefixed expression group.
     * DATA is number of operands (or should it be number of integers).
     * Each OPERAND is one bytecode instruction
     *
     * TODO: What about cases where the argument is more than one element of the int array, such as a list?
     *   Should the evaluator be counting instructions or should it be counting ints?
     *   (This concern also applies to prefixed containers.)
     */
    const val OP_ARGUMENT_VALUE = 0x20

    /** Delimited expression group; do we want delimited or prefixed expression groups in the bytecode? */
    const val OP_START_ARGUMENT_VALUE = 0x21
    const val OP_END_ARGUMENT_VALUE = 0x22

    /** DATA is the index into the calling context's argument pool */
    const val OP_ARGUMENT_REF_TYPE = 0x23

    // Must be preceded by an acceptable number of ARGUMENT_VALUE
    /** DATA is a constant pool index to the `Macro` instance */
    const val OP_INVOKE_MACRO = 0x24

    /** TODO: Special instructions for system macros that should be hard coded, e.g. `add` */
    const val OP_INVOKE_SYS_MACRO = 0x25

    const val EOF = 0x26

    /** DATA is length; OPERAND is start_index */
    const val OP_REF_INT = 0x30
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_FLOAT = 0x31
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
}
