// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.ion.v8

import com.amazon.ion.*
import com.amazon.ion.impl.*
import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.bin.FixedInt.fixedIntLength
import com.amazon.ion.impl.bin.FlexInt.flexIntLength
import com.amazon.ion.impl.bin.Ion_1_1_Constants.*
import com.amazon.ion.impl.bin.utf8.*
import com.amazon.ion.util.*
import com.amazon.ion.v8.IonRawBinaryWriter_1_1.ContainerType.*
import java.io.OutputStream
import java.lang.Double.doubleToRawLongBits
import java.lang.Float.floatToIntBits
import java.math.BigDecimal
import java.math.BigInteger

class IonRawBinaryWriter_1_1 internal constructor(
    private val out: OutputStream,
    private val buffer: WriteBuffer,
    private val lengthPrefixPreallocation: Int,
) : IonRawWriter_1_1, _Private_IonRawWriter_1_1 {

    private fun WriteBuffer.writeByte(byte: Int) {
        writeByte(byte.toByte())
    }

    /**
     * Types of encoding containers.
     */
    enum class ContainerType {
        LIST,
        SEXP,
        STRUCT,
        EEXP,
        EXPR_GROUP,
        TE_LIST,
        TE_SEXP,
        /**
         * Represents the top level stream. The [containerStack] always has [ContainerInfo] for [TOP] at the bottom
         * of the stack so that we never have to check if [currentContainer] is null.
         *
         * TODO: Test if performance is better if we just check currentContainer for nullness.
         */
        TOP,
    }

    private class ContainerInfo(
        @JvmField var type: ContainerType? = null,
        @JvmField var isLengthPrefixed: Boolean = true,
        @JvmField var position: Long = -1,
        /**
         * Where should metadata such as the length prefix and/or the presence bitmap be written,
         * relative to the start of this container.
         */
        @JvmField var metadataOffset: Int = 1,
        /**
         * The number of bytes for everything following the length-prefix (if applicable) in this container.
         */
        @JvmField var length: Long = 0,
        // TODO: Test if performance is better with an Object Reference or an index into the PatchPoint queue.
        @JvmField var patchPoint: PatchPoint? = null,
        /**
         * The number of elements in the expression group or arguments to the macro.
         * This is updated when _finishing_ writing a value or expression group.
         *
         * TODO: Confirm that this is not needed for macros, and then update it only when starting a tagless value.
         */
        @JvmField var numChildren: Int = 0,
    ) {
        /**
         * Clears this [ContainerInfo] of old data and initializes it with the given new data.
         */
        fun reset(type: ContainerType, position: Long, isLengthPrefixed: Boolean = true, metadataOffset: Int = 1) {
            this.type = type
            this.isLengthPrefixed = isLengthPrefixed
            this.position = position
            this.metadataOffset = metadataOffset
            length = 0
            patchPoint = null
            numChildren = 0
        }
    }

    companion object {
        /**
         * Create a new instance for the given OutputStream with the given block size and length preallocation.
         */
        @JvmStatic
        fun from(out: OutputStream, blockSize: Int, preallocation: Int): IonRawBinaryWriter_1_1 {
            return IonRawBinaryWriter_1_1(out, WriteBuffer(BlockAllocatorProviders.basicProvider().vendAllocator(blockSize)) {}, preallocation)
        }
    }

    private val utf8StringEncoder = Utf8StringEncoderPool.getInstance().getOrCreate()

    // TODO: We may want to eliminate the annotation buffers.
    private var annotationsTextBuffer = arrayOfNulls<Any>(8)
    private var annotationsIdBuffer = IntArray(8)
    private var numAnnotations = 0

    private var hasFieldName = false

    private var closed = false

    private val patchPoints = _Private_RecyclingQueue(512) { PatchPoint() }
    private val containerStack = _Private_RecyclingStack(8) { ContainerInfo() }

    private var currentContainer: ContainerInfo = containerStack.push { it.reset(TOP, 0L) }

    override fun flush() {
        if (closed) return
        confirm(depth() == 0) { "Cannot call finish() while in a container" }
        confirm(numAnnotations == 0) { "Cannot call finish with dangling annotations" }

        if (patchPoints.isEmpty) {
            // nothing to patch--write 'em out!
            buffer.writeTo(out)
        } else {
            var bufferPosition: Long = 0

            // Patch length values are long, so they always fit in 10 bytes or fewer.
            val flexUIntScratch = ByteArray(10)

            val iterator = patchPoints.iterate()

            while (iterator.hasNext()) {
                val patch = iterator.next()
                if (patch.length < 0) {
                    continue
                }
                // write up to the thing to be patched
                val bufferLength = patch.oldPosition - bufferPosition
                buffer.writeTo(out, bufferPosition, bufferLength)

                // write out the patch
                // TODO: See if there's a measurable performance benefit if we write directly to the output stream vs using the flexUIntScratch
                val numBytes = FlexInt.flexUIntLength(patch.length)
                FlexInt.writeFlexIntOrUIntInto(flexUIntScratch, 0, patch.length, numBytes)
                out.write(flexUIntScratch, 0, numBytes)

                // skip over the preallocated field
                bufferPosition = patch.oldPosition
                bufferPosition += patch.oldLength.toLong()
            }
            buffer.writeTo(out, bufferPosition, buffer.position() - bufferPosition)
        }

        buffer.reset()
        patchPoints.clear()

        // TODO: Stream flush mode
    }

    override fun close() {
        if (closed) return
        flush()
        buffer.close()
        closed = true
    }

    override fun depth(): Int = containerStack.size() - 1 // "Top" doesn't count when counting depth.

    override fun isInStruct(): Boolean = currentContainer.type == STRUCT

    override fun writeIVM() {
        confirm(currentContainer.type == TOP) { "IVM can only be written at the top level of an Ion stream." }
        confirm(numAnnotations == 0) { "Cannot write an IVM with annotations" }
        buffer.writeBytes(_Private_IonConstants.BINARY_VERSION_MARKER_1_1)
    }

    /**
     * Ensures that there is enough space in the annotation buffers for [n] annotations.
     * If more space is needed, it over-allocates by 8 to ensure that we're not continually allocating when annotations
     * are being added one by one.
     */
    private inline fun ensureAnnotationSpace(n: Int) {
        if (annotationsIdBuffer.size < n || annotationsTextBuffer.size < n) {
            val oldIds = annotationsIdBuffer
            annotationsIdBuffer = IntArray(n + 8)
            oldIds.copyInto(annotationsIdBuffer)
            val oldText = annotationsTextBuffer
            annotationsTextBuffer = arrayOfNulls(n + 8)
            oldText.copyInto(annotationsTextBuffer)
        }
    }

    override fun writeAnnotations(annotation0: Int) {
        confirm(annotation0 >= 0) { "Invalid SID: $annotation0" }
        ensureAnnotationSpace(numAnnotations + 1)
        annotationsIdBuffer[numAnnotations++] = annotation0
    }

    override fun writeAnnotations(annotation0: Int, annotation1: Int) {
        confirm(annotation0 >= 0 && annotation1 >= 0) { "One or more invalid SIDs: $annotation0, $annotation1" }
        ensureAnnotationSpace(numAnnotations + 2)
        annotationsIdBuffer[numAnnotations++] = annotation0
        annotationsIdBuffer[numAnnotations++] = annotation1
    }

    override fun writeAnnotations(annotations: IntArray) {
        confirm(annotations.all { it >= 0 }) { "One or more invalid SIDs: ${annotations.filter { it < 0 }.joinToString()}" }
        ensureAnnotationSpace(numAnnotations + annotations.size)
        annotations.copyInto(annotationsIdBuffer, numAnnotations)
        numAnnotations += annotations.size
    }

    override fun writeAnnotations(annotation0: CharSequence) {
        ensureAnnotationSpace(numAnnotations + 1)
        annotationsTextBuffer[numAnnotations++] = annotation0
    }

    override fun writeAnnotations(annotation0: CharSequence, annotation1: CharSequence) {
        ensureAnnotationSpace(numAnnotations + 2)
        annotationsTextBuffer[numAnnotations++] = annotation0
        annotationsTextBuffer[numAnnotations++] = annotation1
    }

    override fun writeAnnotations(annotations: Array<CharSequence>) {
        if (annotations.isEmpty()) return
        ensureAnnotationSpace(numAnnotations + annotations.size)
        annotations.copyInto(annotationsTextBuffer, numAnnotations)
        numAnnotations += annotations.size
    }

    override fun _private_clearAnnotations() {
        numAnnotations = 0
        // erase the first entries to ensure old values don't leak into `_private_hasFirstAnnotation()`
        annotationsIdBuffer[0] = -1
        annotationsTextBuffer[0] = null
    }

    override fun _private_hasFirstAnnotation(sid: Int, text: String?): Boolean {
        if (numAnnotations == 0) return false
        if (sid >= 0 && annotationsIdBuffer[0] == sid) {
            return true
        }
        if (text != null && annotationsTextBuffer[0] == text) {
            return true
        }
        return false
    }

    /**
     * Helper function for handling annotations and field names when starting a value.
     */
    private inline fun openValue(valueWriterExpression: () -> Unit) {

        if (isInStruct()) {
            confirm(hasFieldName) { "Values in a struct must have a field name." }
        }

        var annotationsTotalLength = 0
        val numAnnotations = this.numAnnotations
        if (numAnnotations > 0) {
            val annotationsIdBuffer = annotationsIdBuffer
            val annotationsTextBuffer = annotationsTextBuffer
            val utf8StringEncoder = utf8StringEncoder
            val buffer = buffer

            for (i in 0 until numAnnotations) {
                val annotationText = annotationsTextBuffer[i]
                if (annotationText != null) {
                    annotationsTextBuffer[i] = null
                    buffer.writeByte(Ops.ANNOTATION_TEXT.toByte())
                    val text = utf8StringEncoder.encode(annotationText.toString())
                    val textLength = text.encodedLength
                    val numLengthBytes: Int = buffer.writeFlexUInt(textLength.toLong())
                    buffer.writeBytes(text.buffer, 0, textLength)
                    annotationsTotalLength += 1 + numLengthBytes + textLength
                } else {
                    buffer.writeByte(Ops.ANNOTATION_SID.toByte())
                    annotationsTotalLength++
                    annotationsTotalLength += buffer.writeFlexUInt(annotationsIdBuffer[i])
                }
            }
            this.numAnnotations = 0
        }

        currentContainer.length += annotationsTotalLength

        hasFieldName = false
        valueWriterExpression()
    }

    /**
     * Helper function for writing scalar values that builds on [openValue] and also includes updating
     * the length of the current container.
     *
     * @param valueWriterExpression should be a function that writes the scalar value to the buffer, and
     *                              returns the number of bytes that were written.
     */
    private inline fun writeScalar(valueWriterExpression: () -> Int) = openValue {
        val numBytesWritten = valueWriterExpression()
        currentContainer.length += numBytesWritten
        currentContainer.numChildren++
    }

    override fun writeFieldName(sid: Int) {
        val currentContainer = currentContainer
        confirm(currentContainer.type == STRUCT) { "Can only write a field name inside of a struct." }
        currentContainer.length += buffer.writeFlexInt(sid.toLong())
        hasFieldName = true
    }

    override fun writeFieldName(text: CharSequence) {
        val currentContainer = currentContainer
        confirm(currentContainer.type == STRUCT) { "Can only write a field name inside of a struct." }
        val encodedText = utf8StringEncoder.encode(text.toString())
        val textLength = encodedText.encodedLength
        var bytesWritten = 0
        bytesWritten += buffer.writeFlexInt(-1 - textLength.toLong())
        buffer.writeBytes(encodedText.buffer, 0, textLength)
        bytesWritten += textLength
        currentContainer.length += bytesWritten
        hasFieldName = true
    }

    override fun _private_hasFieldName(): Boolean = hasFieldName

    override fun writeNull() = writeScalar {
        buffer.writeByte(Ops.NULL_NULL.toByte())
        1
    }

    override fun writeNull(type: IonType) = writeScalar {
        if (type == IonType.NULL) {
            buffer.writeByte(Ops.NULL_NULL)
            1
        } else {
            buffer.writeByte(Ops.TYPED_NULL)
            val typeByte = type.ordinal - 1
            buffer.writeByte(typeByte)
            // TODO: make sure that we're not writing null.datagram, which isn't a real thing.
            2
        }
    }

    override fun writeBool(value: Boolean) = writeScalar {
        val data = if (value) Ops.BOOL_TRUE else Ops.BOOL_FALSE
        buffer.writeByte(data.toByte())
        1
    }

    override fun writeInt(value: Long) = writeScalar {
        if (value == 0L) {
            buffer.writeByte(Ops.INT_0)
            1
        } else {
            val length = fixedIntLength(value)
            buffer.writeByte(Ops.INT_0 + length)
            buffer.writeFixedIntOrUInt(value, length)
            1 + length
        }
    }

    override fun writeInt(value: BigInteger) {
        if (value.bitLength() < Long.SIZE_BITS) {
            writeInt(value.longValueExact())
        } else {
            writeScalar {
                buffer.writeByte(Ops.VARIABLE_LENGTH_INTEGER)
                val intBytes = value.toByteArray()
                val totalBytes = 1 + intBytes.size + buffer.writeFlexUInt(intBytes.size)
                for (i in intBytes.size downTo 1) {
                    buffer.writeByte(intBytes[i - 1])
                }
                totalBytes
            }
        }
    }

    override fun writeFloat(value: Double) = writeScalar {


        // TODO: Optimization to write a 16 bit float for non-finite and possibly other values
        //       We could check the number of significand bits and the value of the exponent
        //       to determine if it can be represented in a smaller format without having a
        //       complete representation of half-precision floating point numbers.
        if (!value.isFinite() || value == value.toFloat().toDouble()) {
            val floatBits = floatToIntBits(value.toFloat())
            when (floatBits) {
                0 -> {
                    buffer.writeByte(Ops.FLOAT_0)
                    1
                }
                FLOAT_32_NEGATIVE_ZERO_BITS -> {
                    buffer.writeByte(Ops.FLOAT_16)
                    buffer.writeFixedIntOrUInt(FLOAT_16_NEGATIVE_ZERO_BITS.toLong(), 2)
                    3
                }
                else -> {
                    buffer.writeByte(Ops.FLOAT_32)
                    buffer.writeFixedIntOrUInt(floatToIntBits(value.toFloat()).toLong(), 4)
                    5
                }
            }
        } else {
            buffer.writeByte(Ops.FLOAT_64)
            buffer.writeFixedIntOrUInt(doubleToRawLongBits(value), 8)
            9
        }
    }

    override fun writeFloat(value: Float) = writeScalar {
        val floatBits = floatToIntBits(value.toFloat())
        when (floatBits) {
            0 -> {
                buffer.writeByte(Ops.FLOAT_0)
                1
            }
            FLOAT_32_NEGATIVE_ZERO_BITS -> {
                buffer.writeByte(Ops.FLOAT_16)
                buffer.writeFixedIntOrUInt(FLOAT_16_NEGATIVE_ZERO_BITS.toLong(), 2)
                3
            }
            else -> {
                buffer.writeByte(Ops.FLOAT_32)
                buffer.writeFixedIntOrUInt(floatToIntBits(value).toLong(), 4)
                5
            }
        }
    }

    override fun writeDecimal(value: BigDecimal) = writeScalar {

        val exponent = -value.scale()
        val numExponentBytes = flexIntLength(exponent.toLong())

        var coefficientBytes: ByteArray? = null
        val numCoefficientBytes: Int
        if (BigDecimal.ZERO.compareTo(value) == 0) {
            numCoefficientBytes = if (Decimal.isNegativeZero(value)) {
                1
            } else if (exponent == 0) {
                buffer.writeByte(Ops.DECIMAL_0)
                return@writeScalar 1
            } else {
                0
            }
        } else {
            coefficientBytes = value.unscaledValue().toByteArray()
            numCoefficientBytes = coefficientBytes.size
        }

        var opCodeAndLengthBytes = 1
        if (numExponentBytes + numCoefficientBytes < 16) {
            val opCode = Ops.DECIMAL_0 + numExponentBytes + numCoefficientBytes
            buffer.writeByte(opCode.toByte())
        } else {
            // Decimal values that require more than 15 bytes can be encoded using the variable-length decimal opcode: 0xF6.
            buffer.writeByte(Ops.VARIABLE_LENGTH_DECIMAL)
            opCodeAndLengthBytes += buffer.writeFlexUInt(numExponentBytes + numCoefficientBytes)
        }

        buffer.writeFlexInt(exponent.toLong())
        if (numCoefficientBytes > 0) {
            if (coefficientBytes != null) {
                buffer.writeFixedIntOrUInt(coefficientBytes)
            } else {
                buffer.writeByte(0.toByte())
            }
        }
        opCodeAndLengthBytes + numCoefficientBytes + numExponentBytes
    }

    override fun writeTimestamp(value: Timestamp) = writeScalar { IonEncoder_1_1.writeTimestampValue(buffer, value) }

    override fun writeSymbol(id: Int) = writeScalar {
        confirm(id >= 0) { "Invalid SID: $id" }
        buffer.writeByte(Ops.SYMBOL_VALUE_SID.toByte())
        1 + buffer.writeFlexUInt(id)
    }

    override fun writeSymbol(text: CharSequence) = writeScalar {
        val encodedText = utf8StringEncoder.encode(text.toString())
        val encodedTextLength = encodedText.encodedLength
        when (encodedTextLength) {
            0 -> {
                buffer.write2Bytes(Ops.VARIABLE_LENGTH_INLINE_SYMBOL.toByte(), 0x01.toByte())
                2
            }
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 -> {
                buffer.writeByte((Ops.SYMBOL_VALUE_TEXT_ONE_LENGTH + encodedTextLength - 1).toByte())
                buffer.writeBytes(encodedText.buffer, 0, encodedTextLength)
                encodedTextLength + 1
            }
            else -> {
                buffer.writeByte(Ops.VARIABLE_LENGTH_INLINE_SYMBOL.toByte())
                val lengthOfLength = buffer.writeFlexUInt(encodedTextLength)
                buffer.writeBytes(encodedText.buffer, 0, encodedTextLength)
                1 + lengthOfLength + encodedTextLength
            }
        }
    }

    override fun writeString(value: CharSequence) = writeScalar {
        val encodedText = utf8StringEncoder.encode(value.toString())
        val encodedTextLength = encodedText.encodedLength
        when (encodedTextLength) {
            0 -> {
                buffer.write2Bytes(Ops.VARIABLE_LENGTH_STRING.toByte(), 0x01.toByte())
                2
            }
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 -> {
                buffer.writeByte((Ops.STRING_LENGTH_1 + encodedTextLength - 1).toByte())
                buffer.writeBytes(encodedText.buffer, 0, encodedTextLength)
                encodedTextLength + 1
            }
            else -> {
                buffer.writeByte(Ops.VARIABLE_LENGTH_STRING.toByte())
                val lengthOfLength = buffer.writeFlexUInt(encodedTextLength)
                buffer.writeBytes(encodedText.buffer, 0, encodedTextLength)
                1 + lengthOfLength + encodedTextLength
            }
        }
    }

    override fun writeBlob(value: ByteArray, start: Int, length: Int) = writeScalar { IonEncoder_1_1.writeBlobValue(buffer, value, start, length) }

    override fun writeClob(value: ByteArray, start: Int, length: Int) = writeScalar { IonEncoder_1_1.writeClobValue(buffer, value, start, length) }

    override fun stepInList(usingLengthPrefix: Boolean) {
        openValue {
            currentContainer = containerStack.push { it.reset(LIST, buffer.position(), usingLengthPrefix) }
            if (usingLengthPrefix) {
                buffer.writeByte(Ops.VARIABLE_LENGTH_LIST)
                buffer.reserve(lengthPrefixPreallocation)
            } else {
                buffer.writeByte(Ops.DELIMITED_LIST)
            }
        }
    }

    override fun stepInSExp(usingLengthPrefix: Boolean) {
        openValue {
            currentContainer = containerStack.push { it.reset(SEXP, buffer.position(), usingLengthPrefix) }
            if (usingLengthPrefix) {
                buffer.writeByte(Ops.VARIABLE_LENGTH_SEXP)
                buffer.reserve(lengthPrefixPreallocation)
            } else {
                buffer.writeByte(Ops.DELIMITED_SEXP)
            }
        }
    }

    override fun stepInStruct(usingLengthPrefix: Boolean) {
        openValue {
            currentContainer = containerStack.push { it.reset(STRUCT, buffer.position(), usingLengthPrefix) }
            if (usingLengthPrefix) {
                buffer.writeByte(Ops.VARIABLE_LENGTH_STRUCT_WITH_SIDS)
                buffer.reserve(lengthPrefixPreallocation)
            } else {
                buffer.writeByte(Ops.DELIMITED_STRUCT)
            }
        }
    }

    override fun writeAbsentArgument() {
        // TODO: Ensure no annotations?
        buffer.writeByte(Ops.NOTHING_ARGUMENT)
        currentContainer.length++
    }

    override fun stepInEExp(name: CharSequence) {
        throw UnsupportedOperationException("Binary writer requires macros to be invoked by their ID.")
    }

    override fun stepInEExp(id: Int, usingLengthPrefix: Boolean) {
        // Length-prefixed e-expression format:
        //     F5 <flexuint-address> <flexuint-length> <presence-bitmap> <args...>
        // Non-length-prefixed e-expression format:
        //     <address/opcode> <presence-bitmap> <args...>

        if (currentContainer.type == STRUCT && !hasFieldName) {
            throw IonException("Cannot write an e-expression in field-name position.")
        }

        currentContainer = containerStack.push { it.reset(EEXP, buffer.position(), usingLengthPrefix) }

        openValue {
            if (usingLengthPrefix) {
                buffer.writeByte(Ops.LENGTH_PREFIXED_MACRO_INVOCATION)
                currentContainer.metadataOffset += buffer.writeFlexUInt(id)
                buffer.reserve(lengthPrefixPreallocation)
            } else {
                writeEExpMacroIdWithoutLengthPrefix(id)
            }
        }
        hasFieldName = false
    }

    private fun writeEExpMacroIdWithoutLengthPrefix(id: Int) {
        if (id < 64) {
            buffer.writeByte(id.toByte())
        } else if (id < 4160) {
            val biasedId = id - 64
            val lowNibble = biasedId / 256
            val adjustedId = biasedId % 256L
            buffer.writeByte((Ops.BIASED_E_EXPRESSION_ONE_BYTE_FIXED_INT + lowNibble).toByte())
            currentContainer.metadataOffset += buffer.writeFixedUInt(adjustedId)
        } else if (id < 1_052_736) {
            val biasedId = id - 4160
            val lowNibble = biasedId / (256 * 256)
            val adjustedId = biasedId % (256 * 256L)
            buffer.writeByte((Ops.BIASED_E_EXPRESSION_TWO_BYTE_FIXED_INT + lowNibble).toByte())
            currentContainer.metadataOffset += buffer.writeFixedIntOrUInt(adjustedId, 2)
        } else {
            buffer.writeByte(Ops.E_EXPRESSION_WITH_FLEX_UINT_ADDRESS)
            currentContainer.metadataOffset += buffer.writeFlexUInt(id)
        }
    }

    override fun stepOut() {
        confirm(!hasFieldName) { "Cannot step out with dangling field name." }
        confirm(numAnnotations == 0) { "Cannot step out with dangling annotations." }

        // The length of the current container. By the end of this method, the total must include
        // any opcodes, length prefixes, or other data that is not counted in ContainerInfo.length
        var thisContainerTotalLength: Long = currentContainer.length

        // currentContainer.type is non-null for any initialized ContainerInfo
        when (currentContainer.type.assumeNotNull()) {
            TE_LIST, TE_SEXP -> {
                // Add one byte to account for the op code
                thisContainerTotalLength++
                thisContainerTotalLength += writeCurrentContainerLength(lengthPrefixPreallocation, currentContainer.numChildren.toLong())
            }

            LIST, SEXP, STRUCT -> {
                // Add one byte to account for the op code
                thisContainerTotalLength++
                // Write closing delimiter if we're in a delimited container.
                // Update length prefix if we're in a prefixed container.
                if (currentContainer.isLengthPrefixed) {
                    val contentLength = currentContainer.length
                    if (contentLength <= 0xF) {
                        // Clean up any unused space that was pre-allocated.
                        buffer.shiftBytesLeft(currentContainer.length.toInt(), lengthPrefixPreallocation)
                        val zeroLengthOpCode = when (currentContainer.type) {
                            LIST -> Ops.LIST_ZERO_LENGTH
                            SEXP -> Ops.SEXP_ZERO_LENGTH
                            STRUCT -> Ops.STRUCT_ZERO_LENGTH
                            else -> TODO("Unreachable")
                        }
                        buffer.writeByteAt(currentContainer.position, zeroLengthOpCode + contentLength)
                    } else {
                        thisContainerTotalLength += writeCurrentContainerLength(lengthPrefixPreallocation)
                    }
                } else {
                    if (isInStruct()) {
                        // Need a sacrificial field name before the closing delimiter. We'll use $0.
                        buffer.writeByte(FlexInt.ZERO)
                        thisContainerTotalLength += 1
                    }
                    thisContainerTotalLength += 1 // For the end marker
                    buffer.writeByte(Ops.DELIMITED_CONTAINER_END)
                }
            }
            EEXP -> {
                // Add to account for the opcode and/or address
                thisContainerTotalLength += currentContainer.metadataOffset
            }
            EXPR_GROUP -> {
                TODO("Not supported by 'Minimal Templates' proposal")
            }
            TOP -> throw IonException("Nothing to step out of.")
        }

        val justExitedContainer = containerStack.pop()
        val currentContainer = containerStack.peek()
        // Update the length of the new current container to include the length of the container that we just stepped out of.
        currentContainer.length += thisContainerTotalLength
        currentContainer.numChildren++
        this.currentContainer = currentContainer
    }

    /**
     * Writes the length of the current container and returns the number of bytes needed to do so.
     * Transparently handles PatchPoints as necessary.
     *
     * @param numPreAllocatedLengthPrefixBytes the number of bytes that were pre-allocated for the length prefix of the
     *                                         current container.
     */
    private fun writeCurrentContainerLength(numPreAllocatedLengthPrefixBytes: Int, lengthToWrite: Long = currentContainer.length): Int {
        val lengthPosition = currentContainer.position + currentContainer.metadataOffset
        val lengthPrefixBytesRequired = FlexInt.flexUIntLength(lengthToWrite)
        if (lengthPrefixBytesRequired == numPreAllocatedLengthPrefixBytes) {
            // We have enough space, so write in the correct length.
            buffer.writeFlexIntOrUIntAt(lengthPosition, lengthToWrite, lengthPrefixBytesRequired)
        } else {
            addPatchPointsToStack()
            // All ContainerInfos are in the stack, so we know that its patchPoint is non-null.
            currentContainer.patchPoint.assumeNotNull().apply {
                oldPosition = lengthPosition
                oldLength = numPreAllocatedLengthPrefixBytes
                length = lengthToWrite
            }
        }
        return lengthPrefixBytesRequired
    }

    private fun addPatchPointsToStack() {
        // TODO: We may be able to improve this by skipping patch points on ancestors that are delimited containers,
        //       since the patch points for delimited containers will go unused anyway. However, the additional branching
        //       may negate the effect of any reduction in allocations.

        // If we're adding a patch point we first need to ensure that all of our ancestors (containing values) already
        // have a patch point. No container can be smaller than the contents, so all outer layers also require patches.
        // Instead of allocating iterator, we share one iterator instance within the scope of the container stack and
        // reset the cursor every time we track back to the ancestors.
        val stackIterator: ListIterator<ContainerInfo> = containerStack.iterator()
        // Walk down the stack until we find an ancestor which already has a patch point
        while (stackIterator.hasNext() && stackIterator.next().patchPoint == null);

        // The iterator cursor is now positioned on an ancestor container that has a patch point
        // Ascend back up the stack, fixing the ancestors which need a patch point assigned before us
        while (stackIterator.hasPrevious()) {
            val ancestor = stackIterator.previous()
            if (ancestor.patchPoint == null) {
                ancestor.patchPoint = patchPoints.pushAndGet { it.clear() }
            }
        }
    }

    override fun writeTaggedPlaceholder() {
        writeScalar {
            buffer.writeByte(Ops.TAGGED_PLACEHOLDER)
            1
        }
    }

    override fun writeTaggedPlaceholderWithDefault(default: (IonRawWriter_1_1) -> Unit) {
        buffer.writeByte(Ops.TAGGED_PLACEHOLDER_WITH_DEFAULT)
        currentContainer.length++
        default(this)
        hasFieldName = false
    }

    override fun writeTaglessPlaceholder(opcode: Int) {
        buffer.write2Bytes(Ops.TAGLESS_PLACEHOLDER.toByte(), opcode.toByte())
        currentContainer.length += 2
        hasFieldName = false
    }

    override fun stepInDirective(directive: Int) {
        currentContainer = containerStack.push { it.reset(SEXP, buffer.position(), isLengthPrefixed = false) }
        buffer.writeByte(directive)
    }

    override fun stepInTaglessElementList(opcode: Int) {
        openValue {
            currentContainer = containerStack.push {
                it.reset(TE_LIST, buffer.position(), true)
            }
            buffer.write2Bytes(Ops.TAGLESS_ELEMENT_LIST.toByte(), opcode.toByte())
            currentContainer.metadataOffset++
            buffer.reserve(lengthPrefixPreallocation)
        }
    }

    override fun stepInTaglessElementList(macroId: Int, macroName: String?) {
        openValue {
            currentContainer = containerStack.push { it.reset(TE_LIST, buffer.position(), true) }
            buffer.writeByte(Ops.TAGLESS_ELEMENT_LIST)
            currentContainer.metadataOffset++
            writeEExpMacroIdWithoutLengthPrefix(macroId)
            buffer.reserve(lengthPrefixPreallocation)
        }
    }

    override fun stepInTaglessElementSExp(opcode: Int) {
        openValue {
            currentContainer = containerStack.push { it.reset(TE_SEXP, buffer.position(), true) }
            buffer.write2Bytes(Ops.TAGLESS_ELEMENT_SEXP.toByte(), opcode.toByte())
            currentContainer.metadataOffset++
            buffer.reserve(lengthPrefixPreallocation)
        }
    }

    override fun stepInTaglessElementSExp(macroId: Int, macroName: String?) {
        openValue {
            currentContainer = containerStack.push { it.reset(TE_SEXP, buffer.position(), true) }
            buffer.writeByte(Ops.TAGLESS_ELEMENT_SEXP)
            currentContainer.metadataOffset++
            writeEExpMacroIdWithoutLengthPrefix(macroId)
            buffer.reserve(lengthPrefixPreallocation)
        }
    }

    override fun stepInTaglessEExp(id: Int, name: CharSequence?) {
        currentContainer = containerStack.push { it.reset(EEXP, buffer.position(), isLengthPrefixed = false) }
    }

    override fun writeTaglessInt(implicitOpcode: Int, value: Int) {
        // TODO: Bounds checking?
        // TODO: We can probably collapse these branches
        val currentContainer = currentContainer
        when (implicitOpcode) {
            Ops.INT_8,
            Ops.INT_16,
            Ops.INT_32,
            Ops.INT_64 -> {
                val length = implicitOpcode - Ops.INT_0
                buffer.writeFixedIntOrUInt(value.toLong(), length)
                currentContainer.length += length
            }
            Ops.TE_UINT_8,
            Ops.TE_UINT_16,
            Ops.TE_UINT_32,
            Ops.TE_UINT_64 -> {
                val length = implicitOpcode - 0xE0
                buffer.writeFixedIntOrUInt(value.toLong(), length)
                currentContainer.length += length
            }
            Ops.TE_FLEX_INT -> currentContainer.length += buffer.writeFlexInt(value.toLong())
            Ops.TE_FLEX_UINT -> currentContainer.length += buffer.writeFlexUInt(value)
            else -> throw IonException("Not a valid tagless int opcode: $implicitOpcode")
        }
        currentContainer.numChildren++
    }

    override fun writeTaglessInt(implicitOpcode: Int, value: Long) {
        // TODO: Bounds checking?
        // TODO: We can probably collapse these branches
        val currentContainer = currentContainer
        when (implicitOpcode) {
            Ops.INT_8,
            Ops.INT_16,
            Ops.INT_32,
            Ops.INT_64 -> {
                val length = implicitOpcode - Ops.INT_0
                buffer.writeFixedIntOrUInt(value, length)
                currentContainer.length += length
            }
            Ops.TE_UINT_8,
            Ops.TE_UINT_16,
            Ops.TE_UINT_32,
            Ops.TE_UINT_64 -> {
                val length = implicitOpcode - 0xE0
                buffer.writeFixedIntOrUInt(value, length)
                currentContainer.length += length
            }
            Ops.TE_FLEX_INT -> currentContainer.length += buffer.writeFlexInt(value)
            Ops.TE_FLEX_UINT -> currentContainer.length += buffer.writeFlexUInt(value)
            else -> throw IonException("Not a valid tagless int opcode: $implicitOpcode")
        }
        currentContainer.numChildren++
    }


    override fun writeTaglessFloat(implicitOpcode: Int, value: Float) {
        val currentContainer = currentContainer
        when (implicitOpcode) {
            Ops.FLOAT_16 -> TODO()
            Ops.FLOAT_32 -> {
                buffer.writeFixedIntOrUInt(floatToIntBits(value).toLong(), 4)
                currentContainer.length += 4
            }
            Ops.FLOAT_64 -> {
                buffer.writeFixedIntOrUInt(doubleToRawLongBits(value.toDouble()), 8)
                currentContainer.length += 8
            }
            else -> throw IonException("Not a valid tagless float opcode: $implicitOpcode")
        }
        currentContainer.numChildren++
    }

    override fun writeTaglessFloat(implicitOpcode: Int, value: Double) {
        val bytesWritten = when (implicitOpcode) {
            Ops.FLOAT_16 -> TODO()
            Ops.FLOAT_32 -> {
                buffer.writeFixedIntOrUInt(floatToIntBits(value.toFloat()).toLong(), 4)
                4
            }
            Ops.FLOAT_64 -> {
                buffer.writeFixedIntOrUInt(doubleToRawLongBits(value), 8)
                8
            }
            else -> throw IonException("Not a valid tagless float opcode: $implicitOpcode")
        }
        val currentContainer = currentContainer
        currentContainer.length += bytesWritten
        currentContainer.numChildren++
    }

    override fun writeTaglessSymbol(implicitOpcode: Int, id: Int) {
        val bytesWritten = when (implicitOpcode) {
            Ops.SYMBOL_VALUE_SID -> buffer.writeFlexUInt(id)
            Ops.TE_ANY_SYMBOL -> buffer.writeFlexInt(id.toLong())
            else -> throw IonException("Not a valid tagless symbol id opcode: $implicitOpcode")
        }
        val currentContainer = currentContainer
        currentContainer.length += bytesWritten
        currentContainer.numChildren++
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun writeTaglessSymbol(implicitOpcode: Int, text: CharSequence) {
        val bytesWritten = when (implicitOpcode) {
            Ops.VARIABLE_LENGTH_INLINE_SYMBOL -> {
                val encodedText = utf8StringEncoder.encode(text.toString())
                val encodedTextLength = encodedText.encodedLength
                val lengthOfLength = buffer.writeFlexUInt(encodedTextLength)
                buffer.writeBytes(encodedText.buffer, 0, encodedTextLength)
                lengthOfLength + encodedTextLength
            }
            Ops.TE_ANY_SYMBOL -> {
                val encodedText = utf8StringEncoder.encode(text.toString())
                val encodedTextLength = encodedText.encodedLength
                val lengthOfLength = buffer.writeFlexInt(-1 - encodedTextLength.toLong())
                buffer.writeBytes(encodedText.buffer, 0, encodedTextLength)
                lengthOfLength + encodedTextLength
            }
            else -> throw IonException("Not a valid tagless symbol text opcode: ${implicitOpcode.toByte().toHexString()}")
        }
        val currentContainer = currentContainer
        currentContainer.length += bytesWritten
        currentContainer.numChildren++
    }

    override fun writeTaglessString(implicitOpcode: Int, value: CharSequence) {
        if (implicitOpcode != Ops.VARIABLE_LENGTH_STRING) {
            throw IonException("Not a valid tagless string opcode: $implicitOpcode")
        }
        val encodedText = utf8StringEncoder.encode(value.toString())
        val encodedTextLength = encodedText.encodedLength
        val lengthOfLength = buffer.writeFlexUInt(encodedTextLength)
        buffer.writeBytes(encodedText.buffer, 0, encodedTextLength)

        val currentContainer = currentContainer
        currentContainer.length += lengthOfLength + encodedTextLength
        currentContainer.numChildren++
    }
}
