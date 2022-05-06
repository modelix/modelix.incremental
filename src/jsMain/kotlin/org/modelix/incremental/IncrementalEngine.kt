package org.modelix.incremental

import kotlinx.coroutines.CoroutineScope

actual class IncrementalEngine actual constructor(coroutineScope: CoroutineScope) : IIncrementalEngine {
    override fun <T> compute(call: IncrementalFunctionCall<T>): T {
        TODO("Not yet implemented")
    }

    override suspend fun <T> computeAll(calls: List<IncrementalFunctionCall<T>>): List<T> {
        TODO("Not yet implemented")
    }

    override fun <T> activate(call: IncrementalFunctionCall<T>): IActiveOutput<T> {
        TODO("Not yet implemented")
    }

    override fun flush() {
        TODO("Not yet implemented")
    }

    actual fun dispose() {
    }

}