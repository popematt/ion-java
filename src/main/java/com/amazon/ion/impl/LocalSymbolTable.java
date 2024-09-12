// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl;

import static com.amazon.ion.SystemSymbols.IMPORTS;
import static com.amazon.ion.SystemSymbols.IMPORTS_SID;
import static com.amazon.ion.SystemSymbols.ION;
import static com.amazon.ion.SystemSymbols.ION_SYMBOL_TABLE;
import static com.amazon.ion.SystemSymbols.MAX_ID_SID;
import static com.amazon.ion.SystemSymbols.NAME_SID;
import static com.amazon.ion.SystemSymbols.SYMBOLS_SID;
import static com.amazon.ion.SystemSymbols.VERSION_SID;
import static com.amazon.ion.impl._Private_Utils.copyOf;
import static com.amazon.ion.impl._Private_Utils.getSidForSymbolTableField;
import static com.amazon.ion.impl._Private_Utils.safeEquals;

import com.amazon.ion.IonCatalog;
import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.ReadOnlyValueException;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.util.IonTextUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A local symbol table.
 * <p>
 * Instances of this class are safe for use by multiple threads.
 */
public class LocalSymbolTable
    implements _Private_LocalSymbolTable
{

    public static class Factory implements _Private_LocalSymbolTableFactory
    {

        private Factory(){} // Should be accessed through the singleton

        public SymbolTable newLocalSymtab(IonCatalog catalog,
                                          IonReader reader,
                                          boolean alreadyInStruct)
        {
            List<String> symbolsList = new ArrayList<String>();
            SymbolTable currentSymbolTable = reader.getSymbolTable();
            LocalSymbolTableImports imports = readLocalSymbolTable(reader,
                                                                   catalog,
                                                                   alreadyInStruct,
                                                                   symbolsList,
                                                                   currentSymbolTable);
            if (imports == null) {
                // This was an LST append, so the existing symbol table was updated.
                return currentSymbolTable;
            }
            return new LocalSymbolTable(imports, symbolsList);
        }

        public SymbolTable newLocalSymtab(SymbolTable defaultSystemSymtab,
                                          SymbolTable... imports)
        {
            LocalSymbolTableImports unifiedSymtabImports =
                new LocalSymbolTableImports(defaultSystemSymtab, imports);

            return new LocalSymbolTable(unifiedSymtabImports,
                                        null /* local symbols */);
        }

    }

    public static final Factory DEFAULT_LST_FACTORY = new Factory();

    /**
     * The system and shared symtabs imported by this symtab. Never null.
     * <p>
     * Note: this member field is immutable and assigned only during
     * construction, hence no synchronization is needed for its method calls.
     */
    private final LocalSymbolTableImports myImportsList;

    /**
     * Map of symbol names to symbol ids of local symbols that are not in
     * imports.
     */
    private final AddressMapImpl<String> mySymbolsMap;

    /**
     * Whether this symbol table is read only, and thus, immutable.
     */
    private boolean isReadOnly;

    /**
     * The local symbol names declared in this symtab; never null.
     * The sid of the first element is {@link #myFirstLocalSid}.
     * Only the first {@link #mySymbolsCount} elements are valid.
     */
    // String[] mySymbolNames;

    /**
     * This is the number of symbols defined in this symbol table
     * locally, that is not imported from some other table.
     */
    int mySymbolsCount;

    /**
     * The sid of the first local symbol, which is stored at
     * {@link #mySymbolNames}[0].
     */
    // final int myFirstLocalSid;

    //==========================================================================
    // Private constructor(s) and static factory methods
    //==========================================================================

    /**
     * @param imports           never null
     * @param symbolsList       may be null or empty
     */
    protected LocalSymbolTable(LocalSymbolTableImports imports, List<String> symbolsList)
    {
        if (symbolsList == null) {
            mySymbolsCount = 0;
        } else {
            mySymbolsCount = symbolsList.size();
        }

        myImportsList = imports;
        // myFirstLocalSid = myImportsList.getMaxId() + 1;

        // Copy locally declared symbols to mySymbolsMap
        // The initial size is chosen so that resizing is avoided. The default load factor is 0.75. Resizing
        // could also be avoided by setting the initial size to mySymbolsCount and setting the load factor to
        // 1.0, but this would lead to more hash collisions.
        // mySymbolsMap //= new HashMap<String, Integer>((int) Math.ceil(mySymbolsCount / 0.75));
        // buildSymbolsMap();

        Integer foo;

        mySymbolsMap = new AddressMapImpl<>();
        // Start with $0
        mySymbolsMap.assign(null);

        for (SymbolTable symbolTable : imports.getImportedTablesNoCopy()) {
            // TODO: Transitive imports?
            symbolTable.iterateDeclaredSymbolNames().forEachRemaining(mySymbolsMap::assign);
        }
        if (symbolsList != null) {
            symbolsList.forEach(mySymbolsMap::assign);
        }
    }

    /**
     * Copy-constructor, performs defensive copying of member fields where
     * necessary. The returned instance is mutable.
     */
    protected LocalSymbolTable(LocalSymbolTable other, int maxId)
    {
        isReadOnly      = false;
        myImportsList   = other.myImportsList;
        mySymbolsCount  = maxId - myImportsList.getMaxId();

        mySymbolsMap = new AddressMapImpl<>();
        // mySymbolsMap.assign(null);
        other.mySymbolsMap.iterator().forEachRemaining(mySymbolsMap::assign);
    }

    /**
     * Parses the symbol table at the reader's current position.
     * @param reader the reader from which to parse the symbol table.
     * @param catalog the catalog from which to resolve shared symbol table imports.
     * @param isOnStruct true if the reader is already positioned on the symbol table struct; otherwise, false.
     * @param symbolsListOut list into which local symbols declared by the parsed symbol table will be deposited.
     * @param currentSymbolTable the symbol table currently active in the stream.
     * @return a new LocalSymbolTableImports instance, or null if this was an LST append. If null, `currentSymbolTable`
     *   continues to be the active symbol table.
     */
    protected static LocalSymbolTableImports readLocalSymbolTable(IonReader reader,
                                                                  IonCatalog catalog,
                                                                  boolean isOnStruct,
                                                                  List<String> symbolsListOut,
                                                                  SymbolTable currentSymbolTable)
    {
        if (! isOnStruct)
        {
            reader.next();
        }

        assert reader.getType() == IonType.STRUCT
            : "invalid symbol table image passed in reader " +
              reader.getType() + " encountered when a struct was expected";

        assert ION_SYMBOL_TABLE.equals(reader.getTypeAnnotations()[0])
            : "local symbol tables must be annotated by " + ION_SYMBOL_TABLE;

        reader.stepIn();

        List<SymbolTable> importsList = new ArrayList<SymbolTable>();
        importsList.add(reader.getSymbolTable().getSystemSymbolTable());

        IonType fieldType;
        boolean foundImportList = false;
        boolean foundLocalSymbolList = false;
        boolean isAppend = false;
        while ((fieldType = reader.next()) != null)
        {
            if (reader.isNullValue()) continue;

            SymbolToken symTok = reader.getFieldNameSymbol();
            int sid = symTok.getSid();
            if (sid == SymbolTable.UNKNOWN_SYMBOL_ID)
            {
                // This is a user-defined IonReader or a pure DOM, fall
                // back to text
                final String fieldName = reader.getFieldName();
                sid = getSidForSymbolTableField(fieldName);
            }

            // TODO amazon-ion/ion-java/issues/36 Switching over SIDs doesn't cover the case
            //      where the relevant field names are defined by a prev LST;
            //      the prev LST could have 'symbols' defined locally with a
            //      different SID!
            switch (sid)
            {
                case SYMBOLS_SID:
                {
                    // As per the Spec, other field types are treated as
                    // empty lists
                    if(foundLocalSymbolList){
                        throw new IonException("Multiple symbol fields found within a single local symbol table.");
                    }
                    foundLocalSymbolList = true;
                    if (fieldType == IonType.LIST)
                    {
                        reader.stepIn();
                        IonType type;
                        while ((type = reader.next()) != null)
                        {
                            final String text;
                            if (type == IonType.STRING)
                            {
                                text = reader.stringValue();
                            }
                            else
                            {
                                text = null;
                            }

                            symbolsListOut.add(text);
                        }
                        reader.stepOut();
                    }
                    break;
                }
                case IMPORTS_SID:
                {
                    if(foundImportList){
                        throw new IonException("Multiple imports fields found within a single local symbol table.");
                    }
                    foundImportList = true;
                    if (fieldType == IonType.LIST)
                    {
                        prepImportsList(importsList, reader, catalog);
                    }
                    else if (fieldType == IonType.SYMBOL && ION_SYMBOL_TABLE.equals(reader.stringValue()))
                    {
                        isAppend = true;
                    }
                    break;
                }
                default:
                {
                    // As per the Spec, any other field is ignored
                    break;
                }
            }
        }
        reader.stepOut();
        if (isAppend && currentSymbolTable.isLocalTable()) {
            // Because the current symbol table is a local symbol table (i.e. not the system symbol table), it can
            // be appended in-place.
            LocalSymbolTable currentLocalSymbolTable = (LocalSymbolTable) currentSymbolTable;
            for (String newSymbol : symbolsListOut) {
                currentLocalSymbolTable.mySymbolsMap.assign(newSymbol);
            }
            return null;
        }
        return new LocalSymbolTableImports(importsList);
    }

    @Override
    public synchronized _Private_LocalSymbolTable makeCopy()
    {
        return new LocalSymbolTable(this, getMaxId());
    }

    synchronized LocalSymbolTable makeCopy(int maxId)
    {
        return new LocalSymbolTable(this, maxId);
    }

    public boolean isLocalTable()
    {
        return true;
    }

    public boolean isSharedTable()
    {
        return false;
    }

    public boolean isSystemTable()
    {
        return false;
    }

    public boolean isSubstitute()
    {
        return false;
    }

    public synchronized boolean isReadOnly()
    {
        return isReadOnly;
    }

    public synchronized void makeReadOnly()
    {
        isReadOnly = true;
    }

    public int getImportedMaxId()
    {
        return myImportsList.getMaxId();
    }

    public synchronized int getMaxId()
    {
        return mySymbolsMap.size() - 1;
    }

    public int getVersion()
    {
        return 0;
    }

    public String getName()
    {
        return null;
    }

    public String getIonVersionId()
    {
        SymbolTable system_table = myImportsList.getSystemSymbolTable();
        return system_table.getIonVersionId();
    }

    public synchronized Iterator<String> iterateDeclaredSymbolNames()
    {
        return mySymbolsMap.iterator(getImportedMaxId() + 1);
    }

    public String findKnownSymbol(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("symbol IDs must be >= 0");
        }

        return mySymbolsMap.get(id);
    }

    public int findSymbol(String name) {
        return mySymbolsMap.get(name);
    }

    public synchronized SymbolToken intern(String text)
    {
        SymbolToken is = find(text);
        if (is == null)
        {
            validateSymbol(text);
            int sid = putSymbol(text);
            is = new SymbolTokenImpl(text, sid);
        }
        return is;
    }

    public SymbolToken find(String text)
    {
        text.getClass(); // fast null check

        int sid;

        synchronized (this) {
            sid = mySymbolsMap.get(text);
        }

        if (sid != UNKNOWN_SYMBOL_ID) {
            // There are (questionable) tests that call `assetSame()` on the symbol texts.
            String name = mySymbolsMap.get(sid);
            return new SymbolTokenImpl(name, sid);
        } else {
            return null;
        }
    }

    private static final void validateSymbol(String name)
    {
        if (name == null)
        {
            throw new IllegalArgumentException("symbols must not be null");
        }
        for (int i = 0; i < name.length(); ++i)
        {
            int c = name.charAt(i);
            if (c >= 0xD800 && c <= 0xDFFF)
            {
                if (c >= 0xDC00)
                {
                    String message = "unpaired trailing surrogate in symbol " +
                            "name at position " + i;
                    throw new IllegalArgumentException(message);
                }
                ++i;
                if (i == name.length())
                {
                    String message = "unmatched leading surrogate in symbol " +
                            "name at position " + i;
                    throw new IllegalArgumentException(message);
                }
                c = name.charAt(i);
                if (c < 0xDC00 || c > 0xDFFF)
                {
                    String message = "unmatched leading surrogate in symbol " +
                            "name at position " + i;
                    throw new IllegalArgumentException(message);
                }
            }
        }
    }

    /**
     * NOT SYNCHRONIZED! Call within constructor or from synch'd method.
     */
    int putSymbol(String symbolName)
    {

        if (isReadOnly)
        {
            throw new ReadOnlyValueException(SymbolTable.class);
        }

        int sid = -1;
        if (symbolName != null)
        {
            sid = mySymbolsMap.assign(symbolName);
        }
        mySymbolsCount++;

        return sid;
    }

    public SymbolTable getSystemSymbolTable()
    {
        return myImportsList.getSystemSymbolTable();
    }

    public SymbolTable[] getImportedTables()
    {
        return myImportsList.getImportedTables();
    }

    @Override
    public SymbolTable[] getImportedTablesNoCopy()
    {
        return myImportsList.getImportedTablesNoCopy();
    }

    public void writeTo(IonWriter writer) throws IOException
    {
        IonReader reader = new SymbolTableReader(this);
        writer.writeValues(reader);
    }

    /**
     * Collects the necessary imports from the reader and catalog, and load
     * them into the passed-in {@code importsList}.
     */
    private static void prepImportsList(List<SymbolTable> importsList,
                                        IonReader reader,
                                        IonCatalog catalog)
    {
        assert IMPORTS.equals(reader.getFieldName());

        reader.stepIn();
        IonType t;
        while ((t = reader.next()) != null)
        {
            if (!reader.isNullValue() && t == IonType.STRUCT)
            {
                SymbolTable importedTable = readOneImport(reader, catalog);

                if (importedTable != null)
                {
                    importsList.add(importedTable);
                }
            }
        }
        reader.stepOut();
    }

    /**
     * Returns a {@link SymbolTable} representation of a single import
     * declaration from the passed-in reader and catalog.
     *
     * @return
     *          symbol table representation of the import; null if the import
     *          declaration is malformed
     */
    private static SymbolTable readOneImport(IonReader ionRep,
                                             IonCatalog catalog)
    {
        assert (ionRep.getType() == IonType.STRUCT);

        String name = null;
        int    version = -1;
        int    maxid = -1;

        ionRep.stepIn();
        IonType t;
        while ((t = ionRep.next()) != null)
        {
            if (ionRep.isNullValue()) continue;

            SymbolToken symTok = ionRep.getFieldNameSymbol();
            int field_id = symTok.getSid();
            if (field_id == UNKNOWN_SYMBOL_ID)
            {
                // this is a user defined reader or a pure DOM
                // we fall back to text here
                final String fieldName = ionRep.getFieldName();
                field_id = getSidForSymbolTableField(fieldName);
            }
            switch(field_id)
            {
                case NAME_SID:
                    if (t == IonType.STRING)
                    {
                        name = ionRep.stringValue();
                    }
                    break;
                case VERSION_SID:
                    if (t == IonType.INT)
                    {
                        version = ionRep.intValue();
                    }
                    break;
                case MAX_ID_SID:
                    if (t == IonType.INT)
                    {
                        maxid = ionRep.intValue();
                    }
                    break;
                default:
                    // we just ignore anything else as "open content"
                    break;
            }
        }
        ionRep.stepOut();

        // Ignore import clauses with malformed name field.
        if (name == null || name.length() == 0 || name.equals(ION))
        {
            return null;
        }

        if (version < 1)
        {
            version = 1;
        }

        SymbolTable itab = null;
        if (catalog != null)
        {
            itab = catalog.getTable(name, version);
        }
        if (maxid < 0)
        {
            if (itab == null || version != itab.getVersion())
            {
                String message =
                    "Import of shared table "
                        + IonTextUtils.printString(name)
                        + " lacks a valid max_id field, but an exact match was not"
                        + " found in the catalog";
                if (itab != null)
                {
                    message += " (found version " + itab.getVersion() + ")";
                }
                // TODO custom exception
                throw new IonException(message);
            }

            // Exact match is found, but max_id is undefined in import
            // declaration, set max_id to largest sid of shared symtab
            maxid = itab.getMaxId();
        }

        if (itab == null)
        {
            assert maxid >= 0;

            // Construct substitute table with max_id undefined symbols
            itab = new SubstituteSymbolTable(name, version, maxid);
        }
        else if (itab.getVersion() != version || itab.getMaxId() != maxid)
        {
            // A match was found BUT specs are not an exact match
            // Construct a substitute with correct specs, containing the
            // original import table that was found
            itab = new SubstituteSymbolTable(itab, version, maxid);
        }

        return itab;
    }

    /**
     * Generate the string representation of a symbol with an unknown id.
     * @param id must be a value greater than zero.
     * @return the symbol name, of the form <code>$NNN</code> where NNN is the
     * integer rendering of <code>id</code>
     */
    public static String unknownSymbolName(int id)
    {
        assert id > 0;
        return "$" + id;
    }

    @Override
    public String toString()
    {
        return "(LocalSymbolTable max_id:" + getMaxId() + ", " + mySymbolsMap + ')';
    }

    /**
     * This method, and the context from which it is called, assumes that the
     * symtabs are not being mutated by another thread.
     * Therefore it doesn't use synchronization.
     */
    boolean symtabExtends(SymbolTable other)
    {
        // Throws ClassCastException if other isn't a local symtab
        LocalSymbolTable subset = (LocalSymbolTable) other;

        // Gather snapshots of each LST's data, so we don't

        // Superset must have same/more known symbols than subset.
        if (getMaxId() < subset.getMaxId()) return false;

        // TODO amazon-ion/ion-java/issues/18 Currently, we check imports by their refs. which
        //      might be overly strict; imports which are not the same ref.
        //      but have the same semantic states fails the extension check.
        if (! myImportsList.equalImports(subset.myImportsList))
            return false;

        int subLocalSymbolCount = subset.mySymbolsCount;

        // Superset extends subset if subset doesn't have any declared symbols.
        if (subLocalSymbolCount == 0) return true;

        return mySymbolsMap.isExtensionOf(subset.mySymbolsMap);
    }

}
