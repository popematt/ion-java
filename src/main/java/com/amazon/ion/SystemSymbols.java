// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion;

/**
 * Constants for symbols defined by the Ion specification.
 *
 */
public final class SystemSymbols
{
    /** No touchy! */
    private SystemSymbols() { }


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     */
    public static final String ION = "$ion";

    /**
     * The ID of system symbol {@value #ION}, as defined by Ion 1.0.
     */
    public static final int    ION_SID = 1;


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     * This value is the Version Identifier for Ion 1.0.
     */
    public static final String ION_1_0 = "$ion_1_0";

    /**
     * The ID of system symbol {@value #ION_1_0}, as defined by Ion 1.0.
     */
    public static final int    ION_1_0_SID = 2;


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     */
    public static final String ION_SYMBOL_TABLE = "$ion_symbol_table";

    /**
     * The ID of system symbol {@value #ION_SYMBOL_TABLE}, as defined by Ion 1.0.
     */
    public static final int    ION_SYMBOL_TABLE_SID = 3;


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     */
    public static final String NAME = "name";

    /**
     * The ID of system symbol {@value #NAME}, as defined by Ion 1.0.
     */
    public static final int    NAME_SID = 4;


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     */
    public static final String VERSION = "version";

    /**
     * The ID of system symbol {@value #VERSION}, as defined by Ion 1.0.
     */
    public static final int    VERSION_SID = 5;


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     */
    public static final String IMPORTS = "imports";

    /**
     * The ID of system symbol {@value #IMPORTS}, as defined by Ion 1.0.
     */
    public static final int    IMPORTS_SID = 6;


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     */
    public static final String SYMBOLS = "symbols";

    /**
     * The ID of system symbol {@value #SYMBOLS}, as defined by Ion 1.0.
     */
    public static final int    SYMBOLS_SID = 7;


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     */
    public static final String MAX_ID = "max_id";

    /**
     * The annotation that denotes an Ion encoding directive in Ion 1.1+.
     */
    public static final String ION_ENCODING = "$ion_encoding";

    /**
     * The name of the symbol table s-expression within an Ion encoding directive.
     */
    public static final String SYMBOL_TABLE = "symbol_table";

    /**
     * The name of the macro table s-expression within an Ion encoding directive.
     */
    public static final String MACRO_TABLE = "macro_table";

    /**
     * The ID of system symbol {@value #MAX_ID}, as defined by Ion 1.0.
     */
    public static final int    MAX_ID_SID = 8;


    /**
     * The text of system symbol {@value}, as defined by Ion 1.0.
     */
    public static final String ION_SHARED_SYMBOL_TABLE =
        "$ion_shared_symbol_table";

    /**
     * The ID of system symbol {@value #ION_SHARED_SYMBOL_TABLE},
     * as defined by Ion 1.0.
     */
    public static final int    ION_SHARED_SYMBOL_TABLE_SID = 9;

    /**
     * The maximum ID of the IDs of system symbols defined by Ion 1.0.
     */
    public static final int    ION_1_0_MAX_ID = 9;
}
