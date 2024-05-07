package com.amazon.ion.view

/**
 * If the returned value is mutable, it must be unlinked from the original data.
 *
 * That is to say, given `f(A) -> B`, no operation on `B` is allowed to have any effect on `A`.
 */
interface IonDataViewFactory<ValueType> {
    fun fromView(data: IonDataView): ValueType
    fun intoView(data: ValueType): IonDataView
}
