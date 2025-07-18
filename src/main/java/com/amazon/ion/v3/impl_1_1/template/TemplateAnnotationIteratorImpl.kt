package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.PrivateAnnotationIterator

internal class TemplateAnnotationIteratorImpl(
    @JvmField
    var annotations: Array<String?>,
    @JvmField
    val pool: TemplateResourcePool,
): AnnotationIterator, PrivateAnnotationIterator {
    @JvmField
    var i: Int = 0

    @JvmField
    var currentAnnotationText: String? = null

    override fun clone(): AnnotationIterator {
        return pool.getAnnotations(annotations)
    }

    override fun hasNext(): Boolean = i < annotations.size
    override fun next(): String? {
        if (!hasNext()) throw NoSuchElementException()
        currentAnnotationText = annotations[i++]
        return currentAnnotationText
    }

    // The text should have been resolved when the macro was compiled. Even if there is a SID,
    // it doesn't necessarily correspond to the current symbol table.
    override fun getSid(): Int = -1
    override fun getText(): String? = currentAnnotationText

    override fun close() {
//        if (this in pool.annotations) throw IllegalStateException("Already closed: $this")
        pool.returnAnnotations(this)
    }

    fun init(annotations: Array<String?>) {
        this.annotations = annotations
        i = 0
    }

    override fun peek() { currentAnnotationText = annotations[0] }

    // TODO: Make a defensive copy?
    override fun toStringArray(): Array<String?> = annotations
}
