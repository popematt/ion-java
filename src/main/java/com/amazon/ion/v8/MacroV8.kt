package com.amazon.ion.v8

import com.amazon.ion.IonType
import com.amazon.ion.impl.macro.Macro
import com.amazon.ion.v3.impl_1_1.TemplateBodyExpressionModel

class MacroV8 private constructor(
    @JvmField
    val signature: Array<Macro.Parameter>,
    // TODO: Body could actually be more than one expression if there are annotations.
    @JvmField
    val body: TemplateBodyExpressionModel,
//    @JvmField
//    val bytecode: IntArray,
//    @JvmField
//    val constants: Array<Any?>,
) {

//    @JvmField
//    val returnType: IonType = TemplateBodyExpressionModel.Kind.ION_TYPES[body.expressionKind]!!

    companion object {
        @JvmStatic
        fun create(body: TemplateBodyExpressionModel): MacroV8 {
            val signature = mutableListOf<Macro.Parameter>()
            addPlaceholders(body, signature)
            return MacroV8(signature.toTypedArray(), body)
        }

        private fun addPlaceholders(body: TemplateBodyExpressionModel, signature: MutableList<Macro.Parameter>) {
            if (body.expressionKind == TemplateBodyExpressionModel.Kind.VARIABLE) {
                // TODO: Optional parameters, default values, tagless encodings
                val n = signature.size
                signature.add(Macro.Parameter("variable$n", Macro.ParameterEncoding.Tagged, Macro.ParameterCardinality.ExactlyOne))
                body.childExpressions.forEach { addPlaceholders(it, signature) }
            }
        }
    }
}
