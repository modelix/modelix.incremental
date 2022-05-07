package org.modelix.incremental

class TrackableList<E>(val list: MutableList<E>) {
    fun size() = list.size
    fun get(index: Int): E {
        DependencyTracking.accessed(ListRangeDependency(this, index, 0))
        return list[index]
    }
    fun set(index: Int, value: E) {
        list[index] = value
        DependencyTracking.modified(ListRangeDependency(this, index, 0))
    }
}

data class ListRangeDependency(val list: TrackableList<*>, val index: Int, val level: Int): IDependencyKey {
    override fun getGroup(): IDependencyKey? {
        if (pow(2, level) >= list.size()) return null
        return ListRangeDependency(list, index / 2, level + 1)
    }
}

private fun pow(base: Int, exponent: Int): Int {
    if (exponent == 0) return 1
    var result = base
    for (i in (2..exponent)) {
        result *= base
    }
    return result
}