package org.modelix.incremental

abstract class SLRUMap<K, V>(val protectedSize: Int, val probationarySize: Int) {
    private val protected = LinkedHashMap<K, V>(protectedSize)
    private val probationary = LinkedHashMap<K, V>(probationarySize)

    val size: Int get() = protected.size + probationary.size

    operator fun get(key: K): V? {
        if (probationary.containsKey(key)) {
            val value = probationary.remove(key) as V
            protected[key] = value
            ensureProtectedSize()
            return value
        } else if (protected.containsKey(key)) {
            val value = protected.remove(key) as V
            protected[key] = value
            return value
        } else {
            return null
        }
    }

    fun remove(key: K): V? {
        if (protected.containsKey(key)) {
            val oldValue = protected.remove(key) as V
            evicted(key, oldValue)
            return oldValue
        } else if (probationary.containsKey(key)) {
            val oldValue = probationary.remove(key) as V
            evicted(key, oldValue)
            return oldValue
        } else {
            return null
        }
    }

    private fun ensureProbationarySize() {
        if (probationary.size > probationarySize) {
            val (key, value) = probationary.entries.first()
            probationary.remove(key)
            evicted(key, value)
        }
    }

    private fun ensureProtectedSize() {
        if (protected.size > protectedSize) {
            val (key, value) = protected.entries.first()
            protected.remove(key)
            probationary[key] = value
            ensureProbationarySize()
        }
    }

    operator fun set(key: K, value: V) {
        remove(key)
        probationary[key] = value
        ensureProbationarySize()
    }

    abstract fun evicted(key: K, value: V)
}