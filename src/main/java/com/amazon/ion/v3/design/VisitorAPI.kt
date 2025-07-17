package com.amazon.ion.v3.design

import com.amazon.ion.*
import com.amazon.ion.v3.impl_1_1.*
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

enum class Instruction {
    CONTINUE,
    STEP_OUT,
}

interface AnnotationIterator: Iterator<String?> {
    /**
     * Reads the next annotation in this sequence of annotations. Once this method has been called,
     * call [getSid] and/or [getText] to get the annotation value.
     */
    override fun next(): String?

    /**
     * Gets the SID of the current annotation, or -1 if the annotation has no SID.
     * Do not call this method until after calling [next].
     */
    fun getSid(): Int

    /**
     * Gets the text of the current annotation, or null if the annotation has unknown text.
     * Do not call this method until after calling [next].
     */
    fun getText(): String?

    /** Resets the iterator to the beginning of the annotations. */
    fun restart()

    /**
     * Convenience method that loads all unvisited annotations into an array of strings.
     * Some implementations may be more efficient than the default implementation.
     */
    fun toStringArray(): Array<String?> {
        val list = ArrayList<String?>(4)
        while (hasNext()) {
            list.add(next())
        }
        return list.toTypedArray()
    }
}

interface IonSequenceVisitor {
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
    fun onSymbol(ann: AnnotationIterator?, sid: Int, text: () -> String): Instruction
    fun onString(ann: AnnotationIterator?, text: () -> String): Instruction
    fun onBlob(ann: AnnotationIterator?, bytes: ByteBuffer): Instruction
    fun onClob(ann: AnnotationIterator?, bytes: ByteBuffer): Instruction

    /**
     * Called when a null value is encountered.
     *
     * @param ann when there is at least one annotation on this value, an iterator over the annotation(s).
     *            When there are no annotation, `null`.
     * @param visit the Ion type of the null value.
     */
    fun onList(ann: AnnotationIterator?, content: (IonSequenceVisitor) -> Unit): Instruction
    fun onSexp(ann: AnnotationIterator?, content: (IonSequenceVisitor) -> Unit): Instruction
    fun onStruct(ann: AnnotationIterator?, content: (IonFieldVisitor) -> Unit): Instruction

    // TODO: Should there be type-specific end methods?
    fun onEnd()

    fun onMacro(
        macro: MacroV2,
        evaluate: (IonSequenceVisitor) -> Unit,
        visitArguments: (IonSequenceVisitor) -> Unit
    )
}

interface IonFieldVisitor {

}
