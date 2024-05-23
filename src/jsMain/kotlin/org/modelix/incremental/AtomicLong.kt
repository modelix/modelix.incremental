package org.modelix.incremental

actual class AtomicLong {
    private var value: Long = 0L
    actual fun incrementAndGet(): Long = ++value
    actual fun decrementAndGet(): Long = --value
    actual fun get(): Long = value
}
