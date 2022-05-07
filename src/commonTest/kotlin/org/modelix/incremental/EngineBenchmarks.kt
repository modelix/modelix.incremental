package org.modelix.incremental

import kotlinx.benchmark.*
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@State(Scope.Benchmark)
class EngineBenchmarks {
    lateinit var engine: IncrementalEngine
    val input = TrackableList((0L..10000L).toMutableList())
    val avgi = incrementalFunction<Long>("avg") { _ ->
        avg()
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
    @OptIn(ExperimentalTime::class)
    fun manyDependencies() = runTest {
        input.set(10, input.get(10) + 1000000)
        //input.set(1456, input.get(1456) + 3000000)
        //input.set(7654, input.get(7654) + 4000000)
        engine.compute(avgi)
    }

    fun avg(): Long {
        val sum = (0 until input.size()).asSequence()
            .map { input.get(it) }
            .fold(0L) { acc, l -> acc + l }
        return sum / input.size()
    }
}