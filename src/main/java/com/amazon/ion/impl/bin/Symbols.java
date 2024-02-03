/*
 * Copyright 2007-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.ion.impl.bin;

import static com.amazon.ion.SystemSymbols.IMPORTS;
import static com.amazon.ion.SystemSymbols.IMPORTS_SID;
import static com.amazon.ion.SystemSymbols.ION;
import static com.amazon.ion.SystemSymbols.ION_1_0;
import static com.amazon.ion.SystemSymbols.ION_1_0_MAX_ID;
import static com.amazon.ion.SystemSymbols.ION_1_0_SID;
import static com.amazon.ion.SystemSymbols.ION_SHARED_SYMBOL_TABLE;
import static com.amazon.ion.SystemSymbols.ION_SHARED_SYMBOL_TABLE_SID;
import static com.amazon.ion.SystemSymbols.ION_SID;
import static com.amazon.ion.SystemSymbols.ION_SYMBOL_TABLE;
import static com.amazon.ion.SystemSymbols.ION_SYMBOL_TABLE_SID;
import static com.amazon.ion.SystemSymbols.MAX_ID;
import static com.amazon.ion.SystemSymbols.MAX_ID_SID;
import static com.amazon.ion.SystemSymbols.NAME;
import static com.amazon.ion.SystemSymbols.NAME_SID;
import static com.amazon.ion.SystemSymbols.SYMBOLS;
import static com.amazon.ion.SystemSymbols.SYMBOLS_SID;
import static com.amazon.ion.SystemSymbols.VERSION;
import static com.amazon.ion.SystemSymbols.VERSION_SID;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import com.amazon.ion.SymbolTable;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.impl._Private_Ion_1_0_SystemSymbolTable;
import com.amazon.ion.impl._Private_Utils;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Utilities for dealing with {@link SymbolToken} and {@link SymbolTable}.
 */
 class Symbols
{
    private Symbols() {}

    /** Constructs a token with a non-null name and positive value. */
    public static SymbolToken symbol(final String name, final int val)
    {
        if (name == null) { throw new NullPointerException(); }
        if (val <= 0) { throw new IllegalArgumentException("Symbol value must be positive: " + val); }
        return _Private_Utils.newSymbolToken(name, val);
    }

    /** Lazy iterator over the symbol names of an iterator of symbol tokens. */
    public static Iterator<String> symbolNameIterator(final Iterator<SymbolToken> tokenIter)
    {
        return new Iterator<String>()
        {
            public boolean hasNext()
            {
                return tokenIter.hasNext();
            }

            public String next()
            {
                return tokenIter.next().getText();
            }

            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final List<SymbolToken> SYSTEM_TOKENS = unmodifiableList(
        asList(
            symbol(ION,                      ION_SID)
          , symbol(ION_1_0,                  ION_1_0_SID)
          , symbol(ION_SYMBOL_TABLE,         ION_SYMBOL_TABLE_SID)
          , symbol(NAME,                     NAME_SID)
          , symbol(VERSION,                  VERSION_SID)
          , symbol(IMPORTS,                  IMPORTS_SID)
          , symbol(SYMBOLS,                  SYMBOLS_SID)
          , symbol(MAX_ID,                   MAX_ID_SID)
          , symbol(ION_SHARED_SYMBOL_TABLE,  ION_SHARED_SYMBOL_TABLE_SID)
        )
    );

    /** Returns a symbol token for a system SID. */
    public static SymbolToken systemSymbol(final int sid) {
        if (sid < 1 || sid > ION_1_0_MAX_ID)
        {
            throw new IllegalArgumentException("No such system SID: " + sid);
        }
        return SYSTEM_TOKENS.get(sid - 1);
    }

    /** Returns a representation of the system symbol table. */
    public static SymbolTable systemSymbolTable()
    {
        return _Private_Ion_1_0_SystemSymbolTable.INSTANCE;
    }

    /** Returns the system symbols as a collection. */
    public static Collection<SymbolToken> systemSymbols()
    {
        return SYSTEM_TOKENS;
    }

    /** Returns a substitute shared symbol table where none of the symbols are known. */
    public static SymbolTable unknownSharedSymbolTable(final String name,
                                                       final int version,
                                                       final int maxId)
    {
        return new AbstractSymbolTable(name, version)
        {

            public Iterator<String> iterateDeclaredSymbolNames()
            {
                return new Iterator<String>()
                {
                    int id = 1;

                    public boolean hasNext()
                    {
                        return id <= maxId;
                    }

                    public String next()
                    {
                        if (!hasNext())
                        {
                            throw new NoSuchElementException();
                        }
                        // all symbols are unknown
                        id++;
                        return null;
                    }

                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            public boolean isSystemTable()
            {
                return false;
            }

            public boolean isSubstitute()
            {
                return true;
            }

            public boolean isSharedTable()
            {
                return true;
            }

            public boolean isReadOnly()
            {
                return true;
            }

            public boolean isLocalTable()
            {
                return false;
            }

            public SymbolToken intern(String text)
            {
                throw new UnsupportedOperationException(
                    "Cannot intern into substitute unknown shared symbol table: "
                    + name + " version " + version
                );
            }

            public SymbolTable getSystemSymbolTable()
            {
                return systemSymbolTable();
            }

            public int getMaxId()
            {
                return maxId;
            }

            public SymbolTable[] getImportedTables()
            {
                return null;
            }

            public int getImportedMaxId()
            {
                return 0;
            }

            public String findKnownSymbol(int id)
            {
                return null;
            }

            public SymbolToken find(String text)
            {
                return null;
            }
        };
    }
}
