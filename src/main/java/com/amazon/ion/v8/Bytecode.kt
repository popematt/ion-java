package com.amazon.ion.v8

import com.amazon.ion.v8.Bytecode.DataFormatters.AS_HEX_BYTE
import com.amazon.ion.v8.Bytecode.DataFormatters.AS_HEX_INT
import com.amazon.ion.v8.Bytecode.DataFormatters.AS_INT
import com.amazon.ion.v8.Bytecode.DataFormatters.AS_SHORT
import com.amazon.ion.v8.Bytecode.DataFormatters.BOOL_DATA
import com.amazon.ion.v8.Bytecode.DataFormatters.BYTECODE_LENGTH
import com.amazon.ion.v8.Bytecode.DataFormatters.CHAR
import com.amazon.ion.v8.Bytecode.DataFormatters.CP_INDEX
import com.amazon.ion.v8.Bytecode.DataFormatters.NO_DATA
import com.amazon.ion.v8.Bytecode.DataFormatters.REF_LENGTH
import com.amazon.ion.v8.Bytecode.DataFormatters.SID
import com.amazon.ion.v8.Bytecode.DataFormatters.SRC_INDEX

/**
 *
 * | Constant                    | Operation | Token Type Bits | Variant Bits | Data Field               | Operands | Description                     |
 * |-----------------------------|-----------|----------------:|--------------|--------------------------|----------|---------------------------------|
 * | `UNSET`                     | 0x00      |           00000 | 000          | Error if 0               | 0        | Uninitialized instruction       |
 * | `OP_NULL_NULL`              | 0x0F      |           00001 | 111          | None                     | 0        | Null value of null type         |
 * | `OP_BOOL`                   | 0x10      |           00010 | 000          | 0=false, 1=true          | 0        | Boolean value                   |
 * | `OP_NULL_BOOL`              | 0x17      |           00010 | 111          | None                     | 0        | Null boolean                    |
 * | `OP_SMALL_INT`              | 0x18      |           00011 | 000          | 16-bit signed int        | 0        | Small integer (-32768 to 32767) |
 * | `OP_INLINE_INT`             | 0x19      |           00011 | 001          | None                     | 1        | 32-bit signed integer           |
 * | `OP_INLINE_LONG`            | 0x1A      |           00011 | 010          | None                     | 2        | 64-bit signed integer           |
 * | `OP_CP_BIG_INT`             | 0x1B      |           00011 | 011          | CP index (u24)           | 0        | Big integer from constant pool  |
 * | `OP_REF_INT`                | 0x1C      |           00011 | 100          | Length                   | 1        | Integer reference (start_index) |
 * | `OP_NULL_INT`               | 0x1F      |           00011 | 111          | None                     | 0        | Null integer                    |
 * | `OP_INLINE_FLOAT`           | 0x20      |           00100 | 000          | None                     | 1        | 32-bit float                    |
 * | `OP_INLINE_DOUBLE`          | 0x21      |           00100 | 001          | None                     | 2        | 64-bit double                   |
 * | `OP_NULL_FLOAT`             | 0x27      |           00100 | 111          | None                     | 0        | Null float                      |
 * | `OP_CP_DECIMAL`             | 0x28      |           00101 | 000          | CP index (u24)           | 0        | Decimal from constant pool      |
 * | `OP_REF_DECIMAL`            | 0x29      |           00101 | 001          | Length                   | 1        | Decimal reference (start_index) |
 * | `OP_NULL_DECIMAL`           | 0x2F      |           00101 | 111          | None                     | 0        | Null decimal                    |
 * | `OP_CP_TIMESTAMP`           | 0x30      |           00110 | 000          | CP index (u24)           | 0        | Timestamp from constant pool    |
 * | `OP_REF_TIMESTAMP_SHORT`    | 0x31      |           00110 | 001          | Opcode (implicit length) | 1        | Short timestamp reference       |
 * | `OP_REF_TIMESTAMP_LONG`     | 0x32      |           00110 | 010          | Length                   | 1        | Long timestamp reference        |
 * | `OP_NULL_TIMESTAMP`         | 0x37      |           00110 | 111          | None                     | 0        | Null timestamp                  |
 * | `OP_CP_STRING`              | 0x38      |           00111 | 000          | CP index (u24)           | 0        | String from constant pool       |
 * | `OP_REF_STRING`             | 0x39      |           00111 | 001          | Length                   | 1        | String reference (start_index)  |
 * | `OP_NULL_STRING`            | 0x3F      |           00111 | 111          | None                     | 0        | Null string                     |
 * | `OP_CP_SYMBOL_TEXT`         | 0x40      |           01000 | 000          | CP index (u24)           | 0        | Symbol text from constant pool  |
 * | `OP_REF_SYMBOL_TEXT`        | 0x41      |           01000 | 001          | Length                   | 1        | Symbol text reference           |
 * | `OP_SYMBOL_SID`             | 0x42      |           01000 | 010          | Symbol ID (u24)          | 0        | Symbol by ID                    |
 * | `OP_SYMBOL_CHAR`            | 0x43      |           01000 | 011          | ASCII character          | 0        | Single character symbol         |
 * | `OP_NULL_SYMBOL`            | 0x47      |           01000 | 111          | None                     | 0        | Null symbol                     |
 * | `OP_CP_CLOB`                | 0x48      |           01001 | 000          | CP index (u24)           | 0        | Clob from constant pool         |
 * | `OP_REF_CLOB`               | 0x49      |           01001 | 001          | Length                   | 1        | Clob reference (start_index)    |
 * | `OP_NULL_CLOB`              | 0x4F      |           01001 | 111          | None                     | 0        | Null clob                       |
 * | `OP_CP_BLOB`                | 0x50      |           01010 | 000          | CP index (u24)           | 0        | Blob from constant pool         |
 * | `OP_REF_BLOB`               | 0x51      |           01010 | 001          | Length                   | 1        | Blob reference (start_index)    |
 * | `OP_NULL_BLOB`              | 0x57      |           01010 | 111          | None                     | 0        | Null blob                       |
 * | `OP_LIST_START`             | 0x58      |           01011 | 000          | Bytecode length          | 0        | Start of list container         |
 * | `OP_REF_LIST`               | 0x59      |           01011 | 001          | Length                   | 1        | List reference (start_index)    |
 * | `OP_NULL_LIST`              | 0x5F      |           01011 | 111          | None                     | 0        | Null list                       |
 * | `OP_SEXP_START`             | 0x60      |           01100 | 000          | Bytecode length          | 0        | Start of s-expression           |
 * | `OP_REF_SEXP`               | 0x61      |           01100 | 001          | Length                   | 1        | S-expression reference          |
 * | `OP_NULL_SEXP`              | 0x67      |           01100 | 111          | None                     | 0        | Null s-expression               |
 * | `OP_STRUCT_START`           | 0x68      |           01101 | 000          | Bytecode length          | 0        | Start of struct container       |
 * | `OP_REF_SID_STRUCT`         | 0x69      |           01101 | 001          | Length                   | 1        | Struct reference                |
 * | `OP_NULL_STRUCT`            | 0x6F      |           01101 | 111          | None                     | 0        | Null struct                     |
 * | `OP_CP_ANNOTATION`          | 0x70      |           01110 | 000          | CP index (u24)           | 0        | Annotation from constant pool   |
 * | `OP_REF_ANNOTATION`         | 0x71      |           01110 | 001          | Length (0=system SID)    | 1        | Annotation reference            |
 * | `OP_ANNOTATION_SID`         | 0x72      |           01110 | 010          | Symbol ID (u24)          | 0        | Annotation by symbol ID         |
 * | `OP_CP_FIELD_NAME`          | 0x78      |           01111 | 000          | CP index (u24)           | 0        | Field name from constant pool   |
 * | `OP_REF_FIELD_NAME_TEXT`    | 0x79      |           01111 | 001          | Length                   | 1        | Field name text reference       |
 * | `OP_FIELD_NAME_SID`         | 0x7A      |           01111 | 010          | Symbol ID (u24)          | 0        | Field name by symbol ID         |
 * | `OP_CONTAINER_END`          | 0x88      |           10001 | 000          | None                     | 0        | End of container                |
 * | `IVM`                       | 0x90      |           10010 | 000          | None                     | 0        | Ion Version Marker              |
 * | `OP_PARAMETER`              | 0xA8      |           10101 | 000          | Default value length     | 0        | Macro parameter                 |
 * | `OP_TAGLESS_PARAMETER`      | 0xA9      |           10101 | 001          | Tagless value opcode     | 0        | Tagless macro parameter         |
 * | `OP_MACRO_SHAPED_PARAMETER` | 0xAA      |           10101 | 010          | Macro ID                 | 0        | Macro-shaped parameter          |
 * | `ABSENT_ARGUMENT`           | 0xB0      |           10110 | 000          | TBD                      | 0        | Absent macro argument           |
 * | `DIRECTIVE_SET_SYMBOLS`     | 0xC0      |           11000 | 000          | TBD                      | 0        | Set symbol table                |
 * | `DIRECTIVE_ADD_SYMBOLS`     | 0xC1      |           11000 | 001          | TBD                      | 0        | Add to symbol table             |
 * | `DIRECTIVE_SET_MACROS`      | 0xC2      |           11000 | 010          | TBD                      | 0        | Set macro table                 |
 * | `DIRECTIVE_ADD_MACROS`      | 0xC3      |           11000 | 011          | TBD                      | 0        | Add to macro table              |
 * | `DIRECTIVE_USE`             | 0xC4      |           11000 | 100          | TBD                      | 0        | Use directive                   |
 * | `DIRECTIVE_MODULE`          | 0xC5      |           11000 | 101          | TBD                      | 0        | Module directive                |
 * | `DIRECTIVE_ENCODING`        | 0xC6      |           11000 | 110          | TBD                      | 0        | Encoding directive              |
 * | `REFILL`                    | 0xC8      |           11001 | 000          | None                     | 0        | Refill bytecode buffer          |
 * | `EOF`                       | 0xD0      |           11010 | 000          | None                     | 0        | End of file                     |
 *
 *
 * Notes:
 *  - It seems that data locality is one of the most important concerns for performance. So, for fixed-sized scalars,
 *    it will probably be cheaper to eagerly materialize them to be able to put them inline. We have done this for all
 *    floats, integers that are 64 bits or fewer, booleans, and single-character symbols.
 *
 * Potential Improvements:
 *  - Currently, the 0x00 operation is reserved for "unset", but consider using it for Ion Binary opcodes instead.
 *    That might allow us to unify the macro reader and the raw binary reader. Will it improve performance? I'm not sure.
 *  - If we need to support data streams more than 4GB in length, add "Long Ref" variants of all ref types that have a uint64 start_index.
 *  - For directive operations, we could try to use the DATA for something useful. They are container-like, but there's
 *    no need to store a length since they cannot be skipped.
 *  - If directive handling can be pushed into a lower layer, we might be able to remove those instructions entirely.
 *  - Figure out some way to encode the operands into the variant so that we don't have to use a lookup table to determine
 *    the number of operands. INT is the only type that might someday need two 2-operand instructions.
 *    If we're willing to go to only 22 bits for DATA, we could encode the number of arguments right after the token type and variant.
 *    Furthermore, if we can mark some as having "child values", then it's even better. That's 4 states... it could work.
 */
