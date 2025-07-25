package com.amazon.ion.v3.ion_reader

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.v3.*

internal class Annotations : Iterator<String?> {

    companion object {
        private inline fun <reified T> Array<T?>.grow(): Array<T?> {
            val newSize = this.size * 2
            val newArray = arrayOfNulls<T>(newSize)
            this.copyInto(newArray)
            return newArray
        }

        private fun IntArray.grow(): IntArray {
            val newSize = this.size * 2
            val newArray = IntArray(newSize)
            this.copyInto(newArray)
            return newArray
        }
    }

    var annotations: Array<String?> = arrayOfNulls(8)
    var annotationsSids = IntArray(8) { -1 }
    var annotationsSize = 0

    fun storeAnnotations(annotationsIterator: AnnotationIterator) {
        var annotationCount = 0
        while (annotationsIterator.hasNext()) {
            if (annotationCount >= annotations.size) {
                annotations = annotations.grow()
                annotationsSids = annotationsSids.grow()
            }
            annotationsIterator.next()
            val sid = annotationsIterator.getSid()
            annotationsSids[annotationCount] = sid
            annotations[annotationCount] = annotationsIterator.getText().also {
                if (it == null) {
                    TODO()
                }
            }
            annotationCount++
        }
        annotationsSize = annotationCount
    }

    fun getTypeAnnotations(): Array<String?> = annotations.copyOf(annotationsSize)

    fun getTypeAnnotationSymbols(): Array<SymbolToken> {
        return Array(annotationsSize) { i ->
            _Private_Utils.newSymbolToken(annotations[i], annotationsSids[i]) as SymbolToken
        }
    }

    fun iterateTypeAnnotations(): Iterator<String?> {
        i = 0
        return this
    }

    var i = 0

    override fun hasNext(): Boolean = i < annotationsSize

    override fun next(): String? {
        if (!hasNext()) throw NoSuchElementException()
        return annotations[i++]
    }
}
