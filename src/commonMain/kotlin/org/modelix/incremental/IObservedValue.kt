package org.modelix.incremental

interface IObservedValue<E> {
    fun dispose()
    fun addObserver(observer: (E)->Unit)
}