package org.modelix.incremental

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun simpleCachingTest() {
        val values = (1..10).map { TrackedValue(it) }
        var numInvocations = 0
        val sum = engine.incrementalFunction<Int> { context ->
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
        val a = TrackedValue(10)
        val b = TrackedValue(5)
        val c = engine.incrementalFunction<Int> {
            a.getValue() + b.getValue()
        }
        val d = engine.incrementalFunction<Int> {
            a.getValue() - b.getValue()
        }
        val e = engine.incrementalFunction<Int> {
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
}