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

    @Test
    fun multipleWrites() = runTestAndCleanup {
        val longListType = StateVariableType<Long, List<Long>>(emptyList()) { it.toList() }
        val svar = StateVariableDeclaration0<Long, List<Long>>("var1", longListType)

        val writeFunction1 = engine.incrementalFunction<Unit>("write1") { context ->
            context.writeStateVariable(svar, 110L)
        }
        val writeFunction2 = engine.incrementalFunction<Unit>("write2") { context ->
            context.writeStateVariable(svar, 200L)
        }
        val avg = engine.incrementalFunction<Long>("read") { context ->
            val values = context.readStateVariable(svar)
            if (values.isEmpty()) 0L else values.sum() / values.size
        }

        assertEquals(0L, avg())
        writeFunction1()
        assertEquals(110L, avg())
        writeFunction2()
        assertEquals(155L, avg())
        writeFunction1()
        assertEquals(155L, avg())
    }

    @Test
    fun revalidateWrite() = runTestAndCleanup {
        val input1 = TrackableValue<Int>(1)
        val longListType = StateVariableType<Long, List<Long>>(emptyList()) {
            println("reduce")
            it.toList()
        }
        val svar = StateVariableDeclaration0<Long, List<Long>>("var1", longListType)

        val writeFunction1 = engine.incrementalFunction<Unit>("write1") { context ->
            println("write1")
            if (input1.getValue() == 1) {
                context.writeStateVariable(svar, 110L)
            }
        }
        val writeFunction2 = engine.incrementalFunction<Unit>("write2") { context ->
            println("write2")
            context.writeStateVariable(svar, 200L)
        }
        val avg = engine.incrementalFunction<Long>("avg") { context ->
            println("avg")
            val values = context.readStateVariable(svar)
            if (values.isEmpty()) 0L else values.sum() / values.size
        }

        assertEquals(0L, avg())
        writeFunction1() // TODO additional test with indirect call of write1
        assertEquals(110L, avg())
        writeFunction2()
        assertEquals(155L, avg())
        writeFunction1()
        assertEquals(155L, avg())
        println("set input1 = 0")
        input1.setValue(0)
        assertEquals(200L, avg())
    }

}