package org.modelix.incremental

interface IDependencyListener {
    fun accessed(key: IStateVariableReference<*>)
    fun modified(key: IStateVariableReference<*>)
}