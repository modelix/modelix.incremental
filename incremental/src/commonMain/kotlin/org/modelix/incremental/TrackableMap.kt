package org.modelix.incremental

import kotlin.jvm.Synchronized

class TrackableMap<K, V>() : IStateVariableGroup {
    private val map: MutableMap<K, V> = HashMap()

    @Synchronized
    operator fun get(key: K): V? {
        DependencyTracking.accessed(TrackableMapEntry(this, key))
        return map[key]
    }

    @Synchronized
    operator fun set(key: K, value: V) {
        if (map[key] == value) return
        map[key] = value
        DependencyTracking.modified(TrackableMapEntry(this, key))
    }

    @Synchronized
    fun remove(key: K) {
        if (!map.containsKey(key)) return
        map.remove(key)
        DependencyTracking.modified(TrackableMapEntry(this, key))
    }

    @Synchronized
    fun clear() {
        if (map.isEmpty()) return
        val removedKeys = map.keys.toList()
        map.clear()
        for (key in removedKeys) {
            DependencyTracking.modified(TrackableMapEntry(this, key))
        }
    }

    @Synchronized
    fun removeWhere(predicate: (Map.Entry<K, V>) -> Boolean) {
        map.asSequence().filter(predicate).map { it.key }.toList().forEach { remove(it) }
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
