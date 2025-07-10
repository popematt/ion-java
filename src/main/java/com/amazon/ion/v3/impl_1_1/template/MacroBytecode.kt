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

    /** No DATA */
    const val OP_NULL_NULL = 0x00
    /** DATA is the IonType ordinal */
    const val OP_NULL_TYPED = 0x01
    /** DATA is 0 for false; 1 for true */
    const val OP_BOOL = 0x02
    /** DATA is a 16-bit signed int */
    const val OP_SMALL_INT = 0x03
    /** OPERAND is 32-bit signed int */
    const val OP_INLINE_INT = 0x04
    /** OPERAND is 64-bit signed int. `(op0 shl 32) or op1` */
    const val OP_INLINE_LONG = 0x05
//    /** DATA is a 16-bit float */
//    const val OP_FLOAT_16 = 0x06

    // TODO: Inline float?
    /** OPERAND is 64-bit float. `Double.fromBits((op0 shl 32) or op1)` */
    const val OP_INLINE_DOUBLE = 0x06

    /** DATA is a 16-bit signed int representing the precision */
    const val OP_DECIMAL_ZERO = 0x07
    const val OP_EMPTY_STRING = 0x08
    const val OP_EMPTY_SYMBOL = 0x09

    /** DATA is length; OPERAND is start_index */
    const val OP_REF_INT = 0x10
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_FLOAT = 0x11
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_DECIMAL = 0x12
    /** DATA is opcode, length is implicit; OPERAND is start_index */
    const val OP_REF_TIMESTAMP_SHORT = 0x13
    /** DATA is length; OPERAND is start_index */
    const val OP_REF_TIMESTAMP_LONG = 0x14
    // TODO: REMAINING REF OPS

    // Materialized (pooled) scalars

    /** DATA is u24 index into constant pool */
    const val OP_CP_BIG_INT = 0x21
    const val OP_CP_DECIMAL = 0x23
    const val OP_CP_TIMESTAMP = 0x24
    const val OP_CP_STRING = 0x25
    const val OP_CP_SYMBOL = 0x26
    const val OP_CP_SYMBOL_ID = 0x27
    const val OP_CP_BLOB = 0x28
    const val OP_CP_CLOB = 0x29

    // Data model containers
    const val OP_LIST_START = 0x33
    const val OP_LIST_END = 0x34
    const val OP_SEXP_START = 0x36
    const val OP_SEXP_END = 0x37
    const val OP_STRUCT_START = 0x39
    const val OP_STRUCT_END = 0x3A

    // Metadata
    /** DATA is the SID */
    const val OP_FIELD_NAME_SID = 0x40
    const val OP_CP_FIELD_NAME = 0x41
    const val OP_ONE_ANNOTATION_SID = 0x42
    /** DATA is constant pool index */
    const val OP_CP_ONE_ANNOTATION = 0x43
    const val OP_N_ANNOTATION_SID = 0x44
    /** DATA is n; followed by n operands which are constant pool indexes */
    const val OP_CP_N_ANNOTATIONS = 0x45

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
    const val OP_ARGUMENT_VALUE = 0x50

    /** Delimited expression group; do we want delimited or prefixed expression groups in the bytecode? */
    const val OP_START_ARGUMENT_VALUE = 0x51
    const val OP_END_ARGUMENT_VALUE = 0x52

    /** DATA is the index into the calling context's argument pool */
    const val OP_ARGUMENT_REF_TYPE = 0x53

    // Must be preceded by an acceptable number of ARGUMENT_VALUE
    /** DATA is a constant pool index to the `Macro` instance */
    const val OP_INVOKE_MACRO = 0x54

    /** TODO: Special instructions for system macros that should be hard coded, e.g. `add` */
    const val OP_INVOKE_SYS_MACRO = 0x55

    const val EOF = 0x60

    /** If DATA is 0, this should raise an error. All other values of DATA may be used for state tracking by reader implementations.  */
    const val UNSET = 0xFF

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
