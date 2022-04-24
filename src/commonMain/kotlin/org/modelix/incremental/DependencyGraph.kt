package org.modelix.incremental

class DependencyGraph<KeyT> {
    private val nodes: MutableMap<KeyT, Node> = HashMap()

    private fun getOrAddComputedNode(key: KeyT) = nodes.getOrPut(key) { ComputedNode(key) }
    private fun getOrAddExternalNode(key: KeyT) = nodes.getOrPut(key) { ExternalNode(key) }

    fun addDependency(from: KeyT, to: KeyT) {
        (getOrAddNode(from) as ComputedNode).addDependency(getOrAddNode(to))
    }

    fun removeDependency(from: KeyT, to: KeyT) {
        val fromNode = nodes[from] ?: return
        val toNode = nodes[to] ?: return
        (fromNode as ComputedNode).removeDependency(toNode)
        if (!fromNode.isConnected()) nodes.remove(from)
        if (!toNode.isConnected()) nodes.remove(to)
    }

    fun contains(key: KeyT) = nodes.containsKey(key)

    private abstract inner class Node(val key: KeyT) {
        protected val reverseDependencies: MutableSet<Node> = HashSet()

        fun addReverseDependency(dependency: Node) {
            reverseDependencies += dependency
        }

        fun removeReverseDependency(dependency: Node) {
            reverseDependencies -= dependency
        }

        fun getReverseDependencies(): Set<Node> = reverseDependencies

        fun isRoot() = reverseDependencies.isEmpty()

        open fun isConnected() = reverseDependencies.isNotEmpty()

        open fun getTransitiveDependencies(result: MutableSet<Node> = HashSet()): Set<Node> = emptySet()
    }

    private inner class ComputedNode(key: KeyT) : Node(key) {
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

        override fun isConnected() = dependencies.isNotEmpty() || super.isConnected()

        override fun getTransitiveDependencies(result: MutableSet<Node>): Set<Node> {
            if (!result.contains(this)) {
                result += dependencies
                dependencies.forEach { it.getTransitiveDependencies(result) }
            }
            return result
        }

    }

    private inner class ExternalNode(key: KeyT) : Node(key) {

    }
}

