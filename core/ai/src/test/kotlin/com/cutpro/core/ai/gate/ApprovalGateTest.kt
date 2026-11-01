package com.cutpro.core.ai.gate

import com.cutpro.core.ai.model.CutSegmentAction
import com.cutpro.core.ai.model.DeleteSilenceAction
import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.ai.model.PushInZoomAction
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.timeline.undo.UndoRedoManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ApprovalGateTest {

    private val dummySource = MediaSourceInfo(
        uriString = "content://media/external/video/test",
        fileName = "test.mp4",
        mimeType = "video/mp4",
        durationUs = 60_000_000L,
        width = 1920,
        height = 1080
    )

    private fun createInitialProject(): ProjectManifest {
        val clip = TimelineClip(
            id = "clip-1",
            trackId = "track-1",
            mediaSource = dummySource,
            timelineStartUs = 0L,
            timelineDurationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L
        )
        val track = TimelineTrack(
            id = "track-1",
            name = "Video Track",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
        return ProjectManifest(
            title = "Test Project",
            tracks = listOf(track),
            durationUs = 10_000_000L
        )
    }

    @Test
    fun calculateDiff_fullPlan() {
        val project = createInitialProject()
        var currentProject = project
        val gate = StandardApprovalGate(
            getCurrentTimeline = { currentProject },
            updateTimeline = { currentProject = it }
        )

        val plan = EditPlan(
            title = "Cut Plan",
            description = "Cut 2 seconds of dead air",
            actions = listOf(
                DeleteSilenceAction(
                    id = "action-1",
                    description = "Cut dead air from 2s to 4s",
                    trackId = "track-1",
                    clipId = "clip-1",
                    startTimeUs = 2_000_000L,
                    endTimeUs = 4_000_000L,
                    ripple = true
                )
            )
        )

        val diff = gate.calculateDiff(plan, project)

        assertThat(diff.beforeDurationUs).isEqualTo(10_000_000L)
        assertThat(diff.afterDurationUs).isEqualTo(8_000_000L)
        assertThat(diff.durationDeltaUs).isEqualTo(-2_000_000L)
        assertThat(diff.totalTimeSavingsUs).isEqualTo(2_000_000L)
        assertThat(diff.approvedActionCount).isEqualTo(1)
        assertThat(diff.rejectedActionCount).isEqualTo(0)
    }

    @Test
    fun calculateSelectiveDiff_filtersRejectedActions() {
        val project = createInitialProject()
        var currentProject = project
        val gate = StandardApprovalGate(
            getCurrentTimeline = { currentProject },
            updateTimeline = { currentProject = it }
        )

        val plan = EditPlan(
            title = "Multi-Cut Plan",
            description = "Two cuts",
            actions = listOf(
                DeleteSilenceAction(
                    id = "action-1",
                    description = "Cut dead air 2s to 4s",
                    trackId = "track-1",
                    startTimeUs = 2_000_000L,
                    endTimeUs = 4_000_000L
                ),
                DeleteSilenceAction(
                    id = "action-2",
                    description = "Cut dead air 6s to 8s",
                    trackId = "track-1",
                    startTimeUs = 6_000_000L,
                    endTimeUs = 8_000_000L
                )
            )
        )

        // Only approve action-1
        val diff = gate.calculateSelectiveDiff(plan, project, setOf("action-1"))

        assertThat(diff.approvedActionCount).isEqualTo(1)
        assertThat(diff.rejectedActionCount).isEqualTo(1)
        assertThat(diff.durationDeltaUs).isEqualTo(-2_000_000L)
    }

    @Test
    fun applyPlan_reverseChronologicalExecution_preservesNonOverlapping() = runTest {
        val project = createInitialProject()
        var currentProject = project
        val undoRedoManager = UndoRedoManager()
        val gate = StandardApprovalGate(
            getCurrentTimeline = { currentProject },
            updateTimeline = { currentProject = it },
            undoRedoManager = undoRedoManager
        )

        val plan = EditPlan(
            title = "Two Silence Cuts",
            description = "Back-to-front execution",
            actions = listOf(
                DeleteSilenceAction(
                    id = "act-1",
                    description = "Cut 2s-3s",
                    trackId = "track-1",
                    startTimeUs = 2_000_000L,
                    endTimeUs = 3_000_000L
                ),
                DeleteSilenceAction(
                    id = "act-2",
                    description = "Cut 6s-7s",
                    trackId = "track-1",
                    startTimeUs = 6_000_000L,
                    endTimeUs = 7_000_000L
                )
            )
        )

        val result = gate.applyPlan(plan, setOf("act-1", "act-2"))
        assertThat(result.isSuccess).isTrue()

        // 10s - 1s - 1s = 8s
        assertThat(currentProject.durationUs).isEqualTo(8_000_000L)

        val track = currentProject.findTrackById("track-1")!!
        assertThat(track.validateNonOverlapping()).isTrue()
        assertThat(track.clips).hasSize(3) // Split into 3 segments: [0..2s], [2s..5s], [5s..8s]
    }

    @Test
    fun applyPlan_atomicCompoundUndoRedo() = runTest {
        val project = createInitialProject()
        var currentProject = project
        val undoRedoManager = UndoRedoManager()
        val gate = StandardApprovalGate(
            getCurrentTimeline = { currentProject },
            updateTimeline = { currentProject = it },
            undoRedoManager = undoRedoManager
        )

        val plan = EditPlan(
            title = "Compound Plan",
            description = "Multi-step plan",
            actions = listOf(
                DeleteSilenceAction(
                    id = "act-1",
                    description = "Cut 2s-3s",
                    trackId = "track-1",
                    startTimeUs = 2_000_000L,
                    endTimeUs = 3_000_000L
                ),
                PushInZoomAction(
                    id = "act-2",
                    description = "Punch in at 5s",
                    trackId = "track-1",
                    clipId = "clip-1",
                    startTimeUs = 0L,
                    endTimeUs = 9_000_000L,
                    splitPointUs = 5_000_000L,
                    zoomScale = 1.15f
                )
            )
        )

        gate.applyPlan(plan, setOf("act-1", "act-2"))
        assertThat(currentProject.durationUs).isLessThan(10_000_000L)

        // 1-Tap Undo must restore exact original project state
        assertThat(undoRedoManager.canUndo).isTrue()
        val undoneProject = undoRedoManager.undo(currentProject)
        currentProject = undoneProject!!

        assertThat(currentProject.durationUs).isEqualTo(10_000_000L)
        assertThat(currentProject.findTrackById("track-1")!!.clips).hasSize(1)

        // 1-Tap Redo restores the plan
        assertThat(undoRedoManager.canRedo).isTrue()
        val redoneProject = undoRedoManager.redo(currentProject)
        currentProject = redoneProject!!

        assertThat(currentProject.durationUs).isLessThan(10_000_000L)
    }

    @Test
    fun rollbackPlan_restoresOriginalState() = runTest {
        val project = createInitialProject()
        var currentProject = project
        val gate = StandardApprovalGate(
            getCurrentTimeline = { currentProject },
            updateTimeline = { currentProject = it }
        )

        val plan = EditPlan(
            title = "Rollback Plan",
            description = "Test rollback",
            actions = listOf(
                CutSegmentAction(
                    id = "cut-1",
                    description = "Cut 4s-6s",
                    trackId = "track-1",
                    startTimeUs = 4_000_000L,
                    endTimeUs = 6_000_000L
                )
            )
        )

        gate.applyPlan(plan, setOf("cut-1"))
        assertThat(gate.isPlanApplied(plan.id)).isTrue()
        assertThat(currentProject.durationUs).isEqualTo(8_000_000L)

        val rollbackResult = gate.rollbackPlan(plan.id)
        assertThat(rollbackResult.isSuccess).isTrue()
        assertThat(gate.isPlanApplied(plan.id)).isFalse()
        assertThat(currentProject.durationUs).isEqualTo(10_000_000L)
    }
}
