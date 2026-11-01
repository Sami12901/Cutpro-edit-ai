package com.cutpro.core.timeline.undo

import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.timeline.command.TimelineCommand

/**
 * Manages atomic Undo and Redo operations using the Command pattern.
 * Enforces maximum history capacity (default: 50 steps), clears redo on new commands,
 * and maintains cyclic undo/redo fidelity.
 */
class UndoRedoManager(
    val maxHistoryLimit: Int = 50
) {
    private val undoStack = ArrayDeque<TimelineCommand>()
    private val redoStack = ArrayDeque<TimelineCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoStackSize: Int get() = undoStack.size
    val redoStackSize: Int get() = redoStack.size

    /**
     * Executes a new command, applies it to the current project manifest,
     * pushes it to the undo stack, and clears the redo stack.
     */
    fun execute(command: TimelineCommand, current: ProjectManifest): ProjectManifest {
        val nextState = command.execute(current)
        undoStack.addLast(command)
        if (undoStack.size > maxHistoryLimit) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        return nextState
    }

    /**
     * Pushes an already-executed command onto the undo stack and clears redo.
     */
    fun push(command: TimelineCommand) {
        undoStack.addLast(command)
        if (undoStack.size > maxHistoryLimit) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    /**
     * Undoes the last command.
     * @param current The current project manifest.
     * @return The updated project manifest after undo, or null if cannot undo.
     */
    fun undo(current: ProjectManifest): ProjectManifest? {
        if (!canUndo) return null
        val command = undoStack.removeLast()
        val revertedState = command.undo(current)
        redoStack.addLast(command)
        return revertedState
    }

    /**
     * Redoes the previously undone command.
     * @param current The current project manifest.
     * @return The updated project manifest after redo, or null if cannot redo.
     */
    fun redo(current: ProjectManifest): ProjectManifest? {
        if (!canRedo) return null
        val command = redoStack.removeLast()
        val redoneState = command.execute(current)
        undoStack.addLast(command)
        return redoneState
    }

    /**
     * Clears all undo and redo history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
