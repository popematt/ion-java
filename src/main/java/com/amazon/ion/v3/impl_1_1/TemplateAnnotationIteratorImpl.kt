package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.v3.*
import com.amazon.ion.v3.PrivateAnnotationIterator
import java.nio.ByteBuffer

internal class TemplateAnnotationIteratorImpl(
    var annotations: List<SymbolToken>,
    val pool: TemplateResourcePool,
): AnnotationIterator, PrivateAnnotationIterator {
    var i: Int = 0
    lateinit var currentAnnotation: SymbolToken

    override fun clone(): AnnotationIterator {
        return pool.getAnnotations(annotations)
    }

    override fun hasNext(): Boolean = i < annotations.size
    override fun next() {
        if (!hasNext()) throw NoSuchElementException()
        currentAnnotation = annotations[i++]
    }

    override fun getSid(): Int = currentAnnotation.sid
    override fun getText(): String? = currentAnnotation.text

    override fun close() {
        pool.annotations.add(this)
    }

    fun init(annotations: List<SymbolToken>) {
        this.annotations = annotations
        i = 0
    }

    override fun peek() { currentAnnotation = annotations[0] }

    override fun toStringArray(): Array<String?> = Array(annotations.size) { next(); getText() }
}
