// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.SymbolTable.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl._Private_IonWriter.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.bin.LengthPrefixStrategy.*
import com.amazon.ion.impl.bin.SymbolInliningStrategy.*
import com.amazon.ion.impl.macro.*
import com.amazon.ion.system.*
import java.io.OutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

/**
 * A managed writer for Ion 1.1 that is generic over whether the raw encoding is text or binary.
 *
 * TODO:
 *  - Handling of shared symbol tables
 *  - Proper handling of user-supplied symbol tables
 *  - Auto-flush (for binary and text)
 *
 * TODO: Check that arguments match the signatures of macros/templates.
 *
 * See also [ManagedWriterOptions_1_1], [SymbolInliningStrategy], and [LengthPrefixStrategy].
 */
internal class IonManagedWriter_1_1(
    private val userData: IonRawWriter_1_1,
    private val systemData: _Private_IonRawWriter_1_1,
    private val options: ManagedWriterOptions_1_1,
    private val onClose: () -> Unit,
) : _Private_IonWriter, MacroV8AwareIonWriter {

    internal fun getRawUserWriter(): IonRawWriter_1_1 = userData

    companion object {
        private val ION_VERSION_MARKER_REGEX = Regex("^\\\$ion_\\d+_\\d+$")

        // These are chosen subjectively to be neither too big nor too small.
        private const val MAX_SYMBOLS_IN_SINGLE_LINE_SYMBOL_TABLE = 10
        private const val NUMBER_OF_SYSTEM_SIDS = 9

        private val SYSTEM_SYMBOLS = mapOf(
            "\$ion" to 1,
            "\$ion_1_0" to 2,
            "\$ion_symbol_table" to 3,
            "name" to 4,
            "version" to 5,
            "imports" to 6,
            "symbols" to 7,
            "max_id" to 8,
            "\$ion_shared_symbol_table" to 9,
        )

        @JvmStatic
        fun textWriter(output: OutputStream, managedWriterOptions: ManagedWriterOptions_1_1, textOptions: _Private_IonTextWriterBuilder_1_1): IonManagedWriter_1_1 {
            // TODO support all options configurable via IonTextWriterBuilder_1_1
            val appender = {
                val bufferedOutput = BufferedOutputStreamFastAppendable(output, BlockAllocatorProviders.basicProvider().vendAllocator(4096))
                _Private_IonTextAppender.forFastAppendable(bufferedOutput, Charsets.UTF_8)
            }

            return IonManagedWriter_1_1(
                userData = IonRawTextWriter_1_1(
                    options = textOptions,
                    output = appender(),
                ),
                systemData = IonRawTextWriter_1_1(
                    options = textOptions,
                    output = appender(),
                ),
                options = managedWriterOptions.copy(internEncodingDirectiveSymbols = false),
                onClose = output::close,
            )
        }

        @JvmStatic
        fun textWriter(output: Appendable, managedWriterOptions: ManagedWriterOptions_1_1, textOptions: _Private_IonTextWriterBuilder_1_1): IonManagedWriter_1_1 {
            val appender = {
                val bufferedOutput = BufferedAppendableFastAppendable(output)
                _Private_IonTextAppender.forFastAppendable(bufferedOutput, Charsets.UTF_8)
            }

            return IonManagedWriter_1_1(
                userData = IonRawTextWriter_1_1(
                    options = textOptions,
                    output = appender(),
                ),
                systemData = IonRawTextWriter_1_1(
                    options = textOptions,
                    output = appender(),
                ),
                options = managedWriterOptions.copy(internEncodingDirectiveSymbols = false),
                onClose = {},
            )
        }

        @JvmStatic
        fun binaryWriter(output: OutputStream, managedWriterOptions: ManagedWriterOptions_1_1, binaryOptions: _Private_IonBinaryWriterBuilder_1_1): IonManagedWriter_1_1 {
            // TODO: Add autoflush
            return IonManagedWriter_1_1(
                userData = IonRawBinaryWriter_1_1(
                    out = output,
                    buffer = WriteBuffer(BlockAllocatorProviders.basicProvider().vendAllocator(binaryOptions.blockSize)) {},
                    lengthPrefixPreallocation = 1
                ),
                systemData = IonRawBinaryWriter_1_1(
                    out = output,
                    buffer = WriteBuffer(BlockAllocatorProviders.basicProvider().vendAllocator(binaryOptions.blockSize)) {},
                    lengthPrefixPreallocation = 1
                ),
                options = managedWriterOptions.copy(internEncodingDirectiveSymbols = true),
                onClose = output::close,
            )
        }
    }

    // Since this is Ion 1.1, we must always start with the IVM.
    private var needsIVM: Boolean = true

    // We take a slightly different approach here by handling the encoding context as a prior encoding context
    // plus a list of symbols added by the current encoding context.
    /** The symbol table for the prior encoding context */
    private var symbolTable: HashMap<String, Int> = HashMap<String, Int>().also { it.putAll(SYSTEM_SYMBOLS) }

    /** Symbols to be interned since the prior encoding context. */
    private var newSymbols: HashMap<String, Int> = LinkedHashMap() // Preserves insertion order.

    /** The macro table of the prior encoding context. Map value is the user-space address. */
    private var macroTable: HashMap<MacroV8, Int> = LinkedHashMap()
    /** Macros to be added since the last encoding directive was flushed. Map value is the user-space address. */
    private var newMacros: HashMap<MacroV8, Int> = LinkedHashMap()
    /** Macro names by user-space address, including new macros. */
    private var macroNames = ArrayList<String?>()
    /** Macro definitions by user-space address, including new macros. */
    private var macrosById = ArrayList<MacroV8>()
    /** The first symbol ID in the current encoding context. */
    private var firstLocalSid: Int = 0

    private var containers = _Private_RecyclingStack(16) { ContainerInfo() }
    init {
        // Push a container to be at the top-level so we don't need to do null checks.
        containers.push { it.reset() }
    }

    // Stores info about the types of the child elements.
    private class ContainerInfo(
        @JvmField var signature: Array<MacroV8.Parameter> = EMPTY_ARRAY,
        @JvmField var type: Int = -1,
        /**
        // If i>=-1, type is an opcode
        // If i==-2, there is no type
        // If i==-3, type is a macro id
        */
        @JvmField var i: Int = 0,
    ) {

        companion object {
            @JvmStatic
            private val EMPTY_ARRAY = emptyArray<MacroV8.Parameter>()
        }

        val macroId: Int get() = -1 - type

        fun reset(signature: Array<MacroV8.Parameter>) {
            this.signature = signature
            i = 0
            prepareNextType()
        }

        fun resetWithMacroShape(macroId: Int) {
            this.signature = EMPTY_ARRAY
            this.type = -1 - macroId
            i = -3
        }

        fun reset(opcode: Int = 0) {
            this.signature = EMPTY_ARRAY
            this.type = opcode
            i = if (opcode == 0) -2 else -1
        }

        fun prepareNextType() {
            if (i >= 0) {
                if (i < signature.size) {
                    type = signature[i++].type
                } else {
                    type = Ops.DELIMITED_CONTAINER_END
                }
            }
        }
    }

    /**
     * Adds a new symbol to the table for this writer, or finds an existing definition of it. This writer does not
     * implement [IonWriter.getSymbolTable], so this method supplies some of that functionality.
     *
     * @return an SID for the given symbol text
     * @see SymbolTable.intern
     */
    fun intern(text: String): Int {
        // Check the current symbol table
        var sid = symbolTable[text]
        if (sid != null) return sid
        // Check the to-be-appended symbols
        sid = newSymbols[text]
        if (sid != null) return sid
        // Add to the to-be-appended symbols
        sid = symbolTable.size + newSymbols.size + 1
        newSymbols[text] = sid
        return sid
    }

    /**
     * Adds a named macro to the macro table
     *
     * Steps:
     * - If the name is not already in use...
     *    - And the macro is already in `newMacros`...
     *      1. Get the address of the macro in `newMacros`
     *      2. Add the name to `macroNames` for the that address
     *      3. return the address
     *    - Else...
     *      1. Add a new entry for the macro to `newMacros` and get a new address
     *      2. Add the name to `macroNames` for the new address
     *      3. Return the new address
     * - If the name is already in use...
     *   - And it is associated with the same macro...
     *      1. Return the address associated with the name
     *   - And it is associated with a different macro...
     *      - This is where the managed writer take an opinion. (Or be configurable.)
     *        - It could mangle the name
     *        - It could remove the name from a macro in macroTable, but then it would have to immediately flush to
     *          make sure that any prior e-expressions are still valid. In addition, we would need to re-export all
     *          the other macros from `_` (the default module).
     *        - For now, we're just throwing an Exception.
     *
     * Visible for testing.
     */
    internal fun getOrAssignMacroAddressAndName(name: String, macro: MacroV8): Int {
        // TODO: This is O(n), but could be O(1).
        var existingAddress = macroNames.indexOf(name)
        if (existingAddress < 0) {
            // Name is not already in use
            existingAddress = newMacros.getOrDefault(macro, -1)

            val address = if (existingAddress < 0) {
                // Macro is not in newMacros
                // Add to newMacros and get a macro address
                assignMacroAddress(macro)
            } else {
                // Macro already exists in newMacros, but doesn't have a name
                existingAddress
            }
            // Set the name of the macro
            macroNames[address] = name
            return address
        } else if (macrosById[existingAddress] == macro) {
            // Macro already in table, and already using the same name
            return existingAddress
        } else {
            // Name is already in use for a different macro.
            // This macro may or may not be in the table under a different name, but that's
            // not particularly relevant unless we want to try to fall back to a different name.
            TODO("Name shadowing is not supported yet. Call finish() before attempting to shadow an existing macro.")
        }
    }

    /**
     * Steps for adding an anonymous macro to the macro table
     *    1. Check macroTable, if found, return that address
     *    2. Check newMacros, if found, return that address
     *    3. Add to newMacros, return new address
     *
     *  Visible for testing
     */
    internal fun getOrAssignMacroAddress(macro: MacroV8): Int {
        var address = macroTable.getOrDefault(macro, -1)
        if (address >= 0) return address
        address = newMacros.getOrDefault(macro, -1)
        if (address >= 0) return address

        return assignMacroAddress(macro)
    }

    override fun startEncodingSegmentWithIonVersionMarker() {
        if (!newSymbols.isEmpty() || !newMacros.isEmpty()) {
            throw IonException("Cannot start a new encoding segment while the previous segment is active.")
        }
        needsIVM = false
        flush()
        systemData.writeIVM()
        resetEncodingContext()
    }

    override fun startEncodingSegmentWithEncodingDirective(
        macros: Map<MacroRef, MacroV8>,
        isMacroTableAppend: Boolean,
        symbols: List<String>,
        isSymbolTableAppend: Boolean,
        encodingDirectiveAlreadyWritten: Boolean
    ) {
        // It is assumed that the IVM is written manually when using endEncodingSegment.
        needsIVM = false
        // First, flush the previous segment. This method begins a new segment.
        flush()
        firstLocalSid = if (isSymbolTableAppend) {
            symbolTable.size
        } else {
            symbolTable.clear()
            symbolTable.putAll(SYSTEM_SYMBOLS)
            symbolTable.size
        }
        for (symbol in symbols) {
            intern(symbol)
        }
        if (!isMacroTableAppend) {
            macroNames.clear()
            macrosById.clear()
            macroTable.clear()
            newMacros.clear()
        }
        for ((macroRef, macro) in macros.entries) {
            if (macroRef.hasName()) {
                getOrAssignMacroAddressAndName(macroRef.name!!, macro)
            } else {
                getOrAssignMacroAddress(macro)
            }
        }
        if (encodingDirectiveAlreadyWritten) {
            // This prevents another encoding directive from being written for this context.
            symbolTable.putAll(newSymbols)
            newSymbols.clear()
            macroTable.putAll(newMacros)
            newMacros.clear()
        } else {
            writeEncodingDirective()
        }
    }

    /** Unconditionally adds a macro to the macro table data structures and returns the new address. */
    private fun assignMacroAddress(macro: MacroV8): Int {
        val address = macrosById.size
        macrosById.add(macro)
        macroNames.add(null)
        newMacros[macro] = address
        return address
    }

    // Only called by `finish()`
    private fun resetEncodingContext() {
        if (depth != 0) throw IllegalStateException("Cannot reset the encoding context while stepped in any value.")
        symbolTable.clear()
        macroNames.clear()
        macrosById.clear()
        macroTable.clear()
        newMacros.clear()

        needsIVM = true
        symbolTable.putAll(SYSTEM_SYMBOLS)
        firstLocalSid = NUMBER_OF_SYSTEM_SIDS
    }

    /**
     * Writes an encoding directive for the current encoding context, and updates internal state accordingly.
     * This always appends to the current encoding context. If there is nothing to append, calling this function
     * is a no-op.
     */
    private fun writeEncodingDirective() {
        if (newSymbols.isEmpty() && newMacros.isEmpty()) return

        writeSymbolTableDirective()
        symbolTable.putAll(newSymbols)
        newSymbols.clear()
        // NOTE: Once we have emitted the symbol table update with set/add_symbols those symbols become available
        //       for use in set/add_macros (if relevant)

        writeMacroTableDirective()
        macroTable.putAll(newMacros)
        newMacros.clear()
    }


    /**
     * Updates the symbols in the encoding context by invoking
     * the `add_symbols` or `set_symbols` system macro.
     * If the symbol table would be empty, writes nothing, which is equivalent
     * to an empty symbol table.
     */
    private fun writeSymbolTableDirective() {
        val hasSymbolsToAdd = newSymbols.isNotEmpty()
        val hasSymbolsToRetain = symbolTable.size > NUMBER_OF_SYSTEM_SIDS
        if (!hasSymbolsToAdd) return
        val directive = if (!hasSymbolsToRetain) Ops.SET_SYMBOLS else Ops.ADD_SYMBOLS

        // Add new symbols
        systemData.stepInDirective(directive)
        if (newSymbols.size <= MAX_SYMBOLS_IN_SINGLE_LINE_SYMBOL_TABLE) {
            systemData.forceNoNewlines(true)
        }
        newSymbols.forEach { (text, _) -> systemData.writeString(text) }
        systemData.stepOut()
        systemData.forceNoNewlines(false)
    }

    private fun writeMacroTableDirective() {
        val hasMacrosToAdd = newMacros.isNotEmpty()
        val hasMacrosToRetain = macroTable.isNotEmpty()
        if (!hasMacrosToAdd) return
        val directive = if (!hasMacrosToRetain) Ops.SET_MACROS else Ops.ADD_MACROS

        // Add new macros
        systemData.stepInDirective(directive)
        newMacros.forEach { (macro, id) ->
            val macroName = macroNames[id]
            systemData.stepInSExp(usingLengthPrefix = false)
            systemData.forceNoNewlines(true)
            if (macroName == null) {
                systemData.writeNull()
            } else {
                systemData.writeSymbol(macroName)
            }
            systemData.forceNoNewlines(false)
            macro.writeTo(systemData)
            systemData.stepOut()
        }
        systemData.stepOut()
    }




    override fun getCatalog(): IonCatalog {
        TODO("Not part of the public API.")
    }

    /** No facets supported */
    override fun <T : Any?> asFacet(facetType: Class<T>?): T? = null

    override fun getSymbolTable(): SymbolTable {
        TODO("Why do we need to expose this to users in the first place?")
    }

    override fun setFieldName(name: String) {
        handleSymbolToken(UNKNOWN_SYMBOL_ID, name, SymbolKind.FIELD_NAME, userData)
    }

    override fun setFieldNameSymbol(name: SymbolToken) {
        handleSymbolToken(name.sid, name.text, SymbolKind.FIELD_NAME, userData)
    }

    override fun addTypeAnnotation(annotation: String) {
        handleSymbolToken(UNKNOWN_SYMBOL_ID, annotation, SymbolKind.ANNOTATION, userData)
    }

    override fun setTypeAnnotations(annotations: Array<String>?) {
        // Interning happens in addTypeAnnotation
        userData._private_clearAnnotations()
        annotations?.forEach { addTypeAnnotation(it) }
    }

    override fun setTypeAnnotationSymbols(annotations: Array<SymbolToken>?) {
        userData._private_clearAnnotations()
        annotations?.forEach { handleSymbolToken(it.sid, it.text, SymbolKind.ANNOTATION, userData) }
    }

    override fun stepIn(containerType: IonType?) {
        val newDepth = depth + 1
        when (containerType) {
            IonType.LIST -> userData.stepInList(options.writeLengthPrefix(ContainerType.LIST, newDepth))
            IonType.SEXP -> userData.stepInSExp(options.writeLengthPrefix(ContainerType.SEXP, newDepth))
            IonType.STRUCT -> {
                if (depth == 0 && userData._private_hasFirstAnnotation(SystemSymbols_1_1.ION_SYMBOL_TABLE.id, SystemSymbols_1_1.ION_SYMBOL_TABLE.text)) {
                    throw IonException("User-defined symbol tables not permitted by the Ion 1.1 managed writer.")
                }
                userData.stepInStruct(options.writeLengthPrefix(ContainerType.STRUCT, newDepth))
            }
            else -> throw IllegalArgumentException("Not a container type: $containerType")
        }
        containers.push { it.reset() }
    }

    fun stepInTaglessElementList(macro: MacroV8) {
        val macroId = getOrAssignMacroAddress(macro)
        userData.stepInTaglessElementList(macroId, null)
        containers.push { it.resetWithMacroShape(macroId) }
    }
    fun stepInTaglessElementList(name: String, macro: MacroV8) {
        val macroId = getOrAssignMacroAddressAndName(name, macro)
        userData.stepInTaglessElementList(macroId, name)
        containers.push { it.resetWithMacroShape(macroId) }
    }
    fun stepInTaglessElementList(scalar: TaglessScalarType) {
        userData.stepInTaglessElementList(scalar.opcode)
        containers.push { it.reset(scalar.opcode) }
    }

    fun stepInTaglessElementSExp(macro: MacroV8) {
        val macroId = getOrAssignMacroAddress(macro)
        userData.stepInTaglessElementSExp(macroId, null)
        containers.push { it.resetWithMacroShape(macroId) }
    }
    fun stepInTaglessElementSExp(name: String, macro: MacroV8) {
        val macroId = getOrAssignMacroAddressAndName(name, macro)
        userData.stepInTaglessElementSExp(macroId, name)
        containers.push { it.resetWithMacroShape(macroId) }
    }
    fun stepInTaglessElementSExp(scalar: TaglessScalarType) {
        userData.stepInTaglessElementSExp(scalar.opcode)
        containers.push { it.reset(scalar.opcode) }
    }

    override fun stepOut() {
        // TODO: Make sure you can't use this function to step out of a macro?
        containers.pop()
        userData.stepOut()
    }

    override fun isInStruct(): Boolean = userData.isInStruct()

    private inline fun <T> T?.writeMaybeNull(type: IonType, writeNotNull: (T) -> Unit) {
        if (this == null) {
            writeNull(type)
        } else {
            writeNotNull(this)
        }
    }

    override fun writeSymbol(content: String?) {
        val container = containers.peek()
        if (container.type != 0) {
            content ?: throw IonException("Cannot write null.symbol for tagless symbol")
            when (container.type) {
                Ops.SYMBOL_VALUE_SID -> userData.writeTaglessSymbol(container.type, intern(content))
                Ops.VARIABLE_LENGTH_INLINE_SYMBOL -> userData.writeTaglessSymbol(container.type, content)
                else -> if (options.shouldWriteInline(SymbolKind.VALUE, content)) {
                    userData.writeTaglessSymbol(container.type, content)
                } else {
                    userData.writeTaglessSymbol(container.type, intern(content))
                }
            }
            container.prepareNextType()
        } else if (content == null) {
            userData.writeNull(IonType.SYMBOL)
        } else {
            handleSymbolToken(UNKNOWN_SYMBOL_ID, content, SymbolKind.VALUE, userData)
        }
    }

    override fun writeSymbolToken(content: SymbolToken?) {
        val container = containers.peek()
        if (container.type != 0) {
            content ?: throw IonException("Cannot write null.symbol for tagless symbol")
            val text = content.text
            if (text != null) {
                when (container.type) {
                    Ops.SYMBOL_VALUE_SID -> userData.writeTaglessSymbol(container.type, intern(text))
                    Ops.VARIABLE_LENGTH_INLINE_SYMBOL -> userData.writeTaglessSymbol(container.type, text)
                    else -> if (options.shouldWriteInline(SymbolKind.VALUE, text)) {
                        userData.writeTaglessSymbol(container.type, text)
                    } else {
                        userData.writeTaglessSymbol(container.type, intern(text))
                    }
                }
            } else {
                userData.writeTaglessSymbol(container.type, 0)
            }
            container.prepareNextType()
        } else if (content == null) {
            userData.writeNull(IonType.SYMBOL)
        } else {
            val text: String? = content.text
            // TODO: Check to see if the SID refers to a user symbol with text that looks like an IVM
            if (text == SystemSymbols_1_1.ION_1_0.text && depth == 0) throw IonException("Can't write a top-level symbol that is the same as the IVM.")
            handleSymbolToken(content.sid, content.text, SymbolKind.VALUE, userData)
        }
    }

    private inline fun IonRawWriter_1_1.write(kind: SymbolKind, sid: Int) = when (kind) {
        SymbolKind.VALUE -> writeSymbol(sid)
        SymbolKind.FIELD_NAME -> writeFieldName(sid)
        SymbolKind.ANNOTATION -> writeAnnotations(sid)
    }

    private inline fun IonRawWriter_1_1.write(kind: SymbolKind, text: String) = when (kind) {
        SymbolKind.VALUE -> writeSymbol(text)
        SymbolKind.FIELD_NAME -> writeFieldName(text)
        SymbolKind.ANNOTATION -> writeAnnotations(text)
    }

    /** Helper function that determines whether to write a symbol token as a SID or inline symbol */
    private inline fun handleSymbolToken(sid: Int, text: String?, kind: SymbolKind, rawWriter: IonRawWriter_1_1, preserveEncoding: Boolean = false) {
        if (text == null) {
            // No text. Decide whether to write $0 or some other SID
            if (sid == UNKNOWN_SYMBOL_ID) {
                // No (known) SID either.
                throw UnknownSymbolException("Cannot write a symbol token with unknown text and unknown SID.")
            } else {
                rawWriter.write(kind, sid)
            }
        } else if (preserveEncoding && sid < 0) {
            rawWriter.write(kind, text)
        } else if (options.shouldWriteInline(kind, text)) {
            rawWriter.write(kind, text)
        } else {
            rawWriter.write(kind, intern(text))
        }
    }

    override fun writeNull() = userData.writeNull()
    override fun writeNull(type: IonType?) = userData.writeNull(type ?: IonType.NULL)
    override fun writeBool(value: Boolean) = userData.writeBool(value)
    override fun writeInt(value: Long) {
        val container = containers.peek()
        if (container.type != 0) {
            userData.writeTaglessInt(container.type, value)
            container.prepareNextType()
        } else {
            userData.writeInt(value)
        }
    }

    // TODO: Tagless int support for BigIntegers
    override fun writeInt(value: BigInteger?) = value.writeMaybeNull(IonType.INT, userData::writeInt)

    override fun writeFloat(value: Double) {
        val container = containers.peek()
        if (container.type != 0) {
            userData.writeTaglessFloat(container.type, value)
            container.prepareNextType()
        } else {
            userData.writeFloat(value)
        }
    }
    override fun writeDecimal(value: BigDecimal?) = value.writeMaybeNull(IonType.DECIMAL, userData::writeDecimal)
    override fun writeTimestamp(value: Timestamp?) = value.writeMaybeNull(IonType.TIMESTAMP, userData::writeTimestamp)
    override fun writeString(value: String?) {
        val container = containers.peek()
        if (container.type != 0) {
            value ?: throw IonException("Cannot write null.string for tagless string")
            userData.writeTaglessString(container.type, value)
            container.prepareNextType()
        } else {
            value.writeMaybeNull(IonType.STRING, userData::writeString)
        }
    }

    override fun writeClob(value: ByteArray?) = value.writeMaybeNull(IonType.CLOB, userData::writeClob)
    override fun writeClob(value: ByteArray?, start: Int, len: Int) = value.writeMaybeNull(IonType.CLOB) { userData.writeClob(it, start, len) }

    override fun writeBlob(value: ByteArray?) = value.writeMaybeNull(IonType.BLOB, userData::writeBlob)
    override fun writeBlob(value: ByteArray?, start: Int, len: Int) = value.writeMaybeNull(IonType.BLOB) { userData.writeBlob(it, start, len) }

    override fun isFieldNameSet(): Boolean {
        return userData._private_hasFieldName()
    }

    override fun getDepth(): Int {
        return userData.depth()
    }

    override fun writeIonVersionMarker() {
        if (depth == 0) {
            // Make sure we write out any symbol tables and buffered values before the IVM
            finish()
        } else {
            writeSymbol("\$ion_1_1")
        }
    }

    @Deprecated("Use IonValue.writeTo(IonWriter) instead.")
    override fun writeValue(value: IonValue) = value.writeTo(this)

    @Deprecated("Use writeTimestamp instead.")
    override fun writeTimestampUTC(value: Date?) {
        TODO("Use writeTimestamp instead.")
    }

    override fun isStreamCopyOptimized(): Boolean = false

    override fun writeValues(reader: IonReader, symbolIdTransformer: IntTransformer) {
        writeValues(reader)
    }

    override fun writeValues(reader: IonReader) {
        // There's a possibility that we could have interference between encoding contexts if we're transferring from a
        // system reader. However, this is the same behavior as the other implementations.

        val startingDepth = reader.depth
        while (true) {
            val nextType = reader.next()
            if (nextType == null) {
                // Nothing more *and* we're at the starting depth? We're all done.
                if (reader.depth == startingDepth) return
                // Otherwise, step out and continue.
                userData.stepOut()
                reader.stepOut()
            } else {
                transferScalarOrStepIn(reader, nextType)
            }
        }
    }

    override fun writeValue(reader: IonReader, symbolIdTransformer: IntTransformer) {
        writeValue(reader)
    }

    override fun writeValue(reader: IonReader) {
        // There's a possibility that we could have interference between encoding contexts if we're transferring from a
        // system reader. However, this is the same behavior as the other implementations.

        if (reader.type == null) return
        val startingDepth = reader.depth
        transferScalarOrStepIn(reader, reader.type)
        if (reader.depth != startingDepth) {
            // We stepped into a container, so write the content of the container and then step out.
            writeValues(reader)
            reader.stepOut()
            userData.stepOut()
        }
    }

    override fun writeObject(obj: WriteAsIon) {
        obj.writeToMacroAware(this)
    }

    /**
     * Can only be called when the reader is positioned on a value. Having [currentType] in the
     * function signature helps to enforce that requirement because [currentType] is not allowed
     * to be `null`.
     */
    private fun transferScalarOrStepIn(reader: IonReader, currentType: IonType) {
        // TODO: If the Ion 1.1 symbol table differs at all from the Ion 1.0 symbol table, and we're copying
        //       from Ion 1.0, we will have to adjust any SIDs that we are writing.

        reader.typeAnnotationSymbols.forEach {
            if (it.text == SystemSymbols_1_1.ION_SYMBOL_TABLE.text) {
                userData.writeAnnotations(SystemSymbols_1_1.ION_SYMBOL_TABLE.text)
            } else {
                handleSymbolToken(it.sid, it.text, SymbolKind.ANNOTATION, userData, preserveEncoding = true)
            }
        }
        if (isInStruct) {
            // TODO: Can't use reader.fieldId, reader.fieldName because it will throw UnknownSymbolException.
            //       However, this might mean we're unnecessarily constructing `SymbolToken` instances.
            val fieldName = reader.fieldNameSymbol
            // If there is no field name, it still may have been set externally, e.g.
            // writer.setFieldName(...); writer.writeValue(reader);
            // This occurs when serializing a sequence of Expressions, which hold field names separate from
            // values.
            if (fieldName != null) {
                handleSymbolToken(fieldName.sid, fieldName.text, SymbolKind.FIELD_NAME, userData, preserveEncoding = true)
            }
        }

        if (reader.isNullValue) {
            userData.writeNull(currentType)
        } else when (currentType) {
            IonType.BOOL -> userData.writeBool(reader.booleanValue())
            IonType.INT -> {
                if (reader.integerSize == IntegerSize.BIG_INTEGER) {
                    userData.writeInt(reader.bigIntegerValue())
                } else {
                    userData.writeInt(reader.longValue())
                }
            }
            IonType.FLOAT -> userData.writeFloat(reader.doubleValue())
            IonType.DECIMAL -> userData.writeDecimal(reader.decimalValue())
            IonType.TIMESTAMP -> userData.writeTimestamp(reader.timestampValue())
            IonType.SYMBOL -> {
                if (reader.isCurrentValueAnIvm()) {
                    // TODO: What about the case where it's an IVM, but the writer is not at depth==0? Should we write
                    //       it as a symbol or just ignore it? (This can only happen if the writer is stepped in, but
                    //       the reader starts at depth==0.)

                    // Just in case—call finish to flush the current system values, then user values, and then write the IVM.
                    finish()
                } else {
                    val symbol = reader.symbolValue()
                    handleSymbolToken(symbol.sid, symbol.text, SymbolKind.VALUE, userData, preserveEncoding = true)
                }
            }
            IonType.STRING -> userData.writeString(reader.stringValue())
            IonType.CLOB -> userData.writeClob(reader.newBytes())
            IonType.BLOB -> userData.writeBlob(reader.newBytes())
            // TODO: See if we can preserve the encoding of containers (delimited vs length-prefixed)
            IonType.LIST -> {
                userData.stepInList(options.writeLengthPrefix(ContainerType.LIST, reader.depth))
                reader.stepIn()
            }
            IonType.SEXP -> {
                userData.stepInSExp(options.writeLengthPrefix(ContainerType.SEXP, reader.depth))
                reader.stepIn()
            }
            IonType.STRUCT -> {
                userData.stepInStruct(options.writeLengthPrefix(ContainerType.STRUCT, reader.depth))
                reader.stepIn()
            }
            else -> TODO("NULL and DATAGRAM are unreachable.")
        }
    }

    private fun IonReader.isCurrentValueAnIvm(): Boolean {
        if (depth != 0 || type != IonType.SYMBOL || typeAnnotationSymbols.isNotEmpty()) return false
        val symbol = symbolValue() ?: return false
        if (symbol.text == null) {
            // TODO FIX: Ion 1.1 system symbols can be removed from the encoding context, so an IVM may not always
            //  have symbol ID 2.
            return symbol.sid == 2
        }
        return ION_VERSION_MARKER_REGEX.matches(symbol.assumeText())
    }

    // Stream termination

    override fun close() {
        flush()
        systemData.close()
        userData.close()
        onClose()
    }

    override fun flush() {
        if (needsIVM) {
            systemData.writeIVM()
            needsIVM = false
        }
        writeEncodingDirective()
        systemData.flush()
        userData.flush()
    }

    override fun finish() {
        flush()
        resetEncodingContext()
    }

    override fun startMacro(macro: MacroV8) {
        val address = getOrAssignMacroAddress(macro)
        // Note: macroNames[address] will be null if the macro is unnamed.
        startMacro(macroNames[address], address, macro)
    }

    override fun startMacro(name: String, macro: MacroV8) {
        val address = getOrAssignMacroAddressAndName(name, macro)
        startMacro(name, address, macro)
    }

    private fun startMacro(name: String?, address: Int, definition: MacroV8) {
        val container = containers.peek()
        val prescribedMacroType = container.macroId
        if (prescribedMacroType < 0) {
            val useNames =
                options.eExpressionIdentifierStrategy == ManagedWriterOptions_1_1.EExpressionIdentifierStrategy.BY_NAME
            if (useNames && name != null) {
                userData.stepInEExp(name)
            } else {
                val includeLengthPrefix = false // options.writeLengthPrefix(ContainerType.EEXP, depth + 1)
                userData.stepInEExp(address, includeLengthPrefix)
            }
        } else {
            userData.stepInTaglessEExp(prescribedMacroType, name)
            container.prepareNextType()
        }
        containers.push { it.reset(definition.signature) }
    }

    override fun endMacro() {
        // TODO: See if there are unwritten parameters, and attempt to write `absent arg` for all of them.
        userData.stepOut()
        containers.pop()
    }

    override fun absentArgument() {
        val container = containers.peek()
        if (container.type != 0) throw IonException("The argument corresponding to a tagless placeholder cannot be absent.")
        userData.writeAbsentArgument()
        container.prepareNextType()
    }
}
