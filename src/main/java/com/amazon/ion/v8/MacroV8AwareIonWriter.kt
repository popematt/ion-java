// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.macro.*

/**
 * Extension of the IonWriter interface that supports writing macros.
 *
 * TODO: Consider exposing this as a Facet.
 *
 * TODO: See if we can have some sort of safe reference to a macro.
 */
interface MacroV8AwareIonWriter : IonWriter {

    /**
     * Starts a new encoding segment with an Ion version marker, flushing
     * the previous segment (if any) and resetting the encoding context.
     */
    fun startEncodingSegmentWithIonVersionMarker()

    /**
     * Starts a new encoding segment with an encoding directive, flushing
     * the previous segment (if any).
     * @param macros the macros added in the new segment.
     * @param isMacroTableAppend true if the macros from the previous segment
     *  are to remain available.
     * @param symbols the symbols added in the new segment.
     * @param isSymbolTableAppend true if the macros from the previous
     *  segment are to remain available.
     * @param encodingDirectiveAlreadyWritten true if the encoding directive
     *  that begins the new segment has already been written to this writer.
     *  If false, the writer will write an encoding directive consistent
     *  with the arguments provided to this method, using verbose
     *  s-expression syntax.
     */
    fun startEncodingSegmentWithEncodingDirective(
        macros: Map<MacroRef, MacroV8>,
        isMacroTableAppend: Boolean,
        symbols: List<String>,
        isSymbolTableAppend: Boolean,
        encodingDirectiveAlreadyWritten: Boolean
    )

    /**
     * Starts writing a macro invocation, adding it to the macro table, if needed.
     */
    fun startMacro(macro: MacroV8)

    /**
     * Starts writing a macro invocation, adding it to the macro table, if needed.
     */
    fun startMacro(name: String, macro: MacroV8)

    /**
     * Ends and steps out of the current macro invocation.
     */
    fun endMacro()

    fun absentArgument()

    fun stepInTaglessElementList(macro: MacroV8)
    fun stepInTaglessElementList(name: String, macro: MacroV8)
    fun stepInTaglessElementList(scalar: TaglessScalarType)

    fun stepInTaglessElementSExp(macro: MacroV8)
    fun stepInTaglessElementSExp(name: String, macro: MacroV8)
    fun stepInTaglessElementSExp(scalar: TaglessScalarType)
}
