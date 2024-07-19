package com.amazon.ion.impl.macro

import java.util.*
import kotlin.collections.ArrayList

internal class Pool<T>(initialCapacity: Int = 64, private val constructor: () -> T) {
    private val free = ArrayList<T>(initialCapacity)
    private val used = Collections.newSetFromMap(IdentityHashMap<T, Boolean>(initialCapacity))

    fun fetch(init: T.() -> Unit): T {
        val t = if (free.isEmpty()) {
            constructor()
        } else {
            free.removeLast()
        }
        used.add(t)
        return t.apply(init)
    }

    fun release(t: T) {
        if (used.remove(t)) {
            free.add(t)
        }
    }
}

