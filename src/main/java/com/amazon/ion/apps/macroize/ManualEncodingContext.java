// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.apps.macroize;

import com.amazon.ion.impl.IonRawWriter_1_1;
import com.amazon.ion.impl.SystemSymbols_1_1;
import com.amazon.ion.impl.macro.ExpressionA;
import com.amazon.ion.impl.macro.ExpressionKind;
import com.amazon.ion.impl.macro.Macro;
import com.amazon.ion.impl.macro.SystemMacro;
import com.amazon.ion.impl.macro.TemplateMacro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An encoding context that is manipulated manually. To be used alongside an IonRawWriter_1_1.
 * TODO consider whether this class may be replaced by something similar from the core library.
 */
class ManualEncodingContext {
    private final Map<String, Integer> symbolToId = new HashMap<>();
    private final Map<String, Integer> macroNameToId = new HashMap<>();
    private final Map<String, TemplateMacro> macroNameToMacro = new HashMap<>();

    int symbolMaxId = 0;
    int macroMaxId = -1;

    public ManualEncodingContext() {
        // Intern the Ion 1.1 special symbols that aren't in the system symbol table.
        // TODO these should be written inline instead of added to the symbol table.
        internSymbol("%");
        internSymbol("?");
    }

    /**
     * Adds the given macro to the macro table.
     * @param macroName the name of the macro.
     * @param macro the macro.
     */
    public void addMacro(String macroName, TemplateMacro macro) {
        macroNameToId.put(macroName, ++macroMaxId);
        macroNameToMacro.put(macroName, macro);
        // Intern the symbols that will occur in the macro signature and template body.
        internSymbol(macroName);
        for (ExpressionA expression : macro.getBody()) {
            if (expression.getKind() == ExpressionKind.FieldName) {
                internSymbol(expression.dataAsString$ion_java());
            }
        }
        for (Macro.Parameter parameter : macro.getSignature()) {
            internSymbol(parameter.getVariableName());
        }
    }

    /**
     * Gets the mapping to the given symbol in the symbol table, or creates a mapping if none yet exists.
     * @param symbol the symbol to intern.
     * @return the symbol ID.
     */
    public int internSymbol(String symbol) {
        return symbolToId.computeIfAbsent(symbol, k -> ++symbolMaxId);
    }

    /**
     * @param symbol a symbol.
     * @return true if the symbol already has a mapping in the symbol table.
     */
    public boolean hasSymbol(String symbol) {
        return symbolToId.get(symbol) != null;
    }

    /**
     * @param macroName the name of a macro.
     * @return the ID of the given macro in the macro table, if present.
     */
    public int getMacroId(String macroName) {
        return macroNameToId.get(macroName);
    }

    /**
     * @param macroName the name of a macro.
     * @return the macro, if present in the macro table.
     */
    public TemplateMacro getMacro(String macroName) {
        return macroNameToMacro.get(macroName);
    }

