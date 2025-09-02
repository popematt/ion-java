package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.v8.Bytecode.opToInstruction
import java.math.BigDecimal

object BytecodeHelper {

    @JvmStatic
    fun emitNullValue(bytecode: IntList, nullType: IonType) {
        val instruction = if (nullType == IonType.NULL) {
            Bytecode.OP_NULL_NULL.opToInstruction()
        } else {
            // Bytecode.OP_NULL_TYPED.opToInstruction(nullType.ordinal)
            TODO()
        }
        bytecode.add(instruction)
    }

    @JvmStatic
    fun emitBooleanValue(bytecode: IntList, boolean: Boolean) {
        bytecode.add(Bytecode.OP_BOOL.opToInstruction(if (boolean) 1 else 0))
    }

    @JvmStatic
    fun emitInt16Value(bytecode: IntList, short: Short) {
        bytecode.add(Bytecode.OP_SMALL_INT.opToInstruction(short.toInt() and 0xFFFF))
    }

    @JvmStatic
    fun emitInt32Value(bytecode: IntList, int: Int) {
        bytecode.add2(Bytecode.OP_INLINE_INT.opToInstruction(), int)
    }

    @JvmStatic
    fun emitInt64Value(bytecode: IntList, longValue: Long) {
        bytecode.add3(Bytecode.OP_INLINE_LONG.opToInstruction(), (longValue shr 32).toInt(), longValue.toInt())
    }

    @JvmStatic
    fun emitFloatValue(bytecode: IntList, floatValue: Float) {
        bytecode.add2(Bytecode.OP_INLINE_FLOAT.opToInstruction(), floatValue.toRawBits())
    }

    @JvmStatic
    fun emitDoubleValue(bytecode: IntList, doubleValue: Double) {
        val doubleBits = doubleValue.toRawBits()
        bytecode.add3(Bytecode.OP_INLINE_DOUBLE.opToInstruction(), doubleBits.toInt(), (doubleBits shr 32).toInt())
    }

    @JvmStatic
    fun emitDecimalReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add2(Bytecode.OP_REF_DECIMAL.opToInstruction(length), start)
    }

    @JvmStatic
    fun emitDecimalConstant(bytecode: IntList, constantPool: MutableList<Any?>, decimalValue: BigDecimal) {
        val cpIndex = constantPool.size
        constantPool.add(decimalValue)
        bytecode.add(Bytecode.OP_CP_DECIMAL.opToInstruction(cpIndex))
    }

    @JvmStatic
    fun emitShortTimestampReference(bytecode: IntList, opcode: Int, start: Int) {
        bytecode.add2(Bytecode.OP_REF_TIMESTAMP_SHORT.opToInstruction(opcode), start)
    }

    @JvmStatic
    fun emitLongTimestampReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add2(Bytecode.OP_REF_TIMESTAMP_LONG.opToInstruction(length), start)
    }

    @JvmStatic
    fun emitTimestampConstant(bytecode: IntList, constantPool: MutableList<Any?>, timestampValue: Timestamp) {
        val cpIndex = constantPool.size
        constantPool.add(timestampValue)
        bytecode.add(Bytecode.OP_CP_TIMESTAMP.opToInstruction(cpIndex))
    }

    @JvmStatic
    fun emitStringReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add2(Bytecode.OP_REF_STRING.opToInstruction(length), start)
    }

    @JvmStatic
    fun emitStringConstant(bytecode: IntList, constantPool: MutableList<Any?>, text: String) {
        val cpIndex = constantPool.size
        constantPool.add(text)
        bytecode.add(Bytecode.OP_CP_STRING.opToInstruction(cpIndex))
    }

    @JvmStatic
    fun emitSymbolTextReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add2(Bytecode.OP_REF_SYMBOL_TEXT.opToInstruction(length), start)
    }

    @JvmStatic
    fun emitSymbolTextConstant(bytecode: IntList, constantPool: MutableList<Any?>, text: String?) {
        if (text == null) {
            bytecode.add(Bytecode.OP_SYMBOL_SID.opToInstruction(0))
        } else {
            val cpIndex = constantPool.size
            constantPool.add(text)
            bytecode.add(Bytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
        }
    }

    @JvmStatic
    fun emitSymbolId(bytecode: IntList, sid: Int) {
        bytecode.add(Bytecode.OP_SYMBOL_SID.opToInstruction(sid))
    }

    @JvmStatic
    fun emitSystemSymbolId(bytecode: IntList, sid: Int) {
        bytecode.add(Bytecode.OP_SYMBOL_SYSTEM_SID.opToInstruction(sid))
    }

    // TODO: BLOB
    // TODO: CLOB

    @JvmStatic
    fun emitListReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add2(Bytecode.OP_REF_LIST.opToInstruction(length), start)
    }

    @JvmStatic
    inline fun emitInlineList(bytecode: IntList, content: () -> Unit) {
        val containerStartIndex = bytecode.reserve()
        val start = containerStartIndex + 1
        content()
        bytecode.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        val end = bytecode.size()
        bytecode[containerStartIndex] = Bytecode.OP_LIST_START.opToInstruction(end - start)
    }

    @JvmStatic
    fun emitSexpReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add2(Bytecode.OP_REF_SEXP.opToInstruction(length), start)
    }

    @JvmStatic
    inline fun emitInlineSexp(bytecode: IntList, content: () -> Unit) {
        val containerStartIndex = bytecode.reserve()
        val start = containerStartIndex + 1
        content()
        bytecode.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        val end = bytecode.size()
        bytecode[containerStartIndex] = Bytecode.OP_SEXP_START.opToInstruction(end - start)
    }

    @JvmStatic
    fun emitStructReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add2(Bytecode.OP_REF_SID_STRUCT.opToInstruction(length), start)
    }

    @JvmStatic
    inline fun emitInlineStruct(bytecode: IntList, content: () -> Unit) {
        val containerStartIndex = bytecode.reserve()
        val start = containerStartIndex + 1
        content()
        bytecode.add(Bytecode.OP_CONTAINER_END.opToInstruction())
        val end = bytecode.size()
        bytecode[containerStartIndex] = Bytecode.OP_STRUCT_START.opToInstruction(end - start)
    }

    @JvmStatic
    fun emitFieldNameTextReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add2(Bytecode.OP_REF_FIELD_NAME_TEXT.opToInstruction(length), start)
    }

    @JvmStatic
    fun emitFieldNameTextConstant(bytecode: IntList, constantPool: MutableList<Any?>, text: String?) {
        if (text == null) {
            bytecode.add(Bytecode.OP_FIELD_NAME_SID.opToInstruction(0))
        } else {
            val cpIndex = constantPool.size
            constantPool.add(text)
            bytecode.add(Bytecode.OP_CP_FIELD_NAME.opToInstruction(cpIndex))
        }
    }

    @JvmStatic
    fun emitFieldNameSid(bytecode: IntList, sid: Int) {
        bytecode.add(Bytecode.OP_FIELD_NAME_SID.opToInstruction(sid))
    }
}
