package com.amazon.ion.v3.impl_1_1

import com.amazon.ion.Decimal
import com.amazon.ion.FakeSymbolToken
import com.amazon.ion.IonException
import com.amazon.ion.IonReader
import com.amazon.ion.IonSystem
import com.amazon.ion.SymbolToken
import com.amazon.ion.TestUtils
import com.amazon.ion.Timestamp
import com.amazon.ion.impl.macro.*
import com.amazon.ion.impl.macro.ParameterFactory.exactlyOneTagged
import com.amazon.ion.system.IonReaderBuilder
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ion.v3.impl_1_1.ExpressionBuilderDsl.Companion.templateBody
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlatMacroCompilerTest {


    val ion: IonSystem = IonSystemBuilder.standard().build()

    private val fakeMacroTable: (MacroRef) -> MacroV2? = {
        if (it.name == "values" || it.id == 12) SystemMacro.Values else null
    }

    private data class MacroSourceAndTemplate(val source: String, val template: MacroV2) : Arguments {
        override fun get(): Array<Any> = arrayOf(source, template.signature, template.body!!)
    }


    private infix fun String.shouldCompileTo(macro: MacroV2) = MacroSourceAndTemplate(this, macro)

    private fun testCases() = listOf(
        "(macro identity (x) (%x))" shouldCompileTo MacroV2(
            listOf(Macro.Parameter("x", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)),
            templateBody { variable(0) },
        ),
        "(macro identity (any::x) (%x))" shouldCompileTo MacroV2(
            listOf(Macro.Parameter("x", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)),
            templateBody { variable(0) },
        ),
        "(macro pi () 3.141592653589793)" shouldCompileTo MacroV2(
            signature = emptyList(),
            body = templateBody {
                decimal(BigDecimal("3.141592653589793"))
            },
        ),
        "(macro cardinality_test (x?) (%x))" shouldCompileTo MacroV2(
            signature = listOf(
                Macro.Parameter(
                    "x",
                    Macro.ParameterEncoding.Tagged,
                    Macro.ParameterCardinality.ZeroOrOne
                )
            ),
            body = templateBody { variable(0) },
        ),
        "(macro cardinality_test (x!) (%x))" shouldCompileTo MacroV2(
            signature = listOf(
                Macro.Parameter(
                    "x",
                    Macro.ParameterEncoding.Tagged,
                    Macro.ParameterCardinality.ExactlyOne
                )
            ),
            templateBody { variable(0) },
        ),
        "(macro cardinality_test (x+) (%x))" shouldCompileTo MacroV2(
            signature = listOf(
                Macro.Parameter(
                    "x",
                    Macro.ParameterEncoding.Tagged,
                    Macro.ParameterCardinality.OneOrMore
                )
            ),
            templateBody { variable(0) },
        ),
        "(macro cardinality_test (x*) (%x))" shouldCompileTo MacroV2(
            signature = listOf(
                Macro.Parameter(
                    "x",
                    Macro.ParameterEncoding.Tagged,
                    Macro.ParameterCardinality.ZeroOrMore
                )
            ),
            templateBody { variable(0) },
        ),
        // Outer '.values' call allows multiple expressions in the body
        // The second `.values` is a macro call that has a single argument: the variable `x`
        // The third `(values x)` is an uninterpreted s-expression.
        """(macro literal_test (x) (.values (.values (%x)) (values x)))""" shouldCompileTo MacroV2(
            signature = listOf(exactlyOneTagged("x")),
            templateBody {
                macro(SystemMacro.Values) {
                    macro(SystemMacro.Values) {
                        variable(0)
                    }
                    sexp {
                        symbol("values")
                        symbol("x")
                    }
                }
            },
        ),
        "(macro each_type () (.values (.. null true 1 ${"9".repeat(50)} 1e0 1d0 2024-01-16T \"foo\" bar [] () {} {{}} {{\"\"}} )))" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                expressionGroup {
                    nullValue()
                    bool(true)
                    int(1L)
                    int(BigInteger("9".repeat(50)))
                    float(1.0)
                    decimal(Decimal.ONE)
                    timestamp(Timestamp.valueOf("2024-01-16T"))
                    string("foo")
                    symbol("bar")
                    list {  }
                    sexp {  }
                    struct {  }
                    blob(ByteArray(0))
                    clob(ByteArray(0))
                }
            },
        ),
        """(macro foo () (.values 42 "hello" false))""" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                macro(SystemMacro.Values) {
                    int(42)
                    string("hello")
                    bool(false)
                }
            }
        ),

        """(macro constant_list () [42, "hello", false])""" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                list {
                    int(42)
                    string("hello")
                    bool(false)
                }
            }
        ),

        """(macro constant_sexp () (42 "hello" false))""" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                sexp {
                    int(42)
                    string("hello")
                    bool(false)
                }
            }
        ),

        """(macro using_expr_group () (.values (.. 42 "hello" false)))""" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                macro(SystemMacro.Values) {
                    expressionGroup {
                        int(42)
                        string("hello")
                        bool(false)
                    }
                }
            }
        ),
        """(macro invoke_by_id () (.12 true false))""" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                macro(SystemMacro.Values) {
                    bool(true)
                    bool(false)
                }
            }
        ),
        "(macro null () \"abc\")" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                string("abc")
            }
        ),
        "(macro foo (x y z) [100, [200, a::b::300], (%x), {y: [true, false, (%z)]}])" shouldCompileTo MacroV2(
            signature = listOf(
                Macro.Parameter("x", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
                Macro.Parameter("y", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne),
                Macro.Parameter("z", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)
            ),
            body = templateBody {
                list {
                    int(100)
                    list {
                        int(200)
                        annotated(arrayOf("a", "b"), ::int, 300)
                    }
                    variable(0)
                    struct {
                        fieldName("y")
                        list {
                            bool(true)
                            bool(false)
                            variable(2)
                        }
                    }
                }
            },
        )
    )

    private fun newReader(source: String): IonReader {
        return IonReaderBuilder.standard()
            .build(TestUtils.ensureBinary(ion, source.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun assertMacroCompilation(source: String, signature: List<Macro.Parameter>, body: List<TemplateBodyExpressionModel>) {
        val reader = newReader(source)
        val compiler = FlatMacroCompiler(fakeMacroTable, reader)
        reader.next()
        val macroDef = compiler.compileMacro()
        val expectedDef = MacroV2(signature, body)
        Assertions.assertEquals(expectedDef, macroDef)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    fun assertMacroCompilationIonReader(source: String, signature: List<Macro.Parameter>, body: List<TemplateBodyExpressionModel>) {
        assertMacroCompilation(source, signature, body)
    }


    @Test
    fun foo() {
        assertMacroCompilation(
            """(macro foo () (.values (.. 42 "hello" false)))""",
            signature = emptyList(),
            templateBody {
                expressionGroup {
                    int(42)
                    string("hello")
                    bool(false)
                }
            }
        )
    }

    @Test
    fun `test reading a list of macros`() {
        // This test case is essentially the same as the last one, except that it puts all the macro definitions into
        // one Ion list, and then compiles them sequentially from that list.
        // If this test fails, do not bother trying to fix it until all cases in the parameterized test are passing.
        val source = "[${testCases().joinToString(",") { it.source }}]"
        val templates = testCases().map { it.template }.iterator()

        val reader = newReader(source)
        val compiler = FlatMacroCompiler(fakeMacroTable, reader)
        // Advance and step into list
        reader.next(); reader.stepIn()
        while (reader.next() != null) {
            val macroDef = compiler.compileMacro()
            val expectedDef = templates.next()
            Assertions.assertEquals(expectedDef, macroDef)
        }
        reader.stepOut()
        reader.close()
    }

    @Test
    fun `macro compiler should return the correct name`() {
        val reader = newReader(
            """
            (macro foo (x) 1)
            (macro bar (y) 2)
            (macro null (z) 3)
        """
        )
        val compiler = FlatMacroCompiler(fakeMacroTable, reader)
        Assertions.assertNull(compiler.macroName)
        reader.next()
        compiler.compileMacro()
        Assertions.assertEquals("foo", compiler.macroName)
        reader.next()
        compiler.compileMacro()
        Assertions.assertEquals("bar", compiler.macroName)
        reader.next()
        compiler.compileMacro()
        Assertions.assertNull(compiler.macroName)
    }

    // macro with invalid variable
    // try compiling something that is not a sexp
    // macro missing keyword
    // macro has invalid name
    // macro has annotations

    private fun badMacros() = listOf(
        // There should be exactly one thing wrong in each of these samples.

        // Problems up to and including the macro name
        "[macro, pi, (), 3.141592653589793]", // Macro def must be a sexp
        "foo::(macro pi () 3.141592653589793)", // Macros cannot be annotated
        """("macro" pi () 3.141592653589793)""", // 'macro' must be a symbol
        "(pi () 3.141592653589793)", // doesn't start with 'macro'
        "(macaroon pi () 3.141592653589793)", // doesn't start with 'macro'
        "(macroeconomics pi () 3.141592653589793)", // will the demand for digits of pi ever match the supply?
        "(macro pi::pi () 3.141592653589793)", // Illegal annotation on macro name
        "(macro () 3.141592653589793)", // No macro name
        "(macro 2.5 () 3.141592653589793)", // Macro name is not a symbol
        """(macro "pi"() 3.141592653589793)""", // Macro name is not a symbol
        "(macro \$0 () 3.141592653589793)", // Macro name must have known text
        "(macro + () 123)", // Macro name cannot be an operator symbol
        "(macro 'a.b' () 123)", // Macro name must be a symbol that can be unquoted (i.e. an identifier symbol)
        "(macro 'false' () 123)", // Macro name must be a symbol that can be unquoted (i.e. an identifier symbol)

        // Problems in the signature
        "(macro identity x x)", // Missing sexp around signature
        "(macro identity [x] x)", // Using list instead of sexp for signature
        "(macro identity any::(x) x)", // Signature sexp should not be annotated
        "(macro identity (foo::x) x)", // Unknown type in signature
        "(macro identity (x any::*) x)", // Annotation should be on parameter name, not the cardinality
        "(macro identity (x! !) x)", // Dangling cardinality modifier
        "(macro identity (x%) x)", // Not a real cardinality sigil
        "(macro identity (x x) x)", // Repeated parameter name
        """(macro identity ("x") x)""", // Parameter name must be a symbol, not a string

        // Problems in the body
        "(macro empty ())", // No body expression
        "(macro transform (x) (%y))", // Unknown variable
        "(macro transform (x) foo::(%x))", // Variable expansion cannot be annotated
        "(macro transform (x) (foo::%x))", // Variable expansion operator cannot be annotated
        "(macro transform (x) (%foo::x))", // Variable name cannot be annotated
        "(macro transform (x) foo::(.values x))", // Macro invocation cannot be annotated
        "(macro transform (x) (foo::.values x))", // Macro invocation operator cannot be annotated
        "(macro transform (x) (.))", // Macro invocation operator must be followed by macro reference
        """(macro transform (x) (."values" x))""", // Macro invocation must start with a symbol or integer id
        """(macro transform (x) (.values foo::(..)))""", // Expression group may not be annotated
        """(macro transform (x) (.values (foo::..)))""", // Expression group operator may not be annotated
        "(macro transform (x) 1 2)", // Template body must be one expression
    )


    @ParameterizedTest
    @MethodSource("badMacros")
    fun assertCompilationFailsIonReader(source: String) {
        assertCompilationFails(source)
    }

    private fun assertCompilationFails(source: String) {
        val reader = newReader(source)
        reader.next()
        val compiler = FlatMacroCompiler(fakeMacroTable, reader)
        assertThrows<IonException> { compiler.compileMacro() }
    }
}
