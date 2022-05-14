package org.modelix.incremental

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlin.math.max

/**
 * Not thread-safe.
 */
class DependencyGraph(val engine: IncrementalEngine) {
    val autoValidationChannel: Channel<InternalStateVariableReference<*>> = Channel(capacity = Channel.UNLIMITED)
    val autoValidations: MutableSet<InternalStateNode<*>> = HashSet()
    private val nodes: MutableMap<IStateVariableGroup, Node> = HashMap()
    private val lru = LinkedHashSet<InternalStateNode<*>>()
    private val clock = VirtualClock()

    fun getSize() = nodes.size

    fun shrinkGraph(targetSize: Int) {
        val oldSize = getSize()
        val itr = lru.iterator()
        for (n1 in itr) {
            if (nodes.size <= targetSize) break
            //if (nodes.size <= oldSize - 1) break
            if (n1.state == ECacheEntryState.VALIDATING || n1.state == ECacheEntryState.NEW) continue
            var removeNode = false
            when (n1) {
//                is ExternalStateGroupNode -> {
//                    if (n1.getReverseDependencies().any { it !is ExternalStateGroupNode }) {
//                        // remove only if not directly referenced by any computation
//                        continue
//                    }
//                    val parentGroup = n1.getParentGroup()
//                    if (parentGroup == null) continue
//                    if (n1.getDependencies().isNotEmpty()) continue
//                    for (n2 in n1.getReverseDependencies().toList()) {
//                        n2.removeDependency(n1)
//                        n2.addDependency(parentGroup)
//                    }
//                    removeNode = true
//                }
                is InternalStateNode<*> -> {
                    if (n1.isAutoValidate()) continue
                    // replace n2->n1->n0 with n2->n0
                    val dependencies = n1.getDependencies().toList()
                    if (dependencies.any { it.state == ECacheEntryState.VALIDATING }) continue
                    if (n1.getReverseDependencies().any { it.state == ECacheEntryState.VALIDATING }) continue
                    dependencies.forEach { n0 -> n1.removeDependency(n0) }
                    for (n2 in n1.getReverseDependencies().toList()) {
                        n2.removeDependency(n1)
                        dependencies.forEach { n0 -> n2.addDependency(n0) }
                        if (n2 is InternalStateNode<*>) n2.shrinkDependencies()
                        //println("Merged $n1 into $n2")
                    }
                    removeNode = true
                }
                else -> throw RuntimeException("Unknown node type: " + n1::class)
            }
            if (removeNode) {
                require(n1.getDependencies().isEmpty()) { "$n1 still has dependencies" }
                require(n1.getReverseDependencies().isEmpty()) { "$n1 still has reverse dependencies" }
                nodes.remove(n1.key)
                itr.remove()
                //println("Removed (${n1.transitiveDependenciesCount}) ${n1.key}")
            }
        }

        //println("shrink $oldSize -> ${getSize()}")
    }

    fun getNode(key: IStateVariableGroup): Node? = nodes[key]

    fun <T> getOrAddNode(key: IStateVariableReference<T>): Node = getOrAddNodeAndGroups(key)

    private fun getOrAddNodeAndGroups(key: IStateVariableGroup): Node {
        var node = nodes[key]
        if (node == null) {
            var parentGroup: IStateVariableGroup? = null
            node = if (key is InternalStateVariableReference<*> && key.engine == engine)
                if (key.decl is IComputationDeclaration)
                    ComputationNode(key)
                else
                    InternalStateNode(key)
            else {
                val external = if (key is IStateVariableReference<*>) ExternalStateNode(key) else ExternalStateGroupNode(key)
                parentGroup = key.getGroup()
                external
            }
            nodes[key] = node
            if (parentGroup != null) {
                val parentNode = getOrAddNodeAndGroups(parentGroup)
                parentNode.addDependency(node)
            }
        }
        if (node is InternalStateNode<*> && lru.contains(node)) {
            lru.remove(node)
            lru.add(node)
        }
        return node
    }

