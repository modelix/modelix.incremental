package org.modelix.incremental

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.HashSet
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class IncrementalEngine : IIncrementalEngine, IStateVariableGroup, IDependencyListener {

    private val graph = DependencyGraph(this)
    private val dispatcher = Dispatchers.Default
    private val activeEvaluation: ThreadContextVariable<Evaluation?> = ThreadContextVariable()
    private var autoValidator: Job? = null
    private var disposed = false
    private val pendingModifications: Channel<IStateVariableReference<*>> = Channel(capacity = Channel.UNLIMITED)
    private val autoValidationsMutex = Mutex()
    private val graphMutex = Mutex()
    private val engineScope = CoroutineScope(dispatcher)

    init {
        DependencyTracking.registerListener(this)
    }

    private fun checkDisposed() {
        if (disposed) throw IllegalStateException("engine is disposed")
    }

    override suspend fun <T> readStateVariable(call: IncrementalFunctionCall<T>): T {
        checkDisposed()
        val engineValueKey = EngineValueDependency(this, call)
        DependencyTracking.accessed(engineValueKey)
        return update(engineValueKey)
    }

    override suspend fun <T> readStateVariables(calls: List<IncrementalFunctionCall<T>>): List<T> {
        checkDisposed()
        val keys = calls.map { EngineValueDependency(this, it) }
        keys.forEach { DependencyTracking.accessed(it) }
        return coroutineScope {
            val futures: List<Deferred<T>> = keys.map { key -> async { update(key) } }
            futures.map { it.await() }
        }
    }

    private suspend fun <T> update(engineValueKey: EngineValueDependency<T>): T {
        checkDisposed()
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
                            asyncValue = engineScope.async(evaluation!! + activeEvaluation.asContextElement(evaluation) + CoroutineName("${engineValueKey.call}")) {
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
        checkDisposed()
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

    override fun accessed(key: IStateVariableReference<*>) {
        val evaluation = activeEvaluation.getValue() ?: return
        evaluation.dependencies += key
    }

    private fun processPendingModifications() {
        if (!graphMutex.isLocked) throw IllegalStateException("lock on graphMutex required")
        var modification = pendingModifications.tryReceive()
        while (modification.isSuccess) {
            val key = modification.getOrThrow()
            graph.getNode(key)?.invalidate()
            modification = pendingModifications.tryReceive()
        }
    }

    override fun modified(key: IStateVariableReference<*>) {
        if (key is EngineValueDependency<*> && key.engine == this) return
        pendingModifications.trySend(key).onFailure { if (it != null) throw it }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        engineScope.cancel(CancellationException("Engine disposed"))
        DependencyTracking.removeListener(this)
    }

    override fun getGroup(): IStateVariableGroup? {
        return null
    }

    override suspend fun flush() {
        checkDisposed()
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

    override fun <T> readStateVariable(call: IncrementalFunctionCall<T>, callback: (T) -> Unit) {
        engineScope.launch { callback(readStateVariable(call)) }
    }

    override fun <T> readStateVariables(calls: List<IncrementalFunctionCall<T>>, callback: (List<T>) -> Unit) {
        engineScope.launch { callback(readStateVariables(calls)) }
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
        override fun readOwnStateVariable(): Optional<RetT> {
            return node.getCurrentOrPreviousValue<RetT>()
        }

        override fun <T> readStateVariable(key: IStateVariableReference<T>): Optional<T> {
            TODO("Not yet implemented")
        }

        override fun <T> writeStateVariable(ref: IStateVariableReference<T>, value: T) {
            TODO("Not yet implemented")
        }
    }

    private class Evaluation(
        val dependencyKey: EngineValueDependency<*>,
        val call: IncrementalFunctionCall<*>,
        val previous: Evaluation?,
    ) : AbstractCoroutineContextElement(Evaluation) {
        companion object Key : CoroutineContext.Key<Evaluation>

        val dependencies: MutableSet<IStateVariableReference<*>> = HashSet()

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