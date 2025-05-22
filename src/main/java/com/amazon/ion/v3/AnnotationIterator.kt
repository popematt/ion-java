package com.amazon.ion.v3

interface AnnotationIterator: Iterator<Unit>, AutoCloseable {
    // TODO: Should this have `nextToken` instead of implementing `Iterator` so that it is consistent
    //       with the other interfaces?

    /**
     * Reads the next annotation in this sequence of annotations. Once this method has been called,
     * call [getSid] and/or [getText] to get the annotation value.
     */
    override fun next(): Unit

    /**
     * Gets the SID of the current annotation, or -1 if the annotation has no SID.
     */
    fun getSid(): Int

    /**
     * Gets the text of the current annotation, or null if the annotation has unknown text.
     */
    fun getText(): String?

    /**
     * Convenience method that loads all annotations into an array of strings.
     */
    fun toStringArray(): Array<String?>

    // TODO: Can we get rid of this?
    fun clone(): AnnotationIterator
}
