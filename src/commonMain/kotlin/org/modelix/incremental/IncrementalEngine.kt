package org.modelix.incremental

class IncrementalEngine : IIncrementalEngine, IDependencyKey, IDependencyListener {

    private val graph = DependencyGraph<IDependencyKey>()
    private var activeEvaluation: Evaluation? = null

    init {
        DependencyTracking.registerListener(this)
    }

    override fun <T> compute(task: ComputationTask<T>): T {
        val engineValueKey = EngineValueDependency(this, task.key)
        val value = graph.getValue(engineValueKey)
        if (value?.getState() == ERecomputableValueState.VALID) {
            return value.getValue() as T
        }
        val evaluation = Evaluation(engineValueKey, task)
        val previousEvaluation = activeEvaluation
        try {
            activeEvaluation = evaluation
            val value = task.value.recompute()
            graph.setValue(evaluation.key, task.value)
            graph.setDependencies(evaluation.key, evaluation.dependencies)
            return value
        } finally {
            activeEvaluation = previousEvaluation
        }
    }

    override fun <T> observe(task: ComputationTask<T>): TrackedValue<T> {
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

    inner class Evaluation(val key: EngineValueDependency, val task: ComputationTask<*>) {
        val dependencies: MutableSet<IDependencyKey> = HashSet()
    }
}