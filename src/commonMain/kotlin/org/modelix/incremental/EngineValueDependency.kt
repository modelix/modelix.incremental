package org.modelix.incremental

data class EngineValueDependency<E>(val engine: IncrementalEngine, val call: IncrementalFunctionCall<E>) : IStateVariableReference, IValueAccessor<E> {
    override fun getGroup() = null
    override suspend fun getValue(): E = engine.compute(call)
}