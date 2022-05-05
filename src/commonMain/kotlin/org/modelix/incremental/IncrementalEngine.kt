package org.modelix.incremental

import kotlin.jvm.Synchronized

class IncrementalEngine : IIncrementalEngine, IDependencyKey, IDependencyListener {

    private val graph = DependencyGraph(this)
    private var activeEvaluations = ArrayList<Evaluation>()
    private val observedOutputs = HashSet<ObservedOutput<*>>()

    init {
        DependencyTracking.registerListener(this)
    }

    @Synchronized
    override fun <T> compute(call: IncrementalFunctionCall<T>): T {
        val engineValueKey = EngineValueDependency(this, call)
        DependencyTracking.accessed(engineValueKey)
        return update(engineValueKey)
    }

    @Synchronized
    private fun <T> update(engineValueKey: EngineValueDependency<T>): T {
        val node = graph.getOrAddNode(engineValueKey) as DependencyGraph.ComputedNode
        if (node.getState() == ECacheEntryState.VALID) {
            return node.getValue() as T
        }

        // dependency cycle detection
        val cycleStart = activeEvaluations.indexOfLast { it.key == engineValueKey }
        if (cycleStart != -1) {
//            val defaultValue = engineValueKey.call.getDefaultValue()
//            if (defaultValue.hasValue()) {
//                return defaultValue.getValue() as T
//            } else {
                throw DependencyCycleException(activeEvaluations.drop(cycleStart).map { it.call })
//            }
        }

        val evaluation = Evaluation(engineValueKey, engineValueKey.call)
        try {
            activeEvaluations += evaluation
            val value: T = node.validate() as T
            graph.setDependencies(engineValueKey, evaluation.dependencies)
            return value
        } finally {
            activeEvaluations.removeLast()
        }
    }

    @Synchronized
    override fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T> {
        val output = ObservedOutput<T>(EngineValueDependency(this, call))
        observedOutputs += output
        compute(call)
        return output
    }

    override fun accessed(key: IDependencyKey) {
        val evaluation = activeEvaluations.lastOrNull() ?: return
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
            update(observedOutput.key)
        }
    }

    private inner class Evaluation(val key: EngineValueDependency<*>, val call: IncrementalFunctionCall<*>) {
        val dependencies: MutableSet<IDependencyKey> = HashSet()
    }

    private inner class ObservedOutput<E>(val key: EngineValueDependency<E>) : IActiveOutput<E> {
        override fun deactivate() {
            observedOutputs -= this
        }
    }
}