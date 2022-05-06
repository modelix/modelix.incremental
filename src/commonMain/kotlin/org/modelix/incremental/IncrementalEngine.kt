package org.modelix.incremental

import kotlinx.coroutines.CoroutineScope

expect class IncrementalEngine(coroutineScope: CoroutineScope) : IIncrementalEngine {
    fun dispose()
}