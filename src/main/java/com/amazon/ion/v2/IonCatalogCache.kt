package com.amazon.ion.v2

import com.amazon.ion.IonCatalog

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
