package org.modelix.incremental

abstract class Optional<E> {
    abstract fun hasValue(): Boolean
    abstract fun getValue(): E
    fun getOrElse(defaultValue: ()->E): E = if (hasValue()) getValue() else defaultValue()
    fun getOrNull(): E? = if (hasValue()) getValue() else null
    companion object {
        private val EMPTY = EmptyOptional<Any?>()
        fun <T> empty(): Optional<T> = EMPTY as Optional<T>
        fun <T> of(value: T): Optional<T> = OptionalWithValue(value)
    }
}

private class EmptyOptional<E> : Optional<E>() {
    override fun hasValue(): Boolean = false
    override fun getValue(): E = throw RuntimeException("Optional has no value")
}

private data class OptionalWithValue<E>(private val value: E) : Optional<E>() {
    override fun hasValue(): Boolean = true
    override fun getValue(): E = value
}