package org.modelix.incremental

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

expect class ThreadContextVariable<E>() {
    fun asContextElement(value: E): CoroutineContext.Element
    fun getValue(): E?
}