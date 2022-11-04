package org.modelix.incremental

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlin.math.max

/**
 * Not thread-safe.
 */
class DependencyGraph(val engine: IncrementalEngine) {
    val autoValidationChannel: Channel<InternalStateVariableReference<*, *>> = Channel(capacity = Channel.UNLIMITED)
    val autoValidations: MutableSet<InternalStateNode<*, *>> = HashSet()
    private val nodes: MutableMap<IStateVariableGroup, Node> = HashMap()
    private val clock = VirtualClock()
    private val lru = object : SLRUMap<IStateVariableGroup, InternalStateNode<*, *>>(engine.maxSize / 2, engine.maxSize / 2) {
        override fun evicted(key: IStateVariableGroup, value: InternalStateNode<*, *>) {
            if (value.state == ECacheEntryState.VALIDATING || value.state == ECacheEntryState.NEW) {
                // will be added back soon
                return
            }
            tryRemoveNode(value)
        }
    }

    fun getSize() = nodes.size

    private fun tryRemoveNode(n1: InternalStateNode<*, *>) {
        if (n1.isAutoValidate()) return
        // replace n2->n1->n0 with n2->n0
        val dependencies = n1.getDependencies().toList()
        //if (dependencies.any { it.state == ECacheEntryState.VALIDATING }) return
        if (n1.getReverseDependencies().any { it.state == ECacheEntryState.VALIDATING }) return
        dependencies.forEach { n0 -> n1.removeDependency(n0) }
        val reverseDependencies = n1.getReverseDependencies().toList()
        for (n2 in reverseDependencies) {
            n2.removeDependency(n1)
            dependencies.forEach { n0 -> n2.addDependency(n0) }
            //println("Merged $n1 into $n2")
        }
        reverseDependencies.filterIsInstance<InternalStateNode<*, *>>().forEach { it.shrinkDependencies() }
        require(n1.getDependencies().isEmpty()) { "$n1 still has dependencies" }
        require(n1.getReverseDependencies().isEmpty()) { "$n1 still has reverse dependencies" }
        nodes.remove(n1.key)
    }

    fun getNode(key: IStateVariableGroup): Node? = nodes[key]

    fun <T> getOrAddNode(key: IStateVariableReference<T>): Node = getOrAddNodeAndGroups(key)

    private fun getOrAddNodeAndGroups(key: IStateVariableGroup): Node {
        var node = nodes[key]
        if (node == null) {
            var parentGroup: IStateVariableGroup? = null
            node = if (key is InternalStateVariableReference<*, *> && key.engine == engine)
                if (key.decl is IComputationDeclaration<*>)
                    ComputationNode(key as InternalStateVariableReference<Any?, Any?>)
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
        if (node is InternalStateNode<*, *>) {
            if (lru[key] == null) { // the get access here is important to move the entry to the MRU end of the queue
                lru[key] = node
            }
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
        if (fromNode is InternalStateNode<*, *>) {
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
                    if (this is InternalStateNode<*, *>) {
                        //lru[key] // get access to move then entry to the MRU end of the queue
                    }
                }
                if (previousState == ECacheEntryState.VALID) {
                    //println("Invalidated: $key")
                }
            }
        private var lastValidation: Long = 0L
        private val reverseDependencies: MutableSet<Node> = HashSet()
        private val dependencies: MutableSet<Node> = HashSet()

        fun addDependency(dependency: Node) {
            if (dependency == this) return
            require(nodes.containsKey(dependency.key)) { "Not part of the graph: $dependency" }
            dependencies += dependency
            dependency.addReverseDependency(this)
        }

        open fun removeDependency(dependency: Node) {
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

    open inner class InternalStateNode<In, Out>(override val key: InternalStateVariableReference<In, Out>) : Node(key) {
        private val inputValues: MutableMap<ComputationNode<*>, In> = HashMap()
        private var outputValue: Optional<Out> = Optional.empty()
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

        fun getValue(): Optional<Out> {
            if (!outputValue.hasValue() && inputValues.isNotEmpty()) {
                outputValue = Optional.of(key.decl.type.reduce(inputValues.values))
            }
            return outputValue
        }
        fun readValue(): Out {
            return getValue().getOrElse { key.decl.type.getDefault() }
        }
        fun writeValue(value: In, source: ComputationNode<*>) {
            outputValue = Optional.empty()
            inputValues[source] = value
            addDependency(source)
            if (state != ECacheEntryState.VALIDATING) {
                getReverseDependencies().forEach { it.dependencyInvalidated() }
            }
        }

        override fun removeDependency(dependency: Node) {
            super.removeDependency(dependency)
            if (inputValues.contains(dependency)) {
                inputValues.remove(dependency)
                outputValue = Optional.empty()
            }
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

        fun startValidation() {
            require(state != ECacheEntryState.VALIDATING) { "There is already an active validation for $key" }
            state = ECacheEntryState.VALIDATING
        }
    }

    inner class ComputationNode<E>(key: InternalStateVariableReference<E, E>) : InternalStateNode<E, E>(key) {
        var lastException: Throwable? = null

        override fun toString(): String = "computation[${key.decl}]"

        fun getComputation(): IComputationDeclaration<E> = key.decl as IComputationDeclaration<E>

        fun validationSuccessful(newValue: E, newDependencies: Set<IStateVariableReference<*>>) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            writeValue(newValue, this)
            lastException = null
            newDependencies.toList().map { getOrAddNode(it) }
                .filterIsInstance<ExternalStateGroupNode>()
                .forEach { it.accessed() }
            setDependencies(this, newDependencies)
            state = ECacheEntryState.VALID
        }
        fun validationFailed(exception: Throwable, newDependencies: Set<IStateVariableReference<*>>) {
            if (state != ECacheEntryState.VALIDATING) {
                throw RuntimeException("There is no active validation for $key", exception)
            }
            lastException = exception
            setDependencies(this, newDependencies)
            state = ECacheEntryState.FAILED
        }

    }
}

