package org.modelix.incremental

/**
 * An IStateVariableDeclaration is an identifier for a state variable inside an IIncrementalEngine.
 * Multiple engine instances can have their own instance of a variable for the same IStateVariableDeclaration.
 * The combination of the IStateVariableDeclaration and the IIncrementalEngine is an IStateVariableReference,
 * (in this case InternalStateVariableReference). IStateVariableReference is unique identifier across the whole system
 * including variables outside any engine.
 * The state variable can have parameters that are accessible from computations.
 */
interface IStateVariableDeclaration<in In, out Out> {
    fun reduce(inputValues: Iterable<In>): Out
}
abstract class StateVariableDeclaration<In, Out>(open val name: String, open val reduceFunction: (Iterable<In>) -> Out) : IStateVariableDeclaration<In, Out> {
    override fun reduce(inputValues: Iterable<In>): Out = reduceFunction(inputValues)
}
data class StateVariableDeclaration0<In, Out>(override val name: String, override val reduceFunction: (Iterable<In>)->Out) : StateVariableDeclaration<In, Out>(name, reduceFunction)
data class StateVariableDeclaration1<In, Out, P1>(override val name: String, val p1: P1, override val reduceFunction: (Iterable<In>)->Out) : StateVariableDeclaration<In, Out>(name, reduceFunction)
data class StateVariableDeclaration2<In, Out, P1, P2>(override val name: String, val p1: P1, val p2: P2, override val reduceFunction: (Iterable<In>)->Out) : StateVariableDeclaration<In, Out>(name, reduceFunction)
data class StateVariableDeclaration3<In, Out, P1, P2, P3>(override val name: String, val p1: P1, val p2: P2, val p3: P3, override val reduceFunction: (Iterable<In>)->Out) : StateVariableDeclaration<In, Out>(name, reduceFunction)
data class StateVariableDeclaration4<In, Out, P1, P2, P3, P4>(override val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4, override val reduceFunction: (Iterable<In>)->Out) : StateVariableDeclaration<In, Out>(name, reduceFunction)
data class StateVariableDeclaration5<In, Out, P1, P2, P3, P4, P5>(override val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4, val p5: P5, override val reduceFunction: (Iterable<In>)->Out) : StateVariableDeclaration<In, Out>(name, reduceFunction)
data class StateVariableDeclaration6<In, Out, P1, P2, P3, P4, P5, P6>(override val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4, val p5: P5, val p6: P6, override val reduceFunction: (Iterable<In>)->Out) : StateVariableDeclaration<In, Out>(name, reduceFunction)

interface IInternalStateVariableReference<in In, out Out> : IStateVariableReference<Out>

data class InternalStateVariableReference<in In, out Out>(val engine: IIncrementalEngine, val decl: IStateVariableDeclaration<In, Out>) : IInternalStateVariableReference<In, Out> {
    override fun getGroup(): IStateVariableGroup? = null

    override fun read(): Out {
        return engine.readStateVariable(decl)
    }
}