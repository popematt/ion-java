// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl;

import com.amazon.ion.IonBufferConfiguration;
import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonCursor;
import com.amazon.ion.IonType;
import com.amazon.ion.OffsetSpan;
import com.amazon.ion.OversizedValueException;
import com.amazon.ion.RawValueSpanProvider;
import com.amazon.ion.SeekableReader;
import com.amazon.ion.Span;
import com.amazon.ion.SpanProvider;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.system.IonReaderBuilder;

import java.io.InputStream;

/**
 * An optionally continuable (i.e., incremental) binary {@link IonReader} implementation. Continuability is enabled
 * using {@code IonReaderBuilder.withIncrementalReadingEnabled(true)}.
 * <p>
 * When continuable reading is enabled, if
 * {@link IonReader#next()} returns {@code null} at the top-level, it indicates that there is not (yet) enough data in
 * the stream to complete a top-level value. The user may wait for more data to become available in the stream and
 * call {@link IonReader#next()} again to continue reading. Unlike the non-incremental reader, the continuable reader
 * will never throw an exception due to unexpected EOF during {@code next()}. If, however, {@link IonReader#close()} is
 * called when an incomplete value is buffered, the reader will raise an {@link IonException}.
 * </p>
 * <p>
 * There is one caveat with the continuable reader implementation: it must be able to buffer an entire top-level value
 * and any preceding system values (Ion version marker(s) and symbol table(s)) in memory. This means that each value
 * and preceding system values must be no larger than any of the following:
 * <ul>
 * <li>The configured maximum buffer size of the {@link IonBufferConfiguration}.</li>
 * <li>The heap memory available in the JVM.</li>
 * <li>2GB, because the buffer is held in a Java {@code byte[]}, which is indexed by an {@code int}.</li>
 * </ul>
 * This will not be a problem for the vast majority of Ion streams, as it is
 * rare for a single top-level value or symbol table to exceed a few megabytes in size. However, if the size of the
 * stream's values risk exceeding the available memory, then continuable reading must not be used.
 * </p>
 */
final class IonReaderContinuableTopLevelBinary extends IonReaderContinuableApplicationBinary implements IonReader, _Private_ReaderWriter {

    private static final byte ION_TYPE_MASK      = 0b00001111;
    // True if continuable reading is disabled.
    private static final byte IS_NON_CONTINUABLE = 0b00010000;
    // True if input is sourced from a non-fixed stream and the reader is non-continuable, meaning that its top level
    // values are not automatically filled during next().
    private static final byte IS_FILL_REQUIRED   = 0b00100000;
    // True if a value is in the process of being filled.
    private static final byte IS_FILLING_VALUE   = 0b01000000;

    private boolean isNonContinuable() {
        return (topLevelReaderPackedFields & IS_NON_CONTINUABLE) != 0;
    }
    private void setIsNonContinuable(boolean value) {
        if (value) {
            topLevelReaderPackedFields |= IS_NON_CONTINUABLE;
        } else {
            topLevelReaderPackedFields &= ~IS_NON_CONTINUABLE;
        }
    }

    private boolean isFillRequired() {
        return (topLevelReaderPackedFields & IS_FILL_REQUIRED) != 0;
    }

    private void setIsFillRequired(boolean value) {
        if (value) {
            topLevelReaderPackedFields |= IS_FILL_REQUIRED;
        } else {
            topLevelReaderPackedFields &= ~IS_FILL_REQUIRED;
        }
    }

    private boolean isFillingValue() {
        return (topLevelReaderPackedFields & IS_FILLING_VALUE) != 0;
    }

    private void setIsFillingValue(boolean isFillingValue) {
        if (isFillingValue) {
            topLevelReaderPackedFields |= IS_FILLING_VALUE;
        } else {
            topLevelReaderPackedFields &= ~IS_FILLING_VALUE;
        }
    }

