package org.modelix.incremental

class CacheEntry<E>(val call: IncrementalFunctionCall<E>) : IIncrementalFunctionContext<E> {
    private var value: E? = null
    private var state: ECacheEntryState = ECacheEntryState.NEW

    fun getValue(): E? {
        require(state == ECacheEntryState.VALID) { "Invalid value requires re-computation" }
        return value
    }

    fun getLatestValue(): E? = value

    fun invalidate() {
        state = ECacheEntryState.INVALID
    }

    fun dependencyInvalidated() {
        state = ECacheEntryState.DEPENDENCY_INVALID
    }

    fun recompute(): E {
        val recomputed = call.invoke(this)
        value = recomputed
        state = ECacheEntryState.VALID
        return recomputed
    }

    fun getState(): ECacheEntryState = state

    override fun getPreviousResult(): Optional<E> {
        return if (state == ECacheEntryState.NEW) Optional.empty() else Optional.of(value as E)
    }

    override fun getPreviousInput(key: IDependencyKey): Optional<*> {
        TODO("Not yet implemented")
    }
}

enum class ECacheEntryState {
    NEW,
    VALID,
    INVALID,
    DEPENDENCY_INVALID
}