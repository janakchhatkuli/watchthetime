package com.watchthetime.domain.engine

import com.watchthetime.domain.event.GameEvent

/**
 * Conflict resolution for offline edits from two devices. Last revision wins per event id;
 * ties (both sides edited the same revision while disconnected) are broken by edit time and
 * then by origin so every device converges to the same result.
 */
object EventMerge {

    fun wins(candidate: GameEvent, current: GameEvent?): Boolean {
        if (current == null) return true
        if (candidate.rev != current.rev) return candidate.rev > current.rev
        if (candidate == current) return false
        val ce = candidate.editedAtWallMs ?: candidate.at.wallMs
        val cu = current.editedAtWallMs ?: current.at.wallMs
        if (ce != cu) return ce > cu
        return candidate.origin > current.origin
    }

    /** Returns the merged map and the list of events that changed locally. */
    fun merge(local: Map<String, GameEvent>, remote: Collection<GameEvent>): Pair<Map<String, GameEvent>, List<GameEvent>> {
        val out = local.toMutableMap()
        val changed = mutableListOf<GameEvent>()
        for (e in remote) {
            if (wins(e, out[e.id])) {
                out[e.id] = e
                changed += e
            }
        }
        return out to changed
    }
}

/** One undoable user action, possibly touching several events (e.g. stop + end period). */
data class Transaction(val label: String, val changes: List<Change>)

/** [before] null means the event was created by this transaction. */
data class Change(val before: GameEvent?, val after: GameEvent)

class UndoStack(private val limit: Int = 100) {
    private val undo = ArrayDeque<Transaction>()
    private val redo = ArrayDeque<Transaction>()

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val undoLabel: String? get() = undo.lastOrNull()?.label
    val redoLabel: String? get() = redo.lastOrNull()?.label

    fun push(t: Transaction) {
        if (t.changes.isEmpty()) return
        undo.addLast(t)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun popUndo(): Transaction? = undo.removeLastOrNull()?.also { redo.addLast(it) }
    fun popRedo(): Transaction? = redo.removeLastOrNull()?.also { undo.addLast(it) }

    fun clear() { undo.clear(); redo.clear() }
}
