package com.amazon.ion.v8

import java.nio.charset.StandardCharsets

/**
 * WARNING, This was used for inspecting data with a specific set of known macros. It is not yet suitable for general purpose use.
 * However, it may be useful as the basis for a general purpose inspector at some point.
 */
object InspectorV8 {
    // TODO: Get away from having this hardcoded, support tagless args
    val macroSignatures = intArrayOf(
        // one
        0,
        // entry [all required parameters]
        13,
        // summary (name sum unit? count?)
        4,
        // group
        6,
        // sample (value repeat?)
        2,
        // dimension (class instance)
        2,
        // metric (name value unit? dimensions?)
        4,
        // metric_single (name value repeat? unit? dimensions?)
        5,
        // summary_ONE (name value count)
        3,
        // summary_ms (name value count?)
        3,
        // summary_B (name value count?)
        3,
        // summary_0 (name)
        // summary_1 (name)
        // summary_2 (name)
        // summary_3 (name)
        1, 1, 1, 1,
        // sample0 ()
        // sample1 ()
        // sample2 ()
        // sample3 ()
        0, 0, 0, 0
    )

    val macroNames = arrayOf(
        "one",
        "entry",
        "summary",
        "group",
        "sample",
        "dimension",
        "metric",
        "metric_single",
        "summary_ONE",
        "summary_ms",
        "summary_B",
        "summary0",
        "summary1",
        "summary2",
        "summary3",
        "sample0",
        "sample1",
        "sample2",
        "sample3",
    )

    val systemEExpNames = arrayOf(
        "",
        "set_symbols",
        "add_symbols",
        "set_macros",
        "add_macros",
        "use",
        "module",
        "encoding",
    )

    val blackhole: (String) -> Unit = {}

    fun inspect(
        source: ByteArray,
        sink: (String) -> Unit = ::println,
        range: IntRange = 0..Int.MAX_VALUE,
    ) {

        var n = 0
        var p = 0

        var effectiveSink = if (n in range) sink else blackhole


        sink("┌────────────┬──────────────────────────────────────────┬──────────────────────────────────────────────────────────────┐")
        p += inspectValue(0, p, source, effectiveSink)
        while (p < source.size) {
            n++
            effectiveSink("├────────────┼──────────────────────────────────────────┼──────────────────────────────────────────────────────────────┤")
            effectiveSink = if (n in range) sink else blackhole
            p += inspectValue(0, p, source, effectiveSink)
        }
    }

    private fun indent(depth: Int, text: Any) = "  ".repeat(depth) + text.toString()

