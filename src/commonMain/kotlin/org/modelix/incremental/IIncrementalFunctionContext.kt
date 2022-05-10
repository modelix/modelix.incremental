package org.modelix.incremental

interface IIncrementalFunctionContext<RetT> {
    fun getPreviousResult(): Optional<RetT>
    fun getPreviousInput(key: IStateVariableReference): Optional<*>
}

fun <T> IIncrementalFunctionContext<T>.getPreviousResultOrElse(defaultValue: ()->T): T {
    return getPreviousResult().getOrElse(defaultValue)
}