    /**
     * Writes the encoding context to the given writer. It is assumed that the symbols in the symbol table are used
     * to encode the macro table, so the symbol table is written first in its own encoding directive, followed by
     * the macro table.
     * @param writer the writer.
     */
    public void writeTo(IonRawWriter_1_1 writer) {
        // write the symbol table
        writer.stepInEExp(SystemMacro.SetSymbols);
        writer.stepInExpressionGroup(false);
        List<Map.Entry<String, Integer>> symbols = new ArrayList<>(symbolToId.entrySet());
        symbols.sort(Map.Entry.comparingByValue());
        symbols.forEach(e -> writer.writeString(e.getKey()));
        writer.stepOut();
        writer.stepOut();

        // write the macro table
        if (macroNameToId.isEmpty()) {
            return;
        }
        writer.stepInEExp(SystemMacro.SetMacros);
        writer.stepInExpressionGroup(false);
        List<Map.Entry<String, Integer>> macros = new ArrayList<>(macroNameToId.entrySet());
        macros.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, Integer> macroAndId : macros) {
            TemplateMacro macro = macroNameToMacro.get(macroAndId.getKey());
            writeMacroTo(writer, macroAndId.getKey(), macro);
        }
        writer.stepOut();
        writer.stepOut();
    }

    /**
     * Writes the given macro.
     * @param writer the writer.
     * @param name the name of the macro to write.
     * @param macro the macro to write.
     */
    private void writeMacroTo(IonRawWriter_1_1 writer, String name, TemplateMacro macro) {
        writeMacroTo(writer, name, macro, symbol -> writer.writeSymbol(internSymbol(symbol)), symbol -> writer.writeFieldName(internSymbol(symbol)));
    }

    /**
     * Writes the given macro.
     * @param writer the writer.
     * @param name the name of the macro to write.
     * @param macro the macro to write.
     * @param symbolWriter function that writes a symbol value.
     * @param fieldNameWriter function that writes a field name.
     */
    private static void writeMacroTo(IonRawWriter_1_1 writer, String name, TemplateMacro macro, Consumer<String> symbolWriter, Consumer<String> fieldNameWriter) {
        writer.stepInSExp(false);
        writer.writeSymbol(SystemSymbols_1_1.MACRO);
        symbolWriter.accept(name);
        writer.stepInSExp(false);
        List<Macro.Parameter> signature = macro.getSignature();
        for (Macro.Parameter parameter : signature) {
            symbolWriter.accept(parameter.getVariableName());
            if (parameter.getCardinality() != Macro.ParameterCardinality.ExactlyOne) {
                symbolWriter.accept("?");
            }
        }
        writer.stepOut();
        List<ExpressionA> body = macro.getBody();
        int index = 0;
        int[] numberOfTimesToStepOut = new int[body.size() + 1];
        Arrays.fill(numberOfTimesToStepOut, 0);
        for (ExpressionA expression : body) {
            for (int i = 0; i < numberOfTimesToStepOut[index]; i++) {
                writer.stepOut();
            }
            if (expression.getKind() == ExpressionKind.ExpressionGroup) {
                // Note: assumes that template bodies are composed of either structs or system macro invocations. Will
                // need to be generalized to fit other use cases as necessary.
                writer.stepInSExp(true);
                symbolWriter.accept(".");
                writer.writeAnnotations(SystemSymbols_1_1.ION);
                writer.writeSymbol(SystemSymbols_1_1.MAKE_STRING);
                writer.stepInSExp(true);
                symbolWriter.accept("..");
                numberOfTimesToStepOut[expression.getEndExclusive()]++;
            } else if (expression.getKind() == ExpressionKind.FieldName) {
                fieldNameWriter.accept(expression.dataAsString$ion_java());
            } else if (expression.getKind() == ExpressionKind.VariableRef) {
                writer.stepInSExp(true);
                symbolWriter.accept("%");
                symbolWriter.accept(signature.get(expression.dataAsInt$ion_java()).getVariableName());
                writer.stepOut();
            } else if (expression.getKind().isTextValue()) {
                writer.writeString(expression.dataAsString$ion_java());
            } else if (expression.getKind() == ExpressionKind.ListValue) {
                writer.stepInList(true);
                numberOfTimesToStepOut[expression.getEndExclusive()]++;
            } else if (expression.getKind() == ExpressionKind.StructValue) {
                writer.stepInStruct(true);
                numberOfTimesToStepOut[expression.getEndExclusive()]++;
            } else if (expression.getKind() == ExpressionKind.BoolValue) {
                writer.writeBool((boolean) expression.value);
            } else {
                throw new UnsupportedOperationException("TODO: unsupported expression type");
            }
            index++;
        }
        for (int i = 0; i < numberOfTimesToStepOut[body.size()]; i++) {
            writer.stepOut();
        }
        writer.stepOut();
    }
}
