// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ion.impl;

import com.amazon.ion.*;
import com.amazon.ion.IonCursor.Event;
import com.amazon.ion.system.IonReaderBuilder;

import java.io.IOException;
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

    // True if continuable reading is disabled.
    private final boolean isNonContinuable;

    // True if input is sourced from a non-fixed stream and the reader is non-continuable, meaning that its top level
    // values are not automatically filled during next().
    private final boolean isFillRequired;

    // True if a value is in the process of being filled.
    private boolean isFillingValue = false;

    // The type of value on which the reader is currently positioned.
    private IonType type = null;

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
        isNonContinuable = !builder.isIncrementalReadingEnabled();
        isFillRequired = isNonContinuable;
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
        isNonContinuable = !builder.isIncrementalReadingEnabled();
        isFillRequired = false;
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
            if (!isFillingValue && nextValue() == Event.NEEDS_DATA) {
                return;
            }
            isFillingValue = true;
            if (coreReader.fillValue() == Event.NEEDS_DATA) {
                return;
            }
            isFillingValue = false;
            if (coreReader.getCurrentEvent() != Event.NEEDS_INSTRUCTION) {
                type = super.getType();
                return;
            }
            // The value was skipped for being too large. Get the next one.
        }
    }

    /**
     * Handles the case where the current value extends beyond the end of the reader's internal buffer.
     */
    private void handleIncompleteValue() {
        if (coreReader.getCurrentEvent() == Event.NEEDS_DATA) {
            // The reader has already consumed all bytes from the buffer. If non-continuable, this is the end of the
            // stream. If continuable, continue to return null from next().
            if (isNonContinuable) {
                coreReader.endStream();
            }
        } else if (isNonContinuable) {
            // The reader is non-continuable and has not yet consumed all bytes from the buffer, so it can continue
            // reading the incomplete container until the end is reached.
            // Each value contains its own length prefix, so it is safe to reset the incomplete flag before attempting
            // to read the value.
            // coreReader.isValueIncomplete = false;
            if (nextValue() == Event.NEEDS_DATA) {
                // Attempting to read the partial value required consuming the remaining bytes in the stream, which
                // is now at its end.
                // coreReader.isValueIncomplete = true;
                coreReader.endStream();
            } else {
                // The reader successfully positioned itself on a value within an incomplete container.
                type = super.getType();
            }
        }
    }

    @Override
    public IonType next() {
        type = null;
//        if (isValueIncomplete) {
//            handleIncompleteValue();
//        } else if (isNonContinuable || coreReader.parent() != null) {
//            if (nextValue() == Event.NEEDS_DATA) {
//                if (isNonContinuable) {
//                    coreReader.endStream();
//                }
//            } else if (isValueIncomplete && !isNonContinuable) {
//                // The value is incomplete and the reader is continuable, so the reader must return null from next().
//                // Setting the event to NEEDS_DATA ensures that if the user attempts to skip past the incomplete
//                // value, null will continue to be returned.
//                event = Event.NEEDS_DATA;
//            } else {
//                isFillingValue = false;
//                type = super.getType();
//            }
//        } else {
//            nextAndFill();
//        }

        if (!isNonContinuable && coreReader.parent() == null) {
            nextAndFill();
        } else {
            nextValue();
            isFillingValue = false;
            type = super.getType();
        }
        return type;
    }

    @Override
    public void stepIn() {
        coreReader.stepIntoContainer();
        type = null;
    }

    @Override
    public void stepOut() {
        coreReader.stepOutOfContainer();
        type = null;
    }

    @Override
    public IonType getType() {
        return type;
    }

    @Override
    public int getFieldId() {
        return coreReader.getFieldId();
    }

    /* //*
     * Prepares a scalar value to be parsed by ensuring it is present in the buffer.
     */
    // @Override
