package org.modelix.incremental

data class EngineValueDependency(val engine: IncrementalEngine, val key: Any) : IDependencyKey {
    override fun getGroup(): IDependencyKey = engine
}