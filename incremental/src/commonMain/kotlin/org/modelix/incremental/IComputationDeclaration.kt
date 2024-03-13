package org.modelix.incremental

interface IComputationDeclaration<E> : IStateVariableDeclaration<E, E> {
    fun invoke(context: IIncrementalFunctionContext<E>): E
}
