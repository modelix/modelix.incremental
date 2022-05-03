package org.modelix.incremental

abstract class Optional<E> {
    abstract fun hasValue(): Boolean
    abstract fun getValue(): E
    fun getOrElse(defaultValue: ()->E): E = if (hasValue()) getValue() else defaultValue()
    fun getOrNull(): E? = if (hasValue()) getValue() else null
    companion object {
        fun <T> empty() = EmptyOptional<T>()
        fun <T> of(value: T) = OptionalWithValue(value)
    }
}

class EmptyOptional<E> : Optional<E>() {
    override fun hasValue(): Boolean = false
    override fun getValue(): E = throw RuntimeException("Optional has no value")
}

class OptionalWithValue<E>(private val value: E) : Optional<E>() {
    override fun hasValue(): Boolean = true
    override fun getValue(): E = value
}