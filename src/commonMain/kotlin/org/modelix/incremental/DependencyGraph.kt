package org.modelix.incremental

class DependencyGraph<KeyT> {
    private val nodes: MutableMap<KeyT, Node> = HashMap()

    private fun getOrAddNode(key: KeyT) = nodes.getOrPut(key) { Node(key) }

    fun addDependency(from: KeyT, to: KeyT) {
        getOrAddNode(from).addDependency(getOrAddNode(to))
    }

    fun removeDependency(from: KeyT, to: KeyT) {
        val fromNode = nodes[from] ?: return
        val toNode = nodes[to] ?: return
        fromNode.removeDependency(toNode)
        if (!fromNode.isConnected()) nodes.remove(from)
        if (!toNode.isConnected()) nodes.remove(to)
    }

    fun contains(key: KeyT) = nodes.containsKey(key)

    private inner class Node(val key: KeyT) {
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
}

