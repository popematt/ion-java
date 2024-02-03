package com.amazon.ion.impl;

import com.amazon.ion.IonException;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.impl.bin.AbstractSymbolTable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static com.amazon.ion.SystemSymbols.*;
import static com.amazon.ion.SystemSymbols.ION_SHARED_SYMBOL_TABLE_SID;

public final class _Private_Ion_1_0_SystemSymbolTable extends AbstractSymbolTable {

    private _Private_Ion_1_0_SystemSymbolTable() {
        super(ION, 1);
    }

    public static final _Private_Ion_1_0_SystemSymbolTable INSTANCE = new _Private_Ion_1_0_SystemSymbolTable();

    /** Constructs a token with a non-null name and positive value. */
    private static SymbolTokenImpl symbol(final String name, final int val) {
        if (name == null) { throw new NullPointerException(); }
        if (val <= 0) { throw new IllegalArgumentException("Symbol value must be positive: " + val); }
        return _Private_Utils.newSymbolToken(name, val);
    }

    private static final SymbolTokenImpl ION_TOKEN = symbol(ION, ION_SID);
    private static final SymbolTokenImpl ION_1_0_TOKEN = symbol(ION_1_0, ION_1_0_SID);
    private static final SymbolTokenImpl ION_SYMBOL_TABLE_TOKEN = symbol(ION_SYMBOL_TABLE, ION_SYMBOL_TABLE_SID);
    private static final SymbolTokenImpl NAME_TOKEN = symbol(NAME, NAME_SID);
    private static final SymbolTokenImpl VERSION_TOKEN = symbol(VERSION, VERSION_SID);
    private static final SymbolTokenImpl IMPORTS_TOKEN = symbol(IMPORTS, IMPORTS_SID);
    private static final SymbolTokenImpl SYMBOLS_TOKEN = symbol(SYMBOLS, SYMBOLS_SID);
    private static final SymbolTokenImpl MAX_ID_TOKEN = symbol(MAX_ID, MAX_ID_SID);
    private static final SymbolTokenImpl ION_SHARED_SYMBOL_TABLE_TOKEN = symbol(ION_SHARED_SYMBOL_TABLE, ION_SHARED_SYMBOL_TABLE_SID);

    private static final SymbolTokenImpl[] SYSTEM_TOKENS_ARRAY = {
            null, // No Symbol token for 0
            ION_TOKEN,
            ION_1_0_TOKEN,
            ION_SYMBOL_TABLE_TOKEN,
            NAME_TOKEN,
            VERSION_TOKEN,
            IMPORTS_TOKEN,
            SYMBOLS_TOKEN,
            MAX_ID_TOKEN,
            ION_SHARED_SYMBOL_TABLE_TOKEN
    };


    private static final String[] SYSTEM_TEXT_ARRAY = {
            null,
            ION,
            ION_1_0,
            ION_SYMBOL_TABLE,
            NAME,
            VERSION,
            IMPORTS,
            SYMBOLS,
            MAX_ID,
            ION_SHARED_SYMBOL_TABLE
    };

    private static final int ION_HASHCODE = ION.hashCode();
    private static final int ION_1_0_HASHCODE = ION_1_0.hashCode();
    private static final int ION_SYMBOL_TABLE_HASHCODE = ION_SYMBOL_TABLE.hashCode();
    private static final int NAME_HASHCODE = NAME.hashCode();
    private static final int VERSION_HASHCODE = VERSION.hashCode();
    private static final int IMPORTS_HASHCODE = IMPORTS.hashCode();
    private static final int SYMBOLS_HASHCODE = SYMBOLS.hashCode();
    private static final int MAX_ID_HASHCODE = MAX_ID.hashCode();
    private static final int ION_SHARED_SYMBOL_TABLE_HASHCODE = ION_SHARED_SYMBOL_TABLE.hashCode();


    public SymbolTable[] getImportedTables() { return null; }
    public int getImportedMaxId() { return 0; }
    public boolean isSystemTable() { return true; }
    public boolean isSubstitute() { return false; }
    public boolean isSharedTable() { return true; }
    public boolean isReadOnly() { return true; }
    public boolean isLocalTable() { return false; }

    public SymbolToken intern(final String text) {
        SymbolToken symbol = find(text);
        if (symbol == null) {
            throw new IonException("Cannot intern new symbol into system symbol table");
        }
        return symbol;
    }

    public String findKnownSymbol(final int id) {
        switch (id) {
            case 0: throw new IllegalArgumentException("SID cannot be less than 1: " + id);
            case ION_SID: return ION;
            case ION_1_0_SID: return ION_1_0;
            case ION_SYMBOL_TABLE_SID: return ION_SYMBOL_TABLE;
            case NAME_SID: return NAME;
            case VERSION_SID: return VERSION;
            case IMPORTS_SID: return IMPORTS;
            case SYMBOLS_SID: return SYMBOLS;
            case MAX_ID_SID: return MAX_ID;
            case ION_SHARED_SYMBOL_TABLE_SID: return ION_SHARED_SYMBOL_TABLE;
            default: return null;
        }
    }

    public SymbolToken find(String text) {

        // Check all symbol hashes without branching!
        int hash = text.hashCode();
        long result = (long) (hash - ION_HASHCODE) *
                (hash - ION_1_0_HASHCODE) *
                (hash - ION_SYMBOL_TABLE_HASHCODE) *
                (hash - NAME_HASHCODE) *
                (hash - VERSION_HASHCODE) *
                (hash - IMPORTS_HASHCODE) *
                (hash - SYMBOLS_HASHCODE) *
                (hash - MAX_ID_HASHCODE) *
                (hash - ION_SHARED_SYMBOL_TABLE_HASHCODE);
        // No hash collisions
        if (result != 0) return null;

        switch (text) {
            case ION: return ION_TOKEN;
            case ION_1_0: return ION_1_0_TOKEN;
            case ION_SYMBOL_TABLE: return ION_SYMBOL_TABLE_TOKEN;
            case NAME: return NAME_TOKEN;
            case VERSION: return VERSION_TOKEN;
            case IMPORTS: return IMPORTS_TOKEN;
            case SYMBOLS: return SYMBOLS_TOKEN;
            case MAX_ID: return MAX_ID_TOKEN;
            case ION_SHARED_SYMBOL_TABLE: return ION_SHARED_SYMBOL_TABLE_TOKEN;
            default: return null;
        }
    }

    public SymbolTable getSystemSymbolTable() { return this; }

    public int getMaxId() { return ION_1_0_MAX_ID; }

    public Iterator<String> iterateDeclaredSymbolNames() {
        return new Ion_1_1_SystemSymbolIterator();
    }


    private static class Ion_1_1_SystemSymbolIterator implements Iterator<String> {
        private int i = 0;

        public boolean hasNext() {
            return i < 9;
        }

        public String next() {
            if (!hasNext()) throw new NoSuchElementException();
            return SYSTEM_TOKENS_ARRAY[++i].getText();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
