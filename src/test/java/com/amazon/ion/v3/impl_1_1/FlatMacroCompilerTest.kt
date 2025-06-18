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
import com.amazon.ion.impl.macro.ParameterFactory.zeroOrOneTagged
import com.amazon.ion.system.IonReaderBuilder
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ion.v3.impl_1_1.ExpressionBuilderDsl.Companion.templateBody
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlatMacroCompilerTest {

    object LogMacro {
        internal fun template(vararg parameters: String, body: TemplateDsl.() -> Unit): MacroV2 {
            val signature = parameters.map {
                val cardinality = Macro.ParameterCardinality.fromSigil("${it.last()}")
                if (cardinality == null) {
                    Macro.Parameter(it, Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne)
                } else {
                    Macro.Parameter(it.dropLast(1), Macro.ParameterEncoding.Tagged, cardinality)
                }
            }
            return MacroV2(signature, templateBody(body))
        }


        val One = template { symbol("") }
        val ONE_DEF = "(macro one ( ) '' )"

        val Sample = MacroV2(
            signature = listOf(exactlyOneTagged("value"), zeroOrOneTagged("repeat")),
            templateBody {
                struct {
                    fieldName("Value")
                    variable(0)
                    fieldName("Repeat")
                    macro(SystemMacro.Default) {
                        variable(1)
                        int(1)
                    }
                }
            }
        )
        val SAMPLE_DEF = "(macro sample (value repeat?) { Value: (%value), Repeat: (.default (%repeat) 1) })"



        val Entry = template(*"start_time end_time marketplace program time operation properties timing counters levels service_metrics metrics groups".split(" ").toTypedArray()) {
            struct {
                fieldName("StartTime"); variable(0)
                fieldName("EndTime"); variable(1)
                fieldName("Marketplace"); variable(2)
                fieldName("Program"); variable(3)
                fieldName("Time"); variable(4)
                fieldName("Operation"); variable(5)
                fieldName("Properties"); variable(6)
                fieldName("ServiceMetrics"); variable(10)
                fieldName("Timing"); variable(7)
                fieldName("Counters"); variable(8)
                fieldName("Levels"); variable(9)
                fieldName("Metrics"); variable(11)
                fieldName("Groups"); variable(12)
            }
        }
        val ENTRY_DEF = "(macro entry ( start_time end_time marketplace program time operation properties timing counters levels service_metrics metrics groups ) { StartTime: ( '%' start_time ), EndTime: ( '%' end_time ), Marketplace: ( '%' marketplace ), Program: ( '%' program ), Time: ( '%' time ), Operation: ( '%' operation ), Properties: ( '%' properties ), ServiceMetrics: ( '%' service_metrics ), Timing: ( '%' timing ), Counters: ( '%' counters ), Levels: ( '%' levels ), Metrics: ( '%' metrics ), Groups: ( '%' groups ), } )"

        // ( macro metric
        //         ( name value unit '\?' dimensions '\?' )
        //         { Name: ( '%' name ), Samples: ( '%' value ), Unit: ( '.' $ion:: default ( '%' unit ) ( '.' 0 ) ), Dimensions: ( '%' dimensions ), } )
        val Metric = template("name", "value", "unit?", "dimensions?") {
            struct {
                fieldName("Name"); variable(0)
                fieldName("Samples"); variable(1)
                fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(One) {} }
                fieldName("Dimensions"); variable(3)
            }
        }
        val FlattenedMetric = template("name", "value", "unit?", "dimensions?") {
            struct {
                fieldName("Name"); variable(0)
                fieldName("Samples"); variable(1)
                fieldName("Unit"); macro(SystemMacro.Default) { variable(2); symbol("") }
                fieldName("Dimensions"); variable(3)
            }
        }
        val METRIC_DEF = "( macro metric ( name value unit? dimensions? ) { Name: ( % name ), Samples: ( % value ), Unit: (.default ( % unit ) (.one) ), Dimensions: (%dimensions ), } )"


        // ( macro metric_single ( name value repeat '?' unit '?' dimensions '?' )
        //         ( . 6 ( '%' name ) [ ( '.' 4 ( '%' value ) ( '%' repeat ) ), ] ( % unit ) ( '%' dimensions ) )
        // )
        val MetricSingle = template("name", "value", "repeat?", "unit?", "dimensions?") {
            macro(Metric) {
                variable(0)
                list {
                    macro(Sample) {
                        variable(1)
                        variable(2)
                    }
                }
                variable(3)
                variable(4)
            }
        }
        val FlattenedMetricSingle = template("name", "value", "repeat?", "unit?", "dimensions?") {
            struct {
                fieldName("Name"); variable(0)
                fieldName("Samples"); list {
                    struct {
                        fieldName("Value")
                        variable(1)
                        fieldName("Repeat")
                        macro(SystemMacro.Default) {
                            variable(2)
                            int(1)
                        }
                    }
                }
                fieldName("Unit"); macro(SystemMacro.Default) { variable(3); symbol("") }
                fieldName("Dimensions"); variable(4)
            }
        }
        val METRIC_SINGLE_DEF = "( macro metric_single ( name value repeat? unit? dimensions? ) ( .metric ( %name ) [ (.sample ( %value ) ( %repeat ) ), ] ( %unit ) ( %dimensions ) ) )"

        // ( macro summary ( name sum unit '\?' count '\?' )
        //         { Name: ( '%' name ), Sum: ( '%' sum ), Unit: ( '.' $ion:: default ( '%' unit ) ( '.' 0 ) ), Count: ( '.' $ion:: default ( '%' count ) 1 ), } )
        val Summary = template("name", "sum", "unit?", "count?") {
            struct {
                fieldName("Name"); variable(0)
                fieldName("Sum"); variable(1)
                fieldName("Unit"); macro(SystemMacro.Default) { variable(2); macro(One) {} }
                fieldName("Count"); macro(SystemMacro.Default) { variable(3); int(1) }
            }
        }
        val FlattenedSummary = template("name", "sum", "unit?", "count?") {
            struct {
                fieldName("Name"); variable(0)
                fieldName("Sum"); variable(1)
                fieldName("Unit"); macro(SystemMacro.Default) { variable(2); symbol("") }
                fieldName("Count"); macro(SystemMacro.Default) { variable(3); int(1) }
            }
        }
        val SUMMARY_DEF = "(macro summary (name sum unit? count? ) { Name: (%name), Sum: (%sum), Unit: (.default (%unit) (.one)), Count: (.default (%count) 1), } )"

        // ( macro summary_ms ( name value count '\?' ) (. 2 (%name) (%value) ms (%count)) )
        val SummaryMs = template("name", "value", "count?") {
            macro(Summary) {
                variable(0)
                variable(1)
                symbol("ms")
                variable(2)
            }
        }
        val FlattenedSummaryMs = template("name", "value", "count?") {
            struct {
                fieldName("Name"); variable(0)
                fieldName("Sum"); variable(1)
                // TODO: Elide this:
                fieldName("Unit"); macro(SystemMacro.Default) { symbol("ms"); symbol("") }
                fieldName("Count"); macro(SystemMacro.Default) { variable(2); int(1) }
            }
        }
        val SUMMARY_MS_DEF = "( macro summary_ms ( name value count? ) (.summary ( %name ) ( %value ) ms ( %count ) ) )"

        // ( macro summary0 (name) (. 2 (%name) 0e0 ) )
        val Summary0 = template("name") {
            macro(Summary) {
                variable(0)
                float(0.0)
            }
        }
        val FlattenedSummary0 = template("name") {
            struct {
                fieldName("Name"); variable(0)
                fieldName("Sum"); float(0.0)
                // TODO: Elide this invocation of default
                fieldName("Unit"); macro(SystemMacro.Default) { expressionGroup {}; symbol("") }
                fieldName("Count"); macro(SystemMacro.Default) {  expressionGroup {}; int(1) }
            }
        }
        val SUMMARY_0_DEF = "(macro summary0 (name) (.summary (%name) 0e0))"

        // ( macro summary1 ( name ) (. 2 (%name) 1e0 ) )
        val Summary1 = template("name") {
            macro(Summary) {
                variable(0)
                float(1.0)
            }
        }
        val FlattenedSummary1 = template("name") {
            struct {
                fieldName("Name"); variable(0)
                fieldName("Sum"); float(1.0)
                // TODO: Elide this invocation of default
                fieldName("Unit"); macro(SystemMacro.Default) { expressionGroup {}; symbol("") }
                fieldName("Count"); macro(SystemMacro.Default) {  expressionGroup {}; int(1) }
            }
        }
        val SUMMARY_1_DEF = "(macro summary1 (name) (.summary (%name) 1e0))"

        /*
        entry (01)
        summary (02)
        sample (04)
        metric (06)
        metric_single (07)
        summary_ms (09)
        summary0 (0b)
        summary1 (0c)
        none (system 00)
         */
    }


    val ion: IonSystem = IonSystemBuilder.standard().build()

    private val fakeMacroTable: (MacroRef) -> MacroV2? = {
        if (it.id == 1) {
            SystemMacro.Values
        } else when (it.name) {
            "values" -> SystemMacro.Values
            "default" -> SystemMacro.Default

            "sample" -> LogMacro.Sample
            "summary" -> LogMacro.FlattenedSummary
            "metric" -> LogMacro.FlattenedMetric
            "one" -> LogMacro.One
            "summary_ms" -> LogMacro.FlattenedSummaryMs
            "summary0" -> LogMacro.FlattenedSummary0
            "entry" -> LogMacro.Entry

            "shmalues" -> MacroV2(
                listOf(exactlyOneTagged("x"), exactlyOneTagged("y")),
                templateBody {
                    list {
                        variable(0)
                        variable(1)
                    }
                }
            )
            else -> null
        }
    }

    @Test
    fun foo() {
        assertMacroCompilation(
            LogMacro.METRIC_SINGLE_DEF,
            signature = LogMacro.MetricSingle.signature,
            LogMacro.FlattenedMetricSingle.body!!
        )
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
        """(macro literal_test (x) (.values (.literal (.values (%x)))))""" shouldCompileTo MacroV2(
            signature = listOf(exactlyOneTagged("x")),
            templateBody {
                sexp {
                    symbol(".")
                    symbol("values")
                    sexp {
                        symbol("%")
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
        """(macro foo () (.values 42))""" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                int(42)
            }
        ),

        """(macro using_expr_group () (.values (.. 42 "hello" false)))""" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                expressionGroup {
                    int(42)
                    string("hello")
                    bool(false)
                }
            }
        ),
        """(macro invoke_by_id () (.1 (.. true false)))""" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                expressionGroup {
                    bool(true)
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

        "(macro null () \"abc\")" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                string("abc")
            }
        ),

        "(macro invocation_inside_container () [1, (.values 2), 3] )" shouldCompileTo MacroV2(
            signature = emptyList(),
            templateBody {
                list {
                    int(1)
                    int(2)
                    int(3)
                }
            }
        ),

        "(macro invocation_inside_container_with_var (x) [1, (.values (%x)), 3] )" shouldCompileTo MacroV2(
            signature = listOf(exactlyOneTagged("x")),
            templateBody {
                list {
                    int(1)
                    variable(0)
                    int(3)
                }
            }
        ),


        "(macro nested_invocation (z) [(.shmalues (%z) 1)] )" shouldCompileTo MacroV2(
            signature = listOf(exactlyOneTagged("z")),
            templateBody {
                list {
                    list {
                        variable(0)
                        int(1)
                    }
                }
            }
        ),

        "(macro un_nestable_macro_invocation (z) [(.default (%z) 1)] )" shouldCompileTo MacroV2(
            signature = listOf(exactlyOneTagged("z")),
            templateBody {
                list {
                    macro(SystemMacro.Default) {
                        variable(0)
                        int(1)
                    }
                }
            }
        ),

        "(macro default_that_can_be_precomputed (z) [ (.default (..) (%z) ) ] )" shouldCompileTo MacroV2(
            signature = listOf(exactlyOneTagged("z")),
            templateBody {
                list {
                    variable(0)
                }
            }
        ),

        "(macro another_default_that_can_be_precomputed (z) [ (.default (.none) (%z) ) ] )" shouldCompileTo MacroV2(
            signature = listOf(exactlyOneTagged("z")),
            templateBody {
                list {
                    variable(0)
                }
            }
        ),

        "(macro default_that_can_be_precomputed (z) [ (.default (..) (%z) ) ] )" shouldCompileTo MacroV2(
            signature = listOf(exactlyOneTagged("z")),
            templateBody {
                list {
                    variable(0)
                }
            }
        ),

        LogMacro.SAMPLE_DEF shouldCompileTo LogMacro.Sample,
        LogMacro.ONE_DEF shouldCompileTo LogMacro.One,
        LogMacro.ENTRY_DEF shouldCompileTo LogMacro.Entry,
        LogMacro.METRIC_SINGLE_DEF shouldCompileTo LogMacro.FlattenedMetricSingle,
        LogMacro.METRIC_DEF shouldCompileTo LogMacro.FlattenedMetric,
        LogMacro.SUMMARY_0_DEF shouldCompileTo LogMacro.FlattenedSummary0,
        LogMacro.SUMMARY_1_DEF shouldCompileTo LogMacro.FlattenedSummary1,
        LogMacro.SUMMARY_MS_DEF shouldCompileTo LogMacro.FlattenedSummaryMs,
        LogMacro.SUMMARY_DEF shouldCompileTo LogMacro.FlattenedSummary,

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

    private fun assertMacroCompilation(source: String, signature: Array<Macro.Parameter>, body: Array<TemplateBodyExpressionModel>) {
        val reader = newReader(source)
        val compiler = FlatMacroCompiler(fakeMacroTable, reader)
        reader.next()
        val macroDef = compiler.compileMacro()
        val expectedDef = MacroV2(signature, body)

        assertEquals(
            expectedDef.body?.mapIndexed { i, value -> " $i. $value"}?.joinToString("\n", prefix = "[\n", postfix = "\n]"),
            macroDef.body?.mapIndexed { i, value -> " $i. $value"}?.joinToString("\n", prefix = "[\n", postfix = "\n]"),
        )

        assertEquals(expectedDef, macroDef)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    fun assertMacroCompilationIonReader(source: String, signature: Array<Macro.Parameter>, body: Array<TemplateBodyExpressionModel>) {
        assertMacroCompilation(source, signature, body)
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
            assertEquals(expectedDef, macroDef)
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
        assertEquals("foo", compiler.macroName)
        reader.next()
        compiler.compileMacro()
        assertEquals("bar", compiler.macroName)
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

    @Test
    fun copyVariableInline() {
        val reader = newReader("")
        val compiler = FlatMacroCompiler(fakeMacroTable, reader)

        val arg0 = templateBody {
            list {
                int(1)
                int(2)
            }
        }.toList()
        val arg1 = templateBody { string("foo") }.toList()
        val arg2 = templateBody { list { string("bar") } }.toList()
        val arg3 = templateBody { sexp {  } }.toList()

        val allArgs = mutableListOf<TemplateBodyExpressionModel>().let {
            it.addAll(arg0)
            it.addAll(arg1)
            it.addAll(arg2)
            it.addAll(arg3)
            it.toTypedArray()
        }
        val argumentIndices = intArrayOf(0, 3, 4, 6)
        val destination = mutableListOf<TemplateBodyExpressionModel>()

        compiler.copyArgValueInline(0, destination, allArgs, argumentIndices)
        assertEquals(arg0, destination)
        destination.clear()

        compiler.copyArgValueInline(1, destination, allArgs, argumentIndices)
        assertEquals(arg1, destination)
        destination.clear()

        compiler.copyArgValueInline(2, destination, allArgs, argumentIndices)
        assertEquals(arg2, destination)
        destination.clear()

        compiler.copyArgValueInline(3, destination, allArgs, argumentIndices)
        assertEquals(arg3, destination)

    }




}
