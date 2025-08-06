package com.amazon.ion.v3.visitor2

import com.amazon.ion.*
import com.amazon.ion.v3.impl_1_1.MacroV2
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

interface IonFieldVisitor {
    /**
     * Called when a null value is encountered.
     *
     * @param ann when there is at least one annotation on this value, an iterator over the annotation(s).
     *            When there are no annotation, `null`.
     * @param type the Ion type of the null value.
     */
    fun onNull(fieldName: String?, ann: AnnotationIterator?, type: IonType): Instruction
    fun onBool(fieldName: String?, ann: AnnotationIterator?, value: Boolean): Instruction
    fun onLong(fieldName: String?, ann: AnnotationIterator?, value: Long): Instruction
    fun onBigInt(fieldName: String?, ann: AnnotationIterator?, value: () -> BigInteger): Instruction
    fun onDouble(fieldName: String?, ann: AnnotationIterator?, value: Double): Instruction
    fun onDecimal(fieldName: String?, ann: AnnotationIterator?, value: () -> BigDecimal): Instruction
    fun onTimestamp(fieldName: String?, ann: AnnotationIterator?, value: () -> Timestamp): Instruction
    fun onSymbol(fieldName: String?, ann: AnnotationIterator?, sid: Int, text: () -> String?): Instruction
    fun onString(fieldName: String?, ann: AnnotationIterator?, text: () -> String): Instruction
    fun onBlob(fieldName: String?, ann: AnnotationIterator?, bytes: ByteBuffer): Instruction
    fun onClob(fieldName: String?, ann: AnnotationIterator?, bytes: ByteBuffer): Instruction
    fun onList(fieldName: String?, ann: AnnotationIterator?, visitContent: (IonVisitor) -> Unit): Instruction
    fun onSexp(fieldName: String?, ann: AnnotationIterator?, visitContent: (IonVisitor) -> Unit): Instruction
    fun onStruct(fieldName: String?, ann: AnnotationIterator?, visitContent: (IonFieldVisitor) -> Unit): Instruction

    /**
     * Only called for template macros that are guaranteed to produce 1 value. Otherwise, it just does regular evaluation.
     */
    fun onMacro(
        fieldName: String?,
        macro: MacroV2,
        evaluate: (IonVisitor) -> Unit,
        visitArguments: (IonVisitor) -> Unit
    )
}
