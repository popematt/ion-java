package com.amazon.ion.v3

internal interface PrivateAnnotationIterator: AnnotationIterator {
    /**
     * Loads the first annotation, leaving the position of the AnnotationIterator unchanged, so that
     * application code can call `next()` to get the first annotation.
     */
    fun peek()
}
