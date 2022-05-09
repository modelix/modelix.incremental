package org.modelix.incremental

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IncrementalIndexTest {
    lateinit var engine: IncrementalEngine
    var invocationCount: Long = 0

    @BeforeTest
    fun before() {
        engine = IncrementalEngine()
        invocationCount = 0
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
    fun test() = runTestAndCleanup {
        val input = TrackableList((5000..6000).toMutableList())
        var buildIndex: (suspend (Int, Int) -> IncrementalList<Pair<Int, Int>>)? = null
        buildIndex =
            engine.incrementalFunction<IncrementalList<Pair<Int, Int>>, Int, Int>("buildIndex") { context, firstIndex, lastIndex ->
                invocationCount++
                if (lastIndex == firstIndex) {
                    IncrementalList.of(input.get(firstIndex) to firstIndex)
                } else {
                    val mid = (lastIndex + firstIndex) / 2
                    IncrementalList.concat(
                        buildIndex!!(firstIndex, mid),
                        buildIndex!!(mid + 1, lastIndex),
                    )
                }
            }
        val createIndex = engine.incrementalFunction<IncrementalIndex<Int, Int>>("createIndex") { context ->
            val index: IncrementalIndex<Int, Int> = context.getPreviousResultOrElse { IncrementalIndex() }
            val incrementalList = buildIndex(0, input.size() - 1)
            index.update(incrementalList)
            index
        }
        assertEquals(500, createIndex().lookup(5500))
        assertEquals(2001, invocationCount)
        assertEquals(1001, createIndex().getNumberOfInsertions())
        input.set(500, 15500)
        input.set(700, 5500)
        assertEquals(700, createIndex().lookup(5500))
        assertEquals(2021, invocationCount)
        assertEquals(1003, createIndex().getNumberOfInsertions())
    }
}