package org.modelix.incremental

actual fun getCurrentThread(): Any {
    return Thread.currentThread()
}
