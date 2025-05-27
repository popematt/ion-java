package com.amazon.ion.v3.ion_reader

import com.amazon.ion.*
import com.amazon.ion.impl.*

internal class ArrayBackedLstSnapshot(private val symbolText: Array<String?>): _Private_LocalSymbolTable {
    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun isLocalTable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isSharedTable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isSubstitute(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isSystemTable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isReadOnly(): Boolean {
        TODO("Not yet implemented")
    }

    override fun makeReadOnly() {
        TODO("Not yet implemented")
    }

    override fun getSystemSymbolTable(): SymbolTable {
        TODO("Not yet implemented")
    }

    override fun getIonVersionId(): String {
        TODO("Not yet implemented")
    }

    override fun getImportedTables(): Array<SymbolTable> {
        TODO("Not yet implemented")
    }

    override fun getImportedMaxId(): Int {
        TODO("Not yet implemented")
    }

    override fun getMaxId(): Int {
        TODO("Not yet implemented")
    }

    override fun intern(text: String?): SymbolToken {
        TODO("Not yet implemented")
    }

    override fun find(text: String?): SymbolToken {
        val sid = symbolText.indexOf(text)
        return _Private_Utils.newSymbolToken(text, sid)
    }

    override fun findSymbol(name: String?): Int {
        TODO("Not yet implemented")
    }

    override fun findKnownSymbol(id: Int): String {
        TODO("Not yet implemented")
    }

    override fun iterateDeclaredSymbolNames(): MutableIterator<String> {
        TODO("Not yet implemented")
    }

    override fun writeTo(writer: IonWriter?) {
        TODO("Not yet implemented")
    }

    override fun makeCopy(): _Private_LocalSymbolTable {
        TODO("Not yet implemented")
    }

    override fun getImportedTablesNoCopy(): Array<SymbolTable> {
        TODO("Not yet implemented")
    }
}