    fun getDependencies(from: IStateVariableReference<*>): Set<IStateVariableGroup> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getDependencies().asSequence().map { it.key }.toSet()
    }

    fun getReverseDependencies(from: IStateVariableReference<*>): Set<IStateVariableGroup> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getReverseDependencies().asSequence().map { it.key }.toSet()
    }

    fun setDependencies(from: IStateVariableReference<*>, to: Set<IStateVariableReference<*>>) {
        val fromNode = getOrAddNode(from)
        setDependencies(fromNode, to)
    }

    fun setDependencies(fromNode: Node, to: Set<IStateVariableReference<*>>) {
        val current = fromNode.getDependencies().asSequence().map { it.key }.toSet()
        val addedDeps: Set<IStateVariableGroup> = to - current
        val removedDeps: Set<IStateVariableGroup> = current - to
        for (dep in removedDeps) {
            fromNode.removeDependency(getOrAddNodeAndGroups(dep))
        }
        for (dep in addedDeps) {
            fromNode.addDependency(getOrAddNodeAndGroups(dep))
        }
        if (fromNode is InternalStateNode<*>) {
            fromNode.shrinkDependencies()
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

    open inner class Node(open val key: IStateVariableGroup) {
        var state: ECacheEntryState = ECacheEntryState.NEW
            set(newState) {
                val previousState = field
                if (newState == previousState) return
                field = newState
                if (newState == ECacheEntryState.VALID) {
                    lastValidation = clock.getTime()
                    if (this is InternalStateNode<*>) {
                        lru.remove(this)
                        lru.add(this)
                    }
                }
                if (previousState == ECacheEntryState.VALID) {
                    //println("Invalidated: $key")
                }
            }
        private var lastValidation: Long = 0L
        private val reverseDependencies: MutableSet<Node> = HashSet()
        private val dependencies: MutableSet<Node> = HashSet()
        var transitiveDependenciesCount: Int = 1 // monotonic growth is intended

        fun addDependency(dependency: Node) {
            if (dependency == this) return
            require(nodes.containsKey(dependency.key)) { "Not part of the graph: $dependency" }
            dependencies += dependency
            dependency.addReverseDependency(this)
            transitiveDependenciesCount = max(transitiveDependenciesCount, dependencies.fold(1) { acc, d -> acc + d.transitiveDependenciesCount })
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
        open fun addReverseDependency(dependency: Node) {
            reverseDependencies += dependency
        }

        open fun removeReverseDependency(dependency: Node) {
            reverseDependencies -= dependency
        }

        fun getReverseDependencies(): Set<Node> = reverseDependencies

        fun isRoot() = reverseDependencies.isEmpty()

        fun isConnected() = dependencies.isNotEmpty() || reverseDependencies.isNotEmpty()

        open fun invalidate() {
            state = ECacheEntryState.INVALID
            for (dep in getReverseDependencies()) {
                dep.dependencyInvalidated()
            }
        }

        open fun dependencyInvalidated() {
            state = ECacheEntryState.DEPENDENCY_INVALID
            for (dep in getReverseDependencies()) {
                dep.dependencyInvalidated()
            }
        }
    }

    open inner class ExternalStateGroupNode(key: IStateVariableGroup) : Node(key) {
        private var parentGroup: ExternalStateGroupNode? = null
        override fun toString(): String = "group[$key]"

        fun accessed() {
            if (state == ECacheEntryState.VALID) return
            state = ECacheEntryState.VALID
            getReverseDependencies().filterIsInstance<ExternalStateGroupNode>().forEach { it.accessed() }
        }

        fun getParentGroup(): ExternalStateGroupNode? {
            return parentGroup
        }

        override fun addReverseDependency(dependency: Node) {
            super.addReverseDependency(dependency)
            if (dependency is ExternalStateGroupNode) {
                parentGroup = dependency
            }
        }

        override fun removeReverseDependency(dependency: Node) {
            super.removeReverseDependency(dependency)
            if (dependency == parentGroup) {
                parentGroup = null
            }
        }

        fun removeIfUnused() {
            if (parentGroup == null) return
            if (getReverseDependencies().size != 1) return
            if (getReverseDependencies().first() != parentGroup) return
            if (getDependencies().isNotEmpty()) return
            parentGroup!!.removeDependency(this)
            nodes.remove(key)
        }
    }

    inner class ExternalStateNode<E>(key: IStateVariableReference<E>) : ExternalStateGroupNode(key) {
        override fun toString(): String = "external[$key]"
    }

    open inner class InternalStateNode<E>(override val key: InternalStateVariableReference<E>) : Node(key) {
        private var value: Optional<E> = Optional.empty()
        /**
         * if true, the engine will validate it directly after it got invalidated, without any external trigger
         */
        private var autoValidate: Boolean = false

        override fun toString(): String = "internal[${key.decl}]"

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

        fun getValue() = value
        fun setValue(value: E) {
            this.value = Optional.of(value)
        }

        override fun dependencyInvalidated() {
            val wasValid = state == ECacheEntryState.VALID
            state = ECacheEntryState.DEPENDENCY_INVALID
            if (wasValid) {
                super.dependencyInvalidated()
                DependencyTracking.modified(key)
                if (autoValidate) autoValidationChannel.trySend(key)
            }
        }

        override fun invalidate() {
            val wasValid = state == ECacheEntryState.VALID
            state = ECacheEntryState.INVALID
            if (wasValid) {
                super.invalidate()
                DependencyTracking.modified(key)
                if (autoValidate) autoValidationChannel.trySend(key)
            }
        }

        fun shrinkDependencies() {
            if (getDependencies().size < 5) return
            getDependencies().filterIsInstance<ExternalStateGroupNode>()
                .groupBy { it.getParentGroup() }
                .filter { it.key != null && it.value.size >= 2 }
                .forEach {
                    it.value.forEach { removeDependency(it) }
                    addDependency(it.key!!)
                    it.value.forEach { it.removeIfUnused() }
                }
        }
    }

    inner class ComputationNode<E>(key: InternalStateVariableReference<E>) : InternalStateNode<E>(key) {
        var lastException: Throwable? = null

        override fun toString(): String = "computation[${key.decl}]"

        fun getComputation(): IComputationDeclaration<E> = key.decl as IComputationDeclaration<E>

        fun startValidation() {
            require(state != ECacheEntryState.VALIDATING) { "There is already an active validation for $key" }
            state = ECacheEntryState.VALIDATING
        }
        fun validationSuccessful(newValue: E, newDependencies: Set<IStateVariableReference<*>>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            setValue(newValue)
            lastException = null
            newDependencies.map { getOrAddNode(it) }
                .filterIsInstance<ExternalStateGroupNode>()
                .forEach { it.accessed() }
            setDependencies(this, newDependencies)
            state = ECacheEntryState.VALID
        }
        fun validationFailed(exception: Throwable, newDependencies: Set<IStateVariableReference<*>>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            lastException = exception
            setDependencies(this, newDependencies)
            state = ECacheEntryState.FAILED
        }

    }
}

