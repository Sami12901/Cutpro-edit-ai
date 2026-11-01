package com.cutpro.core.timeline

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.timeline.command.AddClipCommand
import com.cutpro.core.timeline.command.CompoundTimelineCommand
import com.cutpro.core.timeline.command.DeleteClipCommand
import com.cutpro.core.timeline.undo.UndoRedoManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class UndoRedoManagerTest {

    private lateinit var manager: UndoRedoManager
    private lateinit var baseProject: ProjectManifest
    private lateinit var sampleClip: TimelineClip

    @Before
    fun setUp() {
        manager = UndoRedoManager(maxHistoryLimit = 50)
        val track = TimelineTrack(
            id = "track_v1",
            name = "Video 1",
            type = TrackType.VIDEO,
            zIndex = 0
        )
        baseProject = ProjectManifest(
            id = "proj_1",
            title = "Test Project",
            aspectRatio = CanvasAspectRatio.RATIO_16_9,
            defaultResolution = ExportResolution.RES_1080P,
            defaultFrameRate = ExportFrameRate.FPS_30,
            durationUs = 0L,
            tracks = listOf(track)
        )
        sampleClip = TimelineClip(
            id = "clip_1",
            trackId = "track_v1",
            mediaSource = MediaSourceInfo(
                uriString = "content://media/1",
                fileName = "test.mp4",
                mimeType = "video/mp4",
                durationUs = 5_000_000L
            ),
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L
        )
    }

    @Test
    fun undo_emptyStack_isSafeAndReturnsNull() {
        assertThat(manager.canUndo).isFalse()
        assertThat(manager.canRedo).isFalse()
        val result = manager.undo(baseProject)
        assertThat(result).isNull()
    }

    @Test
    fun redo_emptyStack_isSafeAndReturnsNull() {
        assertThat(manager.canRedo).isFalse()
        val result = manager.redo(baseProject)
        assertThat(result).isNull()
    }

    @Test
    fun execute_and_undo_revertsState() {
        val addCmd = AddClipCommand("track_v1", sampleClip)
        val addedState = manager.execute(addCmd, baseProject)

        assertThat(addedState.tracks.first().clips).hasSize(1)
        assertThat(addedState.durationUs).isEqualTo(5_000_000L)
        assertThat(manager.canUndo).isTrue()
        assertThat(manager.canRedo).isFalse()

        val revertedState = manager.undo(addedState)
        assertThat(revertedState).isNotNull()
        assertThat(revertedState!!.tracks.first().clips).isEmpty()
        assertThat(revertedState.durationUs).isEqualTo(0L)
        assertThat(manager.canUndo).isFalse()
        assertThat(manager.canRedo).isTrue()
    }

    @Test
    fun redo_restoresState() {
        val addCmd = AddClipCommand("track_v1", sampleClip)
        val addedState = manager.execute(addCmd, baseProject)
        val revertedState = manager.undo(addedState)!!

        val redoneState = manager.redo(revertedState)
        assertThat(redoneState).isNotNull()
        assertThat(redoneState!!.tracks.first().clips).hasSize(1)
        assertThat(redoneState.durationUs).isEqualTo(5_000_000L)
        assertThat(manager.canUndo).isTrue()
        assertThat(manager.canRedo).isFalse()
    }

    @Test
    fun cyclicUndoRedo_preservesFidelity() {
        val addCmd = AddClipCommand("track_v1", sampleClip)
        var state = manager.execute(addCmd, baseProject)

        for (i in 0 until 5) {
            state = manager.undo(state)!!
            assertThat(state.tracks.first().clips).isEmpty()
            state = manager.redo(state)!!
            assertThat(state.tracks.first().clips).hasSize(1)
        }
    }

    @Test
    fun newAction_clearsRedoStack() {
        val addCmd = AddClipCommand("track_v1", sampleClip)
        val state1 = manager.execute(addCmd, baseProject)
        val reverted = manager.undo(state1)!!
        assertThat(manager.canRedo).isTrue()

        // Perform a new action
        val deleteCmd = DeleteClipCommand("track_v1", sampleClip)
        manager.execute(deleteCmd, reverted)

        assertThat(manager.canRedo).isFalse()
        assertThat(manager.redoStackSize).isEqualTo(0)
    }

    @Test
    fun maxHistoryLimit_enforced() {
        val smallManager = UndoRedoManager(maxHistoryLimit = 5)
        var state = baseProject
        for (i in 1..10) {
            val clip = sampleClip.copy(
                id = "clip_$i",
                timelineStartUs = i * 1_000_000L
            )
            state = smallManager.execute(AddClipCommand("track_v1", clip), state)
        }

        assertThat(smallManager.undoStackSize).isEqualTo(5)
    }

    @Test
    fun compoundAction_atomicUndo() {
        val clip2 = sampleClip.copy(id = "clip_2", timelineStartUs = 5_000_000L)
        val compound = CompoundTimelineCommand(
            description = "Add Two Clips",
            commands = listOf(
                AddClipCommand("track_v1", sampleClip),
                AddClipCommand("track_v1", clip2)
            )
        )

        val applied = manager.execute(compound, baseProject)
        assertThat(applied.tracks.first().clips).hasSize(2)

        val reverted = manager.undo(applied)!!
        assertThat(reverted.tracks.first().clips).isEmpty()

        val redone = manager.redo(reverted)!!
        assertThat(redone.tracks.first().clips).hasSize(2)
    }
}
