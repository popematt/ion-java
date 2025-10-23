// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.bytecode.bin10
//
// import com.amazon.ion.IonException
// import com.amazon.ion.SystemSymbols
// import com.amazon.ion.bytecode.ir.Instructions
// import com.amazon.ion.bytecode.ir.Instructions.packInstructionData
// import com.amazon.ion.bytecode.ir.OperationKind
// import com.amazon.ion.bytecode.util.AppendableConstantPoolView
// import com.amazon.ion.bytecode.util.BytecodeBuffer
//
// internal class SymbolTableHelper {
//
//    /**
//     * The Bytecode uses Ion 1.1 style directives, which don't quite align with Ion 1.0 directives.
//     * So, we have two options:
//     *  1. We can output the bytecode that does the equivalent stuff
//     *  2. We can add a "classic" symbol table directive to the bytecode
//     *
//     * For option 1, we could:
//     *  - if there's no LST append, and no imports, then we can generate a SET_SYMBOLS instruction
//     *  - if there's LST append, and no imports, then we can generate an ADD_SYMBOLS instruction
//     *  - if there are imports, there cannot also be LST append.
//     *  - if there are imports, and it's not LST append, we can generate an empty SET_SYMBOLS, followed by a USE for all the imports, followed by ADD_SYMBOLS with the local symbols.
//     *
//     * This means we need to buffer some data, but that's okay. We can put the strings into the constant pool, and keep
//     * track of the min and max, since there will be nothing else that we would put in the constant pool while we're processing
//     * a symbol table.
//     *
//     * It's actually beneficial to put the strings in the constant pool now. They can be added to the symbol table from
//     * the constant pool comparatively cheaply, and we can decode the strings eagerly without having as much overhead
//     * from the control flow of calling `readTextReference()` over and over.
//     *
//     * Logic is roughly this:
//     *
//     * ```pseudocode
//     * let symbolsStartInclusive = sizeOf(constantPool)
//     * let isAppend = false
//     * while hasMoreFields():
//     *   let fieldName = readFieldName()
//     *   switch(fieldName):
//     *     "imports":
//     *       let valueType = readValueType()
//     *       switch(valueType):
//     *         symbol:
//     *           readAndValidate "$ion_symbol_table"
//     *           isAppend = true
//     *         list:
//     *           bytecode.add2(SET_SYMBOLS, END_CONTAINER)
//     *           bytecode.add(USE)
//     *           while hasMoreListElements():
//     *             compileCatalogName()
//     *             compileCatalogVersion()
//     *           bytecode.add(END_CONTAINER)
//     *           isAppend = true
//     *         else:
//     *           throw IonException
//     *     "symbols":
//     *       for value in list:
//     *         if value is symbol:
//     *           constantPool.add(readText())
//     *         else:
//     *           constantPool.add(null)
//     * if (isAppend):
//     *   bytecode.add(ADD_SYMBOLS)
//     * else:
//     *   bytecode.add(SET_SYMBOLS)
//     *
//     * let symbolsEndExclusive = sizeOf(constantPool)
//     * for i in symbolsStartInclusive..symbolsEndExclusive:
//     *   bytecode.add(SYMBOL_CP(i))
//     * bytecode.add(END_CONTAINER)
//     * ```
//     */
//    fun readSymbolTableStruct(position: Int, length: Int, source: ByteArray, dest: BytecodeBuffer, cp: AppendableConstantPoolView) {
//        val symbolsCpIndexStartInclusive = cp.size
//
//        var isAppend = false
//
//        var p = position
//        val end = p + length
//
//        while (p < end) {
//            val fieldSidValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//            val fieldSid = (fieldSidValueAndLength ushr 8).toInt()
//            p += fieldSidValueAndLength.toInt() and 0xFF
//
//            var typeId = (source.get(p++).toInt() and 0xFF)
//            var operationKind = TypeIdHelper.operationKindForTypeId(typeId)
//
//            // We ignore annotations inside a symbol table.
//            if (operationKind == OperationKind.ANNOTATIONS) {
//                if (typeId and 0xF == 0xE) {
//                    val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//                    // Skip over the outer annotation length.
//                    p += valueAndLength.toInt() and 0xFF
//                }
//                val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//                // Skip over the inner annotation length and the annotations.
//                p += (valueAndLength.toInt() and 0xFF) + (valueAndLength shr 8).toInt()
//                // Read the next typeId
//                typeId = (source[p++].toInt() and 0xFF)
//                operationKind = TypeIdHelper.operationKindForTypeId(typeId)
//            }
//
//            when (fieldSid) {
//                SystemSymbols.IMPORTS_SID -> {
//                    isAppend = true
//                    when (operationKind) {
//                        OperationKind.SYMBOL -> {
//                            val symbolLength = typeId and 0xF
//                            val sid = when (symbolLength) {
//                                0 -> 0
//                                1 -> source.get(p).toInt() and 0xFF
//                                2 -> source.getShort(p).toInt() and 0xFFFF
//                                3 -> source.getInt(p - 1) and 0xFFFFFF
//                                4 -> source.getInt(p)
//                                else -> throw IonException("SID out of supported range")
//                            }
//                            p += symbolLength
//                            if (sid != SystemSymbols.ION_SYMBOL_TABLE_SID) {
//                                isAppend = false
//                                // TODO: Do we ignore this or throw IonException?
//                            }
//                        }
//                        OperationKind.LIST -> p += readImportsList(typeId, p, source, dest, cp)
//                        else -> {
//                            // TODO: do we ignore this or throw an exception?
//                        }
//                    }
//                }
//                SystemSymbols.SYMBOLS_SID -> {
//                    p = readSymbolsList(typeId, source, p, cp)
//                }
//                else -> {
//                    // Do nothing.
//                }
//            }
//        }
//
//        val symbolsCpIndexEndExclusive = cp.size
//        val directiveOperation = if (isAppend) Instructions.I_DIRECTIVE_ADD_SYMBOLS else Instructions.I_DIRECTIVE_SET_SYMBOLS
//        dest.add(directiveOperation)
//        for (i in symbolsCpIndexStartInclusive..<symbolsCpIndexEndExclusive) {
//            dest.add(Instructions.I_SYMBOL_CP.packInstructionData(i))
//        }
//        dest.add(Instructions.I_END_CONTAINER)
//    }
//
//    /** Returns the number of bytes consumed */
//    private fun readImportsList(listTypeId: Int, position: Int, source: ByteArray, dest: BytecodeBuffer, cp: AppendableConstantPoolView): Int {
//        fun readImport(typeId: Int, position: Int): Int {
//            var p = position
//            when (val operationKind = TypeIdHelper.operationKindForTypeId(typeId)) {
//                OperationKind.UNSET -> TODO("Skip a NOP in the symbol table")
//                // Null symbols for anything that's not a symbol.
//                OperationKind.ANNOTATIONS -> {
//                    // Skip the annotations and do nothing with them, but don't skip the annotated value.
//                    if (typeId and 0xF == 0xE) {
//                        p += VarIntHelper.readVarUIntValueAndLength(source, p).toInt() and 0xFF
//                    }
//                    val result = VarIntHelper.readVarUIntValueAndLength(source, p)
//                    p += (result.toInt() and 0xFF) + (result shr 8).toInt()
//                }
//                OperationKind.NULL -> cp.add(null)
//                OperationKind.BOOL,
//                OperationKind.INT,
//                OperationKind.FLOAT,
//                OperationKind.DECIMAL,
//                OperationKind.TIMESTAMP,
//                OperationKind.STRING,
//                OperationKind.SYMBOL,
//                OperationKind.CLOB,
//                OperationKind.BLOB,
//                OperationKind.LIST,
//                OperationKind.SEXP -> {
//                    // Skip past the value.
//                    if (typeId and 0xF == 0xE) {
//                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//                        p += (valueAndLength.toInt() and 0xFF) + (valueAndLength shr 8).toInt()
//                    } else  {
//                        p += typeId and 0xF
//                    }
//                    // TODO: Should we actually throw an exception?
//                }
//                OperationKind.STRUCT -> p += readImportStruct(typeId, p, source, dest, cp)
//                else -> TODO(OperationKind.nameOf(operationKind))
//            }
//            return p - position
//        }
//
//        // Clear default module symbols and start adding the imports in a USE directive
//        dest.add3(Instructions.I_DIRECTIVE_SET_SYMBOLS, Instructions.I_END_CONTAINER, Instructions.I_DIRECTIVE_USE)
//        val bytesConsumed = iterateList(listTypeId, position, source, ::readImport)
//        // Close the USE directive
//        dest.add(Instructions.I_END_CONTAINER)
//        return bytesConsumed
//    }
//
//    /** Returns the number of bytes consumed */
//    private fun readImportStruct(structTypeId: Int, position: Int, source: ByteArray, dest: BytecodeBuffer, cp: AppendableConstantPoolView): Int {
//
//        var catalogName: String? = null
//        var catalogVersion: Int = 1
//        var maxId: Int = -1
//
//
//        // TODO: Iterate through fields and read the values
//
//        val lengthOfContainer = -1
//
//
//
//        // No name, empty name, or $ion, so we ignore the import clause
//        if (catalogName == null || catalogName == "\$ion" || catalogName == "") return lengthOfContainer
//        val cpIndex = cp.add(catalogName)
//        dest.add(Instructions.I_STRING_CP.packInstructionData(cpIndex))
//        if (catalogVersion < 1) {
//            dest.add(Instructions.I_INT_I16.packInstructionData(1))
//        } else {
//            dest.add2(Instructions.I_INT_I32, catalogVersion)
//        }
//        if (maxId < 0) {
//            dest.add(Instructions.I_NULL_NULL)
//        } else {
//            dest.add2(Instructions.I_INT_I32, maxId)
//        }
//
//        return lengthOfContainer
//    }
//
//    /** Returns the position of the first byte after the symbols list */
//    private fun readSymbolsList(listTypeId: Int, source: ByteArray, position: Int, cp: AppendableConstantPoolView): Int {
//        fun readSymbol(typeId: Int, position: Int): Int {
//            val operationKind = TypeIdHelper.operationKindForTypeId(typeId)
//            var p = position
//            when (operationKind) {
//                OperationKind.UNSET -> TODO("Skip a NOP in the symbol table")
//                // Null symbols for anything that's not a symbol.
//                OperationKind.NULL -> cp.add(null)
//                OperationKind.BOOL,
//                OperationKind.INT,
//                OperationKind.FLOAT,
//                OperationKind.DECIMAL,
//                OperationKind.TIMESTAMP,
//                OperationKind.SYMBOL,
//                OperationKind.CLOB,
//                OperationKind.BLOB,
//                OperationKind.LIST,
//                OperationKind.SEXP,
//                OperationKind.STRUCT -> {
//                    // Skip past the value.
//                    if (typeId and 0xF == 0xE) {
//                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//                        p += (valueAndLength.toInt() and 0xFF) + (valueAndLength shr 8).toInt()
//                    } else  {
//                        p += typeId and 0xF
//                    }
//                    cp.add(null)
//                }
//                OperationKind.STRING -> {
//                    val lengthOfValue = if (typeId and 0xF == 0xE) {
//                        val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//                        p += valueAndLength.toInt() and 0xFF
//                        (valueAndLength shr 8).toInt()
//                    } else {
//                        typeId and 0xF
//                    }
//                    val s = String(source, p, lengthOfValue, Charsets.UTF_8)
//                    cp.add(s)
//                    p += lengthOfValue
//                }
//                OperationKind.ANNOTATIONS -> {
//                    // Skip the annotations and do nothing with them.
//                    if (typeId and 0xF == 0xE) {
//                        p += VarIntHelper.readVarUIntValueAndLength(source, p).toInt() and 0xFF
//                    }
//                    val result = VarIntHelper.readVarUIntValueAndLength(source, p)
//                    p += (result.toInt() and 0xFF) + (result shr 8).toInt()
//                }
//                else -> TODO(OperationKind.nameOf(operationKind))
//            }
//            return p
//        }
//
//        return iterateList(listTypeId, position, source, ::readSymbol)
//    }
//
//    /**
//     * Gets the length for the given TypeId, reading a VarUInt length if needed.
//     * Returns -1 if there is not enough data available to read the full VarUInt length.
//     *
//     * @throws IonException if the typeId is not a legal typeId in Ion 1.0
//     */
//    private fun getLengthForTypeId(typeId: Int, source: ByteArray, position: Int): Long {
//        return when (val l = TypeIdHelper.TYPE_LENGTHS[typeId]) {
//            -1 -> VarIntHelper.readVarUIntValueAndLength(source, position)
//            -2 -> throw IonException("Invalid Type ID: $typeId")
//            else -> l.toLong()
//        }
//    }
//
//    private fun ByteArray.getShort(position: Int): Short {
//        return ((this[position + 1].toInt() and 0xFF) or ((this[position].toInt() and 0xFF) shl 8)).toShort()
//    }
//
//    private fun ByteArray.getInt(position: Int): Int {
//        return (this[position + 3].toInt() and 0xFF) or
//                ((this[position + 2].toInt() and 0xFF) shl 8) or
//                ((this[position + 1].toInt() and 0xFF) shl 16) or
//                ((this[position].toInt() and 0xFF) shl 24)
//    }
//
//    // ==== Helpers for navigating through containers ====
//
//    private inline fun iterateStruct(structTypeId: Int, position: Int, source: ByteArray, fieldHandler: (Int, Int, Int) -> Int): Int {
//        var p = position
//        val lengthOfContainer = if (structTypeId and 0xF == 0xE) {
//            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//            p += valueAndLength.toInt() and 0xFF
//            (valueAndLength shr 8).toInt()
//        } else {
//            structTypeId and 0xF
//        }
//        val end = p + lengthOfContainer
//        while (p < end) {
//            val fieldSidValueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//            val fieldSid = (fieldSidValueAndLength ushr 8).toInt()
//            p += fieldSidValueAndLength.toInt() and 0xFF
//
//            val typeId = (source.get(p++).toInt() and 0xFF)
//            p += fieldHandler(typeId, p, fieldSid)
//        }
//        return p - position
//    }
//
//    /**
//     * Returns number of bytes consumed.
//     */
//    private fun iterateList(listTypeId: Int, position: Int, source: ByteArray, valueHandler: (Int, Int) -> Int): Int {
//        var p = position
//        val lengthOfContainer = if (listTypeId and 0xF == 0xE) {
//            val valueAndLength = VarIntHelper.readVarUIntValueAndLength(source, p)
//            p += valueAndLength.toInt() and 0xFF
//            (valueAndLength shr 8).toInt()
//        } else {
//            listTypeId and 0xF
//        }
//        val end = p + lengthOfContainer
//        while (p < end) {
//            val typeId = (source.get(p++).toInt() and 0xFF)
//            p += valueHandler(typeId, p)
//        }
//        return p - position
//    }
// }
