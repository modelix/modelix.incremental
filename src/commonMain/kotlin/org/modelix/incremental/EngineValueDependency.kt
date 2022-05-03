package org.modelix.incremental

data class EngineValueDependency(val engine: IncrementalEngine, val call: IncrementalFunctionCall<*>) : IDependencyKey {
    override fun getGroup(): IDependencyKey = engine
}