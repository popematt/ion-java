package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import java.math.BigDecimal

object MacroBytecodeHelper {

    @JvmStatic
    fun emitNullValue(bytecode: IntList, nullType: IonType) {
        if (nullType == IonType.NULL) {
            bytecode.add(MacroBytecode.OP_NULL_NULL.opToInstruction())
        } else {
            bytecode.add(MacroBytecode.OP_NULL_TYPED.opToInstruction(nullType.ordinal))
        }
    }

    @JvmStatic
    fun emitBooleanValue(bytecode: IntList, boolean: Boolean) {
        bytecode.add(MacroBytecode.OP_BOOL.opToInstruction(if (boolean) 1 else 0))
    }

    @JvmStatic
    fun emitInt16Value(bytecode: IntList, short: Short) {
        bytecode.add(MacroBytecode.OP_SMALL_INT.opToInstruction(short.toInt() and 0xFFFF))
    }

    @JvmStatic
    fun emitInt32Value(bytecode: IntList, int: Int) {
        bytecode.add(MacroBytecode.OP_INLINE_INT.opToInstruction())
        bytecode.add(int)
    }

    @JvmStatic
    fun emitInt64Value(bytecode: IntList, longValue: Long) {
        bytecode.add(MacroBytecode.OP_INLINE_LONG.opToInstruction())
        bytecode.add((longValue shr 32).toInt())
        bytecode.add(longValue.toInt())
    }

    @JvmStatic
    fun emitFloatValue(bytecode: IntList, floatValue: Float) {
        bytecode.add(MacroBytecode.OP_INLINE_FLOAT.opToInstruction())
        bytecode.add(floatValue.toRawBits())
    }

    @JvmStatic
    fun emitDoubleValue(bytecode: IntList, doubleValue: Double) {
        bytecode.add(MacroBytecode.OP_INLINE_DOUBLE.opToInstruction())
        val doubleBits = doubleValue.toRawBits()
        bytecode.add(doubleBits.toInt())
        bytecode.add((doubleBits shr 32).toInt())
    }

