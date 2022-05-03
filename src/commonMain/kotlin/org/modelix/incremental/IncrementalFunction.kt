package org.modelix.incremental

private class IncrementalFunctionKey(val body: Any, vararg val parameters: Any?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as IncrementalFunctionKey

        if (body != other.body) return false
        if (!parameters.contentEquals(other.parameters)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = body.hashCode()
        result = 31 * result + parameters.contentHashCode()
        return result
    }
}

fun <RetT> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>)->RetT): () -> RetT {
    return { this.compute(ComputationTask(IncrementalFunctionKey(body), RecomputableValue { body(it) })) }
}
fun <RetT, P1> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1)->RetT): (P1) -> RetT {
    return { p1 -> this.compute(ComputationTask(IncrementalFunctionKey(body, p1), RecomputableValue { body(it, p1) })) }
}
fun <RetT, P1, P2> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1, P2)->RetT): (P1, P2) -> RetT {
    return { p1, p2 -> this.compute(ComputationTask(IncrementalFunctionKey(body, p1, p2), RecomputableValue { body(it, p1, p2) })) }
}
fun <RetT, P1, P2, P3> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1, P2, P3)->RetT): (P1, P2, P3) -> RetT {
    return { p1, p2, p3 -> this.compute(ComputationTask(IncrementalFunctionKey(body, p1, p2, p3), RecomputableValue { body(it, p1, p2, p3) })) }
}
fun <RetT, P1, P2, P3, P4> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4)->RetT): (P1, P2, P3, P4) -> RetT {
    return { p1, p2, p3, p4 -> this.compute(ComputationTask(IncrementalFunctionKey(body, p1, p2, p3, p4), RecomputableValue { body(it, p1, p2, p3, p4) })) }
}
