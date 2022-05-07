package org.modelix.incremental

interface IIncrementalEngine {
    /**
     * Returns the cached value or computes it synchronously
     */
    suspend fun <T> compute(call: IncrementalFunctionCall<T>): T
    fun <T> compute(call: IncrementalFunctionCall<T>, callback: (T)->Unit)

    /**
     * Computes multiple values in parallel
     */
    suspend fun <T> computeAll(calls: List<IncrementalFunctionCall<T>>): List<T>
    fun <T> computeAll(calls: List<IncrementalFunctionCall<T>>, callback: (List<T>)->Unit)

    /**
     * Automatically re-executes the function whenever the inputs change.
     * The function usually produces some side effect and is not expected to have any return value.
     */
    suspend fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T>
    fun <T> activate(call: IncrementalFunctionCall<T>, callback: (IActiveOutput<T>)->Unit)

    /**
     * Blocks until all pending functions are recomputed.
     */
    suspend fun flush()
    fun flush(callback: ()->Unit)
}