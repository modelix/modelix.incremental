package org.modelix.incremental

import java.util.concurrent.atomic.AtomicLong

actual class VirtualClock {
    private val time = AtomicLong(0L)
    actual fun getTime(): Long {
        return time.incrementAndGet()
    }
}