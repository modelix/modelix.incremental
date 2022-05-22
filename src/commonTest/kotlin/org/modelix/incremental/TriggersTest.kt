package org.modelix.incremental

import kotlin.test.*

class TriggersTest {
    lateinit var engine: IncrementalEngine

    @BeforeTest
    fun before() {
        engine = IncrementalEngine(1000)
    }

    @AfterTest
    fun after() {
        engine.dispose()
    }

    @Test
    fun directTrigger() {
        val externalState = TrackableValue(0)
        val input = TrackableValue(1)

        val writeToExternal = incrementalFunction<Unit>("writeToExternal") { context ->
            externalState.setValue(input.getValue() * 10)
        }
        val readFromExternal = engine.incrementalFunction<Int>("readFromExternal", listOf(writeToExternal)) { context ->
            context.trigger(writeToExternal)
            externalState.getValue() + 1
        }

        assertEquals(11, readFromExternal())
        assertEquals(10, externalState.getValue())
        input.setValue(2)
        assertEquals(21, readFromExternal())
        assertEquals(20, externalState.getValue())
    }

    @Test
    fun indirectTrigger() {
        val externalState = TrackableValue(0)
        val input = TrackableValue(1)

        val writeToExternal = engine.incrementalFunction<Unit>("writeToExternal") { context ->
            externalState.setValue(input.getValue() * 10)
        }
        val readFromExternal = engine.incrementalFunction<Int>("readFromExternal") { context ->
            writeToExternal()
            externalState.getValue() + 1
        }

        assertEquals(11, readFromExternal())
        assertEquals(10, externalState.getValue())
        input.setValue(2)
        assertEquals(21, readFromExternal())
        assertEquals(20, externalState.getValue())
    }

}