package com.amazon.ion.view

interface Field {
    val fieldName: String?
    val fieldId: Int
    val value: IonDataViewBase

    fun component1(): String? = fieldName
    fun component2(): Int = fieldId
    fun component3(): IonDataViewBase = value

    data class DefaultImpl(
        override val fieldName: String,
        override val fieldId: Int,
        override val value: IonDataViewBase
    ): Field
}
