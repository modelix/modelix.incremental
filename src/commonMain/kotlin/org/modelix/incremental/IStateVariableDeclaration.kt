package org.modelix.incremental

interface IStateVariableType<in In, out Out> {
    fun getDefault(): Out
    fun reduce(inputValues: Iterable<In>): Out
}
class StateVariableType<in In, out Out>(val defaultValue: Out, val reduceFunction: (Iterable<In>)->Out) : IStateVariableType<In, Out> {
    override fun getDefault(): Out {
        return defaultValue
    }

    override fun reduce(inputValues: Iterable<In>): Out {
        return reduceFunction(inputValues)
    }
}

/**
 * An IStateVariableDeclaration is an identifier for a state variable inside an IIncrementalEngine.
 * Multiple engine instances can have their own instance of a variable for the same IStateVariableDeclaration.
 * The combination of the IStateVariableDeclaration and the IIncrementalEngine is an IStateVariableReference,
 * (in this case InternalStateVariableReference). IStateVariableReference is unique identifier across the whole system
 * including variables outside any engine.
 * The state variable can have parameters that are accessible from computations.
 */
interface IStateVariableDeclaration<in In, out Out> {
    val type: IStateVariableType<In, Out>
    fun getTriggers(): List<IComputationDeclaration<*>>
}
abstract class StateVariableDeclaration<In, Out>() : IStateVariableDeclaration<In, Out> {
    abstract val name: String
}
data class StateVariableDeclaration0<In, Out>(
    override val name: String,
    override val type: IStateVariableType<In, Out>,
    private val triggers: List<IComputationDeclaration<*>> = emptyList()
) : StateVariableDeclaration<In, Out>() {
    override fun getTriggers(): List<IComputationDeclaration<*>> = triggers
}
//data class StateVariableDeclaration1<In, Out, P1>(override val name: String, val p1: P1, override val type: IStateVariableType<In, Out>) : StateVariableDeclaration<In, Out>()
//data class StateVariableDeclaration2<In, Out, P1, P2>(override val name: String, val p1: P1, val p2: P2, override val type: IStateVariableType<In, Out>) : StateVariableDeclaration<In, Out>()
//data class StateVariableDeclaration3<In, Out, P1, P2, P3>(override val name: String, val p1: P1, val p2: P2, val p3: P3, override val type: IStateVariableType<In, Out>) : StateVariableDeclaration<In, Out>()
//data class StateVariableDeclaration4<In, Out, P1, P2, P3, P4>(override val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4, override val type: IStateVariableType<In, Out>) : StateVariableDeclaration<In, Out>()
//data class StateVariableDeclaration5<In, Out, P1, P2, P3, P4, P5>(override val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4, val p5: P5, override val type: IStateVariableType<In, Out>) : StateVariableDeclaration<In, Out>()
//data class StateVariableDeclaration6<In, Out, P1, P2, P3, P4, P5, P6>(override val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4, val p5: P5, val p6: P6, override val type: IStateVariableType<In, Out>) : StateVariableDeclaration<In, Out>()

interface IInternalStateVariableReference<in In, out Out> : IStateVariableReference<Out>

data class InternalStateVariableReference<in In, out Out>(val engine: IIncrementalEngine, val decl: IStateVariableDeclaration<In, Out>) : IInternalStateVariableReference<In, Out> {
    override fun getGroup(): IStateVariableGroup? = null

    override fun read(): Out {
        return engine.readStateVariable(decl)
    }

    override fun toString(): String {
        return "state[$decl]"
    }
}