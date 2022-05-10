package org.modelix.incremental

class TrackableValue<E>(initialValue: E) : IStateVariableReference<E>, IValueAccessor<E> {
    private var value: E = initialValue

    override suspend fun getValue(): E {
        DependencyTracking.accessed(this)
        return value
    }

    fun setValue(newValue: E) {
        value = newValue
        DependencyTracking.modified(this)
    }

    override fun getGroup(): IStateVariableGroup? {
        return null
    }

    override suspend fun read(): E  = getValue()
}