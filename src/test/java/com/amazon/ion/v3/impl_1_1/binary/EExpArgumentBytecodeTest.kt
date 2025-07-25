package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.binary.MacroBytecodeGeneratorTest.Companion.assertBytecodeEquals
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.Operations.*
import com.amazon.ion.v3.impl_1_1.template.MacroBytecode.opToInstruction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EExpArgumentBytecodeTest {

    @Test
    fun foo() {
        val m = ExpressionBuilderDsl.macro("foo") {
            struct {
                fieldName("a")
                variable("foo")
            }
        }

        val args = IntList().apply {
            add(MacroBytecode.OP_START_ARGUMENT_VALUE.opToInstruction(2))
            add(MacroBytecode.OP_BOOL.opToInstruction(1))
            add(MacroBytecode.OP_END_ARGUMENT_VALUE.opToInstruction())
        }

        val bytecode = IntList()
        val constants = mutableListOf<Any?>()

        writeMacroBodyAsByteCode(m, args, bytecode, constants)

            assertBytecodeEquals(
                """
            $STRUCT_START 3
            $FNAME_CNST 0
            $BOOL true
            $CONTAINER_END
            """,
                bytecode
            )

        assertEquals(
            listOf("a"),
            constants
        )
    }

}
