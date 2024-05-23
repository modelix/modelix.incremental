package org.modelix.incremental

import kotlinx.coroutines.asContextElement
import kotlin.coroutines.CoroutineContext

actual class ThreadContextVariable<E> actual constructor() {
    private val threadLocal = ThreadLocal<E>()
    actual fun asContextElement(value: E): CoroutineContext.Element {
        return threadLocal.asContextElement(value)
    }

    actual fun getValue(): E? {
        return threadLocal.get()
    }
}
