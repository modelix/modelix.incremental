package org.modelix.incremental

import kotlinx.coroutines.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

actual class IncrementalEngine actual constructor() : IIncrementalEngine, IDependencyKey, IDependencyListener {

    private val graph = DependencyGraph(this)
    private val dispatcher = Dispatchers.Default
    private val graphDispatcher = dispatcher.limitedParallelism(1)
    private val observedOutputs = HashSet<ObservedOutput<*>>()
    private val activeEvaluation: ThreadLocal<Evaluation?> = ThreadLocal()

    init {
        DependencyTracking.registerListener(this)
    }

    override suspend fun <T> compute(call: IncrementalFunctionCall<T>): T {
        val engineValueKey = EngineValueDependency(this, call)
        DependencyTracking.accessed(engineValueKey)
        return update(engineValueKey)
    }

    override suspend fun <T> computeAll(calls: List<IncrementalFunctionCall<T>>): List<T> {
        val keys = calls.map { EngineValueDependency(this, it) }
        keys.forEach { DependencyTracking.accessed(it) }
        return coroutineScope {
            val futures: List<Deferred<T>> = withContext(dispatcher) {
                keys.map { key -> async { update(key) } }
            }
            futures.map { it.await() }
        }
    }

    private suspend fun <T> update(engineValueKey: EngineValueDependency<T>): T {
        return withContext(graphDispatcher) {
            val node: DependencyGraph.ComputedNode = graph.getOrAddNode(engineValueKey) as DependencyGraph.ComputedNode
            when (node.getState()) {
                ECacheEntryState.VALID -> return@withContext node.getValue() as T
                ECacheEntryState.FAILED -> throw node.getValue() as Throwable
            }

            val evaluation = Evaluation(engineValueKey, engineValueKey.call, kotlin.coroutines.coroutineContext[Evaluation])
            evaluation.detectCycle()
            withContext(evaluation) {
                var asyncValue: Deferred<Any?>? = if (node.getState() == ECacheEntryState.VALIDATING) node.activeValidation else null
                if (asyncValue == null) {
                    node.startValidation()
                    asyncValue = async(dispatcher + activeEvaluation.asContextElement(evaluation)) {
                        engineValueKey.call.invoke(IncrementalFunctionContext<T>(node)) as T
                    }
                    node.activeValidation = asyncValue
                    try {
                        val value = asyncValue.await()
                        node.validationSuccessful(value, evaluation.dependencies)
                        value
                    } catch (e: Throwable) {
                        node.validationFailed(e, evaluation.dependencies)
                        throw e
                    }
                }
                asyncValue.await() as T
            }
        }
    }

    override fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T> {
        val output = ObservedOutput<T>(EngineValueDependency(this, call))
        runBlocking {
            withContext(graphDispatcher) {
                observedOutputs += output
            }
            launch {
                compute(call)
            }
        }
        return output
    }

    override fun accessed(key: IDependencyKey) {
        val evaluation = activeEvaluation.get() ?: return
        evaluation.dependencies += key
    }

    override fun modified(key: IDependencyKey) {
        runBlocking {
            withContext(graphDispatcher) {
                val node = graph.getNode(key) ?: return@withContext
                node.invalidate()
            }
        }
    }

    actual fun dispose() {
        DependencyTracking.removeListener(this)
    }

    override fun getGroup(): IDependencyKey? {
        return null
    }

    override fun flush() {
        runBlocking {
            withContext(graphDispatcher) {
                for (observedOutput in observedOutputs) {
                    update(observedOutput.key)
                }
            }
        }
    }

    private inner class IncrementalFunctionContext<RetT>(val node: DependencyGraph.ComputedNode) : IIncrementalFunctionContext<RetT> {
        override fun getPreviousResult(): Optional<RetT> {
            val state = node.getState()
            return if (state == ECacheEntryState.FAILED || state == ECacheEntryState.NEW) {
                Optional.empty()
            } else {
                Optional.of(node.getValue() as RetT)
            }
        }

        override fun getPreviousInput(key: IDependencyKey): Optional<*> {
            TODO("Not yet implemented")
        }
    }

    private class Evaluation(
        val dependencyKey: EngineValueDependency<*>,
        val call: IncrementalFunctionCall<*>,
        val previous: Evaluation?,
    ) : AbstractCoroutineContextElement(Evaluation) {
        companion object Key : CoroutineContext.Key<Evaluation>

        val dependencies: MutableSet<IDependencyKey> = HashSet()

        fun getEvaluations(): List<Evaluation> {
            return (previous?.getEvaluations() ?: emptyList()) + this
        }

        fun detectCycle() {
            var current = previous
            while (current != null) {
                if (current.dependencyKey == dependencyKey) {
                    val activeEvaluations = previous!!.getEvaluations()
                    val cycleStart = activeEvaluations.indexOfLast { it.dependencyKey == dependencyKey }
                    if (cycleStart != -1) {
                        throw DependencyCycleException(activeEvaluations.drop(cycleStart).map { it.call })
                    }
                }
                current = current.previous
            }
        }
    }

    private inner class ObservedOutput<E>(val key: EngineValueDependency<E>) : IActiveOutput<E> {
        override fun deactivate() {
            observedOutputs -= this
        }
    }
}