    @JvmStatic
    fun emitDecimalReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add(MacroBytecode.OP_REF_DECIMAL.opToInstruction(length))
        bytecode.add(start)
    }

    @JvmStatic
    fun emitDecimalConstant(bytecode: IntList, constantPool: MutableList<Any?>, decimalValue: BigDecimal) {
        val cpIndex = constantPool.size
        constantPool.add(decimalValue)
        bytecode.add(MacroBytecode.OP_CP_DECIMAL.opToInstruction(cpIndex))
    }

    @JvmStatic
    fun emitShortTimestampReference(bytecode: IntList, opcode: Int, start: Int) {
        bytecode.add(MacroBytecode.OP_REF_TIMESTAMP_SHORT.opToInstruction(opcode))
        bytecode.add(start)
    }

    @JvmStatic
    fun emitLongTimestampReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add(MacroBytecode.OP_REF_TIMESTAMP_LONG.opToInstruction(length))
        bytecode.add(start)
    }

    @JvmStatic
    fun emitTimestampConstant(bytecode: IntList, constantPool: MutableList<Any?>, timestampValue: Timestamp) {
        val cpIndex = constantPool.size
        constantPool.add(timestampValue)
        bytecode.add(MacroBytecode.OP_CP_TIMESTAMP.opToInstruction(cpIndex))
    }

    @JvmStatic
    fun emitStringReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add(MacroBytecode.OP_REF_STRING.opToInstruction(length))
        bytecode.add(start)
    }

    @JvmStatic
    fun emitStringConstant(bytecode: IntList, constantPool: MutableList<Any?>, text: String) {
        val cpIndex = constantPool.size
        constantPool.add(text)
        bytecode.add(MacroBytecode.OP_CP_STRING.opToInstruction(cpIndex))
    }

    @JvmStatic
    fun emitSymbolTextReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add(MacroBytecode.OP_REF_SYMBOL_TEXT.opToInstruction(length))
        bytecode.add(start)
    }

    @JvmStatic
    fun emitSymbolTextConstant(bytecode: IntList, constantPool: MutableList<Any?>, text: String?) {
        if (text == null) {
            bytecode.add(MacroBytecode.OP_SYMBOL_SID.opToInstruction(0))
        } else {
            val cpIndex = constantPool.size
            constantPool.add(text)
            bytecode.add(MacroBytecode.OP_CP_SYMBOL_TEXT.opToInstruction(cpIndex))
        }
    }

    @JvmStatic
    fun emitSymbolId(bytecode: IntList, sid: Int) {
        bytecode.add(MacroBytecode.OP_SYMBOL_SID.opToInstruction(sid))
    }

    @JvmStatic
    fun emitSystemSymbolId(bytecode: IntList, sid: Int) {
        bytecode.add(MacroBytecode.OP_SYSTEM_SYMBOL_SID.opToInstruction(sid))
    }

    // TODO: BLOB
    // TODO: CLOB

    @JvmStatic
    fun emitListReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add(MacroBytecode.OP_REF_LIST.opToInstruction(length))
        bytecode.add(start)
    }

    @JvmStatic
    inline fun emitInlineList(bytecode: IntList, content: () -> Unit) {
        val containerStartIndex = bytecode.size()
        bytecode.add(MacroBytecode.UNSET.opToInstruction())
        val start = bytecode.size()
        content()
        bytecode.add(MacroBytecode.OP_CONTAINER_END.opToInstruction())
        val end = bytecode.size()
        bytecode[containerStartIndex] = MacroBytecode.OP_LIST_START.opToInstruction(end - start)
    }

    @JvmStatic
    fun emitSexpReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add(MacroBytecode.OP_REF_SEXP.opToInstruction(length))
        bytecode.add(start)
    }

    @JvmStatic
    inline fun emitInlineSexp(bytecode: IntList, content: () -> Unit) {
        val containerStartIndex = bytecode.size()
        bytecode.add(MacroBytecode.UNSET.opToInstruction())
        val start = bytecode.size()
        content()
        bytecode.add(MacroBytecode.OP_CONTAINER_END.opToInstruction())
        val end = bytecode.size()
        bytecode[containerStartIndex] = MacroBytecode.OP_SEXP_START.opToInstruction(end - start)
    }

    @JvmStatic
    fun emitStructReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add(MacroBytecode.OP_REF_SID_STRUCT.opToInstruction(length))
        bytecode.add(start)
    }

    @JvmStatic
    inline fun emitInlineStruct(bytecode: IntList, content: () -> Unit) {
        val containerStartIndex = bytecode.size()
        bytecode.add(MacroBytecode.UNSET.opToInstruction())
        val start = bytecode.size()
        content()
        bytecode.add(MacroBytecode.OP_CONTAINER_END.opToInstruction())
        val end = bytecode.size()
        bytecode[containerStartIndex] = MacroBytecode.OP_STRUCT_START.opToInstruction(end - start)
    }

    @JvmStatic
    fun emitFieldNameTextReference(bytecode: IntList, start: Int, length: Int) {
        bytecode.add(MacroBytecode.OP_REF_FIELD_NAME_TEXT.opToInstruction(length))
        bytecode.add(start)
    }

    @JvmStatic
    fun emitFieldNameTextConstant(bytecode: IntList, constantPool: MutableList<Any?>, text: String?) {
        if (text == null) {
            bytecode.add(MacroBytecode.OP_FIELD_NAME_SID.opToInstruction(0))
        } else {
            val cpIndex = constantPool.size
            constantPool.add(text)
            bytecode.add(MacroBytecode.OP_CP_FIELD_NAME.opToInstruction(cpIndex))
        }
    }

    @JvmStatic
    fun emitFieldNameSid(bytecode: IntList, sid: Int) {
        bytecode.add(MacroBytecode.OP_FIELD_NAME_SID.opToInstruction(sid))
    }

    @JvmStatic
    fun emitFieldNameSystemSid(bytecode: IntList, sid: Int) {
        bytecode.add(MacroBytecode.OP_FIELD_NAME_SYSTEM_SID.opToInstruction(sid))
    }


    // TODO: Annotations

    // TODO: Tagless expression groups?
    // TODO: Macros?


    @JvmStatic
    inline fun emitArgumentValue(bytecode: IntList, content: () -> Unit) {
        val containerStartIndex = bytecode.size()
        bytecode.add(MacroBytecode.UNSET.opToInstruction())
        val start = bytecode.size()
        content()
        bytecode.add(MacroBytecode.OP_END_ARGUMENT_VALUE.opToInstruction())
        val end = bytecode.size()
        bytecode[containerStartIndex] = MacroBytecode.OP_START_ARGUMENT_VALUE.opToInstruction(end - start)
    }
}
