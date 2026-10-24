package studio.buttonforge.editorcore.history

/** Undo/redo stacks bounded by entry count and by total command cost. Oldest entries are dropped first. */
class CommandHistory(
    private val maxEntries: Int = 200,
    private val maxCost: Long = 200_000L,
) {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()
    private var cost = 0L

    val undoSize: Int get() = undoStack.size
    val redoSize: Int get() = redoStack.size
    val totalCost: Long get() = cost

    /** Records a command that has already been applied. Clears redo. May merge with the previous command. */
    fun record(command: Command) {
        redoStack.clear()
        val top = undoStack.lastOrNull()
        val merged = top?.mergeWith(command)
        if (top != null && merged != null) {
            undoStack.removeLast()
            cost -= top.cost
            undoStack.addLast(merged)
            cost += merged.cost
        } else {
            undoStack.addLast(command)
            cost += command.cost
        }
        trim()
    }

    fun popUndo(): Command? {
        val c = undoStack.removeLastOrNull() ?: return null
        cost -= c.cost
        redoStack.addLast(c)
        return c
    }

    fun popRedo(): Command? {
        val c = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(c)
        cost += c.cost
        return c
    }

    private fun trim() {
        while (undoStack.size > 1 && (undoStack.size > maxEntries || cost > maxCost)) {
            cost -= undoStack.removeFirst().cost
        }
    }
}
