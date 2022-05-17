package org.modelix.incremental

abstract class IncrementalFunctionImplementation<RetT>(
    val name: String
) {
    open fun getDefaultValue(): Optional<RetT> = Optional.empty()
}

class IncrementalFunctionImplementation0<RetT>(
    name: String,
    val function: (IIncrementalFunctionContext<RetT>) -> RetT,
) : IncrementalFunctionImplementation<RetT>(name) {
    fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function(context)
}

class IncrementalFunctionImplementation1<RetT, P1>(
    name: String,
    val function: (IIncrementalFunctionContext<RetT>, P1) -> RetT,
) : IncrementalFunctionImplementation<RetT>(name) {
    fun invoke(context: IIncrementalFunctionContext<RetT>, p1: P1): RetT = function(context, p1)
}

class IncrementalFunctionImplementation2<RetT, P1, P2>(
    name: String,
    val function: (IIncrementalFunctionContext<RetT>, P1, P2) -> RetT,
) : IncrementalFunctionImplementation<RetT>(name) {
    fun invoke(context: IIncrementalFunctionContext<RetT>, p1: P1, p2: P2): RetT = function(context, p1, p2)
}

class IncrementalFunctionImplementation3<RetT, P1, P2, P3>(
    name: String,
    val function: (IIncrementalFunctionContext<RetT>, P1, P2, P3) -> RetT,
) : IncrementalFunctionImplementation<RetT>(name) {
    fun invoke(context: IIncrementalFunctionContext<RetT>, p1: P1, p2: P2, p3: P3): RetT = function(context, p1, p2, p3)
}

class IncrementalFunctionImplementation4<RetT, P1, P2, P3, P4>(
    name: String,
    val function: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4) -> RetT,
) : IncrementalFunctionImplementation<RetT>(name) {
    fun invoke(context: IIncrementalFunctionContext<RetT>, p1: P1, p2: P2, p3: P3, p4: P4): RetT = function(context, p1, p2, p3, p4)
}

class IncrementalFunctionImplementation5<RetT, P1, P2, P3, P4, P5>(
    name: String,
    val function: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5) -> RetT,
) : IncrementalFunctionImplementation<RetT>(name) {
    fun invoke(context: IIncrementalFunctionContext<RetT>, p1: P1, p2: P2, p3: P3, p4: P4, p5: P5): RetT = function(context, p1, p2, p3, p4, p5)
}

class IncrementalFunctionImplementation6<RetT, P1, P2, P3, P4, P5, P6>(
    name: String,
    val function: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5, P6) -> RetT,
) : IncrementalFunctionImplementation<RetT>(name) {
    fun invoke(context: IIncrementalFunctionContext<RetT>, p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6): RetT = function(context, p1, p2, p3, p4, p5, p6)
}

/**
 * @param defaultValue Is used when a dependency cycle is detected.
 */
abstract class IncrementalFunctionCall<RetT>() : IComputationDeclaration<RetT>, IStateVariableType<RetT, RetT> {
    protected abstract val additionalTriggers: List<IComputationDeclaration<*>>
    /**
     * Shouldn't be invoked directly. Use IIncrementalEngine instead.
     */
    abstract override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT
    fun bind(engine: IIncrementalEngine): () -> RetT = { engine.readStateVariable(this) }
    override fun reduce(inputValues: Iterable<RetT>): RetT {
        return inputValues.first()
    }
    override val type: IStateVariableType<RetT, RetT> get() = this
    override fun getDefault(): RetT {
        throw RuntimeException("Function is expected to be executed before reading the result")
    }

