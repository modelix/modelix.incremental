package org.modelix.incremental

class MNode(val type: String, var role: String? = null) : IStateVariableReference<MNode> {
    private val parent: MNode? = null
    private val children: MutableList<MNode> = ArrayList()
    private val properties: MutableMap<String, String> = HashMap()
    private val references: MutableMap<String, MNode> = HashMap()

    constructor(type: String, initializer: MNode.() -> Unit) : this(type, null) {
        initializer()
    }

    override fun read(): MNode = this

    fun getChildren(role: String): List<MNode> {
        DependencyTracking.accessed(ChildrenDependency(this, role))
        return children.filter { it.role == role }
    }

    fun getAllChildren(): List<MNode> = children

    fun child(type: String, role: String, initializer: MNode.()->Unit): MNode {
        val child = MNode(type, role)
        children += child
        DependencyTracking.modified(ChildrenDependency(this, role))
        DependencyTracking.modified(AllChildrenDependency(this))
        initializer(child)
        return child
    }

    fun property(name: String, value: String?) {
        if (value == null) {
            properties.remove(name)
        } else {
            properties[name] = value
        }
        DependencyTracking.modified(PropertyDependency(this, name))
    }

    fun getProperty(role: String): String? {
        DependencyTracking.accessed(PropertyDependency(this, role))
        return properties[role]
    }

    override fun getGroup(): IStateVariableReference<MNode>? {
        return parent
    }
}

data class PropertyDependency(val node: MNode, val role: String) : IStateVariableReference<String?> {
    override fun getGroup() = node
    override fun read(): String? = node.getProperty(role)
}
data class ChildrenDependency(val node: MNode, val role: String) : IStateVariableReference<List<MNode>> {
    override fun getGroup() = node
    override fun read(): List<MNode> = node.getChildren(role)
}
data class AllChildrenDependency(val node: MNode) : IStateVariableReference<List<MNode>> {
    override fun getGroup() = node
    override fun read(): List<MNode> = node.getAllChildren()
}