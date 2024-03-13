package org.modelix.incremental

class DependencyCycleException(val cycle: List<IStateVariableDeclaration<*, *>>) : Exception() {
    override val message: String?
        get() = "Cycle detected:\n" + cycle.joinToString("\n") { "  $it" }
}
