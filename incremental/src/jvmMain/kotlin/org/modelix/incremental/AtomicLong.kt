package org.modelix.incremental

actual class AtomicLong {
    private val value = java.util.concurrent.atomic.AtomicLong()
    actual fun incrementAndGet(): Long = value.incrementAndGet()
    actual fun decrementAndGet(): Long = value.decrementAndGet()
    actual fun get(): Long = value.get()
}
