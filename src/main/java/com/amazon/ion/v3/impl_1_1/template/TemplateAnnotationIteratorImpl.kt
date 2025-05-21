package com.amazon.ion.v3.impl_1_1.template

import com.amazon.ion.SymbolToken
import com.amazon.ion.v3.AnnotationIterator
import com.amazon.ion.v3.PrivateAnnotationIterator

internal class TemplateAnnotationIteratorImpl(
    @JvmField
    var annotations: List<SymbolToken>,
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
        currentAnnotationText = annotations[i++].text
        return currentAnnotationText
    }

    // The text should have been resolved when the macro was compiled. Even if there is a SID,
    // it doesn't necessarily correspond to the current symbol table.
    override fun getSid(): Int = -1
    override fun getText(): String? = currentAnnotationText

    override fun close() {
//        if (this in pool.annotations) throw IllegalStateException("Already closed: $this")
        pool.annotations.add(this)
    }

    fun init(annotations: List<SymbolToken>) {
        this.annotations = annotations
        i = 0
    }

    override fun peek() { currentAnnotationText = annotations[0].text }

    override fun toStringArray(): Array<String?> = Array(annotations.size) { next() }
}
