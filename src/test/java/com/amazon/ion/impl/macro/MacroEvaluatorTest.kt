package com.amazon.ion.impl.macro

import com.amazon.ion.FakeSymbolToken
import com.amazon.ion.IonType
import com.amazon.ion.impl.macro.Expression.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MacroEvaluatorTest {

    // Variables in a list
    // Args pass through to nested macros
    // Nested constant evaluation
    // trivial variable substitution
    // Trivial constant evaluation
    // Make String
    // Values
    // Annotate

    private object Macros {
        val MAKE_STRING = "make_string" to SystemMacro.MakeString
        val VALUES = "values" to SystemMacro.Values
        val IDENTITY = "identity" to template("x!") {
            +VariableReference(0)
        }
        val DOUBLE_IDENTITY = "double_identity" to template("x!") {
            +ListValue(emptyList(), 0, 2)
            +VariableReference(0)
            +VariableReference(0)
        }
        val PI = "pi" to template("") {
            +FloatValue(emptyList(), 3.14159)
        }
    }

    val encodingContext = encodingContext(
        Macros.VALUES,
        Macros.MAKE_STRING,
        Macros.IDENTITY,
        Macros.DOUBLE_IDENTITY,
        Macros.PI,
        "nested_identity" to template("x!") {
            +MacroInvocation(MacroRef.ByName("identity"), 0, 1)
            +VariableReference(0)
        },
        "special_number" to template("") {
            +MacroInvocation(MacroRef.ByName("pi"), 0, 0)
        },
        "foo" to template("") {
            +ListValue(emptyList(), 0, 3)
            +StringValue(emptyList(), "a")
            +StringValue(emptyList(), "b")
            +StringValue(emptyList(), "c")
        },
        "voidable_identity" to template("x?") {
            +VariableReference(0)
        },
    )
    val evaluator = MacroEvaluator(encodingContext)

    @Test
    fun `a trivial constant macro evaluation`() {
        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("pi"), 0, 0)
            )
        )
        assertEquals(FloatValue(emptyList(), 3.14159), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `a nested constant macro evaluation`() {
        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("special_number"), 0, 0)
            )
        )
        assertEquals(FloatValue(emptyList(), 3.14159), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `constant macro with empty list`() {
        val evaluator = MacroEvaluator(
            encodingContext(
                "foo" to template("") {
                    +ListValue(emptyList(), 0, 0)
                }
            )
        )

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("foo"), 0, 0)
            )
        )

        assertEquals(IonType.LIST, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `constant macro with single element list`() {
        val evaluator = MacroEvaluator(
            encodingContext(
                "foo" to template("") {
                    +ListValue(emptyList(), 0, 1)
                    +StringValue(value = "a")
                }
            )
        )

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("foo"), 0, 0)
            )
        )

        assertEquals(IonType.LIST, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(StringValue(value = "a"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `constant macro with multi element list`() {
        val evaluator = MacroEvaluator(
            encodingContext(
                "foo" to template("") {
                    +ListValue(emptyList(), 0, 3)
                    +StringValue(value = "a")
                    +StringValue(value = "b")
                    +StringValue(value = "c")
                }
            )
        )

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("foo"), 0, 0)
            )
        )

        assertEquals(IonType.LIST, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(StringValue(value = "a"), evaluator.expandNext())
        assertEquals(StringValue(value = "b"), evaluator.expandNext())
        assertEquals(StringValue(value = "c"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }


    @Test
    fun `a trivial variable substitution`() {

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("identity"), 0, 1),
                BoolValue(emptyList(), true)
            )
        )

        assertEquals(BoolValue(emptyList(), true), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `a trivial variable substitution with empty list`() {

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("identity"), 0, 1),
                ListValue(emptyList(), 1, 1)
            )
        )

        assertEquals(IonType.LIST, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `a trivial variable substitution with single element list`() {

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("identity"), 0, 2),
                ListValue(emptyList(), 1, 2),
                StringValue(value = "a"),
            )
        )

        assertEquals(IonType.LIST, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(StringValue(value = "a"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `a variable that gets used twice`() {

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("double_identity"), 0, 1),
                StringValue(value = "a"),
            )
        )

        assertEquals(IonType.LIST, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(StringValue(value = "a"), evaluator.expandNext())
        assertEquals(StringValue(value = "a"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `invoke values with scalars`() {

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("values"), 0, 3),
                ExpressionGroup(1, 3),
                LongIntValue(emptyList(), 1),
                StringValue(emptyList(), "a")
            )
        )

        assertEquals(LongIntValue(emptyList(), 1), evaluator.expandNext())
        assertEquals(StringValue(emptyList(), "a"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `a trivial nested variable substitution`() {

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("nested_identity"), 0, 1),
                BoolValue(emptyList(), true)
            )
        )

        assertEquals(BoolValue(emptyList(), true), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `a trivial void variable substitution`() {
        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("voidable_identity"), 0, 0),
            )
        )
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `simple make_string`() {

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("make_string"), 0, 4),
                ExpressionGroup(1, 4),
                StringValue(emptyList(), "a"),
                StringValue(emptyList(), "b"),
                StringValue(emptyList(), "c"),
            )
        )

        assertEquals(StringValue(emptyList(), "abc"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `nested make_string`() {

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("make_string"), 0, 7),
                ExpressionGroup(1, 7),
                StringValue(emptyList(), "a"),
                EExpression(MacroRef.ByName("make_string"), 3, 7),
                ExpressionGroup(4, 7),
                StringValue(emptyList(), "b"),
                StringValue(emptyList(), "c"),
                StringValue(emptyList(), "d"),
            )
        )

        assertEquals(StringValue(emptyList(), "abcd"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `macro with a variable substitution in struct field position`() {
        val evaluator = MacroEvaluator(
            encodingContext(
                "foo" to template("x*") {
                    +StructValue(emptyList(), 0, 2, mapOf("foo" to listOf(2)))
                    +FieldName(FakeSymbolToken("foo", -1))
                    +VariableReference(0)
                }
            )
        )

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("foo"), 0, 1),
                StringValue(value = "bar")
            )
        )

        assertEquals(IonType.STRUCT, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(FieldName(FakeSymbolToken("foo", -1)), evaluator.expandNext())
        assertEquals(StringValue(value = "bar"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `macro with a variable substitution in struct field position with multiple arguments`() {
        val evaluator = MacroEvaluator(
            encodingContext(
                "foo" to template("x*") {
                    +StructValue(emptyList(), 0, 2, mapOf("foo" to listOf(2)))
                    +FieldName(FakeSymbolToken("foo", -1))
                    +VariableReference(0)
                }
            )
        )

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("foo"), 0, 1),
                ExpressionGroup(1, 3),
                StringValue(value = "bar"),
                StringValue(value = "baz")
            )
        )

        assertEquals(IonType.STRUCT, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(FieldName(FakeSymbolToken("foo", -1)), evaluator.expandNext())
        assertEquals(StringValue(value = "bar"), evaluator.expandNext())
        assertEquals(StringValue(value = "baz"), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `macro with a variable substitution in struct field position with void argument`() {
        val evaluator = MacroEvaluator(
            encodingContext(
                "foo" to template("x*") {
                    +StructValue(emptyList(), 0, 2, mapOf("foo" to listOf(2)))
                    +FieldName(FakeSymbolToken("foo", -1))
                    +VariableReference(0)
                }
            )
        )

        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("foo"), 0, 1),
                ExpressionGroup(1, 1),
            )
        )

        assertEquals(IonType.STRUCT, evaluator.expandNext()?.type)
        evaluator.stepIn()
        assertEquals(FieldName(FakeSymbolToken("foo", -1)), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
        evaluator.stepOut()
        assertEquals(null, evaluator.expandNext())
    }

    @Test
    fun `e-expression with another e-expression as one of the arguments`() {
        evaluator.initExpansion(
            listOf(
                EExpression(MacroRef.ByName("identity"), 0, 1),
                EExpression(MacroRef.ByName("pi"), 1, 1),
            )
        )

        assertEquals(FloatValue(emptyList(), 3.14159), evaluator.expandNext())
        assertEquals(null, evaluator.expandNext())
    }

    companion object {
        private fun encodingContext(vararg pairs: Pair<Any, Macro>): EncodingContext {
            return EncodingContext(pairs.associate { (k, v) ->
                when (k) {
                    is Number -> MacroRef.ById(k.toLong())
                    is String -> MacroRef.ByName(k)
                    else -> throw IllegalArgumentException("Unsupported macro id $k")
                } to v
            })
        }

        fun signature(text: String): List<Macro.Parameter> {
            if (text.isBlank()) return emptyList()
            return text.split(Regex(" +")).map {
                val cardinality = Macro.ParameterCardinality.fromSigil("${it.last()}")
                if (cardinality == null) {
                    Macro.Parameter(it, Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)
                } else {
                    Macro.Parameter(it.dropLast(1), Macro.ParameterEncoding.Tagged, cardinality)
                }
            }
        }

        fun template(parameters: String, body: BodyBuilder.() -> Unit): TemplateMacro {
            return TemplateMacro(
                signature(parameters),
                BodyBuilder().apply(body).expressions
            )
        }
    }

    class BodyBuilder {
        val expressions = mutableListOf<TemplateBodyExpression>()

        operator fun TemplateBodyExpression.unaryPlus() {
            expressions += this
        }
    }
}
