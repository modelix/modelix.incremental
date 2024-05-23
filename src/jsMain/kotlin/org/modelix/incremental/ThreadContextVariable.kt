package org.modelix.incremental

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

actual class ThreadContextVariable<E : Any?> actual constructor() {
    private var currentValue: E? = null

    actual fun asContextElement(value: E): CoroutineContext.Element {
        return MyContext(value)
    }

    actual fun getValue(): E? {
        return currentValue
    }

    inner class MyContext(
        private var value: E,
    ) : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
        @OptIn(ExperimentalStdlibApi::class)
        override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
            val dispatcher = continuation.context[CoroutineDispatcher] ?: Dispatchers.Default
            return dispatcher.interceptContinuation(Wrapper(continuation))
        }

        inner class Wrapper<T>(private val continuation: Continuation<T>) : Continuation<T> {
            private inline fun wrap(block: () -> Unit) {
                currentValue = value
                block()
            }

            override val context: CoroutineContext get() = continuation.context
            override fun resumeWith(result: Result<T>) = wrap {
                continuation.resumeWith(result)
            }
        }
    }
}
