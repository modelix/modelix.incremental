package org.modelix.incremental

class DependencyCycleException(val cycle: List<IComputationDeclaration<*>>) : Exception() {
    override val message: String?
        get() = "Cycle detected:\n" + cycle.joinToString("\n") { "  $it" }
}