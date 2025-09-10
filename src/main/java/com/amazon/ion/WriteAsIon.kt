// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion

import com.amazon.ion.v8.MacroV8AwareIonWriter

/**
 * Indicates that the implementing class has a standardized/built-in way to serialize as Ion.
 */
interface WriteAsIon {

    /**
     * Writes this object to an IonWriter capable of producing macro invocations.
     */
    fun writeToMacroAware(writer: MacroAwareIonWriter) = writeTo(writer as IonWriter)

    fun writeToMacroAware(writer: MacroV8AwareIonWriter) = writeTo(writer as IonWriter)

    /**
     * Writes this object to a standard [IonWriter].
     */
    fun writeTo(writer: IonWriter)
}
