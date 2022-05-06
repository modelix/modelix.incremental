package org.modelix.incremental

import kotlinx.coroutines.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Synchronized

actual class IncrementalEngine actual constructor(val coroutineScope: CoroutineScope) : IIncrementalEngine, IDependencyKey, IDependencyListener {

    private val graph = DependencyGraph(this)
    private var activeEvaluations = ArrayList<Evaluation>()
    private val observedOutputs = HashSet<ObservedOutput<*>>()

    init {
        DependencyTracking.registerListener(this)
    }

    //@Synchronized
    override fun <T> compute(call: IncrementalFunctionCall<T>): T {
        val engineValueKey = EngineValueDependency(this, call)
        DependencyTracking.accessed(engineValueKey)
        return update(engineValueKey)
    }

    override suspend fun <T> computeAll(calls: List<IncrementalFunctionCall<T>>): List<T> {
        val keys = calls.map { EngineValueDependency(this, it) }
        keys.forEach { DependencyTracking.accessed(it) }
        val futures: List<Deferred<T>> = keys.map { key -> coroutineScope.async {
            update(key)
        } }
        return futures.map { it.await() }
    }

    //@Synchronized
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

        val evaluation = Evaluation(engineValueKey, engineValueKey.call, null)
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

    actual fun dispose() {
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

    private class Evaluation(
        val dependencyKey: EngineValueDependency<*>,
        val call: IncrementalFunctionCall<*>,
        val previous: Evaluation?,
    ) : AbstractCoroutineContextElement(Evaluation) {
        val dependencies: MutableSet<IDependencyKey> = HashSet()
        companion object Key : CoroutineContext.Key<Evaluation>
    }

    private inner class ObservedOutput<E>(val key: EngineValueDependency<E>) : IActiveOutput<E> {
        override fun deactivate() {
            observedOutputs -= this
        }
    }
}