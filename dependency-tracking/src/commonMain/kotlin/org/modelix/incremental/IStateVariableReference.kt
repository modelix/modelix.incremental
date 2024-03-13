package org.modelix.incremental

interface IStateVariableReference<out E> : IStateVariableGroup {
    fun read(): E
}
