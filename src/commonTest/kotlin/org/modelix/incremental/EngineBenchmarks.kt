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
    fun manyDependencies() = runTest {
        input.set(10, input.get(10) + 1)
        engine.compute(avgi)
    }

    @Benchmark
    fun nonIncremental() = runTest {
        input.set(10, input.get(10) + 1)
        avg()
    }

    fun avg(): Long {
        val sum = input.asSequence()
            .fold(0L) { acc, l -> acc + l }
        return sum / input.size()
    }
}