package org.modelix.incremental

interface IIncrementalEngine {
    /**
     * Returns the cached value or computes it synchronously
     */
    suspend fun <T> compute(call: IncrementalFunctionCall<T>): T

    /**
     * Computes multiple values in parallel
     */
    suspend fun <T> computeAll(calls: List<IncrementalFunctionCall<T>>): List<T>

    /**
     * Automatically re-executes the function whenever the inputs change.
     * The function usually produces some side effect and is not expected to have any return value.
     */
    suspend fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T>

    /**
     * Blocks until all pending functions are recomputed.
     */
    suspend fun flush()
}