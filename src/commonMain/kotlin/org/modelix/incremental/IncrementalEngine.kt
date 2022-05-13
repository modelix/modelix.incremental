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

    override suspend fun <T> readStateVariable(call: IStateVariableDeclaration<T>): T {
        checkDisposed()
        val engineValueKey = InternalStateVariableReference(this, call)
        DependencyTracking.accessed(engineValueKey)
        return update(engineValueKey)
    }

    override suspend fun <T> readStateVariables(calls: List<IStateVariableDeclaration<T>>): List<T> {
        checkDisposed()
        val keys = calls.map { InternalStateVariableReference(this, it) }
        keys.forEach { DependencyTracking.accessed(it) }
        return coroutineScope {
            val futures: List<Deferred<T>> = keys.map { key -> async { update(key) } }
            futures.map { it.await() }
        }
    }

    private suspend fun <T> update(engineValueKey: InternalStateVariableReference<T>): T {
        checkDisposed()
        var value: T? = null
        var exception: Throwable? = null
        var state: ECacheEntryState = ECacheEntryState.NEW
        var asyncValue: Deferred<T>? = null
        var node_: DependencyGraph.InternalStateNode<T>
        var evaluation: Evaluation? = null
        graphMutex.withLock {
            processPendingModifications()

            node_ = graph.getOrAddNode(engineValueKey) as DependencyGraph.InternalStateNode<T>
            val node = node_
            if (node is DependencyGraph.ComputationNode) {
                state = node.getState()
                when (state) {
                    ECacheEntryState.VALID -> {
                        value = node.getValue().getValue()
                    }
                    ECacheEntryState.FAILED -> {
                        exception = node.lastException
                    }
                    else -> {
                        val decl = engineValueKey.decl
                        if (decl is IComputationDeclaration) {
                            evaluation = Evaluation(engineValueKey, decl, kotlin.coroutines.coroutineContext[Evaluation])
                            evaluation!!.detectCycle()
                            if (state == ECacheEntryState.VALIDATING) {
                                asyncValue = node.activeValidation
                            } else {
                                withContext(evaluation!!) {
                                    node.startValidation()
                                    asyncValue = engineScope.async(evaluation!! + activeEvaluation.asContextElement(evaluation) + CoroutineName("${engineValueKey.decl}")) {
                                        decl.invoke(IncrementalFunctionContext(node))
                                    }
                                    node.activeValidation = asyncValue
                                }
                            }
                        } else {
                            // TODO run triggers
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
                val node = node_
                if (node is DependencyGraph.ComputationNode) {
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
                } else {
                    node.getValue().getValue()
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

        val key = InternalStateVariableReference(this@IncrementalEngine, call)
        val node = graph.getOrAddNode(key) as DependencyGraph.ComputationNode<T>
        node.setAutoValidate(true)
        graph.autoValidationChannel.send(key)
        return ObservedOutput<T>(key)
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
        if (key is InternalStateVariableReference<*> && key.engine == this) return
        pendingModifications.trySend(key).onFailure { if (it != null) throw it }
    }

    override fun parentGroupChanged(childGroup: IStateVariableGroup) {
        TODO("Not yet implemented")
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

    override fun <T> readStateVariable(call: IStateVariableDeclaration<T>, callback: (T) -> Unit) {
        engineScope.launch { callback(readStateVariable(call)) }
    }

    override fun <T> readStateVariables(calls: List<IStateVariableDeclaration<T>>, callback: (List<T>) -> Unit) {
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

    private inner class IncrementalFunctionContext<RetT>(val node: DependencyGraph.ComputationNode<RetT>) : IIncrementalFunctionContext<RetT> {
        override fun readOwnStateVariable(): Optional<RetT> {
            return node.getValue()
        }

        override fun <T> readStateVariable(key: IStateVariableReference<T>): Optional<T> {
            TODO("Not yet implemented")
        }

        override fun <T> writeStateVariable(ref: IStateVariableReference<T>, value: T) {
            TODO("Not yet implemented")
        }
    }

    private class Evaluation(
        val dependencyKey: IStateVariableReference<*>,
        val call: IComputationDeclaration<*>,
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

    private inner class ObservedOutput<E>(val key: IStateVariableReference<E>) : IActiveOutput<E> {
        override suspend fun deactivate() {
            graphMutex.withLock {
                val node = (graph.getNode(key) ?: return@withLock) as DependencyGraph.ComputationNode<E>
                // TODO there could be multiple instances for the same key
                node.setAutoValidate(false)
            }
        }

        protected fun finalize() {
            engineScope.launch { deactivate() }
        }
    }
}