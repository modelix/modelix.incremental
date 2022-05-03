package org.modelix.incremental

abstract class IncrementalFunctionCall<RetT> {
    /**
     * Shouldn't be invoked directly. Use IIncrementalEngine.compute(...) instead.
     */
    abstract fun invoke(context: IIncrementalFunctionContext<RetT>): RetT
}
data class IncrementalFunctionCall0<RetT>(val function: (IIncrementalFunctionContext<RetT>) -> RetT) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function(context)
}
data class IncrementalFunctionCall1<RetT, P1>(
    val function: (IIncrementalFunctionContext<RetT>, P1) -> RetT,
    val p1: P1,
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function(context, p1)
}
data class IncrementalFunctionCall2<RetT, P1, P2>(
    val function: (IIncrementalFunctionContext<RetT>, P1, P2) -> RetT,
    val p1: P1,
    val p2: P2,
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function(context, p1, p2)
}
data class IncrementalFunctionCall3<RetT, P1, P2, P3>(
    val function: (IIncrementalFunctionContext<RetT>, P1, P2, P3) -> RetT,
    val p1: P1,
    val p2: P2,
    val p3: P3,
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function(context, p1, p2, p3)
}
data class IncrementalFunctionCall4<RetT, P1, P2, P3, P4>(
    val function: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4) -> RetT,
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function(context, p1, p2, p3, p4)
}
data class IncrementalFunctionCall5<RetT, P1, P2, P3, P4, P5>(
    val function: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5) -> RetT,
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
    val p5: P5,
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function(context, p1, p2, p3, p4, p5)
}
data class IncrementalFunctionCall6<RetT, P1, P2, P3, P4, P5, P6>(
    val function: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5, P6) -> RetT,
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
    val p5: P5,
    val p6: P6,
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function(context, p1, p2, p3, p4, p5, p6)
}

fun <RetT> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>)->RetT): () -> RetT {
    return { this.compute(IncrementalFunctionCall0(body)) }
}
fun <RetT, P1> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1)->RetT): (P1) -> RetT {
    return { p1 -> this.compute(IncrementalFunctionCall1(body, p1)) }
}
fun <RetT, P1, P2> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1, P2)->RetT): (P1, P2) -> RetT {
    return { p1, p2 -> this.compute(IncrementalFunctionCall2(body, p1, p2)) }
}
fun <RetT, P1, P2, P3> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1, P2, P3)->RetT): (P1, P2, P3) -> RetT {
    return { p1, p2, p3 -> this.compute(IncrementalFunctionCall3(body, p1, p2, p3)) }
}
fun <RetT, P1, P2, P3, P4> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4)->RetT): (P1, P2, P3, P4) -> RetT {
    return { p1, p2, p3, p4 -> this.compute(IncrementalFunctionCall4(body, p1, p2, p3, p4)) }
}
fun <RetT, P1, P2, P3, P4, P5> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5)->RetT): (P1, P2, P3, P4, P5) -> RetT {
    return { p1, p2, p3, p4, p5 -> this.compute(IncrementalFunctionCall5(body, p1, p2, p3, p4, p5)) }
}
fun <RetT, P1, P2, P3, P4, P5, P6> IIncrementalEngine.incrementalFunction(body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5, P6)->RetT): (P1, P2, P3, P4, P5, P6) -> RetT {
    return { p1, p2, p3, p4, p5, p6 -> this.compute(IncrementalFunctionCall6(body, p1, p2, p3, p4, p5, p6)) }
}
