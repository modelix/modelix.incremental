package org.modelix.incremental

class RecomputableValue<E>(val function: (context: IIncrementalFunctionContext<E>)->E) : IIncrementalFunctionContext<E> {
    private var value: E? = null
    private var state: ERecomputableValueState = ERecomputableValueState.NEW

    fun getValue(): E? {
        require(state == ERecomputableValueState.VALID) { "Invalid value requires re-computation" }
        return value
    }

    fun getLatestValue(): E? = value

    fun invalidate() {
        state = ERecomputableValueState.INVALID
    }

    fun dependencyInvalidated() {
        state = ERecomputableValueState.DEPENDENCY_INVALID
    }

    fun recompute(): E {
        val recomputed = function(this)
        value = recomputed
        state = ERecomputableValueState.VALID
        return recomputed
    }

    fun getState(): ERecomputableValueState = state

    override fun getPreviousResult(): Optional<E> {
        return if (state == ERecomputableValueState.NEW) Optional.empty() else Optional.of(value as E)
    }

    override fun getPreviousInput(key: IDependencyKey): Optional<*> {
        TODO("Not yet implemented")
    }
}

enum class ERecomputableValueState {
    NEW,
    VALID,
    INVALID,
    DEPENDENCY_INVALID
}