package com.amazon.ion.v3

import com.amazon.ion.IonCatalog

/**
 * A class that can help by caching an Array of strings representing the symbols in a shared
 * symbol table.
 *
 * Since the V2 reader uses arrays for symbol lookup, this makes it a little cheaper to import
 * symbols into a symbol table.
 */
internal class IonCatalogCache(private val catalog: IonCatalog) {

    private val cache = HashMap<Pair<String, Int>, Array<String?>>()

    fun getSymbols(name: String, version: Int): Array<String?> {
        val cacheKey = name to version
        return cache.getOrPut(cacheKey) {
            val table = catalog.getTable(name, version)
            val iter = table.iterateDeclaredSymbolNames()
            val symbols = Array(table.maxId) { iter.next() }
            symbols
        }
    }
}
