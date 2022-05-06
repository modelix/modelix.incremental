package org.modelix.incremental

import kotlin.test.*
import kotlinx.coroutines.test.runTest

class IncrementalMathTest {
    lateinit var engine: IncrementalEngine

    @BeforeTest
    fun before() {
        engine = IncrementalEngine()
    }

    @AfterTest
    fun after() {
        engine.dispose()
    }

    @Test
    fun simpleCachingTest() = runTest {
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
    fun transitiveDependencies() = runTest {
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
    fun sideEffects() = runTest {
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
    fun cycleDetection() = runTest {
        val a = TrackableValue(5)
        var b: (suspend ()->Int)? = null
        val c = engine.incrementalFunction<Int>("c") { b!!() / 2 }
        b = engine.incrementalFunction<Int>("b") { a.getValue() + c() }

        assertFailsWith(DependencyCycleException::class) { c() }
        assertFailsWith(DependencyCycleException::class) { b() }
    }

    @Test
    fun parallelComputation() = runTest {
        var primeFactors: ((Int) -> IncrementalFunctionCall1<List<Int>, Int>)? = null
        primeFactors = incrementalFunction<List<Int>, Int>("f") { _, n ->
            if (n < 2) emptyList() else ((2 .. n / 2).filter { p -> n % p == 0 }).filter { engine.compute(primeFactors!!(it)).isEmpty() }
        }

        val b: IntRange = 2..10000
        val allFactors = engine.computeAll(b.map { primeFactors(it) }).flatten().distinct().sorted()
        assertEquals(669, allFactors.size)
    }
}