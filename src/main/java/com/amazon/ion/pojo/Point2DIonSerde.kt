package com.amazon.ion.pojo

import com.amazon.ion.impl.bin.*
import com.amazon.ion.impl.macro.ExpressionBuilderDsl
import com.amazon.ion.impl.macro.ParameterFactory
import com.amazon.ion.impl.macro.TemplateMacro

object Point2DIonSerde: IonSerdeHelper<Point2D> {
    override val MACRO_NAME = Point2D::class.simpleName!!.replace(".", "_")
    override val MACRO = TemplateMacro(
        signature = listOf(
            ParameterFactory.exactlyOneTagged("x"),
            ParameterFactory.exactlyOneTagged("y"),
        ),
        ExpressionBuilderDsl.templateBody {
            struct {
                fieldName("x")
                variable(0)
                fieldName("y")
                variable(1)
            }
        }
    )

    override fun writeArguments(value: Point2D, buffer: WriteBuffer) {
        IonSerdeHelper.writeTaggedInteger(value.x, buffer)
        IonSerdeHelper.writeTaggedInteger(value.y, buffer)
    }
}
