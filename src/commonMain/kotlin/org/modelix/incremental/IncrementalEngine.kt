package org.modelix.incremental

class IncrementalEngine : IIncrementalEngine, IDependencyKey, IDependencyListener {

    private val graph = DependencyGraph(this)
    private var activeEvaluation: Evaluation? = null

    init {
        DependencyTracking.registerListener(this)
    }

    override fun <T> compute(call: IncrementalFunctionCall<T>): T {
        val engineValueKey = EngineValueDependency(this, call)
        var entry = graph.getValue(engineValueKey)
        if (entry != null) {
            if (entry.getState() == ERecomputableValueState.VALID) {
                return entry.getValue() as T
            }
        } else {
            entry = RecomputableValue(call)
            graph.setValue(engineValueKey, entry)
        }
        val evaluation = Evaluation(engineValueKey, call)
        val previousEvaluation = activeEvaluation
        try {
            activeEvaluation = evaluation
            val value: T = entry.recompute() as T
            graph.setValue(evaluation.key, entry)
            graph.setDependencies(evaluation.key, evaluation.dependencies)
            return value
        } finally {
            activeEvaluation = previousEvaluation
        }
    }

    override fun <T> observe(call: IncrementalFunctionCall<T>): TrackedValue<T> {
        TODO("Not yet implemented")
    }

    override fun accessed(key: IDependencyKey) {
        val evaluation = activeEvaluation ?: return
        evaluation.dependencies += key
    }

    override fun modified(key: IDependencyKey) {
        val deps = graph.getReverseDependencies(key)
        for (dep in deps) {
            invalidateReverseDependencies(dep)
        }
    }

    private fun invalidateReverseDependencies(key: IDependencyKey) {
        val value = graph.getValue(key) ?: return
        when (value.getState()) {
            ERecomputableValueState.VALID -> {
                value.dependencyInvalidated()
                for (reverseDependency in graph.getReverseDependencies(key)) {
                    invalidateReverseDependencies(reverseDependency)
                }
            }
            ERecomputableValueState.INVALID, ERecomputableValueState.DEPENDENCY_INVALID -> {}
        }
    }

    fun dispose() {
        DependencyTracking.removeListener(this)
    }

    override fun getGroup(): IDependencyKey? {
        return null
    }

    inner class Evaluation(val key: EngineValueDependency, val call: IncrementalFunctionCall<*>) {
        val dependencies: MutableSet<IDependencyKey> = HashSet()
    }
}