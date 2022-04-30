package org.modelix.incremental

class RecomputableValue<E>(val function: ()->E) {
    private var value: E? = null
    private var state: ERecomputableValueState = ERecomputableValueState.INVALID

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
        val recomputed = recompute()
        value = recomputed
        return recomputed
    }

    fun getState(): ERecomputableValueState = state
}

enum class ERecomputableValueState {
    VALID,
    INVALID,
    DEPENDENCY_INVALID
}