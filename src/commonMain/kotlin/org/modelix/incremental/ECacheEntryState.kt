package org.modelix.incremental

enum class ECacheEntryState(private val valid: Boolean) {
    SUCCESSFUL(true),
    FAILED(true),
    VALIDATING(false),
    INVALID(false);

    fun isValid() = valid
    fun isInvalid() = !valid
}