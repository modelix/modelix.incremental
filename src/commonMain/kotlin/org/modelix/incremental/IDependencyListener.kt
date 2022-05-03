package org.modelix.incremental

interface IDependencyListener {
    fun accessed(key: IDependencyKey)
    fun modified(key: IDependencyKey)
}