    override fun getTriggers(): List<IComputationDeclaration<*>> = additionalTriggers
}
data class IncrementalFunctionCall0<RetT>(
    val function: IncrementalFunctionImplementation0<RetT>,
    override val additionalTriggers: List<IComputationDeclaration<*>> = emptyList(),
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function.invoke(context)
    override fun toString(): String {
        return "${function.name}()"
    }

}
data class IncrementalFunctionCall1<RetT, P1>(
    val function: IncrementalFunctionImplementation1<RetT, P1>,
    val p1: P1,
    override val additionalTriggers: List<IComputationDeclaration<*>> = emptyList(),
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function.invoke(context, p1)
    override fun toString(): String {
        return "${function.name}($p1)"
    }
}
data class IncrementalFunctionCall2<RetT, P1, P2>(
    val function: IncrementalFunctionImplementation2<RetT, P1, P2>,
    val p1: P1,
    val p2: P2,
    override val additionalTriggers: List<IComputationDeclaration<*>> = emptyList(),
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function.invoke(context, p1, p2)
    override fun toString(): String {
        return "${function.name}($p1, $p2)"
    }
}
data class IncrementalFunctionCall3<RetT, P1, P2, P3>(
    val function: IncrementalFunctionImplementation3<RetT, P1, P2, P3>,
    val p1: P1,
    val p2: P2,
    val p3: P3,
    override val additionalTriggers: List<IComputationDeclaration<*>> = emptyList(),
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function.invoke(context, p1, p2, p3)
    override fun toString(): String {
        return "${function.name}($p1, $p2, $p3)"
    }
}
data class IncrementalFunctionCall4<RetT, P1, P2, P3, P4>(
    val function: IncrementalFunctionImplementation4<RetT, P1, P2, P3, P4>,
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
    override val additionalTriggers: List<IComputationDeclaration<*>> = emptyList(),
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function.invoke(context, p1, p2, p3, p4)
    override fun toString(): String {
        return "${function.name}($p1, $p2, $p3, $p4)"
    }
}
data class IncrementalFunctionCall5<RetT, P1, P2, P3, P4, P5>(
    val function: IncrementalFunctionImplementation5<RetT, P1, P2, P3, P4, P5>,
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
    val p5: P5,
    override val additionalTriggers: List<IComputationDeclaration<*>> = emptyList(),
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function.invoke(context, p1, p2, p3, p4, p5)
    override fun toString(): String {
        return "${function.name}($p1, $p2, $p3, $p4, $p5)"
    }
}
data class IncrementalFunctionCall6<RetT, P1, P2, P3, P4, P5, P6>(
    val function: IncrementalFunctionImplementation6<RetT, P1, P2, P3, P4, P5, P6>,
    val p1: P1,
    val p2: P2,
    val p3: P3,
    val p4: P4,
    val p5: P5,
    val p6: P6,
    override val additionalTriggers: List<IComputationDeclaration<*>> = emptyList(),
) : IncrementalFunctionCall<RetT>() {
    override fun invoke(context: IIncrementalFunctionContext<RetT>): RetT = function.invoke(context, p1, p2, p3, p4, p5, p6)
    override fun toString(): String {
        return "${function.name}($p1, $p2, $p3, $p4, $p5, $p6)"
    }
}

fun <RetT> IIncrementalEngine.incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>)->RetT): () -> RetT {
    val impl = IncrementalFunctionImplementation0(name, body)
    val call = IncrementalFunctionCall0(impl, triggers)
    return { this.readStateVariable(call) }
}

fun <RetT, P1> IIncrementalEngine.incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1)->RetT): (P1) -> RetT {
    val impl = IncrementalFunctionImplementation1(name, body)
    return { p1 -> this.readStateVariable(IncrementalFunctionCall1(impl, p1, triggers)) }
}