object Bytecode {

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
    const val OP_BOOL = TokenTypeConst.BOOL shl TOKEN_TYPE_SHIFT_AMOUNT
    const val OP_SMALL_INT = TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT
    const val OP_INLINE_INT = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_INLINE_LONG = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    const val OP_CP_BIG_INT = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 3
    const val OP_REF_INT = (TokenTypeConst.INT shl TOKEN_TYPE_SHIFT_AMOUNT) + 4
    const val OP_INLINE_FLOAT = (TokenTypeConst.FLOAT shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val OP_INLINE_DOUBLE = (TokenTypeConst.FLOAT shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_CP_DECIMAL = (TokenTypeConst.DECIMAL shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val OP_REF_DECIMAL = (TokenTypeConst.DECIMAL shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_CP_TIMESTAMP = (TokenTypeConst.TIMESTAMP shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val OP_REF_TIMESTAMP_SHORT = (TokenTypeConst.TIMESTAMP shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_REF_TIMESTAMP_LONG = (TokenTypeConst.TIMESTAMP shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    const val OP_CP_STRING = (TokenTypeConst.STRING shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val OP_REF_STRING = (TokenTypeConst.STRING shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_CP_SYMBOL_TEXT = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val OP_REF_SYMBOL_TEXT = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_SYMBOL_SID = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    const val OP_SYMBOL_CHAR = (TokenTypeConst.SYMBOL shl TOKEN_TYPE_SHIFT_AMOUNT) + 3
    const val OP_CP_BLOB = (TokenTypeConst.BLOB shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val OP_REF_BLOB = (TokenTypeConst.BLOB shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_CP_CLOB = (TokenTypeConst.CLOB shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val OP_REF_CLOB = (TokenTypeConst.CLOB shl TOKEN_TYPE_SHIFT_AMOUNT) + 1

    // Data model containers
    // They are both length prefixed and delimited.
    const val OP_LIST_START = TokenTypeConst.LIST shl TOKEN_TYPE_SHIFT_AMOUNT
    const val OP_SEXP_START = TokenTypeConst.SEXP shl TOKEN_TYPE_SHIFT_AMOUNT
    const val OP_STRUCT_START = TokenTypeConst.STRUCT shl TOKEN_TYPE_SHIFT_AMOUNT
    const val OP_CONTAINER_END = TokenTypeConst.END shl TOKEN_TYPE_SHIFT_AMOUNT

    const val OP_CP_FIELD_NAME = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val OP_REF_FIELD_NAME_TEXT = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_FIELD_NAME_SID = (TokenTypeConst.FIELD_NAME shl TOKEN_TYPE_SHIFT_AMOUNT) + 2

    const val OP_CP_ANNOTATION = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 0
    const val OP_REF_ANNOTATION = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val OP_ANNOTATION_SID = (TokenTypeConst.ANNOTATIONS shl TOKEN_TYPE_SHIFT_AMOUNT) + 2

    // Macros
    /** DATA is the length of the default value. */
    const val OP_PLACEHOLDER = TokenTypeConst.VARIABLE_REF shl TOKEN_TYPE_SHIFT_AMOUNT
    /** DATA is tagless value's opcode. */
    const val OP_TAGLESS_PLACEHOLDER = (TokenTypeConst.VARIABLE_REF shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    /** No data */
    const val ABSENT_ARGUMENT = (TokenTypeConst.ABSENT_ARGUMENT shl TOKEN_TYPE_SHIFT_AMOUNT)

    // Directives:
    const val DIRECTIVE_SET_SYMBOLS = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT)
    const val DIRECTIVE_ADD_SYMBOLS = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 1
    const val DIRECTIVE_SET_MACROS = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 2
    const val DIRECTIVE_ADD_MACROS = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 3
    const val DIRECTIVE_USE = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 4
    const val DIRECTIVE_MODULE = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 5
    const val DIRECTIVE_ENCODING = (TokenTypeConst.SYSTEM_VALUE shl TOKEN_TYPE_SHIFT_AMOUNT) + 6
    const val IVM = (TokenTypeConst.IVM shl TOKEN_TYPE_SHIFT_AMOUNT)

    // Bytecode interpreter control
    const val EOF = TokenTypeConst.EOF shl TOKEN_TYPE_SHIFT_AMOUNT
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
                if (!lax) return

                continue
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
        val AS_SHORT = { data: Int -> " ${data.toShort()}" }
        val REF_LENGTH = { data: Int -> " $data" }
        val BYTECODE_LENGTH = { data: Int -> " $data" }
        /** Represents an unsigned 32-bit integer. */
        val SRC_INDEX = { data: Int -> " $data" }
        val CP_INDEX = { data: Int -> " $data" }
        val SID = { data: Int -> " $data" }
        val CHAR = { data: Int -> " '${data.toChar()}'" }
        val NO_DATA = { _: Int -> "" }
        val BOOL_DATA = { it: Int -> " " + (it == 1).toString() }
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

        BOOL(OP_BOOL, BOOL_DATA),

        INT16(OP_SMALL_INT, AS_SHORT),
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
        FNAME_SID(OP_FIELD_NAME_SID, SID),
        FNAME_CNST(OP_CP_FIELD_NAME, CP_INDEX),
        FNAME_REF(OP_REF_FIELD_NAME_TEXT, REF_LENGTH, 1, SRC_INDEX),
        ANN_CNST(OP_CP_ANNOTATION, CP_INDEX),
        ANN_SID(OP_ANNOTATION_SID, SID),
        ANN_REF(OP_REF_ANNOTATION, REF_LENGTH, 1, SRC_INDEX),
        PLACEHOLDER(OP_PLACEHOLDER, BYTECODE_LENGTH),
        TAGLESS_PLACEHOLDER(OP_TAGLESS_PLACEHOLDER, AS_HEX_BYTE),
        ABSENT_ARG(ABSENT_ARGUMENT, NO_DATA),
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
