package org.modelix.incremental.benchmarks

import kotlinx.benchmark.*
import kotlinx.coroutines.test.runTest
import org.modelix.incremental.IncrementalEngine
import org.modelix.incremental.IncrementalFunctionCall2
import org.modelix.incremental.TrackableList
import org.modelix.incremental.incrementalFunction

abstract class RecursiveSum(val graphSize: Int) {
    lateinit var engine: IncrementalEngine
    val input = TrackableList((0L..10000L).toMutableList())
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
        engine = IncrementalEngine(maxSize = graphSize, maxActiveValidations = 100)
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

@State(Scope.Benchmark)
class RecursiveSum100000() : RecursiveSum(100000)
@State(Scope.Benchmark)
class RecursiveSum10000() : RecursiveSum(10000)
@State(Scope.Benchmark)
class RecursiveSum5000() : RecursiveSum(5000)
@State(Scope.Benchmark)
class RecursiveSum2500() : RecursiveSum(2500)
@State(Scope.Benchmark)
class RecursiveSum1250() : RecursiveSum(1250)


