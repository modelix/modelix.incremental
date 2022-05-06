package org.modelix.incremental

interface IActiveOutput<E> {
    suspend fun deactivate()
}