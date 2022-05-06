package org.modelix.incremental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

actual val coroutineScope: CoroutineScope = GlobalScope