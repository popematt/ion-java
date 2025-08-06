package com.amazon.ion.v3.visitor2

import com.amazon.ion.v3.impl_1_1.binary.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VisitingIonReader(
    private val source: ByteBuffer
) {
    init {
        source.order(ByteOrder.LITTLE_ENDIAN)
    }
    private var i = 0

    /**
     * Returns true if a value was visited.
     */
    fun next(visitor: IonVisitor): Boolean {
        return false
    }

    private fun visitTopLevelUserValueAt(source: ByteBuffer, position: Int, visitor: IonVisitor): Int {
        var p = position
        val opcode = (source.get(p++).toInt() and 0xFF)

        when (opcode) {
            0x60 -> visitor.onLong(null, 0)
            0x61 -> visitor.onLong(null, IntHelper.readFixedIntAt(source, p++, 1))
            0x62 -> {
                visitor.onLong(null, IntHelper.readFixedIntAt(source, p, 2))
                p += 2
            }


        }

        return p - position
    }

    private fun visitPrefixedSeqAt(source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        // While p < end, visit the value
        return p - position
    }

    private fun visitDelimitedSeqAt(source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        // While not 0xF0, visit the value
        return p - position
    }

    private fun visitPrefixedSidStructAt(source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        // While p < end, read a field name and then visit the value
        return p - position
    }

    private fun visitPrefixedFlexSymStructAt(source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        // While p < end, read a field name and then visit the value
        return p - position
    }

    private fun visitDelimitedStructAt(source: ByteBuffer, position: Int, visitor: IonFieldVisitor): Int {
        var p = position
        // While not 0xF0, read a field name and then visit the value
        return p - position
    }
}



