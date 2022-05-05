package org.modelix.incremental

class TrackableValue<E>(initialValue: E) : IDependencyKey, IValueAccessor<E> {
    private var value: E = initialValue

    override fun getValue(): E {
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