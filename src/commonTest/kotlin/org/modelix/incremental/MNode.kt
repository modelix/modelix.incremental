package org.modelix.incremental

class MNode(val type: String, var role: String? = null) {
    val children: MutableList<MNode> = ArrayList()
    val properties: MutableMap<String, String> = HashMap()
    val references: MutableMap<String, MNode> = HashMap()

    fun getChildren(role: String): List<MNode> {
        return children.filter { it.role == role }
    }

    fun child(type: String, role: String, initializer: MNode.()->Unit): MNode {
        val child = MNode(type, role)
        children += child
        initializer(child)
        return child
    }

    fun property(name: String, value: String?) {
        if (value == null) {
            properties.remove(name)
        } else {
            properties[name] = value
        }
    }

    fun getProperty(role: String): String? = properties[role]
}