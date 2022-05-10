package org.modelix.incremental

class MNode(val type: String, var role: String? = null) : IStateVariableReference {
    private val parent: MNode? = null
    private val children: MutableList<MNode> = ArrayList()
    private val properties: MutableMap<String, String> = HashMap()
    private val references: MutableMap<String, MNode> = HashMap()

    fun getChildren(role: String): List<MNode> {
        DependencyTracking.accessed(RoleDependency(this, role))
        return children.filter { it.role == role }
    }

    fun getAllChildren(): List<MNode> = children

    fun child(type: String, role: String, initializer: MNode.()->Unit): MNode {
        val child = MNode(type, role)
        children += child
        DependencyTracking.modified(RoleDependency(this, role))
        initializer(child)
        return child
    }

    fun property(name: String, value: String?) {
        if (value == null) {
            properties.remove(name)
        } else {
            properties[name] = value
        }
        DependencyTracking.modified(RoleDependency(this, name))
    }

    fun getProperty(role: String): String? {
        DependencyTracking.accessed(RoleDependency(this, role))
        return properties[role]
    }

    override fun getGroup(): IStateVariableReference? {
        return parent
    }
}

data class RoleDependency(val node: MNode, val role: String) : IStateVariableReference {
    override fun getGroup() = node
}