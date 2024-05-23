package org.modelix.incremental

import kotlin.coroutines.CoroutineContext

expect class ThreadContextVariable<E>() {
    fun asContextElement(value: E): CoroutineContext.Element
    fun getValue(): E?
}
