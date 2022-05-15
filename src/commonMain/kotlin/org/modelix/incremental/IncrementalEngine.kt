package org.modelix.incremental

import kotlinx.coroutines.*
import kotlin.collections.HashSet
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Synchronized

class IncrementalEngine(val maxSize: Int = 100_000) : IIncrementalEngine, IStateVariableGroup, IDependencyListener {

    private val graph = DependencyGraph(this)
    private var activeEvaluation: Evaluation? = null
    private var autoValidator: Job? = null
    private var disposed = false
    private val engineScope = CoroutineScope(Dispatchers.Default)

    init {
        DependencyTracking.registerListener(this)
    }

    fun getGraphSize() = graph.getSize()

    private fun checkDisposed() {
        if (disposed) throw IllegalStateException("engine is disposed")
    }

    override fun <T> readStateVariable(call: IStateVariableDeclaration<*, T>): T {
        checkDisposed()
        val engineValueKey = InternalStateVariableReference(this, call)
        DependencyTracking.accessed(engineValueKey)
        return update(engineValueKey)
    }

    override fun <T> readStateVariables(calls: List<IStateVariableDeclaration<*, T>>): List<T> {
        checkDisposed()
        val keys = calls.map { InternalStateVariableReference(this, it) }
        keys.forEach { DependencyTracking.accessed(it) }
        return keys.map { update(it) }
    }

    @Synchronized
    private fun <T> update(engineValueKey: InternalStateVariableReference<*, T>): T {
        checkDisposed()

        val node = graph.getOrAddNode(engineValueKey) as DependencyGraph.InternalStateNode<*, T>
        when (val state: ECacheEntryState = node.state) {
            ECacheEntryState.VALID -> {
                return node.getValue().getValue()
            }
            ECacheEntryState.FAILED -> {
                throw (if (node is DependencyGraph.ComputationNode<*>) node.lastException else null) ?: RuntimeException()
            }
            else -> {
                val decl = engineValueKey.decl
                val evaluation = Evaluation(engineValueKey, activeEvaluation)
                evaluation.detectCycle()
                try {
                    activeEvaluation = evaluation

                    // This dependency has to be added here, because the node may be removed from the graph
                    // before the parent finishes evaluation and then the transitive dependencies are lost.
                    evaluation.parent?.let { graph.getOrAddNode(it.dependencyKey).addDependency(node) }

                    if (state == ECacheEntryState.VALIDATING) {
                        TODO("shouldn't happen")
                    } else {
                        node.startValidation()
                        if (decl is IComputationDeclaration<*> && node is DependencyGraph.ComputationNode<*>) {
                            try {
                                val value = (decl as IComputationDeclaration<T>).invoke(IncrementalFunctionContext(evaluation, node) as IIncrementalFunctionContext<T>)
                                (node as DependencyGraph.ComputationNode<T>).validationSuccessful(value, evaluation.dependencies)
                                return value
                            } catch (e : Throwable) {
                                node.validationFailed(e, evaluation.dependencies)
                                throw e
                            }
                        } else {
                            try {
                                val earlierWriters = node.getDependencies()
                                    .filterIsInstance<DependencyGraph.ComputationNode<*>>()
                                    .filter { it.state != ECacheEntryState.VALID }
                                for (earlierWriter in earlierWriters) {
                                    node.removeDependency(earlierWriter)
                                    update(earlierWriter.key)
                                }
                                //TODO("run triggers")
                                node.state = ECacheEntryState.VALID
                                return node.readValue()
                            } catch (e : Throwable) {
                                node.state = ECacheEntryState.FAILED
                                throw e
                            }
                        }
                    }
                } finally {
                    activeEvaluation = evaluation.parent
                }
            }
        }
    }

