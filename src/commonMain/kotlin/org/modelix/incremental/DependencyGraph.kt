package org.modelix.incremental

import kotlinx.coroutines.channels.Channel

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
            if (value is ComputationNode && value.state == ECacheEntryState.VALIDATING) {
                // will be added back soon
                // TODO there is a risc of a memory leak here. We should keep track of the nodes that were evicted but not removed.
                return
            }
            tryRemoveNode(value)
        }
    }

    fun getSize() = nodes.size

    private fun tryRemoveNode(n1: InternalStateNode<*, *>): Boolean {
        if (n1.preventRemoval) return false
        if (n1.isAutoValidate()) return false
        //if (dependencies.any { it.state == ECacheEntryState.VALIDATING }) return
        if (n1.getReverseDependencies(EDependencyType.READ).any { it is ComputationNode<*> && it.state == ECacheEntryState.VALIDATING }) return false
        if (n1.getDependencies(EDependencyType.WRITE).isNotEmpty()) {
            // n1 is directly writing a state variable. The map with the values contains a reference to n1.
            return false
        }

        val readReverseDependencies = n1.getReverseDependencies(EDependencyType.READ).toList()

        // replace n2->n1->n0 with n2->n0
        for (dtype in EDependencyType.values()) {
            val dependencies = n1.getDependencies(dtype).toList()
            val reverseDependencies = n1.getReverseDependencies(dtype).toList()
            dependencies.forEach { n0 -> n1.removeDependency(n0, dtype) }
            for (n2 in reverseDependencies) {
                n2.removeDependency(n1, dtype)
                dependencies.forEach { n0 -> n2.addDependency(n0, dtype) }
                //println("Merged $n1 into $n2")
            }
        }

        readReverseDependencies.filterIsInstance<InternalStateNode<*, *>>().forEach { it.shrinkDependencies() }

        require(n1.getDependencies(EDependencyType.READ).isEmpty()) { "$n1 still has dependencies" }
        require(n1.getReverseDependencies(EDependencyType.READ).isEmpty()) { "$n1 still has reverse dependencies" }

        nodes.remove(n1.key)
        n1.dispose()
        return true
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
                parentNode.addDependency(node, EDependencyType.READ)
            }
        }
        if (node is InternalStateNode<*, *>) {
            if (lru[key] == null) { // the get access here is important to move the entry to the MRU end of the queue
                lru[key] = node
            }
        }
        return node
    }

    fun getDependencies(from: IStateVariableReference<*>, type: EDependencyType): Set<IStateVariableGroup> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getDependencies(type).asSequence().map { it.key }.toSet()
    }

    fun getReverseDependencies(from: IStateVariableReference<*>, type: EDependencyType): Set<IStateVariableGroup> {
        val fromNode = nodes[from] ?: return emptySet()
        return fromNode.getReverseDependencies(type).asSequence().map { it.key }.toSet()
    }

    fun setDependencies(from: IStateVariableReference<*>, to: Set<IStateVariableReference<*>>, type: EDependencyType) {
        val fromNode = getOrAddNode(from)
        setDependencies(fromNode, to, type)
    }

    fun setDependencies(fromNode: Node, to: Set<IStateVariableReference<*>>, type: EDependencyType) {
        val current = fromNode.getDependencies(type).asSequence().map { it.key }.toSet()
        val addedDeps: Set<IStateVariableGroup> = to - current
        val removedDeps: Set<IStateVariableGroup> = current - to
        for (dep in removedDeps) {
            fromNode.removeDependency(getOrAddNodeAndGroups(dep), type)
        }
        for (dep in addedDeps) {
            fromNode.addDependency(getOrAddNodeAndGroups(dep), type)
        }
        if (fromNode is InternalStateNode<*, *>) {
            fromNode.shrinkDependencies()
        }
    }

    fun addDependency(from: IStateVariableReference<*>, to: IStateVariableReference<*>, type: EDependencyType) {
        getOrAddNode(from).addDependency(getOrAddNode(to), type)
    }

    fun removeDependency(from: IStateVariableReference<*>, to: IStateVariableReference<*>, type: EDependencyType) {
        val fromNode = nodes[from] ?: return
        val toNode = nodes[to] ?: return
        fromNode.removeDependency(toNode, type)
    }

    fun contains(key: IStateVariableReference<*>) = nodes.containsKey(key)

    open inner class Node(open val key: IStateVariableGroup) {
        private var disposed = false
        private var reachable: Boolean? = null
        var isRoot = false
            set(value) {
                require(value) { "Changing a node to non-root is not supported yet" }
                field = value
                reachable = null
            }
        var anyTransitiveReadInvalid = true
        private val reverseDependencies: Array<MutableSet<Node>> = EDependencyType.values().map { HashSet<Node>() }.toTypedArray()
        private val dependencies: Array<MutableSet<Node>> = EDependencyType.values().map { HashSet<Node>() }.toTypedArray()
        var preventRemoval = false

        open fun isReachable(): Boolean {
            var result = reachable
            if (result == null) {
                result = isRoot || getReverseDependencies(EDependencyType.READ).any { it.isReachable() }
                reachable = result
            }
            return result
        }

        fun dispose() {
            disposed = true
        }

        fun checkNodeDisposed() {
            require(!disposed) { "Node is disposed: $key" }
        }

        fun isDisposed() = disposed

        fun addDependency(dependency: Node, type: EDependencyType) {
            checkNodeDisposed()
            if (dependency == this) return
            require(nodes.containsKey(dependency.key)) { "Not part of the graph: $dependency" }
            dependencies[type.ordinal] += dependency
            dependency.addReverseDependency(this, type)
        }

        open fun removeDependency(dependency: Node, type: EDependencyType) {
            checkNodeDisposed()
            dependencies[type.ordinal] -= dependency
            dependency.removeReverseDependency(this, type)
        }

        fun getDependencies(type: EDependencyType): Set<Node> {
            checkNodeDisposed()
            return dependencies[type.ordinal]
        }

        fun getTransitiveDependencies(result: MutableSet<Node>, type: EDependencyType): Set<Node> {
            checkNodeDisposed()
            if (!result.contains(this)) {
                result += dependencies[type.ordinal]
                dependencies[type.ordinal].forEach { it.getTransitiveDependencies(result, type) }
            }
            return result
        }

        open fun addReverseDependency(dependency: Node, type: EDependencyType) {
            checkNodeDisposed()
            reverseDependencies[type.ordinal] += dependency
            if (type == EDependencyType.READ) reachable = null
        }

        open fun removeReverseDependency(dependency: Node, type: EDependencyType) {
            checkNodeDisposed()
            if (type == EDependencyType.READ) reachable = null
            reverseDependencies[type.ordinal] -= dependency
        }

        fun getReverseDependencies(type: EDependencyType): Set<Node> {
            checkNodeDisposed()
            return reverseDependencies[type.ordinal]
        }

        fun transitiveReadModified() {
            checkNodeDisposed()
            if (anyTransitiveReadInvalid) return
            anyTransitiveReadInvalid = true
            for (dep in getReverseDependencies(EDependencyType.READ)) {
                dep.transitiveReadModified()
            }
        }

        open fun modified() {
            checkNodeDisposed()
            for (dep in getReverseDependencies(EDependencyType.READ)) {
                if (dep is ComputationNode<*>) dep.invalidate()
            }
            transitiveReadModified()
        }
    }

    open inner class ExternalStateGroupNode(key: IStateVariableGroup) : Node(key) {
        private var parentGroup: ExternalStateGroupNode? = null
        override fun toString(): String = "group[$key]"

        fun getParentGroup(): ExternalStateGroupNode? {
            return parentGroup
        }

        override fun addReverseDependency(dependency: Node, type: EDependencyType) {
            super.addReverseDependency(dependency, type)
            if (dependency is ExternalStateGroupNode) {
                parentGroup = dependency
            }
        }

        override fun removeReverseDependency(dependency: Node, type: EDependencyType) {
            super.removeReverseDependency(dependency, type)
            if (dependency == parentGroup) {
                parentGroup = null
            }
        }

        fun removeIfUnused() {
            if (parentGroup == null) return
            if (getReverseDependencies(EDependencyType.READ).size != 1) return
            if (getReverseDependencies(EDependencyType.READ).first() != parentGroup) return
            if (getDependencies(EDependencyType.READ).isNotEmpty()) return
            parentGroup!!.removeDependency(this, EDependencyType.READ)
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

        override fun isReachable(): Boolean {
            return autoValidate || super.isReachable()
        }

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
            updateValue(Optional.of(value), source)
        }

        fun updateValue(value: Optional<In>, source: ComputationNode<*>) {
            val oldValue = getValue()
            outputValue = Optional.empty()
            if (value.hasValue()) {
                inputValues[source] = value.getValue()
            } else {
                inputValues.remove(source)
            }
            if (inputValues.size != 1 || oldValue != getValue()) {
                modified()
                DependencyTracking.modified(key)
                if (autoValidate) autoValidationChannel.trySend(key)
            }
        }

        override fun removeReverseDependency(dependency: Node, type: EDependencyType) {
            super.removeReverseDependency(dependency, type)
            if (dependency is ComputationNode<*> && type == EDependencyType.WRITE && inputValues.contains(dependency)) {
                updateValue(Optional.empty(), dependency)
            }
        }

        fun shrinkDependencies() {
            if (getDependencies(EDependencyType.READ).size < 5) return
            getDependencies(EDependencyType.READ).filterIsInstance<ExternalStateGroupNode>()
                .groupBy { it.getParentGroup() }
                .filter { it.key != null && it.value.size >= 2 }
                .forEach {
                    it.value.forEach { removeDependency(it, EDependencyType.READ) }
                    addDependency(it.key!!, EDependencyType.READ)
                    it.value.forEach { it.removeIfUnused() }
                }
        }
    }

    inner class ComputationNode<E>(key: InternalStateVariableReference<E, E>) : InternalStateNode<E, E>(key) {
        var state: ECacheEntryState = ECacheEntryState.INVALID
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
        var lastException: Throwable? = null

        override fun isReachable(): Boolean {
            require(state != ECacheEntryState.INVALID) { "Validate $key first before checking the reachability" }
            return super.isReachable()
        }

        override fun toString(): String = "computation[${key.decl}]"

        fun getComputation(): IComputationDeclaration<E> = key.decl as IComputationDeclaration<E>

        fun startValidation() {
            require(state != ECacheEntryState.VALIDATING) { "There is already an active validation for $key" }
            state = ECacheEntryState.VALIDATING
        }

        fun validationSuccessful(
            newValue: E,
            newDependencies: Set<IStateVariableReference<*>>,
            newWrites: Set<IStateVariableReference<*>>,
        ) {
            require(state == ECacheEntryState.VALIDATING) { "There is no active validation for $key" }
            writeValue(newValue, this)
            lastException = null
            setDependencies(this, newDependencies, EDependencyType.READ)
            setDependencies(this, newWrites, EDependencyType.WRITE)
            state = ECacheEntryState.VALID
        }

        fun validationFailed(
            exception: Throwable,
            newDependencies: Set<IStateVariableReference<*>>,
            newWrites: Set<IStateVariableReference<*>>,
        ) {
            if (state != ECacheEntryState.VALIDATING) {
                throw RuntimeException("There is no active validation for $key", exception)
            }
            updateValue(Optional.empty(), this)
            lastException = exception
            setDependencies(this, newDependencies, EDependencyType.READ)
            setDependencies(this, newWrites, EDependencyType.WRITE)
            state = ECacheEntryState.FAILED
        }

        fun invalidate() {
            if (state == ECacheEntryState.VALIDATING || state == ECacheEntryState.INVALID) return
            state = ECacheEntryState.INVALID
        }
    }
}

enum class EDependencyType {
    READ,
    WRITE,
}
