package org.modelix.incremental.benchmark

import kotlinx.benchmark.*
import kotlinx.coroutines.test.runTest
import org.modelix.incremental.IncrementalEngine
import org.modelix.incremental.IncrementalFunctionCall2
import org.modelix.incremental.TrackableList
import org.modelix.incremental.incrementalFunction
import kotlin.random.Random

abstract class RecursiveSumLarge(val graphSize: Int, val modificationsRange: Int) {
    lateinit var engine: IncrementalEngine
    val input = TrackableList((0L..1000000L).toMutableList())
    var sum: ((Int, Int) -> IncrementalFunctionCall2<Long, Int, Int>)? = null
    var modificationIndex: Int = 0
    var rand = Random(12345)
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
        engine = IncrementalEngine(graphSize)
        rand = Random(12345)
    }

    @TearDown
    fun after() {
        println("size: ${engine.getGraphSize()}")
        engine.dispose()
    }

    @Benchmark
    fun incremental() = runTest {
        input.set(modificationIndex, input.get(modificationIndex) + 1)
        modificationIndex = (modificationIndex + rand.nextInt(-5, 7) + modificationsRange) % modificationsRange
        engine.readStateVariable(sum!!(0, input.size() - 1))
    }
}

@State(Scope.Benchmark)
class RecursiveLargeSum10_000_000() : RecursiveSumLarge(10_000_000, 1_000_000)
@State(Scope.Benchmark)
class RecursiveLargeSum1_000() : RecursiveSumLarge(1_000, 100)


