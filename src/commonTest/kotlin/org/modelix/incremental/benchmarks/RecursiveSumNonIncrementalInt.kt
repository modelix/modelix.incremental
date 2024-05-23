package org.modelix.incremental.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import org.modelix.incremental.TrackableList

@State(Scope.Benchmark)
class RecursiveSumNonIncrementalInt {
    val input = TrackableList((0..10000).toMutableList())
    var modificationIndex: Int = 0

    @Benchmark
    fun nonIncremental() {
        input.set(10, input.get(10) + 1)
        nonIncrementalSum(0, input.size() - 1)
    }

    fun nonIncrementalSum(rangeStart: Int, rangeEnd: Int): Int {
        return if (rangeStart == rangeEnd) {
            input.get(rangeStart)
            //            } else if ((rangeEnd - rangeStart) == 1) {
            //                input.get(rangeStart) + input.get(rangeEnd)
        } else {
            val mid = (rangeStart + rangeEnd) / 2
            nonIncrementalSum(rangeStart, mid) + nonIncrementalSum(mid + 1, rangeEnd)
        }
    }

    @Benchmark
    fun nonRecursive() {
        input.asSequence().fold(0) { acc, i -> acc + i }
    }
}
