package com.amazon.ion.v3.visitor2

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
