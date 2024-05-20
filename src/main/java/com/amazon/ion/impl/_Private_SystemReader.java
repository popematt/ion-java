// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.impl;

/**
 * Interface containing methods to help distinguish between system and user values.
 *
 * DO NOT USE OR IMPLEMENT outside this library.
 */
public interface _Private_SystemReader {

    /**
     * Indicates whether a reader is a user reader. User readers that extend a system reader must override this to
     * return true.
     */
    default boolean isUserReader() {
        return false;
    }

    /**
     * Returns true if the reader is positioned on a value that is actually an IVM.
     * For legacy reasons, all system readers expose IVMs as symbols. This allows code to have a reliable
     * way to distinguish between symbols and IVMs.
     */
    boolean isCurrentValueActuallyAnIVM();
}
