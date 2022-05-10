package org.modelix.incremental.benchmark

import kotlinx.benchmark.*
import kotlinx.coroutines.test.runTest
import org.modelix.incremental.IncrementalEngine
import org.modelix.incremental.IncrementalFunctionCall2
import org.modelix.incremental.TrackableList
import org.modelix.incremental.incrementalFunction

@State(Scope.Benchmark)
class RecursiveSumLarge {
    lateinit var engine: IncrementalEngine
    val input = TrackableList((0L..1000000L).toMutableList())
    var sum: ((Int, Int) -> IncrementalFunctionCall2<Long, Int, Int>)? = null
    var modificationIndex: Int = 0
    init {
        sum = incrementalFunction<Long, Int, Int>("sum") { _, rangeStart, rangeEnd ->
            if (rangeStart == rangeEnd) {
                input.get(rangeStart)
                //            } else if ((rangeEnd - rangeStart) == 1) {
                //                input.get(rangeStart) + input.get(rangeEnd)
            } else {
                val mid = (rangeStart + rangeEnd) / 2
                val subsums = engine.readStateVariables(listOf(sum!!(rangeStart, mid), sum!!(mid + 1, rangeEnd)))
                subsums[0] + subsums[1]
            }
        }
    }

    @Setup
    fun before() {
        engine = IncrementalEngine()
    }

    @TearDown
    fun after() {
        engine.dispose()
    }

    @Benchmark
    fun incremental() = runTest {
        input.set(modificationIndex, input.get(modificationIndex) + 1)
        modificationIndex = (modificationIndex + 1) % input.size()
        engine.readStateVariable(sum!!(0, input.size() - 1))
    }
}



