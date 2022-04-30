package org.modelix.incremental

object DependencyTracking {

    private var listeners: List<IDependencyTrackingListener> = emptyList()

    fun registerListener(l: IDependencyTrackingListener) {
        listeners += l
    }

    fun removeListener(l: IDependencyTrackingListener) {
        listeners -= l
    }

    fun accessed(key: IDependencyKey) {
        for (it in listeners) {
            it.accessed(key)
        }
    }

    fun modified(key: IDependencyKey) {
        for (it in listeners) {
            it.modified(key)
        }
    }

}