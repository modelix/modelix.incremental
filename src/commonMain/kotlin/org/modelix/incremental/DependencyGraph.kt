package org.modelix.incremental

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel

/**
 * Not thread-safe.
 */
class DependencyGraph(val engine: IncrementalEngine) {
    val autoValidationChannel: Channel<EngineValueDependency<*>> = Channel(capacity = Channel.UNLIMITED)
    private val nodes: MutableMap<IDependencyKey, Node> = HashMap()

    fun getNode(key: IDependencyKey): Node? = nodes[key]

    fun getOrAddNode(key: IDependencyKey): Node = nodes.getOrPut(key) {
        if (key is EngineValueDependency<*> && key.engine == engine) ComputedNode(key) else InputNode(key)
    }

    fun getDependencies(from: IDependencyKey): Set<IDependencyKey> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getDependencies().asSequence().map { it.key }.toSet()
    }

    fun getReverseDependencies(from: IDependencyKey): Set<IDependencyKey> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getReverseDependencies().asSequence().map { it.key }.toSet()
    }

    fun setDependencies(from: IDependencyKey, to: Set<IDependencyKey>) {
        val fromNode = getOrAddNode(from)
        setDependencies(fromNode, to)
    }

    fun setDependencies(fromNode: Node, to: Set<IDependencyKey>) {
        val current = fromNode.getDependencies().asSequence().map { it.key }.toSet()
        val addedDeps: Set<IDependencyKey> = to - current
        val removedDeps: Set<IDependencyKey> = current - to
        for (dep in removedDeps) {
            fromNode.removeDependency(getOrAddNode(dep))
        }
        for (dep in addedDeps) {
            fromNode.addDependency(getOrAddNode(dep))
        }
    }

    fun addDependency(from: IDependencyKey, to: IDependencyKey) {
        getOrAddNode(from).addDependency(getOrAddNode(to))
    }

    fun removeDependency(from: IDependencyKey, to: IDependencyKey) {
        val fromNode = nodes[from] ?: return
        val toNode = nodes[to] ?: return
        fromNode.removeDependency(toNode)
    }

    fun contains(key: IDependencyKey) = nodes.containsKey(key)

    open inner class Node(open val key: IDependencyKey) {
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

    inner class InputNode(key: IDependencyKey) : Node(key) {

    }

    inner class ComputedNode(override val key: EngineValueDependency<*>) : Node(key) {
        private var value: Any? = null
        private var state: ECacheEntryState = ECacheEntryState.NEW
        var activeValidation: Deferred<Any?>? = null

        /**
         * if true, the engine will validate it directly after it got invalidated, without any external trigger
         */
        var autoValidate: Boolean = false
        fun getState(): ECacheEntryState = state
        fun getValue(): Any? = value
        fun startValidation() {
            require(state != ECacheEntryState.VALIDATING) { "There is already an active validation for $key" }
            state = ECacheEntryState.VALIDATING
        }
        fun validationSuccessful(newValue: Any?, newDependencies: Set<IDependencyKey>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            value = newValue
            setDependencies(this, newDependencies)
            state = ECacheEntryState.VALID
        }
        fun validationFailed(exception: Throwable, newDependencies: Set<IDependencyKey>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            value = exception
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

