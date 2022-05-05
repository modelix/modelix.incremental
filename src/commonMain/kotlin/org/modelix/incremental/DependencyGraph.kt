package org.modelix.incremental

/**
 * Not thread-safe.
 */
class DependencyGraph(val engine: IncrementalEngine) {
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

    open inner class Node(val key: IDependencyKey) {
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

    inner class ComputedNode(key: EngineValueDependency<*>) : Node(key) {
        private val value: CacheEntry<*> = CacheEntry(key.call)
        fun getState(): ECacheEntryState = value.getState()
        fun getValue(): Any? = value.getValue()
        fun validate(): Any? = value.recompute()
        override fun dependencyInvalidated() {
            val wasValid = getState() == ECacheEntryState.VALID
            value.dependencyInvalidated()
            if (wasValid) {
                super.dependencyInvalidated()
            }
        }

        override fun invalidate() {
            val wasValid = getState() == ECacheEntryState.VALID
            value.invalidate()
            if (wasValid) {
                super.invalidate()
            }
        }
    }
}

