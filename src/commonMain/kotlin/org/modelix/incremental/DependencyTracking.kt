package org.modelix.incremental

object DependencyTracking {
    private val logger = mu.KotlinLogging.logger {}
    private var listeners: List<IDependencyListener> = emptyList()

    fun registerListener(l: IDependencyListener) {
        listeners += l
    }

    fun removeListener(l: IDependencyListener) {
        listeners -= l
    }

    fun accessed(key: IStateVariableReference<*>) {
        for (it in listeners) {
            try {
                it.accessed(key)
            } catch (e: Exception) {
                logger.error(e) { "Exception in listener" }
            }
        }
    }

    fun modified(key: IStateVariableReference<*>) {
        for (it in listeners) {
            try {
                it.modified(key)
            } catch (e: Exception) {
                logger.error(e) { "Exception in listener" }
            }
        }
    }

    fun parentGroupChanged(childGroup: IStateVariableGroup) {
        for (it in listeners) {
            try {
                it.parentGroupChanged(childGroup)
            } catch (e: Exception) {
                logger.error(e) { "Exception in listener" }
            }
        }
    }
}