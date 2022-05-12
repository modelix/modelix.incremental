package org.modelix.incremental

interface IStateVariableGroup {
    /**
     * Groups form a tree of dependencies that are more detailed towards the leafs.
     * To save memory the engine can decrease the granularity of recorded dependencies and just record one of the groups
     * instead of the dependency itself. This may trigger a re-computation more often, but the goal is to reduce the
     * granularity only for those inputs that don't change often. It's a space-time trade off.
     *
     * Example: In case of a dependency on a node in the model the groups would the ancestors of that node.
     *
     * A change of the group has to be notified.
     */
    fun getGroup(): IStateVariableGroup?
}