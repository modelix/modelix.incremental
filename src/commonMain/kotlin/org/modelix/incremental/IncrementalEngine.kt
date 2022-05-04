package org.modelix.incremental

class IncrementalEngine : IIncrementalEngine, IDependencyKey, IDependencyListener {

    private val graph = DependencyGraph(this)
    private var activeEvaluation: Evaluation? = null

    init {
        DependencyTracking.registerListener(this)
    }

    override fun <T> compute(call: IncrementalFunctionCall<T>): T {
        val engineValueKey = EngineValueDependency(this, call)
        DependencyTracking.accessed(engineValueKey)
        val node = graph.getOrAddNode(engineValueKey) as DependencyGraph.ComputedNode
        if (node.getState() == ECacheEntryState.VALID) {
            return node.getValue() as T
        }
        val evaluation = Evaluation(engineValueKey, call)
        val previousEvaluation = activeEvaluation
        try {
            activeEvaluation = evaluation
            val value: T = node.recompute() as T
            graph.setDependencies(engineValueKey, evaluation.dependencies)
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
        val node = graph.getNode(key) ?: return
        node.invalidate()
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