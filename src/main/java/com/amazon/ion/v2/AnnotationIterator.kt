package com.amazon.ion.v2

interface AnnotationIterator: Iterator<Unit>, AutoCloseable {
    fun getSid(): Int
    fun getText(): String?

    fun toStringArray(): Array<String?> {
        val strings = ArrayList<String?>(4)
        while (hasNext()) {
            next()
            strings.add(getText())
        }
        return strings.toTypedArray()
    }

    fun clone(): AnnotationIterator
}
