package org.modelix.incremental

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class IncrementalMathTest {
    lateinit var engine: IncrementalEngine

    @BeforeTest
    fun before() {
        engine = IncrementalEngine(1000)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun runTestAndCleanup(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            engine.dispose()
        }
    }

    @Test
    fun simpleCachingTest() = runTestAndCleanup {
        val values = (1..10).map { TrackableValue(it) }
        var numInvocations = 0
        val sum = engine.incrementalFunction<Int>("sum") { context ->
            println("compute sum")
            numInvocations++
            values.fold(0) { acc, value -> acc + value.getValue() }
        }
        assertEquals(55, sum())
        values[5].setValue(10 + values[5].getValue())
        assertEquals(65, sum())
        assertEquals(65, sum())
        assertEquals(2, numInvocations)
    }

    @Test
    fun transitiveDependencies() = runTestAndCleanup {
        val a = TrackableValue(10)
        val b = TrackableValue(5)
        val c = engine.incrementalFunction<Int>("c") {
            a.getValue() + b.getValue()
        }
        val d = engine.incrementalFunction<Int>("d") {
            a.getValue() - b.getValue()
        }
        val e = engine.incrementalFunction<Int>("e") {
            c() * d()
        }

        assertEquals((10 + 5) * (10 - 5), e())
        assertEquals(10 + 5, c())
        assertEquals(10 - 5, d())
        a.setValue(11)
        assertEquals(11 + 5, c())
        assertEquals(11 - 5, d())
        assertEquals((11 + 5) * (11 - 5), e())
    }

    @Test
    fun sideEffects() = runTestAndCleanup {
        val input = (1..3).map { TrackableValue(it) }
        val states = Array<Int>(3) { 0 }
        val f: IncrementalFunctionCall<Unit> = IncrementalFunctionCall0(IncrementalFunctionImplementation0("f") {
            states.indices.forEach { states[it] = input[it].getValue() }
        })
        assertEquals(listOf(0, 0, 0), states.asList())

        val activeOutput = engine.activate(f)
        engine.flush()
        assertEquals(listOf(1, 2, 3), states.asList())

        input[0].setValue(10)
        engine.flush()
        assertEquals(listOf(10, 2, 3), states.asList())

        input[2].setValue(30)
        engine.flush()
        assertEquals(listOf(10, 2, 30), states.asList())

        activeOutput.deactivate()
        input[1].setValue(20)
        engine.flush()
        assertEquals(listOf(10, 2, 30), states.asList())

    }

    @Test
    fun cycleDetection() = runTestAndCleanup {
        val a = TrackableValue(5)
        var b: (suspend ()->Int)? = null
        val c = engine.incrementalFunction<Int>("c") {
            b!!() / 2
        }
        b = engine.incrementalFunction<Int>("b") {
            a.getValue() + c()
        }

        assertFailsWith(DependencyCycleException::class) { c() }
        assertFailsWith(DependencyCycleException::class) { b() }
    }

    @Test
    fun parallelComputation() = runTestAndCleanup {
        var primeFactors: ((Int) -> IncrementalFunctionCall1<List<Int>, Int>)? = null
        primeFactors = incrementalFunction<List<Int>, Int>("f") { _, n ->
            if (n < 2) emptyList() else ((2 .. n / 2).filter { p -> n % p == 0 }).filter { engine.readStateVariable(primeFactors!!(it)).isEmpty() }
        }

        val b: IntRange = 2..1000
        val allFactors = engine.readStateVariables(b.map { primeFactors(it) }).flatten().distinct().sorted()
        assertEquals(95, allFactors.size)
    }

    @Test
    fun singleThread() {
        var primeFactors: ((Int) -> List<Int>)? = null
        primeFactors = { n ->
            if (n < 2) emptyList() else ((2 .. n / 2).filter { p -> n % p == 0 }).filter { primeFactors!!(it).isEmpty() }
        }

        val b: IntRange = 2..10000
        val allFactors = b.map { primeFactors(it) }.flatten().distinct().sorted()
        assertEquals(669, allFactors.size)
    }

    @Test
    fun singleThread2() {
        val b: IntRange = 2..10000
        val allFactors = b.map { singleThread2_primeFactors(it) }.flatten().distinct().sorted()
        assertEquals(669, allFactors.size)
    }

    private val singleThread2_cache = HashMap<Int, List<Int>>()
    private fun singleThread2_primeFactors(n: Int): List<Int> {
        val cached = singleThread2_cache[n]
        if (cached != null) return cached
        val result = if (n < 2) {
            emptyList()
        } else {
            ((2 .. n / 2).filter { p -> n % p == 0 }).filter { singleThread2_primeFactors(it).isEmpty() }
        }
        singleThread2_cache[n] = result
        return result
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun incremental() = runTestAndCleanup {
        val input = TrackableList((0L..1000L).toMutableList())

        var sum: ((Int, Int) -> IncrementalFunctionCall2<Long, Int, Int>)? = null
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

        println("Initial: " + measureTime {
            assertEquals(500500L, engine.readStateVariable(sum!!(0, input.size() - 1)))
        })
        input.set(10, input.get(10) + 13)
        input.set(145, input.get(145) + 7)
        input.set(765, input.get(765) + 23)
        println("Incremental: " + measureTime {
            assertEquals(500500L + 13 + 7 + 23, engine.readStateVariable(sum!!(0, input.size() - 1)))
        })
        println("Non-incremental: " + measureTime {
            assertEquals(500500L + 13 + 7 + 23, input.asSequence().fold(0L) { acc, l -> acc + l })
        })
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun manyDependencies() = runTestAndCleanup {
        val input = TrackableList((0L..1000L).toMutableList())
        val avg: () -> Long = {
            val sum = (0 until input.size()).asSequence()
                .map { input.get(it) }
                .fold(0L) { acc, l -> acc + l }
            sum / input.size() }
        val avgi = engine.incrementalFunction<Long>("avg") { _ ->
            avg()
        }

        println("Initial: " + measureTime {
            assertEquals(500L, avgi())
        })
        input.set(10, input.get(10) + 100000)
        input.set(145, input.get(145) + 300000)
        input.set(765, input.get(765) + 400000)
        println("Incremental: " + measureTime {
            assertEquals(1299L, avgi())
        })
        println("Direct: " + measureTime {
            assertEquals(1299L, avg())
        })
    }
}