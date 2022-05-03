package org.modelix.incremental

/**
 * Not thread-safe.
 */
class DependencyGraph(val engine: IncrementalEngine) {
    private val nodes: MutableMap<IDependencyKey, Node> = HashMap()

    private fun getOrAddNode(key: IDependencyKey): Node = nodes.getOrPut(key) {
        if (key is EngineValueDependency && key.engine == engine) ComputedNode(key) else InputNode(key)
    }

    fun setValue(key: IDependencyKey, value: RecomputableValue<*>) {
        (getOrAddNode(key) as ComputedNode).value = value
    }

    fun getValue(key: IDependencyKey): RecomputableValue<*>? = (nodes[key] as ComputedNode?)?.value

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

    private open inner class Node(val key: IDependencyKey) {
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

    }

    private inner class InputNode(key: IDependencyKey) : Node(key) {

    }

    private inner class ComputedNode(key: IDependencyKey) : Node(key) {
        var value: RecomputableValue<*>? = null
    }
}

