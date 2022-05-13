package org.modelix.incremental

actual class VirtualClock {
    private var time: Long = 0L
    actual fun getTime(): Long {
        return ++time
    }
}