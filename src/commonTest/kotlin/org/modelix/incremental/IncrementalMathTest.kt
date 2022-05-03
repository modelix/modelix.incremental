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
    fun test() {
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

}