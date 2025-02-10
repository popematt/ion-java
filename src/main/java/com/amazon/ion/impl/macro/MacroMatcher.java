// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl.macro;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonReaderBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Matches source data to macro definitions.
 * TODO not supported yet: nested invocations
 */
public class MacroMatcher {

    private final TemplateMacro macro;
    private final String name;

    /**
     * Creates a matcher for the given TDL text.
     * @param macroText the TDL text that defines a single macro.
     * @param macroTable the macro table's mapping function.
     */
    public MacroMatcher(String macroText, Function<MacroRef, Macro> macroTable) {
        try (IonReader macroReader = IonReaderBuilder.standard().build(macroText)) {
            MacroCompiler compiler = new MacroCompiler(macroTable::apply, new ReaderAdapterIonReader(macroReader));
            macroReader.next();
            macro = compiler.compileMacro();
            name = compiler.getMacroName();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Creates a matcher for the macro on which the given reader is positioned.
     * @param macroReader the reader positioned on a TDL definition of a single macro.
     * @param macroTable the macro table's mapping function.
     */
    public MacroMatcher(IonReader macroReader, Function<MacroRef, Macro> macroTable) {
        MacroCompiler compiler = new MacroCompiler(macroTable::apply, new ReaderAdapterIonReader(macroReader));
        macro = compiler.compileMacro();
        name = compiler.getMacroName();
    }

    /**
     * @return the name of the macro.
     */
    public String name() {
        return name;
    }

    /**
     * @return the macro.
     */
    public TemplateMacro macro() {
        return macro;
    }

    private ExpressionA requireExpressionType(ExpressionA expression, ExpressionKind kind0) {
        if (expression.getKind() == kind0) {
            return expression;
        }
        return null;
    }
    private ExpressionA requireExpressionType(ExpressionA expression, ExpressionKind kind0, ExpressionKind kind1) {
        if (expression.getKind() == kind0 || expression.getKind() == kind1) {
            return expression;
        }
        return null;
    }

    /**
     * Attempts to match the value on which the reader is positioned to this matcher's macro by iterating over the value
     * and the macro body in lockstep until either an incompatibility is found (no match) or the value and body end
     * (match).
     * @param reader a reader positioned on a value to attempt to match to this matcher's macro.
     * @return true if the value matches this matcher's macro.
     */
    public boolean match(IonReader reader) {
        Iterator<ExpressionA> bodyIterator = macro.getBody().iterator();
        int index = 0;
        int[] numberOfContainerEndsAtExpressionIndex = new int[macro.getBody().size() + 1];
        while (true) {
            for (int i = 0; i < numberOfContainerEndsAtExpressionIndex[index]; i++) {
                if (reader.next() != null) {
                    return false;
                }
                reader.stepOut();
            }
            IonType type = reader.next();
            boolean hasNextExpression = bodyIterator.hasNext();
            ExpressionA expression = null;
            if (hasNextExpression) {
                expression = bodyIterator.next();
            } else if (type != null) {
                return false;
            }
            if (type == null) {
                if (expression != null && expression.getKind() == ExpressionKind.FieldName) {
                    expression = bodyIterator.next();
                }
                if (expression != null && expression.getKind() == ExpressionKind.VariableRef) {
                    if (macro.getSignature().get((int) expression.value).getCardinality().canBeVoid) {
                        // This is a trailing optional argument that is omitted in the source data, which is still
                        // considered compatible with the signature.
                        continue;
                    }
                    return false;
                } else if (hasNextExpression) {
                    return false;
                }
                break;
            }
            index++;
            if (expression != null && expression.getKind() == ExpressionKind.FieldName) {
                if (!expression.dataAsString$ion_java().equals(reader.getFieldName())) {
                    return false;
                }
                if (!bodyIterator.hasNext()) {
                    throw new IllegalStateException("dangling field name");
                }
                expression = bodyIterator.next();
                index++;
            }
            if (expression != null && expression.getKind() == ExpressionKind.VariableRef) {
                // For now, a variable matches any value at the current position.
                // TODO check cardinality and encoding type.
                continue;
            }
            if (expression != null && expression.getKind() == ExpressionKind.ExpressionGroup) {
                throw new UnsupportedOperationException("TODO: handle expression groups");
            }
            if (expression != null && expression.getKind() == ExpressionKind.MacroInvocation) {
                throw new UnsupportedOperationException("TODO: handle nested invocations");
            }
            if (expression != null && expression.getKind().isDataModelValue()) {
                if (!Arrays.asList(reader.getTypeAnnotationSymbols()).equals(expression.annotations)) {
                    return false;
                }
            }
            switch (type) {
                case NULL:
                    ExpressionA nullValue = requireExpressionType(expression, ExpressionKind.NullValue);
                    if (nullValue == null) {
                        return false;
                    }
                    break;
                case BOOL:
                    ExpressionA boolValue = requireExpressionType(expression, ExpressionKind.BoolValue);
                    if (boolValue == null || ((boolean) boolValue.value != reader.booleanValue())) {
                        return false;
                    }
                    break;
                case INT:
                    switch (reader.getIntegerSize()) {
                        case INT:
                        case LONG:
                            expression = requireExpressionType(expression, ExpressionKind.LongIntValue, ExpressionKind.BigIntValue);
                            if (expression == null || expression.dataAsLong$ion_java() != reader.longValue()) {
                                return false;
                            }
                            break;
                        case BIG_INTEGER:
                            expression = requireExpressionType(expression, ExpressionKind.LongIntValue, ExpressionKind.BigIntValue);
                            if (expression == null || ! expression.dataAsBigInt$ion_java().equals(reader.bigIntegerValue())) {
                                return false;
                            }
                            break;
                    }
                    break;
                case FLOAT:
                    ExpressionA floatValue = requireExpressionType(expression, ExpressionKind.FloatValue);
                    if (floatValue == null || (Double.compare((double) floatValue.value, reader.doubleValue()) != 0)) {
                        return false;
                    }
                    break;
                case DECIMAL:
                    ExpressionA decimalValue = requireExpressionType(expression, ExpressionKind.DecimalValue);
                    if (decimalValue == null || (!decimalValue.value.equals(reader.bigDecimalValue()))) {
                        return false;
                    }
                    break;
                case TIMESTAMP:
                    ExpressionA timestampValue = requireExpressionType(expression, ExpressionKind.TimestampValue);
                    if (timestampValue == null || (!timestampValue.value.equals(reader.timestampValue()))) {
                        return false;
                    }
                    break;
                case SYMBOL:
                    ExpressionA symbolValue = requireExpressionType(expression, ExpressionKind.SymbolValue);
                    if (symbolValue == null || (!symbolValue.dataAsString$ion_java().equals(reader.symbolValue().assumeText()))) {
                        return false;
                    }
                    break;
                case STRING:
                    ExpressionA stringValue = requireExpressionType(expression, ExpressionKind.StringValue);
                    if (stringValue == null || (!stringValue.dataAsString$ion_java().equals(reader.stringValue()))) {
                        return false;
                    }
                    break;
                case CLOB:
                    ExpressionA clobValue = requireExpressionType(expression, ExpressionKind.ClobValue);
                    if (clobValue == null || (!Arrays.equals((byte[]) clobValue.value, reader.newBytes()))) {
                        return false;
                    }
                    break;
                case BLOB:
                    ExpressionA blobValue = requireExpressionType(expression, ExpressionKind.BlobValue);
                    if (blobValue == null || (!Arrays.equals((byte[]) blobValue.value, reader.newBytes()))) {
                        return false;
                    }
                    break;
                case LIST:
                    reader.stepIn();
                    ExpressionA listValue = requireExpressionType(expression, ExpressionKind.ListValue);
                    if (listValue == null) {
                        return false;
                    }
                    numberOfContainerEndsAtExpressionIndex[listValue.getEndExclusive()]++;
                    break;
                case SEXP:
                    reader.stepIn();
                    ExpressionA sexpValue = requireExpressionType(expression, ExpressionKind.SExpValue);
                    if (sexpValue == null) {
                        return false;
                    }
                    numberOfContainerEndsAtExpressionIndex[sexpValue.getEndExclusive()]++;
                    break;
                case STRUCT:
                    reader.stepIn();
                    ExpressionA structValue = requireExpressionType(expression, ExpressionKind.StructValue);
                    if (structValue == null) {
                        return false;
                    }
                    numberOfContainerEndsAtExpressionIndex[structValue.getEndExclusive()]++;
                    break;
                case DATAGRAM:
                    throw new IllegalStateException();
            }
        }
        return true;
    }

    /**
     * @see #match(IonReader)
     * @param value the value to attempt to match.
     * @return true if the value matches this matcher's macro.
     */
    public boolean match(IonValue value) {
        try (IonReader domReader = IonReaderBuilder.standard().build(value)) {
            return match(domReader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
