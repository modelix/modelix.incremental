package org.modelix.incremental

interface IStateVariableReference<E> : IStateVariableGroup {
    suspend fun read(): E
}