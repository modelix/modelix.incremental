package org.modelix.incremental

class DependencyGraph<KeyT> {
    private val nodes: MutableMap<KeyT, Node> = HashMap()

    private fun getOrAddNode(key: KeyT) = nodes.getOrPut(key) { Node(key) }

    fun setValue(key: KeyT, value: RecomputableValue<*>) {
        getOrAddNode(key).value = value
    }

    fun getValue(key: KeyT): RecomputableValue<*>? = nodes[key]?.value

    fun getDependencies(from: KeyT): Set<KeyT> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getDependencies().asSequence().map { it.key }.toSet()
    }

    fun getReverseDependencies(from: KeyT): Set<KeyT> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getReverseDependencies().asSequence().map { it.key }.toSet()
    }

    fun setDependencies(from: KeyT, to: Set<KeyT>) {
        val fromNode = getOrAddNode(from)
        val current = fromNode.getDependencies().asSequence().map { it.key }.toSet()
        val addedDeps: Set<KeyT> = to - current
        val removedDeps: Set<KeyT> = current - to
        for (dep in removedDeps) {
            fromNode.removeDependency(getOrAddNode(dep))
        }
        for (dep in addedDeps) {
            fromNode.addDependency(getOrAddNode(dep))
        }
    }

    fun addDependency(from: KeyT, to: KeyT) {
        getOrAddNode(from).addDependency(getOrAddNode(to))
    }

    fun removeDependency(from: KeyT, to: KeyT) {
        val fromNode = nodes[from] ?: return
        val toNode = nodes[to] ?: return
        fromNode.removeDependency(toNode)
    }

    fun contains(key: KeyT) = nodes.containsKey(key)

    private inner class Node(val key: KeyT) {
        var value: RecomputableValue<*>? = null
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

