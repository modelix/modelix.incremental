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

    private fun updateCallers(node: DependencyGraph.Node) {
        if (!node.anyTransitiveCallInvalid) return
        val callers = node.getReverseDependencies(EDependencyType.READ).filter { it.anyTransitiveCallInvalid }
        for (caller in callers) {
            updateCallers(caller)
        }
        update(node.key as InternalStateVariableReference<*, *>)
    }

    @Synchronized
    private fun <T> update(engineValueKey: InternalStateVariableReference<*, T>): T {
        checkDisposed()

        val node = graph.getOrAddNode(engineValueKey) as DependencyGraph.InternalStateNode<*, T>
        val evaluation = Evaluation(engineValueKey, activeEvaluation)
        try {
            node.preventRemoval = true
            evaluation.detectCycle()
            if (evaluation.parent == null) node.isRoot = true
            activeEvaluation = evaluation

            if (node.anyTransitiveReadInvalid) {
                node.anyTransitiveReadInvalid = false
                for (dep in node.getDependencies(EDependencyType.READ).toList()) {
                    val depKey = dep.key
                    if (dep.anyTransitiveReadInvalid && depKey is InternalStateVariableReference<*, *>) {
                        update(depKey)
                    }
                }
            }
            for (dep in node.getReverseDependencies(EDependencyType.WRITE).toList()) {
                updateCallers(dep)
                node.anyTransitiveCallInvalid = false
            }
            if (node is DependencyGraph.ComputationNode<*>) {
                when (val state: ECacheEntryState = node.state) {
                    ECacheEntryState.VALID -> {
                        return node.getValue().getValue()
                    }
                    ECacheEntryState.FAILED -> {
                        throw node.lastException ?: RuntimeException()
                    }
                    else -> {
                        val decl = engineValueKey.decl
                        // This dependency has to be added here, because the node may be removed from the graph
                        // before the parent finishes evaluation and then the transitive dependencies are lost.
                        evaluation.parent?.let { graph.getOrAddNode(it.dependencyKey).addDependency(node, EDependencyType.READ) }

//                    for (trigger in node.key.decl.getTriggers()) {
//                        update(InternalStateVariableReference(this, trigger))
//                    }

                        node.startValidation()
                        if (decl is IComputationDeclaration<*>) {
                            try {
                                val value = (decl as IComputationDeclaration<T>).invoke(IncrementalFunctionContext(evaluation, node) as IIncrementalFunctionContext<T>)
                                (node as DependencyGraph.ComputationNode<T>).validationSuccessful(
                                    value,
                                    evaluation.readDependencies,
                                    evaluation.writeDependencies,
                                )
                                return value
                            } catch (e : Throwable) {
                                node.validationFailed(e, evaluation.readDependencies, evaluation.writeDependencies)
                                throw e
                            }
                        } else {
                            try {
                                val earlierWriters = node.getDependencies(EDependencyType.READ)
                                    .filterIsInstance<DependencyGraph.ComputationNode<*>>()
                                    .filter { it.state != ECacheEntryState.VALID }
                                for (earlierWriter in earlierWriters) {
                                    node.removeDependency(earlierWriter, EDependencyType.READ)
                                    update(earlierWriter.key)
                                }
                                node.state = ECacheEntryState.VALID
                                return node.readValue()
                            } catch (e : Throwable) {
                                node.state = ECacheEntryState.FAILED
                                throw e
                            }
                        }
                    }
                }
            } else {
                return node.readValue()
            }
        } finally {
            node.preventRemoval = false
            activeEvaluation = evaluation.parent
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
            evaluation.readDependencies += key
//            if (key is InternalStateVariableReference<*, *> && key.engine == this && key.decl is IComputationDeclaration<*>) {
//                evaluation.triggerDependencies += key
//            }
        }
    }

    @Synchronized
    override fun modified(key: IStateVariableReference<*>) {
        if (key is InternalStateVariableReference<*, *> && key.engine == this) return
        val evaluation = activeEvaluation
        if (evaluation != null && evaluation.thread == getCurrentThread()) {
            evaluation.writeDependencies += key
        }
        for (group in key.iterateGroups()) {
            val node = graph.getNode(group) as DependencyGraph.ExternalStateGroupNode?
            if (node != null) {
                node.modified()
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
        for (autoValidation in graph.autoValidations.filter { it !is DependencyGraph.ComputationNode<*> || it.state != ECacheEntryState.VALID }) {
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
            evaluation.readDependencies += key
            return value
        }

        override fun <T> readStateVariable(key: IStateVariableDeclaration<*, T>): T {
            return readStateVariable(InternalStateVariableReference(this@IncrementalEngine, key))
        }

        override fun <T> writeStateVariable(ref: IInternalStateVariableReference<T, *>, value: T) {
            val targetNode = graph.getOrAddNode(ref) as DependencyGraph.InternalStateNode<T, *>
            targetNode.writeValue(value, node)
            evaluation.writeDependencies += ref
        }
        override fun <T> writeStateVariable(ref: IStateVariableDeclaration<T, *>, value: T) {
            writeStateVariable(InternalStateVariableReference(this@IncrementalEngine, ref), value)
        }

        override fun trigger(decl: IComputationDeclaration<*>) {
            TODO("deprectated")
        }
    }

    private class Evaluation(
        val dependencyKey: InternalStateVariableReference<*, *>,
        val parent: Evaluation?,
    ) : AbstractCoroutineContextElement(Evaluation) {
        companion object Key : CoroutineContext.Key<Evaluation>

        val thread: Any = getCurrentThread()
        val readDependencies: MutableSet<IStateVariableReference<*>> = HashSet()
        val writeDependencies: MutableSet<IStateVariableReference<*>> = HashSet()

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