    private void setIonType(IonType ionType) {
        topLevelReaderPackedFields &= ~ION_TYPE_MASK;
        if (ionType == null) {
            return;
        }
        switch (ionType) {
            case NULL:      topLevelReaderPackedFields |= 0x1; return;
            case BOOL:      topLevelReaderPackedFields |= 0x2; return;
            case INT:       topLevelReaderPackedFields |= 0x3; return;
            case FLOAT:     topLevelReaderPackedFields |= 0x4; return;
            case DECIMAL:   topLevelReaderPackedFields |= 0x5; return;
            case TIMESTAMP: topLevelReaderPackedFields |= 0x6; return;
            case SYMBOL:    topLevelReaderPackedFields |= 0x7; return;
            case STRING:    topLevelReaderPackedFields |= 0x8; return;
            case CLOB:      topLevelReaderPackedFields |= 0x9; return;
            case BLOB:      topLevelReaderPackedFields |= 0xA; return;
            case LIST:      topLevelReaderPackedFields |= 0xB; return;
            case SEXP:      topLevelReaderPackedFields |= 0xC; return;
            case STRUCT:    topLevelReaderPackedFields |= 0xD; return;
            case DATAGRAM:  topLevelReaderPackedFields |= 0xE;
        }
    }

    private IonType getIonType() {
        switch (topLevelReaderPackedFields & ION_TYPE_MASK) {
            case 0x0: return null;
            case 0x1: return IonType.NULL;
            case 0x2: return IonType.BOOL;
            case 0x3: return IonType.INT;
            case 0x4: return IonType.FLOAT;
            case 0x5: return IonType.DECIMAL;
            case 0x6: return IonType.TIMESTAMP;
            case 0x7: return IonType.SYMBOL;
            case 0x8: return IonType.STRING;
            case 0x9: return IonType.CLOB;
            case 0xA: return IonType.BLOB;
            case 0xB: return IonType.LIST;
            case 0xC: return IonType.SEXP;
            case 0xD: return IonType.STRUCT;
            case 0xE: return IonType.DATAGRAM;
            default:
                throw new IllegalStateException("Cannot get IonType from packed fields: " + topLevelReaderPackedFields);
        }
    }

    private void clearIonType() {
        topLevelReaderPackedFields &= ~ION_TYPE_MASK;
    }

    private boolean hasIonType() {
        return (topLevelReaderPackedFields & ION_TYPE_MASK) != 0;
    }

    // The SymbolTable that was transferred via the last call to pop_passed_symbol_table.
    private SymbolTable symbolTableLastTransferred = null;

    /**
     * Constructs a new reader from the given input stream.
     * @param builder the builder containing the configuration for the new reader.
     * @param alreadyRead the byte array containing the bytes already read (often the IVM).
     * @param alreadyReadOff the offset into 'alreadyRead` at which the first byte that was already read exists.
     * @param alreadyReadLen the number of bytes already read from `alreadyRead`.
     */
    IonReaderContinuableTopLevelBinary(IonReaderBuilder builder, InputStream inputStream, byte[] alreadyRead, int alreadyReadOff, int alreadyReadLen) {
        super(builder, inputStream, alreadyRead, alreadyReadOff, alreadyReadLen);
        setIsNonContinuable(!builder.isIncrementalReadingEnabled());
        setIsFillRequired(isNonContinuable());
    }

    /**
     * Constructs a new reader from the given byte array.
     * @param builder the builder containing the configuration for the new reader.
     * @param data the byte array containing the bytes to read.
     * @param offset the offset into the byte array at which the first byte of Ion data begins.
     * @param length the number of bytes to be read from the byte array.
     */
    IonReaderContinuableTopLevelBinary(IonReaderBuilder builder, byte[] data, int offset, int length) {
        super(builder, data, offset, length);
        setIsNonContinuable(!builder.isIncrementalReadingEnabled());
        setIsFillRequired(false);
    }