    @Synchronized
    override fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T> {
        checkDisposed()
        if (autoValidator == null) {
            autoValidator = engineScope.launch {
                while (!disposed) {
                    val key = graph.autoValidationChannel.receive()
                    update(key)
                }
            }
        }

        val key = InternalStateVariableReference(this@IncrementalEngine, call)
        val node = graph.getOrAddNode(key) as DependencyGraph.ComputationNode<T>
        node.setAutoValidate(true)
        graph.autoValidationChannel.trySend(key)
        return ObservedOutput<T>(key)
    }

    override fun accessed(key: IStateVariableReference<*>) {
        val evaluation = activeEvaluation ?: return
        if (evaluation.thread == getCurrentThread()) {
            evaluation.dependencies += key
        }
    }

    @Synchronized
    override fun modified(key: IStateVariableReference<*>) {
        if (key is InternalStateVariableReference<*, *> && key.engine == this) return
        for (group in key.iterateGroups()) {
            val node = graph.getNode(group)
            if (node != null) {
                node.invalidate()
                break
            }
        }
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

    @Synchronized
    override fun flush() {
        checkDisposed()
        for (autoValidation in graph.autoValidations.filter { it.state != ECacheEntryState.VALID }) {
            update(autoValidation.key)
        }
    }

    override fun flush(callback: () -> Unit) {
        engineScope.launch {
            flush()
            callback()
        }
    }

    private inner class IncrementalFunctionContext<RetT>(val evaluation: Evaluation, val node: DependencyGraph.ComputationNode<RetT>) : IIncrementalFunctionContext<RetT> {
        override fun readOwnStateVariable(): Optional<RetT> {
            return node.getValue()
        }

        override fun <T> readStateVariable(key: IInternalStateVariableReference<*, T>): T {
            val value = update(key as InternalStateVariableReference<*, T>)
            evaluation.dependencies += key
            return value
        }

        override fun <T> readStateVariable(key: IStateVariableDeclaration<*, T>): T {
            return readStateVariable(InternalStateVariableReference(this@IncrementalEngine, key))
        }

        override fun <T> writeStateVariable(ref: IInternalStateVariableReference<T, *>, value: T) {
            val targetNode = graph.getOrAddNode(ref) as DependencyGraph.InternalStateNode<T, *>
            targetNode.writeValue(value, node)
            targetNode.addDependency(node)
        }
        override fun <T> writeStateVariable(ref: IStateVariableDeclaration<T, *>, value: T) {
            writeStateVariable(InternalStateVariableReference(this@IncrementalEngine, ref), value)
        }
    }

    private class Evaluation(
        val dependencyKey: InternalStateVariableReference<*, *>,
        val parent: Evaluation?,
    ) : AbstractCoroutineContextElement(Evaluation) {
        companion object Key : CoroutineContext.Key<Evaluation>

        val thread: Any = getCurrentThread()
        val dependencies: MutableSet<IStateVariableReference<*>> = HashSet()

        fun getEvaluations(): List<Evaluation> {
            return (parent?.getEvaluations() ?: emptyList()) + this
        }

        fun detectCycle() {
            var current = parent
            while (current != null) {
                if (current.dependencyKey == dependencyKey) {
                    val activeEvaluations = parent!!.getEvaluations()
                    val cycleStart = activeEvaluations.indexOfLast { it.dependencyKey == dependencyKey }
                    if (cycleStart != -1) {
                        throw DependencyCycleException(activeEvaluations.drop(cycleStart).map { it.dependencyKey.decl })
                    }
                }
                current = current.parent
            }
        }
    }

    @Synchronized
    private fun setAutoValidate(key: IStateVariableReference<*>, value: Boolean) {
        val node = (graph.getNode(key) ?: return) as DependencyGraph.ComputationNode<*>
        // TODO there could be multiple instances for the same key
        node.setAutoValidate(value)
    }

    private inner class ObservedOutput<E>(val key: IStateVariableReference<E>) : IActiveOutput<E> {
        override fun deactivate() {
            setAutoValidate(key, false)
        }

        protected fun finalize() {
            engineScope.launch { deactivate() }
        }
    }
}