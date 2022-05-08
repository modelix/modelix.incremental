package org.modelix.incremental

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.HashSet
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

actual class IncrementalEngine actual constructor() : IIncrementalEngine, IDependencyKey, IDependencyListener {

    private val graph = DependencyGraph(this)
    private val dispatcher = Dispatchers.Default
    private val activeEvaluation: ThreadLocal<Evaluation?> = ThreadLocal()
    private var autoValidator: Job? = null
    private var disposed = false
    private val pendingModifications: Channel<IDependencyKey> = Channel(capacity = Channel.UNLIMITED)
    private val autoValidationsMutex = Mutex()
    private val graphMutex = Mutex()
    private val engineScope = CoroutineScope(dispatcher)

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
            val futures: List<Deferred<T>> = keys.map { key -> async { update(key) } }
            futures.map { it.await() }
        }
    }

    private suspend fun <T> update(engineValueKey: EngineValueDependency<T>): T {
        var value: T? = null
        var exception: Throwable? = null
        var state: ECacheEntryState = ECacheEntryState.NEW
        var asyncValue: Deferred<Any?>? = null
        var node: DependencyGraph.ComputedNode
        var evaluation: Evaluation? = null
        graphMutex.withLock {
            processPendingModifications()

            node = graph.getOrAddNode(engineValueKey) as DependencyGraph.ComputedNode
            state = node.getState()
            when (state) {
                ECacheEntryState.VALID -> {
                    value = node.getValue() as T
                }
                ECacheEntryState.FAILED -> {
                    exception = node.getValue() as Throwable
                }
                else -> {
                    evaluation = Evaluation(engineValueKey, engineValueKey.call, kotlin.coroutines.coroutineContext[Evaluation])
                    evaluation!!.detectCycle()
                    if (state == ECacheEntryState.VALIDATING) {
                        asyncValue = node.activeValidation
                    } else {
                        withContext(evaluation!!) {
                            node.startValidation()
                            asyncValue = engineScope.async(evaluation!! + activeEvaluation.asContextElement(evaluation)) {
                                engineValueKey.call.invoke(IncrementalFunctionContext<T>(node)) as T
                            }
                            node.activeValidation = asyncValue
                        }
                    }
                }
            }
        }

        return when (state) {
            ECacheEntryState.VALID -> {
                value as T
            }
            ECacheEntryState.FAILED -> {
                throw exception!!
            }
            ECacheEntryState.VALIDATING -> {
                asyncValue!!.await() as T
            }
            else -> {
                try {
                    val value = asyncValue!!.await() as T
                    graphMutex.withLock {
                        node.validationSuccessful(value, evaluation!!.dependencies)
                    }
                    value
                } catch (e: Throwable) {
                    graphMutex.withLock {
                        node.validationFailed(e, evaluation!!.dependencies)
                    }
                    throw e
                }
            }
        }
    }

    override suspend fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T> {
        autoValidationsMutex.withLock {
            if (autoValidator == null) {
                autoValidator = engineScope.launch(dispatcher) {
                    launch {
                        while (!disposed) {
                            autoValidationsMutex.withLock {
                                var key = graph.autoValidationChannel.tryReceive()
                                while (key.isSuccess) {
                                    update(key.getOrThrow())
                                    key = graph.autoValidationChannel.tryReceive()
                                }
                            }
                            delay(10)
                        }
                    }
                    launch {
                        while (!disposed) {
                            graphMutex.withLock {
                                processPendingModifications()
                            }
                            delay(10)
                        }
                    }
                }
            }
        }

        val key = EngineValueDependency(this@IncrementalEngine, call)
        val node = graph.getOrAddNode(key) as DependencyGraph.ComputedNode
        node.setAutoValidate(true)
        graph.autoValidationChannel.send(key)
        return ObservedOutput<T>(EngineValueDependency(this, call))
    }

    override fun accessed(key: IDependencyKey) {
        val evaluation = activeEvaluation.get() ?: return
        evaluation.dependencies += key
    }

    /**
     * requires lock on graphMutex
     */
    private fun processPendingModifications() {
        var modification = pendingModifications.tryReceive()
        while (modification.isSuccess) {
            graph.getNode(modification.getOrThrow())?.invalidate()
            modification = pendingModifications.tryReceive()
        }
    }

    override fun modified(key: IDependencyKey) {
        if (key is EngineValueDependency<*> && key.engine == this) return
        pendingModifications.trySend(key).onFailure { if (it != null) throw it }
    }

    actual fun dispose() {
        disposed = true
        engineScope.cancel()
        DependencyTracking.removeListener(this)
    }

    override fun getGroup(): IDependencyKey? {
        return null
    }

    override suspend fun flush() {
        graphMutex.withLock {
            processPendingModifications()
        }
        coroutineScope {
            autoValidationsMutex.withLock {
                var autoValidation = graph.autoValidationChannel.tryReceive()
                while (autoValidation.isSuccess) {
                    val key = autoValidation.getOrThrow()
                    launch { // engineScope is not used because flush should wait for the update calls
                        update(key)
                    }
                    autoValidation = graph.autoValidationChannel.tryReceive()
                }
            }
        }
    }

    override fun <T> compute(call: IncrementalFunctionCall<T>, callback: (T) -> Unit) {
        engineScope.launch { callback(compute(call)) }
    }

    override fun <T> computeAll(calls: List<IncrementalFunctionCall<T>>, callback: (List<T>) -> Unit) {
        engineScope.launch { callback(computeAll(calls)) }
    }

    override fun <T> activate(call: IncrementalFunctionCall<T>, callback: (IActiveOutput<T>) -> Unit) {
        engineScope.launch { callback(activate(call)) }
    }

    override fun flush(callback: () -> Unit) {
        engineScope.launch {
            flush()
            callback()
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
        override suspend fun deactivate() {
            graphMutex.withLock {
                val node = (graph.getNode(key) ?: return@withLock) as DependencyGraph.ComputedNode
                // TODO there could be multiple instances for the same key
                node.setAutoValidate(false)
            }
        }
    }
}