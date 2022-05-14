package org.modelix.incremental

interface IStateVariableReference<E> : IStateVariableGroup {
    fun read(): E
}