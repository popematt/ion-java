// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ion.impl;

/**
 * Holds the start and end indices of a slice of the buffer.
 */
public class Marker {

    /**
     * The type ID that governs the slice.
     */
    public IonTypeID typeId = null;

    /**
     * Index of the first byte in the slice.
     */
    public long startIndex;

    /**
     * Index of the first byte after the end of the slice.
     */
    public long endIndex;

    /**
     * @param startIndex index of the first byte in the slice.
     * @param length     the number of bytes in the slice.
     */
    public Marker(final int startIndex, final int length) {
        this.startIndex = startIndex;
        this.endIndex = startIndex + length;
    }

    /**
     * @return a String representation of this object (for debugging).
     */
    @Override
    public String toString() {
        return String.format("%s[%d:%d]", typeId, startIndex, endIndex);
    }
}
