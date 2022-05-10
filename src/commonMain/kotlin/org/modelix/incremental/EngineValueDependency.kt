package org.modelix.incremental

data class EngineValueDependency<E>(val engine: IncrementalEngine, val call: IncrementalFunctionCall<E>) : IStateVariableReference<E>, IValueAccessor<E> {
    override fun getGroup() = null
    override suspend fun getValue(): E = engine.readStateVariable(call)
}