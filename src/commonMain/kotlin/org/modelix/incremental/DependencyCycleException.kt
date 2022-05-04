package org.modelix.incremental

class DependencyCycleException(val cycle: List<IncrementalFunctionCall<*>>) : Exception() {
    override val message: String?
        get() = "Cycle detected:\n" + cycle.joinToString("\n") { "  $it" }
}