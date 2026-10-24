package studio.buttonforge.editorcore.history

import studio.buttonforge.document.Document

/**
 * One undoable edit. Commands store only what they change (old and new values), never whole documents,
 * except layer operations, which keep references to immutable lists that share structure with the document.
 */
interface Command {
    val label: String

    /** Rough size, used for the history memory budget. */
    val cost: Int get() = 1

    fun apply(doc: Document): Document
    fun revert(doc: Document): Document

    /** Returns one command equivalent to this followed by [next], or null if they must stay separate. */
    fun mergeWith(next: Command): Command? = null
}