    @OptIn(ExperimentalStdlibApi::class)
    private fun inspectValue(depth: Int, position: Int, source: ByteArray, sink: (String) -> Unit): Int {

        var p = position
        try {
            val opcode = source[p++].toInt().and(0xFF)

            when (opcode) {
                in 0x00..0x3F -> {
                    sink.writeRow(
                        position,
                        byteArrayOf(opcode.toByte()),
                        indent(depth, "(:${macroNames[opcode]}  <$opcode>")
                    )
                    val nArgs = macroSignatures[opcode]
                    repeat(nArgs) {
                        p += inspectValue(depth + 1, p, source, sink)
                    }
                    sink.writeRow(-1, byteArrayOf(), indent(depth, ")"))
                }

                in 0x40..0x5F -> TODO("Opcode ${opcode.toHexString()} at position=$position")
                in Ops.INT_0..Ops.INT_64 -> {
                    val length = opcode and 0xF
                    val value = IntHelper.readFixedIntAt(source, p, length)
                    sink.writeRow(position, source.sliceArray(position until p + length), indent(depth, value))
                    p += length
                }

                0x69 -> TODO("Invalid")
                Ops.FLOAT_0 -> {
                    sink.writeRow(position, byteArrayOf(0x6A), indent(depth, "0e0"))
                }

                Ops.FLOAT_16 -> TODO()
                Ops.FLOAT_32 -> {
                    val bits = IntHelper.readFixedIntAt(source, p, 4)
                    val float = Float.fromBits(bits.toInt())
                    sink.writeRow(position, source.sliceArray(position until p + 4), indent(depth, float))
                    p += 4
                }

                Ops.FLOAT_64 -> {
                    val bits = IntHelper.readFixedIntAt(source, p, 8)
                    val float = Double.fromBits(bits)
                    sink.writeRow(p - 1, source.sliceArray(position until p + 8), indent(depth, float))
                    p += 8
                }

                Ops.BOOL_TRUE -> sink.writeRow(p - 1, byteArrayOf(0x6E), indent(depth, true))
                Ops.BOOL_FALSE -> sink.writeRow(p - 1, byteArrayOf(0x6F), indent(depth, false))
                in Ops.DECIMAL_0..0x7F -> {
                    val length = opcode and 0xF
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p + length),
                        indent(depth, "(decimal value)")
                    )
                    p += length
                }

                in Ops.TIMESTAMP_YEAR_PRECISION..Ops.TIMESTAMP_NANOS_PRECISION_WITH_OFFSET -> {
                    val length = TimestampHelper.lengthOfShortTimestamp(opcode)
                    val ts = TimestampHelper.readShortTimestampAt(opcode, source, p)
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p + length),
                        indent(depth, "$ts")
                    )
                    p += length
                }

                Ops.ANNOTATION_SID -> {}
                Ops.ANNOTATION_TEXT -> {}
                Ops.NULL_NULL -> {}
                Ops.TYPED_NULL -> {}

                in (Ops.STRING_LENGTH_1)..(Ops.STRING_LENGTH_15) -> {
                    val length = opcode and 0xF
                    val text = String(source, p, length, StandardCharsets.UTF_8)
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p + length),
                        indent(depth, "\"$text\"")
                    )
                    p += length
                }

                Ops.SYMBOL_VALUE_SID -> {
                    val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
                    val length = valueAndLength.toInt().and(0xFF)
                    val sid = valueAndLength.ushr(8).toInt()
                    sink.writeRow(position, source.sliceArray(position until p + length), indent(depth, "\$$sid"))
                    p += length
                }

                in Ops.SYMBOL_VALUE_TEXT_ONE_LENGTH..Ops.SYMBOL_LENGTH_15 -> {
                    val length = opcode and 0xF
                    val text = String(source, p, length, StandardCharsets.UTF_8)
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p + length),
                        indent(depth, "'$text'")
                    )
                    p += length
                }

                in Ops.LIST_ZERO_LENGTH..Ops.LIST_LENGTH_15 -> {
                    val length = opcode and 0xF
                    sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "["))
                    val end = p + length
                    while (p < end) {
                        p += inspectValue(depth + 1, p, source, sink)
                    }
                    sink.writeRow(-1, byteArrayOf(), indent(depth, "]"))
                    p += length
                }

                in Ops.SEXP_ZERO_LENGTH..Ops.SEXP_LENGTH_15 -> {
                    val length = opcode and 0xF
                    sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "("))
                    val end = p + length
                    while (p < end) {
                        p += inspectValue(depth + 1, p, source, sink)
                    }
                    sink.writeRow(-1, byteArrayOf(), indent(depth, ")"))
                    p += length
                }

                0xD1 -> TODO("Invalid")
                in Ops.STRUCT_ZERO_LENGTH..Ops.STRUCT_LENGTH_15 -> {
                    val length = opcode and 0xF
                    sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "{"))
                    val end = p + length
                    while (p < end) {
                        p += inspectFieldName(depth + 1, p, source, sink)
                        p += inspectValue(depth + 2, p, source, sink)
                    }
                    sink.writeRow(-1, byteArrayOf(), indent(depth, "}"))
                    p += length
                }

                Ops.IVM -> {
                    sink.writeRow(position, source.sliceArray(position until position + 4), indent(depth, "< IVM >"))
                    p += 3
                }

                in Ops.SET_SYMBOLS ..Ops.ENCODING -> {
                    sink.writeRow(
                        position,
                        byteArrayOf(opcode.toByte()),
                        indent(depth, "(:\$${systemEExpNames[opcode and 0xF]}")
                    )
                    while (source[p].toInt().and(0xFF) != 0xF0) {
                        p += inspectValue(depth + 1, p, source, sink)
                    }
                    sink.writeRow(p, byteArrayOf(0xF0.toByte()), indent(depth, ")"))
                    p++
                }

                Ops.NOTHING_ARGUMENT -> sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "(:)"))
                Ops.TAGGED_PLACEHOLDER -> sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "(:?)"))
                Ops.TAGGED_PLACEHOLDER_WITH_DEFAULT -> {
                    sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "(:?"))
                    p += inspectValue(depth + 1, p, source, sink)
                    sink.writeRow(-1, byteArrayOf(), indent(depth, ")"))
                }

                0xEB -> TODO("Tagless parameters")
                0xEC -> TODO("Invalid/Reserved")
                0xED -> TODO("Homogeneous List")
                Ops.NOP -> sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "< NOP >"))
                Ops.NOP_L -> TODO("NOP with length")
                Ops.DELIMITED_CONTAINER_END -> TODO("Handled elsewhere")
                Ops.DELIMITED_LIST -> {
                    sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "["))
                    while (source[p].toInt().and(0xFF) != 0xF0) {
                        p += inspectValue(depth + 1, p, source, sink)
                    }
                    sink.writeRow(p, byteArrayOf(0xF0.toByte()), indent(depth, "]"))
                    p++
                }

                Ops.DELIMITED_SEXP -> {
                    sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "("))
                    while (source[p].toInt().and(0xFF) != 0xF0) {
                        p += inspectValue(depth + 1, p, source, sink)
                    }
                    sink.writeRow(p, byteArrayOf(0xF0.toByte()), indent(depth, ")"))
                    p++
                }

                Ops.DELIMITED_STRUCT -> {
                    sink.writeRow(position, byteArrayOf(opcode.toByte()), indent(depth, "{"))
                    p += inspectFieldName(depth + 1, p, source, sink)
                    while (source[p].toInt().and(0xFF) != 0xF0) {
                        p += inspectValue(depth + 2, p, source, sink)
                        p += inspectFieldName(depth + 1, p, source, sink)
                    }
                    sink.writeRow(p, byteArrayOf(0xF0.toByte()), indent(depth, "}"))
                    p++
                }

                0xF4 -> TODO("EExp w/ FlexUInt address")
                0xF5 -> TODO("EExp w/ FlexUInt address and FlexUInt length")
                0xFB, 0xFC -> {
                    TODO("Var prefixed list/sexp")
                }

                0xFD -> {
                    val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
                    val lengthOfLength = valueAndLength.toInt() and 0xFF
                    val lengthOfValue = valueAndLength.ushr(8).toInt()
                    p += lengthOfLength

                    val end = p + lengthOfValue

                    sink.writeRow(position, source.sliceArray(position..p), indent(depth, "{   (L=$lengthOfValue)"))

                    while (p < end) {
                        p += inspectFieldName(depth + 1, p, source, sink)
                        p += inspectValue(depth + 2, p, source, sink)
                    }
                    sink.writeRow(-1, byteArrayOf(), indent(depth, "}"))
                    p++
                }

                0xF6 -> {
                    val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
                    val lengthOfLength = valueAndLength.toInt() and 0xFF
                    val lengthOfValue = valueAndLength.ushr(8).toInt()
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p + lengthOfLength + lengthOfValue),
                        indent(depth, "(integer value)")
                    )
                    p += lengthOfLength + lengthOfValue
                }

                0xF7 -> {
                    val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
                    val lengthOfLength = valueAndLength.toInt() and 0xFF
                    val lengthOfValue = valueAndLength.ushr(8).toInt()
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p + lengthOfLength + lengthOfValue),
                        indent(depth, "(decimal value)")
                    )
                    p += lengthOfLength + lengthOfValue
                }

                0xF8 -> {
                    val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
                    val lengthOfLength = valueAndLength.toInt() and 0xFF
                    val lengthOfValue = valueAndLength.ushr(8).toInt()
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p + lengthOfLength + lengthOfValue),
                        indent(depth, "(timestamp value)")
                    )
                    p += lengthOfLength + lengthOfValue
                }

                0xF9 -> {
                    val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
                    val lengthOfLength = valueAndLength.toInt() and 0xFF
                    val lengthOfValue = valueAndLength.ushr(8).toInt()
                    p += lengthOfLength
                    val text = String(source, p, lengthOfValue, StandardCharsets.UTF_8)
                    p += lengthOfValue
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p),
                        indent(depth, "\"$text\"")
                    )
                }

                0xFA -> {
                    val valueAndLength = IntHelper.readFlexUIntValueAndLengthAt(source, p)
                    val lengthOfLength = valueAndLength.toInt() and 0xFF
                    val lengthOfValue = valueAndLength.ushr(8).toInt()
                    p += lengthOfLength
                    val text = String(source, p, lengthOfValue, StandardCharsets.UTF_8)
                    p += lengthOfValue
                    sink.writeRow(
                        position,
                        source.sliceArray(position until p),
                        indent(depth, "'$text'")
                    )
                }

                else -> TODO(opcode.toHexString())
            }


            return p - position
        } catch (e: Exception) {
            throw Exception("Problem at position $position, p=$p, slice=${source.sliceArray(position..p+10).joinToString(" ") { it.toHexString() }}", e)
        }
    }

    private fun inspectFieldName(depth: Int, position: Int, source: ByteArray, sink: (String) -> Unit): Int {
        val valueAndLength = IntHelper.readFlexIntValueAndLengthAt(source, position)
        val lengthOfLength = valueAndLength.toInt().and(0xFF)
        val sidOrTextLength = valueAndLength.shr(8).toInt()

        val end: Int
        val fieldNameNote: String

        if (sidOrTextLength < 0) {
            val textLength = -1 - sidOrTextLength
            end = position + lengthOfLength + textLength
            fieldNameNote = String(source, position + lengthOfLength, textLength, StandardCharsets.UTF_8) + ":"
        } else {
            end = position + lengthOfLength
            fieldNameNote = "\$$sidOrTextLength:"
        }
        sink.writeRow(position, source.sliceArray(position until end), "  ".repeat(depth) + fieldNameNote)
        return end - position
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun ((String) -> Unit).writeRow(pos: Int, value: ByteArray, note: String) {
        val position = if (pos < 0) "" else pos.toString()
        val byteString = if (value.size > 12) {
            value.take(8).joinToString(" ") { it.toHexString() } + " ... (${value.size - 8})"
        } else {
            value.joinToString(" ") { it.toHexString() }
        }
        this(String.format("│ %10s │ %-40s │ %-60s │", position, byteString, note))
    }
}