    @Override
    public SymbolTable pop_passed_symbol_table() {
        SymbolTable currentSymbolTable = getSymbolTable();
        if (currentSymbolTable == symbolTableLastTransferred) {
            // This symbol table has already been returned. Since the contract is that it is a "pop", it should not
            // be returned twice.
            return null;
        }
        symbolTableLastTransferred = currentSymbolTable;
        if (symbolTableLastTransferred.isLocalTable()) {
            // This method is called when transferring the reader's symbol table to either a writer or an IonDatagram.
            // Those cases require a mutable copy of the reader's symbol table.
            return ((_Private_LocalSymbolTable) symbolTableLastTransferred).makeCopy();
        }
        return symbolTableLastTransferred;
    }

    @Override
    public boolean hasNext() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Advances to the next value and attempts to fill it.
     */
    private void nextAndFill() {
        while (true) {
            if (!isFillingValue() && nextValue() == IonCursor.Event.NEEDS_DATA) {
                return;
            }
            setIsFillingValue(true);
            if (fillValue() == IonCursor.Event.NEEDS_DATA) {
                return;
            }
            setIsFillingValue(false);
            if (event != IonCursor.Event.NEEDS_INSTRUCTION) {
                setIonType(super.getType());
                return;
            }
            // The value was skipped for being too large. Get the next one.
        }
    }

    /**
     * Handles the case where the current value extends beyond the end of the reader's internal buffer.
     */
    private void handleIncompleteValue() {
        if (event == Event.NEEDS_DATA) {
            // The reader has already consumed all bytes from the buffer. If non-continuable, this is the end of the
            // stream. If continuable, continue to return null from next().
            if (isNonContinuable()) {
                endStream();
            }
        } else if (isNonContinuable()) {
            // The reader is non-continuable and has not yet consumed all bytes from the buffer, so it can continue
            // reading the incomplete container until the end is reached.
            // Each value contains its own length prefix, so it is safe to reset the incomplete flag before attempting
            // to read the value.
            setIsValueIncomplete(false);
            if (nextValue() == IonCursor.Event.NEEDS_DATA) {
                // Attempting to read the partial value required consuming the remaining bytes in the stream, which
                // is now at its end.
                setIsValueIncomplete(true);
                endStream();
            } else {
                // The reader successfully positioned itself on a value within an incomplete container.
                setIonType(super.getType());
            }
        }
    }

    @Override
    public IonType next() {
        clearIonType();
        if (isValueIncomplete()) {
            handleIncompleteValue();
        } else if (!isSlowMode() || isNonContinuable() || !isPositionedAtTopLevelOfStream()) {
            if (nextValue() == IonCursor.Event.NEEDS_DATA) {
                if (isNonContinuable()) {
                    endStream();
                }
            } else if (isValueIncomplete() && !isNonContinuable()) {
                // The value is incomplete and the reader is continuable, so the reader must return null from next().
                // Setting the event to NEEDS_DATA ensures that if the user attempts to skip past the incomplete
                // value, null will continue to be returned.
                event = Event.NEEDS_DATA;
            } else {
                setIsFillingValue(false);
                setIonType(super.getType());
            }
        } else {
            nextAndFill();
        }
        return getIonType();
    }

    @Override
    public void stepIn() {
        super.stepIntoContainer();
        clearIonType();
    }

    @Override
    public void stepOut() {
        super.stepOutOfContainer();
        clearIonType();
    }

    @Override
    public IonType getType() {
        return getIonType();
    }

    /**
     * Prepares a scalar value to be parsed by ensuring it is present in the buffer.
     */
    @Override
    void prepareScalar() {
        if (!isValueIncomplete()) {
            if (!isSlowMode() || event == IonCursor.Event.VALUE_READY) {
                super.prepareScalar();
                return;
            }
            if (isFillRequired()) {
                if (fillValue() == Event.VALUE_READY) {
                    super.prepareScalar();
                    return;
                }
                if (event == Event.NEEDS_INSTRUCTION) {
                    throw new OversizedValueException();
                }
            } else {
                super.prepareScalar();
                return;
            }
        }
        throw new IonException("Unexpected EOF.");
    }

    private static class IonReaderBinarySpan extends DowncastingFaceted implements Span, OffsetSpan {
        final long bufferOffset;
        final long bufferLimit;
        final long totalOffset;
        final SymbolTable symbolTable;

