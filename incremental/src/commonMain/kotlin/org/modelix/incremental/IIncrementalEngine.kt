package org.modelix.incremental

interface IIncrementalEngine {
    /**
     * Returns the cached value or computes it synchronously
     */
    fun <T> readStateVariable(call: IStateVariableDeclaration<*, T>): T

    /**
     * Computes multiple values in parallel
     */
    fun <T> readStateVariables(calls: List<IStateVariableDeclaration<*, T>>): List<T>

    /**
     * Automatically re-executes the function whenever the inputs change.
     * The function usually produces some side effect and is not expected to have any return value.
     */
    fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T>

    /**
     * Blocks until all pending functions are recomputed.
     */
    fun flush()
    fun flush(callback: () -> Unit)
}
