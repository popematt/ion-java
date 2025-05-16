package com.amazon.ion.v2.impl_1_1

import java.nio.ByteBuffer

/**
 * Reads the raw E-expression arguments
 */
class EExpArgumentReader(
    source: ByteBuffer,
    pool: ResourcePool,
    symbolTable: Array<String?>,
): ValueReaderBase(source, pool, symbolTable) {

    // Presence Bits
    // Signature

    override fun close() {
        // TODO: Pool these?
    }

    // fun expressionGroup(): SequenceReader
    // reuse regular sequence reader for tagged groups
    // returns tagless expression group reader for other groups
}
