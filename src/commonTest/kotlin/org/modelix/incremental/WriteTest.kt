package org.modelix.incremental

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class WriteTest {
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
    fun simpleWriteTest() = runTestAndCleanup {
        val singleLong = StateVariableType<Long, Long>(0L) { it.first() }
        val svar = StateVariableDeclaration0<Long, Long>("var1", singleLong)

        val writeFunction = engine.incrementalFunction<Unit>("write") { context ->
            context.writeStateVariable(svar, 10L)
        }

        assertEquals(0L, engine.readStateVariable(svar))
        writeFunction()
        assertEquals(10L, engine.readStateVariable(svar))
    }

    @Test
    fun dependencyOnWriteTest() = runTestAndCleanup {
        val singleLong = StateVariableType<Long, Long>(0L) { it.first() }
        val svar = StateVariableDeclaration0<Long, Long>("var1", singleLong)

        val writeFunction = engine.incrementalFunction<Unit>("write") { context ->
            context.writeStateVariable(svar, 10L)
        }
        val readFunction = engine.incrementalFunction<Long>("read") { context ->
            context.readStateVariable(svar) + 1
        }

        assertEquals(1L, readFunction())
        writeFunction()
        assertEquals(11L, readFunction())
    }

}