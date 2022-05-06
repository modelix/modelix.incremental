package org.modelix.incremental

interface IValueAccessor<E> {
    suspend fun getValue(): E
}