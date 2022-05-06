package org.modelix.incremental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt

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
        val factors: (Int) -> IncrementalFunctionCall1<List<Int>, Int> = engine.incrementalFunctionP<List<Int>, Int>("factors") { _, n ->
            (2 .. n / 2).filter { p -> n % p == 0 }
        }
        var primeFactors: ((Int) -> IncrementalFunctionCall1<List<Int>, Int>)? = null
        primeFactors = engine.incrementalFunctionP<List<Int>, Int>("f") { _, n ->
            if (n < 2) emptyList() else ((2 .. n / 2).filter { p -> n % p == 0 }).filter { engine.compute(primeFactors!!(it)).isEmpty() }
        }

        val b: IntRange = 2..1000
        val allFactors = engine.computeAll(b.map { primeFactors(it) }).flatten().distinct().sorted()
        assertEquals(listOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89,
            97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197, 199,
            211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313, 317, 331,
            337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433, 439, 443, 449, 457,
            461, 463, 467, 479, 487, 491, 499), allFactors)
        //val avg = engine.incrementalFunction<Int>("avg") { _ -> engine.computeAll(b.map { f(it) }).sum() / b.count() }

        //assertEquals(505, avg())
    }
}