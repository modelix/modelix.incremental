package org.modelix.incremental

class TrackableMap<K, V>() : IStateVariableGroup {
    private val map: MutableMap<K, V> = HashMap()

    operator fun get(key: K): V? {
        DependencyTracking.accessed(TrackableMapEntry(this, key))
        return map[key]
    }

    operator fun set(key: K, value: V) {
        map[key] = value
        DependencyTracking.modified(TrackableMapEntry(this, key))
    }

    override fun getGroup(): IStateVariableGroup? {
        return null
    }
}

data class TrackableMapEntry<K, V>(val map: TrackableMap<K, V>, val key: K) : IStateVariableReference<V?>, IValueAccessor<V?> {
    override fun getGroup(): IStateVariableGroup = map

    override fun read(): V? {
        return map[key]
    }

    override fun getValue(): V? {
        return map[key]
    }
}