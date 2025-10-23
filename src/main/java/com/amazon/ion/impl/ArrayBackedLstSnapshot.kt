// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl

import com.amazon.ion.IonWriter
import com.amazon.ion.SymbolTable
import com.amazon.ion.SymbolToken
import com.amazon.ion.bytecode.util.StringPool

internal class ArrayBackedLstSnapshot private constructor(private val symbolText: Array<String?>) : _Private_LocalSymbolTable {

    constructor(symbols: StringPool) : this(symbols.toArray())

    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun isLocalTable(): Boolean = false

    override fun isSharedTable(): Boolean = false

    override fun isSubstitute(): Boolean = false

    override fun isSystemTable(): Boolean = false

    override fun isReadOnly(): Boolean = true

    override fun makeReadOnly() {
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
        return symbolText.indexOf(name)
    }

    override fun findKnownSymbol(id: Int): String? {
        return symbolText[id]
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
