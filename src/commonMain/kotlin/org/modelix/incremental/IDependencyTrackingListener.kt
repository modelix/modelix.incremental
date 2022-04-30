package org.modelix.incremental

interface IDependencyTrackingListener {
    fun accessed(key: IDependencyKey)
    fun modified(key: IDependencyKey)
}