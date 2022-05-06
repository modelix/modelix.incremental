package org.modelix.incremental

typealias ComputationKey = Any

interface IIncrementalEngine {
    /**
     * Returns the cached value or computes it synchronously
     */
    fun <T> compute(call: IncrementalFunctionCall<T>): T

    /**
     * Computes multiple values in parallel
     */
    suspend fun <T> computeAll(calls: List<IncrementalFunctionCall<T>>): List<T>

    /**
     * Automatically re-executes the function whenever the inputs change.
     * The function usually produces some side effect and is not expected to have any return value.
     */
    fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T>

    /**
     * Blocks until all pending functions are recomputed.
     */
    fun flush()
}