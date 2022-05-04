package org.modelix.incremental

class IncrementalEngine : IIncrementalEngine, IDependencyKey, IDependencyListener {

    private val graph = DependencyGraph(this)
    private var activeEvaluation: Evaluation? = null
    private val observedOutputs = HashSet<ObservedOutput<*>>()

    init {
        DependencyTracking.registerListener(this)
    }

    override fun <T> compute(call: IncrementalFunctionCall<T>): T {
        val engineValueKey = EngineValueDependency(this, call)
        DependencyTracking.accessed(engineValueKey)
        return update(engineValueKey)
    }

    private fun <T> update(engineValueKey: EngineValueDependency): T {
        val node = graph.getOrAddNode(engineValueKey) as DependencyGraph.ComputedNode
        if (node.getState() == ECacheEntryState.VALID) {
            return node.getValue() as T
        }
        val evaluation = Evaluation(engineValueKey, engineValueKey.call)
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

    override fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T> {
        val output = ObservedOutput<T>(EngineValueDependency(this, call))
        observedOutputs += output
        compute(call)
        return output
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

    override fun flush() {
        for (observedOutput in observedOutputs) {
            update<Any?>(observedOutput.key)
        }
    }

    private inner class Evaluation(val key: EngineValueDependency, val call: IncrementalFunctionCall<*>) {
        val dependencies: MutableSet<IDependencyKey> = HashSet()
    }

    private inner class ObservedOutput<E>(val key: EngineValueDependency) : IActiveOutput<E> {
        override fun deactivate() {
            observedOutputs -= this
        }
    }
}