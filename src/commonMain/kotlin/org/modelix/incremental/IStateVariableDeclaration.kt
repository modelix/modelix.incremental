package org.modelix.incremental

/**
 * An IStateVariableDeclaration is an identifier for a state variable inside an IIncrementalEngine.
 * Multiple engine instances can have their own instance of a variable for the same IStateVariableDeclaration.
 * The combination of the IStateVariableDeclaration and the IIncrementalEngine is an IStateVariableReference,
 * (in this case InternalStateVariableReference). IStateVariableReference is unique identifier across the whole system
 * including variables outside any engine.
 * The state variable can have parameters that are accessible from computations.
 */
interface IStateVariableDeclaration<V>
data class StateVariableDeclaration0<V>(val name: String) : IStateVariableDeclaration<V>
data class StateVariableDeclaration1<V, P1>(val name: String, val p1: P1) : IStateVariableDeclaration<V>
data class StateVariableDeclaration2<V, P1, P2>(val name: String, val p1: P1, val p2: P2) : IStateVariableDeclaration<V>
data class StateVariableDeclaration3<V, P1, P2, P3>(val name: String, val p1: P1, val p2: P2, val p3: P3) : IStateVariableDeclaration<V>
data class StateVariableDeclaration4<V, P1, P2, P3, P4>(val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4) : IStateVariableDeclaration<V>
data class StateVariableDeclaration5<V, P1, P2, P3, P4, P5>(val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4, val p5: P5) : IStateVariableDeclaration<V>
data class StateVariableDeclaration6<V, P1, P2, P3, P4, P5, P6>(val name: String, val p1: P1, val p2: P2, val p3: P3, val p4: P4, val p5: P5, val p6: P6) : IStateVariableDeclaration<V>

interface IInternalStateVariableReference<E> : IStateVariableReference<E>

data class InternalStateVariableReference<E>(val engine: IIncrementalEngine, val decl: IStateVariableDeclaration<E>) : IInternalStateVariableReference<E> {
    override fun getGroup(): IStateVariableGroup? = null

    override fun read(): E {
        return engine.readStateVariable(decl)
    }
}