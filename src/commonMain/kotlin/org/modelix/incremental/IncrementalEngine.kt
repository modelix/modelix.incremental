package org.modelix.incremental

class IncrementalEngine : IIncrementalEngine {

    private val graph = DependencyGraph<IDependency>()

    override fun <T> compute(task: ComputationTask<T>): T {
        TODO("Not yet implemented")
    }

    override fun <T> observe(task: ComputationTask<T>): IObservedValue<T> {
        TODO("Not yet implemented")
    }


}