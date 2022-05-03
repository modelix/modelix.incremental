package org.modelix.incremental

class TrackedValue<E>(initialValue: E) : IDependencyKey {
    private var value: E = initialValue

    fun getValue(): E {
        DependencyTracking.accessed(this)
        return value
    }
    fun setValue(newValue: E) {
        value = newValue
        DependencyTracking.modified(this)
    }

    override fun getGroup(): IDependencyKey? {
        return null
    }
}