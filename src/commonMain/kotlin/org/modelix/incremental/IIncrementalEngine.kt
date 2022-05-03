package org.modelix.incremental

typealias ComputationKey = Any

interface IIncrementalEngine {
    fun <T> compute(call: IncrementalFunctionCall<T>): T
    fun <T> observe(call: IncrementalFunctionCall<T>): TrackedValue<T>
}