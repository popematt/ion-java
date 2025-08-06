package com.amazon.ion.v3.visitor2

import com.amazon.ion.*
import com.amazon.ion.v3.impl_1_1.MacroV2
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

interface IonVisitor {
    /**
     * Called when a null value is encountered.
     *
     * @param ann when there is at least one annotation on this value, an iterator over the annotation(s).
     *            When there are no annotation, `null`.
     * @param type the Ion type of the null value.
     */
    fun onNull(ann: AnnotationIterator?, type: IonType): Instruction
    fun onBool(ann: AnnotationIterator?, value: Boolean): Instruction
    fun onLong(ann: AnnotationIterator?, value: Long): Instruction
    fun onBigInt(ann: AnnotationIterator?, value: () -> BigInteger): Instruction
    fun onDouble(ann: AnnotationIterator?, value: Double): Instruction
    fun onDecimal(ann: AnnotationIterator?, value: () -> BigDecimal): Instruction
    fun onTimestamp(ann: AnnotationIterator?, value: () -> Timestamp): Instruction
    fun onSymbol(ann: AnnotationIterator?, sid: Int, text: () -> String?): Instruction
    fun onString(ann: AnnotationIterator?, text: () -> String): Instruction
    fun onBlob(ann: AnnotationIterator?, bytes: ByteBuffer): Instruction
    fun onClob(ann: AnnotationIterator?, bytes: ByteBuffer): Instruction
    fun onList(ann: AnnotationIterator?, content: (IonVisitor) -> Unit): Instruction
    fun onSexp(ann: AnnotationIterator?, content: (IonVisitor) -> Unit): Instruction
    fun onStruct(ann: AnnotationIterator?, content: (IonFieldVisitor) -> Unit): Instruction
    /*
    fun onField(fieldNameSid: Int, fieldName: () -> String?, visit: (IonVisitor) -> Unit)
     */


    /**
     * Only called for template macros that are guaranteed to produce 1 value. Otherwise, it just does regular evaluation.
     */
    fun onMacro(
        macro: MacroV2,
        evaluate: (IonVisitor) -> Unit,
        visitArguments: (IonVisitor) -> Unit
    )
}
