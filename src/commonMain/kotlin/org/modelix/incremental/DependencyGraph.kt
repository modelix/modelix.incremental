package org.modelix.incremental

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel

/**
 * Not thread-safe.
 */
class DependencyGraph(val engine: IncrementalEngine) {
    val autoValidationChannel: Channel<InternalStateVariableReference<*>> = Channel(capacity = Channel.UNLIMITED)
    val autoValidations: MutableSet<ComputationNode<*>> = HashSet()
    private val nodes: MutableMap<IStateVariableReference<*>, Node> = HashMap()

    fun getNode(key: IStateVariableReference<*>): Node? = nodes[key]

    fun <T> getOrAddNode(key: IStateVariableReference<T>): Node = nodes.getOrPut(key) {
        if (key is InternalStateVariableReference && key.engine == engine)
            if (key.decl is IComputationDeclaration)
                ComputationNode<T>(key)
            else
                InternalStateNode(key)
        else
            ExternalStateNode(key)
    }

    fun getDependencies(from: IStateVariableReference<*>): Set<IStateVariableReference<*>> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getDependencies().asSequence().map { it.key }.toSet()
    }

    fun getReverseDependencies(from: IStateVariableReference<*>): Set<IStateVariableReference<*>> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getReverseDependencies().asSequence().map { it.key }.toSet()
    }

    fun setDependencies(from: IStateVariableReference<*>, to: Set<IStateVariableReference<*>>) {
        val fromNode = getOrAddNode(from)
        setDependencies(fromNode, to)
    }

    fun setDependencies(fromNode: Node, to: Set<IStateVariableReference<*>>) {
        val current = fromNode.getDependencies().asSequence().map { it.key }.toSet()
        val addedDeps: Set<IStateVariableReference<*>> = to - current
        val removedDeps: Set<IStateVariableReference<*>> = current - to
        for (dep in removedDeps) {
            fromNode.removeDependency(getOrAddNode(dep))
        }
        for (dep in addedDeps) {
            fromNode.addDependency(getOrAddNode(dep))
        }
    }

    fun addDependency(from: IStateVariableReference<*>, to: IStateVariableReference<*>) {
        getOrAddNode(from).addDependency(getOrAddNode(to))
    }

    fun removeDependency(from: IStateVariableReference<*>, to: IStateVariableReference<*>) {
        val fromNode = nodes[from] ?: return
        val toNode = nodes[to] ?: return
        fromNode.removeDependency(toNode)
    }

    fun contains(key: IStateVariableReference<*>) = nodes.containsKey(key)

    open inner class Node(open val key: IStateVariableReference<*>) {
        private val reverseDependencies: MutableSet<Node> = HashSet()
        private val dependencies: MutableSet<Node> = HashSet()

        fun addDependency(dependency: Node) {
            if (dependency == this) return
            dependencies += dependency
            dependency.addReverseDependency(this)
        }

        fun removeDependency(dependency: Node) {
            dependencies -= dependency
            dependency.removeReverseDependency(this)
        }

        fun getDependencies(): Set<Node> = dependencies

        fun getTransitiveDependencies(result: MutableSet<Node>): Set<Node> {
            if (!result.contains(this)) {
                result += dependencies
                dependencies.forEach { it.getTransitiveDependencies(result) }
            }
            return result
        }
        fun addReverseDependency(dependency: Node) {
            reverseDependencies += dependency
        }

        fun removeReverseDependency(dependency: Node) {
            reverseDependencies -= dependency
        }

        fun getReverseDependencies(): Set<Node> = reverseDependencies

        fun isRoot() = reverseDependencies.isEmpty()

        fun isConnected() = dependencies.isNotEmpty() || reverseDependencies.isNotEmpty()

        open fun invalidate() {
            for (dep in getReverseDependencies()) {
                dep.dependencyInvalidated()
            }
        }

        open fun dependencyInvalidated() {
            for (dep in getReverseDependencies()) {
                dep.dependencyInvalidated()
            }
        }

    }

    inner class ExternalStateNode(key: IStateVariableReference<*>) : Node(key) {

    }

    open inner class InternalStateNode<E>(override val key: InternalStateVariableReference<E>) : Node(key) {
        private var value: Optional<E> = Optional.empty()
        fun getValue() = value
        fun setValue(value: E) {
            this.value = Optional.of(value)
        }
    }

    inner class ComputationNode<E>(key: InternalStateVariableReference<E>) : InternalStateNode<E>(key) {
        var lastException: Throwable? = null
        private var state: ECacheEntryState = ECacheEntryState.NEW
        var activeValidation: Deferred<E>? = null

        /**
         * if true, the engine will validate it directly after it got invalidated, without any external trigger
         */
        private var autoValidate: Boolean = false
        fun getComputation(): IComputationDeclaration<E> = key.decl as IComputationDeclaration<E>
        fun getState(): ECacheEntryState = state
        fun setAutoValidate(newValue: Boolean) {
            if (newValue == autoValidate) return
            autoValidate = newValue
            if (newValue) {
                autoValidations += this
            } else {
                autoValidations -= this
            }
        }
        fun isAutoValidate() = autoValidate
        fun startValidation() {
            require(state != ECacheEntryState.VALIDATING) { "There is already an active validation for $key" }
            state = ECacheEntryState.VALIDATING
        }
        fun validationSuccessful(newValue: E, newDependencies: Set<IStateVariableReference<*>>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            setValue(newValue)
            lastException = null
            setDependencies(this, newDependencies)
            state = ECacheEntryState.VALID
        }
        fun validationFailed(exception: Throwable, newDependencies: Set<IStateVariableReference<*>>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            lastException = exception
            setDependencies(this, newDependencies)
            state = ECacheEntryState.FAILED
        }
        override fun dependencyInvalidated() {
            val wasValid = getState() == ECacheEntryState.VALID
            state = ECacheEntryState.DEPENDENCY_INVALID
            if (wasValid) {
                super.dependencyInvalidated()
                DependencyTracking.modified(key)
                if (autoValidate) autoValidationChannel.trySend(key)
            }
        }

        override fun invalidate() {
            val wasValid = getState() == ECacheEntryState.VALID
            state = ECacheEntryState.INVALID
            if (wasValid) {
                super.invalidate()
                DependencyTracking.modified(key)
                if (autoValidate) autoValidationChannel.trySend(key)
            }
        }
    }
}

