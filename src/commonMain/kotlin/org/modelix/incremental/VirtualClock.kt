package org.modelix.incremental

expect class VirtualClock() {
    fun getTime(): Long
}