        /**
         * @param bufferOffset the offset of the span's first byte in the cursor's internal buffer.
         * @param bufferLimit the offset after the span's last byte in the cursor's internal buffer.
         * @param totalOffset the total stream offset of the span's first byte. This can differ from 'bufferOffset' if
         *                    the cursor's internal buffer is refillable, such as when it consumes data from an input
         *                    stream.
         * @param symbolTable the symbol table active where the span occurs.
         */
        IonReaderBinarySpan(long bufferOffset, long bufferLimit, long totalOffset, SymbolTable symbolTable) {
            this.bufferOffset = bufferOffset;
            this.bufferLimit = bufferLimit;
            this.totalOffset = totalOffset;
            this.symbolTable = symbolTable;
        }

        @Override
        public long getStartOffset() {
            return totalOffset;
        }

        @Override
        public long getFinishOffset() {
            return totalOffset + (bufferLimit - bufferOffset);
        }

    }

    private class SpanProviderFacet implements SpanProvider {

        @Override
        public Span currentSpan() {
            if (!hasIonType()) {
                throw new IllegalStateException("IonReader isn't positioned on a value");
            }
            return new IonReaderBinarySpan(
                valuePreHeaderIndex,
                valueMarker.endIndex,
                getTotalOffset(),
                getSymbolTable()
            );
        }
    }

    private class RawValueSpanProviderFacet implements RawValueSpanProvider {

        @Override
        public Span valueSpan() {
            if (!hasIonType()) {
                throw new IllegalStateException("IonReader isn't positioned on a value");
            }
            return new IonReaderBinarySpan(
                valueMarker.startIndex,
                valueMarker.endIndex,
                valueMarker.startIndex,
                null
            );
        }

        @Override
        public byte[] buffer() {
            return buffer;
        }
    }

    private class SeekableReaderFacet extends SpanProviderFacet implements SeekableReader {

        @Override
        public void hoist(Span span) {
            if (! (span instanceof IonReaderBinarySpan)) {
                throw new IllegalArgumentException("Span isn't compatible with this reader.");
            }
            IonReaderBinarySpan binarySpan = (IonReaderBinarySpan) span;
            if (binarySpan.symbolTable == null) {
                throw new IllegalArgumentException("Span is not seekable.");
            }
            // Note: setting the limit at the end of the hoisted value causes the reader to consider the end
            // of the value to be the end of the stream, in order to comply with the SeekableReader contract. From
            // an implementation perspective, this is not necessary; if we leave the buffer's limit unchanged, the
            // reader can continue after processing the hoisted value.
            restoreSymbolTable(binarySpan.symbolTable);
            slice(binarySpan.bufferOffset, binarySpan.bufferLimit, binarySpan.symbolTable.getIonVersionId());
            clearIonType();
        }
    }

    @Override
    public <T> T asFacet(Class<T> facetType) {
        if (facetType == SpanProvider.class) {
            return facetType.cast(new SpanProviderFacet());
        }
        // Note: because IonCursorBinary has an internal buffer that can grow, it is possible to relax the restriction
        // that readers must have been constructed with a byte array in order to be seekable or provide raw value spans.
        // However, it requires some considerations that do not fit well with the existing interfaces. Most importantly,
        // because the cursor's internal buffer is a byte array, its size is limited to 2GB. Pinning all bytes after the
        // first span is requested could lead to buffer overflow for large streams, so there would need to be a way for
        // a user to release a span and allow the reader to reclaim its bytes. This functionality is not included in the
        // existing Span interfaces. See: amzn/ion-java/issues/17
        if (isByteBacked()) {
            if (facetType == SeekableReader.class) {
                return facetType.cast(new SeekableReaderFacet());
            }
            if (facetType == RawValueSpanProvider.class) {
                return facetType.cast(new RawValueSpanProviderFacet());
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (!isNonContinuable()) {
            endStream();
        }
        super.close();
    }
}
