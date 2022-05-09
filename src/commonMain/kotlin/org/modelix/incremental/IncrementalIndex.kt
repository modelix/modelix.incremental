package org.modelix.incremental

import kotlin.jvm.Synchronized

class IncrementalIndex<K, V> {

    private var unsorted = IncrementalList.empty<Pair<K, V>>()
    private val map: MutableMap<K, V> = HashMap()
    private var removalCounter: Long = 0
    private var insertionCounter: Long = 0

    fun update(newEntries: IncrementalList<Pair<K, V>>) {
        val allRemovals = ArrayList<List<Pair<K, V>>>()
        val allInsertions = ArrayList<List<Pair<K, V>>>()
        newEntries.diff(unsorted, object : IIncrementalListDiffVisitor<Pair<K, V>> {
            override fun rangeReplaced(index: Int, oldElements: List<Pair<K, V>>, newElements: List<Pair<K, V>>) {
                removalCounter += oldElements.size
                insertionCounter += newElements.size
                allRemovals += oldElements
                allInsertions += newElements
            }
        })
        allRemovals.forEach { it.forEach { map -= it.first } }
        allInsertions.forEach { it.forEach { map += it } }
        unsorted = newEntries
    }

    fun lookup(key: K): V? = map[key]

    fun getNumberOfRemovals(): Long = removalCounter
    fun getNumberOfInsertions(): Long = insertionCounter
}