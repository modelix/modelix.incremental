package org.modelix.incremental

enum class ECacheEntryState {
    NEW,
    VALID,
    FAILED,
    VALIDATING,
    INVALID,
    DEPENDENCY_INVALID,
}
