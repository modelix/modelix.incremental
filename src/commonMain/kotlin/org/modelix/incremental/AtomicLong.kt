package org.modelix.incremental

expect class AtomicLong() {
    fun incrementAndGet(): Long
    fun decrementAndGet(): Long
    fun get(): Long
}