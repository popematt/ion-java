package com.amazon.ion.impl.macro

import com.amazon.ion.impl.macro.Expr.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Macro2Test {

    @Test
    fun `variables in a list`() {
        val encodingContext = encodingContext(
            templateMacro("xy", "x! y!", list(var_(0), var_(1)))
        )

        val evaluator = Evaluator2(encodingContext)

        val expandedIterator = evaluator.evaluate(
            eexp("xy",
                int(1L),
                int(2L)
            )
        )

        expandedIterator.expect<ListValue> {
            expectInt(1)
            expectInt(2)
        }
    }

    @Test
    fun `a constant list`() {
        val macro = TemplateMacro2(
            listOf(),
            ListValue(listOf(LongIntValue(1L), LongIntValue(2L)))
        )

        val encodingContext = Encoding2Context(mapOf(MacroRef.ByName("xy") to macro))

        val evaluator = Evaluator2(encodingContext)

        val expandedIterator = evaluator.evaluate(EExpression(MacroRef.ByName("xy"), listOf()))

        expandedIterator.expect<ListValue> {
            expectInt(1)
            expectInt(2)
        }
    }

    @Test
    fun `a trivial variable substitution`() {
        val macro = TemplateMacro2(
            listOf(Macro.Parameter("x", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)),
            VariableReference(0)
        )

        val encodingContext = Encoding2Context(mapOf(MacroRef.ByName("xy") to macro))

        val evaluator = Evaluator2(encodingContext)

        val expandedIterator = evaluator.evaluate(
            EExpression(MacroRef.ByName("xy"), listOf(LongIntValue(1L)))
        )
        expandedIterator.expectInt(1)
    }

    @Test
    fun `nested constant evaluation`() {
        val fooMacro = TemplateMacro2(listOf(),
            ListValue(listOf(
                MacroInvocation(MacroRef.ByName("bar"), listOf()),
                MacroInvocation(MacroRef.ByName("bar"), listOf()),
            ))
        )
        val barMacro = TemplateMacro2(listOf(), LongIntValue(123L))

        val encodingContext = encodingContext(
            "foo" to fooMacro,
            "bar" to barMacro,
        )

        val evaluator = Evaluator2(encodingContext)

        val expandedIterator = evaluator.evaluate(
            EExpression(MacroRef.ByName("foo"), listOf(LongIntValue(1L)))
        )
        expandedIterator.expect<ListValue> {
            expect<MacroInvocation> { expectInt(123) }
            expect<MacroInvocation> { expectInt(123) }
        }
    }

    @Test
    fun `args pass through to nested macros`() {
        val fooMacro = TemplateMacro2(listOf(
            Macro.Parameter("a", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
            Macro.Parameter("b", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
        ),
            ListValue(listOf(
                MacroInvocation(MacroRef.ByName("bar"), listOf(VariableReference(0))),
                MacroInvocation(MacroRef.ByName("bar"), listOf(VariableReference(1))),
            )
        ))
        val barMacro = TemplateMacro2(listOf(
            Macro.Parameter("c", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
        ), ListValue(listOf(VariableReference(0))))

        val encodingContext = encodingContext(
            "foo" to fooMacro,
            "bar" to barMacro,
        )

        val evaluator = Evaluator2(encodingContext)

        val expandedIterator = evaluator.evaluate(
            EExpression(MacroRef.ByName("foo"), listOf(LongIntValue(11), LongIntValue(22)))
        )
        with(expandedIterator) {
            expect<ListValue> {
                expect<MacroInvocation> {
                    expect<ListValue> {
                        expectInt(11)
                    }
                }
                expect<MacroInvocation> {
                    expect<ListValue> {
                        expectInt(22)
                    }
                }
            }
        }
    }

    private inline fun <reified T: IterableExpr> ExprIterator.expect(contentExpectations: ExprIterator.() -> Unit) {
        assertTrue(hasNext())
        next().let { expr ->
            assertIsInstance<T>(expr)
            stepInto(expr)
                .apply(contentExpectations)
                .let { assertFalse(it.hasNext()) }
        }
    }

    private fun ExprIterator.expectInt(expected: Long) {
        assertTrue(hasNext())
        val next = next()
        assertIsInstance<IntValue>(next)
        when (next) {
            is LongIntValue -> assertEquals(expected, next.value)
            is BigIntValue -> assertEquals(expected.toBigInteger(), next.value)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <reified T> assertIsInstance(value: Any?) {
        contract { returns() implies (value is T) }
        if (value is T) return
        assertEquals(
            T::class.qualifiedName,
            value?.let { it::class.qualifiedName }
        )
    }

    private fun encodingContext(vararg pairs: Pair<Any, Macro2>): Encoding2Context {
        return Encoding2Context(pairs.associate { (k, v) ->
            when (k) {
                is Number -> MacroRef.ById(k.toLong())
                is String -> MacroRef.ByName(k)
                else -> throw IllegalArgumentException("Unsupported macro id $k")
            } to v
        })
    }

    fun templateMacro(name: String, signature: String, body: Expr): Pair<String, TemplateMacro2> = name to TemplateMacro2(signature.split(Regex(" +")).map {
        val cardinality = Macro.ParameterCardinality.fromSigil("${it.last()}")
        if (cardinality == null) {
            Macro.Parameter(it, Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)
        } else {
            Macro.Parameter(it.dropLast(1), Macro.ParameterEncoding.Tagged, cardinality)
        }
    }, body)

    fun list(vararg content: Expr): Expr = ListValue(content.toList())
    fun sexp(vararg content: Expr): Expr = SExpValue(content.toList())
    fun group(vararg content: Expr): Expr = ExpressionGroup(content.toList())
    fun macro(name: String, vararg args: Expr): Expr = MacroInvocation(MacroRef.ByName(name), args.toList())
    fun eexp(name: String, vararg args: Expr) = EExpression(MacroRef.ByName(name), args.toList())
    fun int(value: Long): Expr = LongIntValue(value)
    fun var_(index: Int): Expr = VariableReference(index)

}
