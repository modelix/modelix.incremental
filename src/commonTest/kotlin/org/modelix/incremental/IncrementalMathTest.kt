package org.modelix.incremental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlin.test.*
import kotlinx.coroutines.test.runTest

expect val coroutineScope: CoroutineScope

class IncrementalMathTest {
    lateinit var engine: IncrementalEngine

    @BeforeTest
    fun before() {
        engine = IncrementalEngine(coroutineScope)
    }

    @AfterTest
    fun after() {
        engine.dispose()
    }

    @Test
    fun simpleCachingTest() {
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
    fun transitiveDependencies() {
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
    fun sideEffects() {
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
    fun cycleDetection() {
        val a = TrackableValue(5)
        var b: (()->Int)? = null
        val c = engine.incrementalFunction<Int>("c") { b!!() / 2 }
        b = engine.incrementalFunction<Int>("b") { a.getValue() + c() }

        assertFailsWith(DependencyCycleException::class) { c() }
        assertFailsWith(DependencyCycleException::class) { b() }
    }

    @Test
    fun parallelComputation() = runTest {
        val a = TrackableValue(5)
        val f = engine.incrementalFunctionP<Int, Int>("f") { _, b ->
            a.getValue() + b
        }

        val b = 1..1000
        val sum = engine.computeAll(b.map { f(it) }).sum()
        assertEquals(505500, sum)
        //val avg = engine.incrementalFunction<Int>("avg") { _ -> engine.computeAll(b.map { f(it) }).sum() / b.count() }

        //assertEquals(505, avg())
    }
}