fun <RetT, P1, P2> IIncrementalEngine.incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2)->RetT): (P1, P2) -> RetT {
    val impl = IncrementalFunctionImplementation2(name, body)
    return { p1, p2 -> this.readStateVariable(IncrementalFunctionCall2(impl, p1, p2, triggers)) }
}
fun <RetT, P1, P2, P3> IIncrementalEngine.incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2, P3)->RetT): (P1, P2, P3) -> RetT {
    val impl = IncrementalFunctionImplementation3(name, body)
    return { p1, p2, p3 -> this.readStateVariable(IncrementalFunctionCall3(impl, p1, p2, p3, triggers)) }
}
fun <RetT, P1, P2, P3, P4> IIncrementalEngine.incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4)->RetT): (P1, P2, P3, P4) -> RetT {
    val impl = IncrementalFunctionImplementation4(name, body)
    return { p1, p2, p3, p4 -> this.readStateVariable(IncrementalFunctionCall4(impl, p1, p2, p3, p4, triggers)) }
}
fun <RetT, P1, P2, P3, P4, P5> IIncrementalEngine.incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5)->RetT): (P1, P2, P3, P4, P5) -> RetT {
    val impl = IncrementalFunctionImplementation5(name, body)
    return { p1, p2, p3, p4, p5 -> this.readStateVariable(IncrementalFunctionCall5(impl, p1, p2, p3, p4, p5, triggers)) }
}
fun <RetT, P1, P2, P3, P4, P5, P6> IIncrementalEngine.incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5, P6)->RetT): (P1, P2, P3, P4, P5, P6) -> RetT {
    val impl = IncrementalFunctionImplementation6(name, body)
    return { p1, p2, p3, p4, p5, p6 -> this.readStateVariable(IncrementalFunctionCall6(impl, p1, p2, p3, p4, p5, p6, triggers)) }
}

fun <RetT> incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>)->RetT): IncrementalFunctionCall0<RetT> {
    val impl = IncrementalFunctionImplementation0(name, body)
    return IncrementalFunctionCall0(impl, triggers)
}
fun <RetT, P1> incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1)->RetT): (P1) -> IncrementalFunctionCall1<RetT, P1> {
    val impl = IncrementalFunctionImplementation1(name, body)
    return { p1 -> IncrementalFunctionCall1(impl, p1, triggers) }
}
fun <RetT, P1, P2> incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2)->RetT): (P1, P2) -> IncrementalFunctionCall2<RetT, P1, P2> {
    val impl = IncrementalFunctionImplementation2(name, body)
    return { p1, p2 -> IncrementalFunctionCall2(impl, p1, p2, triggers) }
}
fun <RetT, P1, P2, P3> incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2, P3)->RetT): (P1, P2, P3) -> IncrementalFunctionCall3<RetT, P1, P2, P3> {
    val impl = IncrementalFunctionImplementation3(name, body)
    return { p1, p2, p3 -> IncrementalFunctionCall3(impl, p1, p2, p3, triggers) }
}
fun <RetT, P1, P2, P3, P4> incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4)->RetT): (P1, P2, P3, P4) -> IncrementalFunctionCall4<RetT, P1, P2, P3, P4> {
    val impl = IncrementalFunctionImplementation4(name, body)
    return { p1, p2, p3, p4 -> IncrementalFunctionCall4(impl, p1, p2, p3, p4, triggers) }
}
fun <RetT, P1, P2, P3, P4, P5> incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5)->RetT): (P1, P2, P3, P4, P5) -> IncrementalFunctionCall5<RetT, P1, P2, P3, P4, P5> {
    val impl = IncrementalFunctionImplementation5(name, body)
    return { p1, p2, p3, p4, p5 -> IncrementalFunctionCall5(impl, p1, p2, p3, p4, p5, triggers) }
}
fun <RetT, P1, P2, P3, P4, P5, P6> incrementalFunction(name: String, triggers: List<IComputationDeclaration<*>> = emptyList(), body: (IIncrementalFunctionContext<RetT>, P1, P2, P3, P4, P5, P6)->RetT): (P1, P2, P3, P4, P5, P6) -> IncrementalFunctionCall6<RetT, P1, P2, P3, P4, P5, P6> {
    val impl = IncrementalFunctionImplementation6(name, body)
    return { p1, p2, p3, p4, p5, p6 -> IncrementalFunctionCall6(impl, p1, p2, p3, p4, p5, p6, triggers) }
}
