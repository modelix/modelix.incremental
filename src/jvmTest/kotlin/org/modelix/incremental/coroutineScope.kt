package org.modelix.incremental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking

actual val coroutineScope: CoroutineScope = CoroutineScope(newFixedThreadPoolContext(10, "Tests Pool"))
