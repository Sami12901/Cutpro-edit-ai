package com.cutpro.core.timeline.command

import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.timeline.TimelineOperations

/**
 * Command for adding a clip to a track.
 */
class AddClipCommand(
    val trackId: String,
    val clip: TimelineClip,
    override val description: String = "Add Clip"
) : TimelineCommand {
    override fun execute(current: ProjectManifest): ProjectManifest =
        TimelineOperations.addClipToProject(current, trackId, clip)

    override fun undo(current: ProjectManifest): ProjectManifest =
        TimelineOperations.removeClipFromProject(current, clip.id)
}

/**
 * Command for deleting a clip from a track.
 */
class DeleteClipCommand(
    val trackId: String,
    val clip: TimelineClip,
    override val description: String = "Delete Clip"
) : TimelineCommand {
    override fun execute(current: ProjectManifest): ProjectManifest =
        TimelineOperations.removeClipFromProject(current, clip.id)

    override fun undo(current: ProjectManifest): ProjectManifest =
        TimelineOperations.addClipToProject(current, trackId, clip)
}

/**
 * Command for trimming a clip in-point or out-point.
 */
class TrimClipCommand(
    val trackId: String,
    val oldClip: TimelineClip,
    val newClip: TimelineClip,
    override val description: String = "Trim Clip"
) : TimelineCommand {
    override fun execute(current: ProjectManifest): ProjectManifest =
        TimelineOperations.replaceClipInProject(current, trackId, newClip)

    override fun undo(current: ProjectManifest): ProjectManifest =
        TimelineOperations.replaceClipInProject(current, trackId, oldClip)
}

/**
 * Command for splitting a clip into two clips.
 */
class SplitClipCommand(
    val trackId: String,
    val originalClip: TimelineClip,
    val leftClip: TimelineClip,
    val rightClip: TimelineClip,
    override val description: String = "Split Clip"
) : TimelineCommand {
    override fun execute(current: ProjectManifest): ProjectManifest {
        val withoutOriginal = TimelineOperations.removeClipFromProject(current, originalClip.id)
        val withLeft = TimelineOperations.addClipToProject(withoutOriginal, trackId, leftClip)
        return TimelineOperations.addClipToProject(withLeft, trackId, rightClip)
    }

    override fun undo(current: ProjectManifest): ProjectManifest {
        val withoutRight = TimelineOperations.removeClipFromProject(current, rightClip.id)
        val withoutLeft = TimelineOperations.removeClipFromProject(withoutRight, leftClip.id)
        return TimelineOperations.addClipToProject(withoutLeft, trackId, originalClip)
    }
}

/**
 * Command for repositioning or moving a clip (potentially across tracks).
 */
class RepositionClipCommand(
    val oldTrackId: String,
    val newTrackId: String,
    val oldClip: TimelineClip,
    val newClip: TimelineClip,
    override val description: String = "Reposition Clip"
) : TimelineCommand {
    override fun execute(current: ProjectManifest): ProjectManifest {
        val removed = TimelineOperations.removeClipFromProject(current, oldClip.id)
        return TimelineOperations.addClipToProject(removed, newTrackId, newClip)
    }

    override fun undo(current: ProjectManifest): ProjectManifest {
        val removed = TimelineOperations.removeClipFromProject(current, newClip.id)
        return TimelineOperations.addClipToProject(removed, oldTrackId, oldClip)
    }
}

/**
 * Command for updating clip properties (speed, transform, audio properties, etc.).
 */
class UpdateClipCommand(
    val trackId: String,
    val oldClip: TimelineClip,
    val newClip: TimelineClip,
    override val description: String = "Update Clip Properties"
) : TimelineCommand {
    override fun execute(current: ProjectManifest): ProjectManifest =
        TimelineOperations.replaceClipInProject(current, trackId, newClip)

    override fun undo(current: ProjectManifest): ProjectManifest =
        TimelineOperations.replaceClipInProject(current, trackId, oldClip)
}

/**
 * Compound command aggregating multiple sub-commands into a single atomic undo/redo action.
 */
class CompoundTimelineCommand(
    override val description: String,
    val commands: List<TimelineCommand>
) : TimelineCommand {
    override fun execute(current: ProjectManifest): ProjectManifest {
        var state = current
        for (cmd in commands) {
            state = cmd.execute(state)
        }
        return state
    }

    override fun undo(current: ProjectManifest): ProjectManifest {
        var state = current
        for (cmd in commands.reversed()) {
            state = cmd.undo(state)
        }
        return state
    }
}

/**
 * Snapshot command capturing complete project manifests before and after an operation.
 */
class SnapshotTimelineCommand(
    override val description: String,
    val beforeState: ProjectManifest,
    val afterState: ProjectManifest
) : TimelineCommand {
    override fun execute(current: ProjectManifest): ProjectManifest = afterState
    override fun undo(current: ProjectManifest): ProjectManifest = beforeState
}
