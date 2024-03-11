package org.modelix.incremental

/**
 * A general requirement for values computed and cached by the incremental engine is that they are immutable.
 * This IncrementalList data structure is immutable and designed to be used as a return value of IncrementalFunctions.
 *
 * It's especially useful when recursively collecting items from a tree like input data structure.
 */
abstract class IncrementalList<E> {

    fun diff(oldList: IncrementalList<E>, visitor: IIncrementalListDiffVisitor<E>) = diff(0, oldList, visitor)

    abstract fun diff(globalIndex: Int, oldList: IncrementalList<E>, visitor: IIncrementalListDiffVisitor<E>)

    abstract fun getSize(): Int

    abstract fun collectElements(acc: MutableCollection<E>)

    abstract fun asSequence(): Sequence<E>

    fun toList(): List<E> {
        val acc = ArrayList<E>(getSize())
        collectElements(acc)
        return acc
    }

    companion object {
        fun <T> empty(): IncrementalList<T> = IncrementalListEmpty<T>()
        fun <T> of(vararg elements: T) = of(elements.toList())
        fun <T> of(elements: List<T>): IncrementalList<T> {
            return when (elements.size) {
                0 -> empty<T>()
                1 -> IncrementalListLeaf(elements[0])
                else -> IncrementalListSubtree<T>(elements.map { IncrementalListLeaf(it) }.toTypedArray())
            }
        }
        fun <T> concat(vararg lists: IncrementalList<T>): IncrementalList<T> = concat(lists.toList())
        fun <T> concat(lists: List<IncrementalList<T>>): IncrementalList<T> {
            return IncrementalListSubtree<T>(lists.toTypedArray())
        }
    }
}

interface IIncrementalListDiffVisitor<E> {
    fun rangeReplaced(index: Int, oldElements: List<E>, newElements: List<E>)
}

private class IncrementalListEmpty<E>() : IncrementalList<E>() {
    override fun getSize(): Int = 0
    override fun collectElements(acc: MutableCollection<E>) {}
    override fun asSequence(): Sequence<E> = emptySequence()
    override fun diff(globalIndex: Int, oldList: IncrementalList<E>, visitor: IIncrementalListDiffVisitor<E>) {
        if (oldList.getSize() > 0) {
            visitor.rangeReplaced(globalIndex, oldList.toList(), toList())
        }
    }
}

private class IncrementalListLeaf<E>(val element: E) : IncrementalList<E>() {
    override fun getSize(): Int = 1
    override fun collectElements(acc: MutableCollection<E>) {
        acc.add(element)
    }

    override fun asSequence(): Sequence<E> = sequenceOf(element)

    override fun diff(globalIndex: Int, oldList: IncrementalList<E>, visitor: IIncrementalListDiffVisitor<E>) {
        if (oldList === this) return
        visitor.rangeReplaced(globalIndex, oldList.toList(), toList())
    }
}

private class IncrementalListSubtree<E>(val children: Array<out IncrementalList<E>>) : IncrementalList<E>() {
    private val size = children.fold(0) { acc, child -> acc + child.getSize() }
    override fun getSize(): Int = size
    override fun collectElements(acc: MutableCollection<E>) {
        for (child in children) {
            child.collectElements(acc)
        }
    }

    override fun asSequence(): Sequence<E> = children.asSequence().flatMap { it.asSequence() }

    override fun diff(globalIndex: Int, oldList: IncrementalList<E>, visitor: IIncrementalListDiffVisitor<E>) {
        if (oldList === this) return
        if (oldList is IncrementalListSubtree) {
            val newIndexes = children.withIndex().associate { it.value to it.index }
            val oldIndexes = oldList.children.withIndex().associate { it.value to it.index }
            val allChildren = oldList.children.toMutableList()
            for (newChild in children.withIndex().reversed()) {
                val oldIndex = oldIndexes[newChild.value]
                if (oldIndex != null) continue
                val anchorIndex = children.take(newChild.index).indexOfLast { oldIndexes.contains(it) }
                val insertPosition = if (anchorIndex != -1) anchorIndex + 1 else newChild.index
                allChildren.add(insertPosition.coerceAtMost(allChildren.size), newChild.value)
            }

            val ops = allChildren.map {
                val oldIndex = oldIndexes[it]
                val newIndex = newIndexes[it]
                if (oldIndex == null) {
                    InsertOp(it)
                } else if (newIndex == null) {
                    RemoveOp(it)
                } else {
                    UnmodifiedOp(it)
                }
            }.toMutableList()

            val filteredOps = ArrayList<DiffOp<IncrementalList<E>>>(ops.size)
            for (op in ops) {
                val previousOp = filteredOps.lastOrNull()
                if (op is InsertOp && previousOp is RemoveOp) {
                    filteredOps[filteredOps.size - 1] = ModifiedOp(previousOp.element, op.element)
                } else if (op is RemoveOp && previousOp is InsertOp) {
                    filteredOps[filteredOps.size - 1] = ModifiedOp(op.element, previousOp.element)
                } else {
                    filteredOps += op
                }
            }

            for (op in filteredOps) {
                when (op) {
                    is UnmodifiedOp -> {}
                    is InsertOp -> {
                        visitor.rangeReplaced(-1, emptyList(), op.element.toList())
                    }
                    is RemoveOp -> {
                        visitor.rangeReplaced(-1, op.element.toList(), emptyList())
                    }
                    is ModifiedOp -> {
                        op.newElement.diff(-1, op.element, visitor)
                    }
                }
            }
        } else {
            visitor.rangeReplaced(globalIndex, oldList.toList(), toList())
        }


    }
}

private abstract class DiffOp<E>(val element: E)
private class InsertOp<E>(element: E) : DiffOp<E>(element)
private class RemoveOp<E>(element: E) : DiffOp<E>(element)
private class ModifiedOp<E>(element: E, val newElement: E) : DiffOp<E>(element)
private class UnmodifiedOp<E>(element: E) : DiffOp<E>(element)