package com.amazon.ion.v3.impl_1_1.binary

import com.amazon.ion.*
import com.amazon.ion.impl.bin.IntList
import com.amazon.ion.impl.macro.*
import com.amazon.ion.v3.impl_1_1.*
import com.amazon.ion.v3.impl_1_1.ExpressionBuilderDsl.Companion.templateBody
import com.amazon.ion.v3.impl_1_1.TemplateDsl
import com.amazon.ion.v3.impl_1_1.template.*
import com.amazon.ionelement.api.field
import java.lang.StringBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MacroBytecodeGeneratorTest {

    @Test
    fun test() {
        val m = template("foo") {
            struct {
                fieldName("some_ints")
                list {
                    int(1)
                    int(2)
                    int(-3)
                    int(1000000)
                    int(1000000000000)
                }
                fieldName("foo")
                variable("foo")
                fieldName("an_annotated_string")
                annotated(arrayOf("a", "b", "c"), ::string, "def")
                fieldName("a_float")
                float(1.0)
                fieldName("nulls")
                sexp {
                    nullValue()
                    nullValue(IonType.CLOB)
                }
                fieldName("a_bool")
                bool(true)
            }
        }

        MacroBytecode.debugString(m.bytecode)
    }

    @Test
    fun `multi-annotation value`() = expectBytecodeForMacro(
        template {
            annotated(arrayOf("foo", "bar"), ::nullValue, IonType.NULL)
        },
        """
        ANN_CNST 0
        ANN_CNST 1
        NULL
        EOF
        """
    )

    @Test
    fun `positive short int`() = expectBytecodeForMacro(
        template { int(5) },
        """
        INT16 5
        EOF
        """
    )

    @Test
    fun `negative short int`() = expectBytecodeForMacro(
        template { int(-3) },
        """
        INT16 -3
        EOF
        """
    )


    private fun expectBytecodeForMacro(macroV2: MacroV2, expectedBytecode: String) {
        assertBytecodeEquals(expectedBytecode, macroV2.bytecode)
    }

    companion object {
        fun assertBytecodeEquals(expected: String, actual: IntList) {
            assertBytecodeEquals(expected, actual.toArray())
        }

        fun assertBytecodeEquals(expected: String, actual: IntArray) {
            val actualBytecode = StringBuilder()
            MacroBytecode.debugString(actual, actualBytecode::append)
            assertEquals(
                expected
                    .trimIndent()
                    .lines()
                    .joinToString("\n") { it.trimEnd() },
                actualBytecode.toString()
                    .trim()
                    .lines()
                    .joinToString("\n") { it.trimEnd() },
            )
            println(actualBytecode)
        }
    }

    private fun template(vararg parameters: String, body: TemplateDsl.() -> Unit): MacroV2 {
        val signature = parameters.map {
            val cardinality = Macro.ParameterCardinality.fromSigil("${it.last()}")
            if (cardinality == null) {
                Macro.Parameter(it, Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)
            } else {
                Macro.Parameter(it.dropLast(1), Macro.ParameterEncoding.Tagged, cardinality)
            }
        }
        return MacroV2(signature, templateBody(signature, body))
    }
}