//    void prepareScalar() {
//        if (!coreReader.isValueIncomplete) {
//            if (!coreReader.isSlowMode || coreReader.event == Event.VALUE_READY) {
//                coreReader.prepareScalar();
//                return;
//            }
//            if (isFillRequired) {
//                if (fillValue() == Event.VALUE_READY) {
//                    coreReader.prepareScalar();
//                    return;
//                }
//                if (coreReader.event == Event.NEEDS_INSTRUCTION) {
//                    throw new OversizedValueException();
//                }
//            }
//        }
//        throw new IonException("Unexpected EOF.");
//    }

    private static class ApplicationReaderSpan implements Span {

        final SymbolTable symbolTable;
        final Span coreSpan;

        private ApplicationReaderSpan(SymbolTable symbolTable, Span coreSpan) {
            this.symbolTable = symbolTable;
            this.coreSpan = coreSpan;
        }

        @Override
        public <T> T asFacet(Class<T> facetType) {
            return coreSpan.asFacet(facetType);
        }
    }

    private static class ApplicationReaderOffsetSpan extends ApplicationReaderSpan implements Span, OffsetSpan {

        private ApplicationReaderOffsetSpan(SymbolTable symbolTable, Span coreSpan) {
            super(symbolTable, coreSpan);
        }

        @Override
        public long getStartOffset() {
            return ((OffsetSpan) coreSpan).getStartOffset();
        }

        @Override
        public long getFinishOffset() {
            return ((OffsetSpan) coreSpan).getFinishOffset();
        }
    }

    private class SpanProviderFacet implements SpanProvider {

        private final SpanProvider coreSpanProvider;

        public SpanProviderFacet(SpanProvider coreSpanProvider) {
            this.coreSpanProvider = coreSpanProvider;
        }

        @Override
        public Span currentSpan() {
            if (getType() == null) {
                throw new IllegalStateException("IonReader isn't positioned on a value");
            }
            return new ApplicationReaderOffsetSpan(getSymbolTable(), coreSpanProvider.currentSpan());
        }
    }

    private class SeekableReaderFacet extends SpanProviderFacet implements SeekableReader {

        private final SeekableReader coreSeekableReader;

        public SeekableReaderFacet(SpanProvider coreSeekableReader, SeekableReader readerDelegate) {
            super(coreSeekableReader);
            this.coreSeekableReader = readerDelegate;
        }

        @Override
        public void hoist(Span span) {
            if (! (span instanceof ApplicationReaderSpan)) {
                throw new IllegalArgumentException("Span isn't compatible with this reader.");
            }
            ApplicationReaderSpan applicationSpan = (ApplicationReaderSpan) span;
            if (applicationSpan.symbolTable == null) {
                throw new IllegalArgumentException("Span is not seekable.");
            }
            restoreSymbolTable(applicationSpan.symbolTable);
            coreSeekableReader.hoist(applicationSpan.coreSpan);
            type = null;
        }
    }

    @Override
    public <T> T asFacet(Class<T> facetType) {

        if (facetType == SpanProvider.class) {
            SpanProvider coreSpanProvider = coreReader.asFacet(SpanProvider.class);
            if (coreSpanProvider == null) return null;
            return facetType.cast(new SpanProviderFacet(coreSpanProvider));
        }
        if (facetType == SeekableReader.class) {
            SpanProvider coreSpanProvider = coreReader.asFacet(SpanProvider.class);
            if (coreSpanProvider == null) return null;
            SeekableReader coreSeekableReader = coreReader.asFacet(SeekableReader.class);
            if (coreSeekableReader == null) return null;
            return facetType.cast(new SeekableReaderFacet(coreSpanProvider, coreSeekableReader));
        }

        // If it's a facet that's not supported directly by this reader, give the core reader a chance to fulfill it.
        return coreReader.asFacet(facetType);
    }

    @Override
    public void close() {
        if (!isNonContinuable) {
            coreReader.endStream();
        }
        try {
            coreReader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
