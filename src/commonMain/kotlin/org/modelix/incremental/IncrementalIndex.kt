package org.modelix.incremental

class IncrementalIndex<K, V> {

    private var unsorted = IncrementalList.empty<Pair<K, V>>()
    private val map: MutableMap<K, List<V>> = HashMap()
    private var removalCounter: Long = 0
    private var insertionCounter: Long = 0

    fun update(newEntries: IncrementalList<Pair<K, V>>) {
        val allRemovals = ArrayList<List<Pair<K, V>>>()
        val allInsertions = ArrayList<List<Pair<K, V>>>()
        newEntries.diff(
            unsorted,
            object : IIncrementalListDiffVisitor<Pair<K, V>> {
                override fun rangeReplaced(index: Int, oldElements: List<Pair<K, V>>, newElements: List<Pair<K, V>>) {
                    removalCounter += oldElements.size
                    insertionCounter += newElements.size
                    allRemovals += oldElements
                    allInsertions += newElements
                }
            },
        )
        allRemovals.forEach { it.forEach { removeEntry(it) } }
        allInsertions.forEach { it.forEach { addEntry(it) } }
        unsorted = newEntries
        // TODO support change tracking of entries
    }

    private fun removeEntry(entry: Pair<K, V>) {
        val list = map[entry.first] ?: return
        val newList = list - entry.second
        if (newList.isEmpty()) {
            map.remove(entry.first)
        } else {
            map[entry.first] = newList
        }
    }

    private fun addEntry(entry: Pair<K, V>) {
        map[entry.first] = (map[entry.first] ?: emptyList()) + entry.second
    }

    fun lookup(key: K): List<V> = map[key] ?: emptyList()

    fun getNumberOfRemovals(): Long = removalCounter
    fun getNumberOfInsertions(): Long = insertionCounter
}
