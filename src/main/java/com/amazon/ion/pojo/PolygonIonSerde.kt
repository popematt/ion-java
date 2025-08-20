package com.amazon.ion.pojo

import com.amazon.ion.IonException
import com.amazon.ion.impl.bin.FixedInt
import com.amazon.ion.impl.bin.OpCodes
import com.amazon.ion.impl.bin.WriteBuffer
import com.amazon.ion.impl.macro.ExpressionBuilderDsl
import com.amazon.ion.impl.macro.ParameterFactory
import com.amazon.ion.impl.macro.TemplateMacro

object PolygonIonSerde: IonSerdeHelper<Polygon> {
    override val MACRO_NAME = Polygon::class.simpleName!!.replace(".", "_")
    override val MACRO = TemplateMacro(
        signature = listOf(
            ParameterFactory.zeroToManyTagged("points"),
        ),
        ExpressionBuilderDsl.templateBody {
            struct {
                fieldName("points")
                list {
                    variable(0)
                }
            }
        }
    )

    override fun writeArguments(value: Polygon, buffer: WriteBuffer) {
        val points = value.points

        // We're going to assume that the polygon has at least 3 points, so we'll always use an expression group.

        // Presence bits
        buffer.writeByte(0b00000010)

        // We're going to assume that you never need only part of the polygon, so we won't bother using prefixes in the arguments.

        // FixedUInt 0 -- signalling delimited expression group.
        buffer.writeByte(0x01)

        val macroAddress = 1

        for (point in points) {
            IonSerdeHelper.writeMacroAddress(macroAddress, buffer)
            Point2DIonSerde.writeArguments(point, buffer)
        }
        // Delimited end marker
        buffer.writeByte(0xF0.toByte())
    }

    fun read(position: Int, source: ByteArray): Polygon {
        val opcode = source[position]

        // TODO: If macro, read macro address
        //       Then look up macro definition.
        // if macro matches, use `readArguments` else use `readExpandedValue`
        TODO()
    }

    fun readArguments(position: Int, source: ByteArray): Polygon {
        var p = position
        val presenceBits = source[p++]

        val points = when (presenceBits.toInt()) {
            0 -> emptyList()
            1 -> {

                listOf()
            }
            2 -> {
                val l = mutableListOf<Point2D>()
                // read the macro address

                // read the point value


                l
            }
            else -> throw IonException("Invalid presence bits as position $position")
        }

        return Polygon(points)
    }

}
