package com.amazon.ion.v3

interface AnnotationIterator: Iterator<Unit>, AutoCloseable {
    fun getSid(): Int
    fun getText(): String?

    /**
     * Convenience method that loads all annotations into an array of strings.
     */
    fun toStringArray(): Array<String?>

    fun clone(): AnnotationIterator
}
