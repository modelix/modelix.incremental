package org.modelix.incremental

interface IIncrementalFunctionContext<RetT> {
    fun readOwnStateVariable(): Optional<RetT>
    fun <T> readStateVariable(key: IStateVariableReference<T>): Optional<T>
    fun <T> writeStateVariable(ref: IStateVariableReference<T>, value: T)
}

fun <T> IIncrementalFunctionContext<T>.readOwnStateVariable(defaultValue: ()->T): T {
    return readOwnStateVariable().getOrElse(defaultValue)
}