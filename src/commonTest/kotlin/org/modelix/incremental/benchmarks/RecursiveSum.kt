package org.modelix.incremental.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.test.runTest
import org.modelix.incremental.IncrementalEngine
import org.modelix.incremental.IncrementalFunctionCall2
import org.modelix.incremental.TrackableList
import org.modelix.incremental.incrementalFunction
import kotlin.random.Random
import kotlin.test.assertEquals

abstract class RecursiveSum(val graphSize: Int) {
    lateinit var engine: IncrementalEngine
    val input = TrackableList((0L..10000L).toMutableList())
    var sum: ((Int, Int) -> IncrementalFunctionCall2<Long, Int, Int>)? = null
    var modificationIndex: Int = 0
    var expectedResult: Long = input.asSequence().fold(0L) { acc, i -> acc + i }
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
        engine = IncrementalEngine(maxSize = graphSize)
        rand = Random(12345)
    }

    @TearDown
    fun after() {
        engine.dispose()
    }

    @Benchmark
    fun incremental() = runTest {
        input.set(modificationIndex, input.get(modificationIndex) + 1)
        expectedResult++
        // This simulates local changes, but still moves across the whole input.
        // This is similar to how you would edit a model, a lot of local changes at one place and then a lot of changes
        // somewhere else.
        modificationIndex = (modificationIndex + rand.nextInt(-5, 7) + input.size()) % input.size()
        val actualResult = engine.readStateVariable(sum!!(0, input.size() - 1))
        assertEquals(expectedResult, actualResult)
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
