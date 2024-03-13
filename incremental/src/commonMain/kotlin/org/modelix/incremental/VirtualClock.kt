package org.modelix.incremental

class VirtualClock() {
    private val time = AtomicLong()
    fun getTime(): Long {
        return time.incrementAndGet()
    }
}
