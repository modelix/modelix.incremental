package org.modelix.incremental

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel

/**
 * Not thread-safe.
 */
class DependencyGraph(val engine: IncrementalEngine) {
    val autoValidationChannel: Channel<EngineValueDependency<*>> = Channel(capacity = Channel.UNLIMITED)
    val autoValidations: MutableSet<ComputedNode> = HashSet()
    private val nodes: MutableMap<IStateVariableReference<*>, Node> = HashMap()

    fun getNode(key: IStateVariableReference<*>): Node? = nodes[key]

    fun getOrAddNode(key: IStateVariableReference<*>): Node = nodes.getOrPut(key) {
        if (key is EngineValueDependency<*> && key.engine == engine) ComputedNode(key) else InputNode(key)
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

    inner class InputNode(key: IStateVariableReference<*>) : Node(key) {

    }

    inner class ComputedNode(override val key: EngineValueDependency<*>) : Node(key) {
        private var value: Any? = null
        private var valueInitialized = false
        private var state: ECacheEntryState = ECacheEntryState.NEW
        var activeValidation: Deferred<Any?>? = null

        /**
         * if true, the engine will validate it directly after it got invalidated, without any external trigger
         */
        private var autoValidate: Boolean = false
        fun getState(): ECacheEntryState = state
        fun getValue(): Any? = value
        fun <T> getCurrentOrPreviousValue(): Optional<T> = if (valueInitialized) Optional.of(value as T) else Optional.empty()
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
        fun validationSuccessful(newValue: Any?, newDependencies: Set<IStateVariableReference<*>>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            value = newValue
            valueInitialized = true
            setDependencies(this, newDependencies)
            state = ECacheEntryState.VALID
        }
        fun validationFailed(exception: Throwable, newDependencies: Set<IStateVariableReference<*>>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            value = exception
            valueInitialized = false
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

