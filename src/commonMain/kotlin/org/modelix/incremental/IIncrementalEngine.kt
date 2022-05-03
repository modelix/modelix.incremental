package org.modelix.incremental

interface IIncrementalEngine {
    fun <T> compute(task: ComputationTask<T>): T
    fun <T> observe(task: ComputationTask<T>): TrackedValue<T>
}