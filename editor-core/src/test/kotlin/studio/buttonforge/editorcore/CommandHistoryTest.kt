package studio.buttonforge.editorcore

import studio.buttonforge.document.Document
import studio.buttonforge.editorcore.history.Command
import studio.buttonforge.editorcore.history.CommandHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandHistoryTest {
    private class Fake(
        override val cost: Int = 1,
        val key: String? = null,
        override val label: String = "fake",
    ) : Command {
        override fun apply(doc: Document) = doc
        override fun revert(doc: Document) = doc
        override fun mergeWith(next: Command): Command? =
            if (key != null && next is Fake && next.key == key) Fake(cost, key, "merged") else null
    }

    @Test
    fun undoAndRedoMoveEntriesBetweenStacks() {
        val h = CommandHistory()
        h.record(Fake())
        h.record(Fake())
        assertEquals(2, h.undoSize)
        assertNotNull(h.popUndo())
        assertEquals(1, h.undoSize)
        assertEquals(1, h.redoSize)
        assertNotNull(h.popRedo())
        assertEquals(2, h.undoSize)
        assertNull(h.popRedo())
    }

    @Test
    fun newCommandClearsRedo() {
        val h = CommandHistory()
        h.record(Fake())
        h.popUndo()
        h.record(Fake())
        assertEquals(0, h.redoSize)
    }

    @Test
    fun entryCountIsBounded() {
        val h = CommandHistory(maxEntries = 5)
        repeat(100) { h.record(Fake()) }
        assertEquals(5, h.undoSize)
    }

    @Test
    fun totalCostIsBounded() {
        val h = CommandHistory(maxEntries = 1000, maxCost = 100)
        repeat(1000) { h.record(Fake(cost = 30)) }
        assertTrue(h.totalCost <= 100, "cost=${h.totalCost}")
        assertTrue(h.undoSize in 1..3)
    }

    @Test
    fun aSingleHugeCommandIsKept() {
        val h = CommandHistory(maxCost = 10)
        h.record(Fake(cost = 1000))
        assertEquals(1, h.undoSize)
    }

    @Test
    fun mergeableCommandsCollapseIntoOneEntry() {
        val h = CommandHistory()
        repeat(50) { h.record(Fake(key = "slider")) }
        assertEquals(1, h.undoSize)
        h.record(Fake(key = "other"))
        assertEquals(2, h.undoSize)
    }
}
