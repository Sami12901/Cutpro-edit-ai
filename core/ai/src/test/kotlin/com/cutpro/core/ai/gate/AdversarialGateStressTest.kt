package com.cutpro.core.ai.gate

import com.cutpro.core.ai.model.AdjustSpeedAction
import com.cutpro.core.ai.model.CutSegmentAction
import com.cutpro.core.ai.model.DeleteSilenceAction
import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.ai.model.MuteNoiseAction
import com.cutpro.core.ai.model.PushInZoomAction
import com.cutpro.core.ai.model.TrimClipAction
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.timeline.undo.UndoRedoManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

/**
 * Adversarial stress testing for PlanExecutionEngine and ApprovalGate.
 * Tests complex multi-cut ripple timestamp integrity, out-of-order partial approvals,
 * compound atomic 1-tap undo/redo, programmatic rollback, and extreme boundaries.
 */
class AdversarialGateStressTest {

    private val testSource = MediaSourceInfo(
        uriString = "content://media/external/video/stress_test",
        fileName = "stress_test.mp4",
        mimeType = "video/mp4",
        durationUs = 120_000_000L,
        width = 1920,
        height = 1080
    )

    private fun createMultiTrackProject(): ProjectManifest {
        // Track 1 (Video): 3 clips with gaps
        // Clip A: [0s .. 10s]
        // Gap: [10s .. 12s] (2s)
        // Clip B: [12s .. 22s]
        // Gap: [22s .. 25s] (3s)
        // Clip C: [25s .. 40s]
        val clipA = TimelineClip(
            id = "clip-v-A",
            trackId = "track-video-1",
            mediaSource = testSource,
            timelineStartUs = 0L,
            timelineDurationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L
        )
        val clipB = TimelineClip(
            id = "clip-v-B",
            trackId = "track-video-1",
            mediaSource = testSource,
            timelineStartUs = 12_000_000L,
            timelineDurationUs = 10_000_000L,
            sourceInUs = 10_000_000L,
            sourceOutUs = 20_000_000L
        )
        val clipC = TimelineClip(
            id = "clip-v-C",
            trackId = "track-video-1",
            mediaSource = testSource,
            timelineStartUs = 25_000_000L,
            timelineDurationUs = 15_000_000L,
            sourceInUs = 20_000_000L,
            sourceOutUs = 35_000_000L
        )
        val videoTrack = TimelineTrack(
            id = "track-video-1",
            name = "Main Video",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clipA, clipB, clipC)
        )

        // Track 2 (Audio): 1 continuous clip [0s .. 45s]
        val clipAudio = TimelineClip(
            id = "clip-a-1",
            trackId = "track-audio-1",
            mediaSource = testSource,
            timelineStartUs = 0L,
            timelineDurationUs = 45_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 45_000_000L
        )
        val audioTrack = TimelineTrack(
            id = "track-audio-1",
            name = "Background Audio",
            type = TrackType.AUDIO,
            zIndex = 1,
            clips = listOf(clipAudio)
        )

        return ProjectManifest(
            title = "Adversarial Stress Project",
            tracks = listOf(videoTrack, audioTrack),
            durationUs = 45_000_000L
        )
    }

    @Test
    fun multiCutRipple_complexInterleavedCuts_preservesCoordinatesAndZeroOverlaps() {
        val initialProject = createMultiTrackProject()

        // 6 cuts across both tracks with interleaving timestamps:
        // Cut 1 on Clip C: [30s .. 34s] (4s duration)
        // Cut 2 on Clip C: [26s .. 28s] (2s duration)
        // Cut 3 on Clip B: [15s .. 18s] (3s duration)
        // Cut 4 on Clip A: [3s .. 6s] (3s duration)
        // Cut 5 on Track 2: [10s .. 15s] (5s duration)
        // Cut 6 on Track 2: [35s .. 38s] (3s duration)
        val actions = listOf(
            CutSegmentAction(
                id = "cut-1",
                description = "Cut in Clip C (30s-34s)",
                trackId = "track-video-1",
                clipId = "clip-v-C",
                startTimeUs = 30_000_000L,
                endTimeUs = 34_000_000L,
                ripple = true
            ),
            DeleteSilenceAction(
                id = "cut-2",
                description = "Silence in Clip C (26s-28s)",
                trackId = "track-video-1",
                clipId = "clip-v-C",
                startTimeUs = 26_000_000L,
                endTimeUs = 28_000_000L,
                ripple = true
            ),
            CutSegmentAction(
                id = "cut-3",
                description = "Cut in Clip B (15s-18s)",
                trackId = "track-video-1",
                clipId = "clip-v-B",
                startTimeUs = 15_000_000L,
                endTimeUs = 18_000_000L,
                ripple = true
            ),
            DeleteSilenceAction(
                id = "cut-4",
                description = "Silence in Clip A (3s-6s)",
                trackId = "track-video-1",
                clipId = "clip-v-A",
                startTimeUs = 3_000_000L,
                endTimeUs = 6_000_000L,
                ripple = true
            ),
            CutSegmentAction(
                id = "cut-5",
                description = "Audio cut (10s-15s)",
                trackId = "track-audio-1",
                clipId = "clip-a-1",
                startTimeUs = 10_000_000L,
                endTimeUs = 15_000_000L,
                ripple = true
            ),
            DeleteSilenceAction(
                id = "cut-6",
                description = "Audio silence (35s-38s)",
                trackId = "track-audio-1",
                clipId = "clip-a-1",
                startTimeUs = 35_000_000L,
                endTimeUs = 38_000_000L,
                ripple = true
            )
        )

        val resultProject = PlanExecutionEngine.executeActions(initialProject, actions)

        // Verify Video Track
        val videoTrack = resultProject.findTrackById("track-video-1")!!
        assertThat(videoTrack.validateNonOverlapping()).isTrue()

        // Clip A [0s..10s] was cut at [3s..6s] (3s):
        // Piece 1: [0s..3s] (duration 3s)
        // Piece 2: [3s..7s] (duration 4s)
        // Total Clip A duration = 7s
        assertThat(videoTrack.clips[0].timelineStartUs).isEqualTo(0L)
        assertThat(videoTrack.clips[0].timelineDurationUs).isEqualTo(3_000_000L)
        assertThat(videoTrack.clips[1].timelineStartUs).isEqualTo(3_000_000L)
        assertThat(videoTrack.clips[1].timelineDurationUs).isEqualTo(4_000_000L)

        // Gap between Clip A and Clip B was 2s in original.
        // Piece 2 ends at 7s, so Clip B must start at 7s + 2s = 9s.
        assertThat(videoTrack.clips[2].timelineStartUs).isEqualTo(9_000_000L)

        // Clip B [12s..22s] was cut at [15s..18s] (3s):
        // Piece 1: [9s..12s] (duration 3s)
        // Piece 2: [12s..16s] (duration 4s)
        // Total Clip B duration = 7s
        assertThat(videoTrack.clips[2].timelineDurationUs).isEqualTo(3_000_000L)
        assertThat(videoTrack.clips[3].timelineStartUs).isEqualTo(12_000_000L)
        assertThat(videoTrack.clips[3].timelineDurationUs).isEqualTo(4_000_000L)

        // Gap between Clip B and Clip C was 3s in original.
        // Piece 2 ends at 16s, so Clip C must start at 16s + 3s = 19s.
        assertThat(videoTrack.clips[4].timelineStartUs).isEqualTo(19_000_000L)

        // Clip C [25s..40s] had cuts at [26s..28s] (2s) and [30s..34s] (4s):
        // Piece 1: [19s..20s] (duration 1s)
        // Piece 2: [20s..22s] (duration 2s)
        // Piece 3: [22s..28s] (duration 6s)
        // Total Clip C duration = 9s
        assertThat(videoTrack.clips[4].timelineDurationUs).isEqualTo(1_000_000L)
        assertThat(videoTrack.clips[5].timelineStartUs).isEqualTo(20_000_000L)
        assertThat(videoTrack.clips[5].timelineDurationUs).isEqualTo(2_000_000L)
        assertThat(videoTrack.clips[6].timelineStartUs).isEqualTo(22_000_000L)
        assertThat(videoTrack.clips[6].timelineDurationUs).isEqualTo(6_000_000L)

        // Total video duration: 28s (original 40s - 3s - 3s - 2s - 4s = 28s)
        assertThat(videoTrack.durationUs).isEqualTo(28_000_000L)

        // Verify Audio Track
        val audioTrack = resultProject.findTrackById("track-audio-1")!!
        assertThat(audioTrack.validateNonOverlapping()).isTrue()
        // Audio had cuts [10s..15s] (5s) and [35s..38s] (3s) -> 3 pieces
        assertThat(audioTrack.clips).hasSize(3)
        // Piece 1: [0s..10s]
        assertThat(audioTrack.clips[0].timelineStartUs).isEqualTo(0L)
        assertThat(audioTrack.clips[0].timelineDurationUs).isEqualTo(10_000_000L)
        // Piece 2: [10s..30s] (was 15s..35s, shifted by 5s)
        assertThat(audioTrack.clips[1].timelineStartUs).isEqualTo(10_000_000L)
        assertThat(audioTrack.clips[1].timelineDurationUs).isEqualTo(20_000_000L)
        // Piece 3: [30s..37s] (was 38s..45s, shifted by 5s + 3s = 8s)
        assertThat(audioTrack.clips[2].timelineStartUs).isEqualTo(30_000_000L)
        assertThat(audioTrack.clips[2].timelineDurationUs).isEqualTo(7_000_000L)
        // Total audio duration: 37s (original 45s - 5s - 3s = 37s)
        assertThat(audioTrack.durationUs).isEqualTo(37_000_000L)

        // Overall Project duration: max(28s, 37s) = 37s
        assertThat(resultProject.durationUs).isEqualTo(37_000_000L)
    }

    @Test
    fun selectivePartialApprovals_outOfOrderExecution_preservesIntegrity() = runTest {
        val initialProject = createMultiTrackProject()
        var currentProject = initialProject
        val gate = StandardApprovalGate(
            getCurrentTimeline = { currentProject },
            updateTimeline = { currentProject = it }
        )

        // Plan with 5 cuts on Clip A [0s .. 10s]:
        // Action 1: [1s .. 2s] (1s)
        // Action 2: [3s .. 4s] (1s)
        // Action 3: [5s .. 6s] (1s)
        // Action 4: [7s .. 8s] (1s)
        // Action 5: [8.5s .. 9.5s] (1s)
        val plan = EditPlan(
            title = "Granular Cuts Plan",
            description = "5 cuts across Clip A",
            actions = listOf(
                DeleteSilenceAction(id = "act-1", description = "1s-2s", trackId = "track-video-1", startTimeUs = 1_000_000L, endTimeUs = 2_000_000L),
                DeleteSilenceAction(id = "act-2", description = "3s-4s", trackId = "track-video-1", startTimeUs = 3_000_000L, endTimeUs = 4_000_000L),
                DeleteSilenceAction(id = "act-3", description = "5s-6s", trackId = "track-video-1", startTimeUs = 5_000_000L, endTimeUs = 6_000_000L),
                DeleteSilenceAction(id = "act-4", description = "7s-8s", trackId = "track-video-1", startTimeUs = 7_000_000L, endTimeUs = 8_000_000L),
                DeleteSilenceAction(id = "act-5", description = "8.5s-9.5s", trackId = "track-video-1", startTimeUs = 8_500_000L, endTimeUs = 9_500_000L)
            )
        )

        // Selective approval out-of-order: approve Action 4 and Action 2 (reject 1, 3, 5)
        val approvedSet = setOf("act-4", "act-2")
        val diff = gate.calculateSelectiveDiff(plan, currentProject, approvedSet)
        assertThat(diff.approvedActionCount).isEqualTo(2)
        assertThat(diff.rejectedActionCount).isEqualTo(3)

        val result = gate.applyPlan(plan, approvedSet)
        assertThat(result.isSuccess).isTrue()

        val videoTrack = currentProject.findTrackById("track-video-1")!!
        assertThat(videoTrack.validateNonOverlapping()).isTrue()

        // Clip A originally [0s..10s].
        // Cut 2 [3s..4s] and Cut 4 [7s..8s] were removed.
        // Piece 1: [0s..3s] (3s)
        // Piece 2: [3s..6s] (was 4s..7s, shifted by 1s) (3s)
        // Piece 3: [6s..8s] (was 8s..10s, shifted by 2s) (2s)
        // Total Clip A = 8s
        assertThat(videoTrack.clips[0].timelineStartUs).isEqualTo(0L)
        assertThat(videoTrack.clips[0].timelineDurationUs).isEqualTo(3_000_000L)
        assertThat(videoTrack.clips[1].timelineStartUs).isEqualTo(3_000_000L)
        assertThat(videoTrack.clips[1].timelineDurationUs).isEqualTo(3_000_000L)
        assertThat(videoTrack.clips[2].timelineStartUs).isEqualTo(6_000_000L)
        assertThat(videoTrack.clips[2].timelineDurationUs).isEqualTo(2_000_000L)

        // Clip B was at 12s. Since Clip A lost 2s, Clip B shifted from 12s to 10s!
        // Gap of 2s between Clip A (ends at 8s) and Clip B (starts at 10s) preserved.
        assertThat(videoTrack.clips[3].timelineStartUs).isEqualTo(10_000_000L)
    }

    @Test
    fun compoundAtomicUndoRedo_singleTapRestoresExactPrePlanState() = runTest {
        val initialProject = createMultiTrackProject()
        var currentProject = initialProject
        val undoRedoManager = UndoRedoManager()
        val gate = StandardApprovalGate(
            getCurrentTimeline = { currentProject },
            updateTimeline = { currentProject = it },
            undoRedoManager = undoRedoManager
        )

        val plan = EditPlan(
            title = "Multi-Action Compound Plan",
            description = "Cut, push-in zoom, and mute",
            actions = listOf(
                DeleteSilenceAction(
                    id = "act-cut",
                    description = "Cut 2s-5s",
                    trackId = "track-video-1",
                    startTimeUs = 2_000_000L,
                    endTimeUs = 5_000_000L
                ),
                PushInZoomAction(
                    id = "act-zoom",
                    description = "Zoom 1.15x at 15s",
                    trackId = "track-video-1",
                    clipId = "clip-v-B",
                    startTimeUs = 12_000_000L,
                    endTimeUs = 22_000_000L,
                    splitPointUs = 15_000_000L,
                    zoomScale = 1.15f
                ),
                MuteNoiseAction(
                    id = "act-mute",
                    description = "Mute audio noise",
                    trackId = "track-audio-1",
                    clipId = "clip-a-1",
                    startTimeUs = 0L,
                    endTimeUs = 10_000_000L,
                    targetVolume = 0.0f
                )
            )
        )

        val applyResult = gate.applyPlan(plan, setOf("act-cut", "act-zoom", "act-mute"))
        assertThat(applyResult.isSuccess).isTrue()

        // Timeline is now mutated
        val mutatedDuration = currentProject.durationUs
        val mutatedVideoClipsCount = currentProject.findTrackById("track-video-1")!!.clips.size
        assertThat(mutatedVideoClipsCount).isGreaterThan(3)

        // 1-Tap Undo
        assertThat(undoRedoManager.canUndo).isTrue()
        val undoneProject = undoRedoManager.undo(currentProject)
        assertThat(undoneProject).isNotNull()
        currentProject = undoneProject!!

        // Verify EXACT restoration to initial project state
        assertThat(currentProject.durationUs).isEqualTo(initialProject.durationUs)
        val restoredVideo = currentProject.findTrackById("track-video-1")!!
        assertThat(restoredVideo.clips).hasSize(3)
        assertThat(restoredVideo.clips[0].id).isEqualTo("clip-v-A")
        assertThat(restoredVideo.clips[0].timelineDurationUs).isEqualTo(10_000_000L)
        assertThat(restoredVideo.clips[1].id).isEqualTo("clip-v-B")
        assertThat(restoredVideo.clips[1].timelineStartUs).isEqualTo(12_000_000L)
        val restoredAudio = currentProject.findTrackById("track-audio-1")!!
        assertThat(restoredAudio.clips[0].audioProperties.volume).isEqualTo(1.0f)

        // 1-Tap Redo
        assertThat(undoRedoManager.canRedo).isTrue()
        val redoneProject = undoRedoManager.redo(currentProject)
        assertThat(redoneProject).isNotNull()
        currentProject = redoneProject!!

        // Verify post-plan state restored cleanly
        assertThat(currentProject.durationUs).isEqualTo(mutatedDuration)
        assertThat(currentProject.findTrackById("track-video-1")!!.clips.size).isEqualTo(mutatedVideoClipsCount)
    }

    @Test
    fun rollbackPlan_programmaticRollbackAndErrorHandling() = runTest {
        val initialProject = createMultiTrackProject()
        var currentProject = initialProject
        val gate = StandardApprovalGate(
            getCurrentTimeline = { currentProject },
            updateTimeline = { currentProject = it }
        )

        val plan = EditPlan(
            title = "Rollback Target Plan",
            description = "To be rolled back",
            actions = listOf(
                CutSegmentAction(
                    id = "cut-rb",
                    description = "Cut 1s-4s",
                    trackId = "track-video-1",
                    startTimeUs = 1_000_000L,
                    endTimeUs = 4_000_000L
                )
            )
        )

        gate.applyPlan(plan, setOf("cut-rb"))
        assertThat(gate.isPlanApplied(plan.id)).isTrue()

        // Rollback plan
        val rollbackResult = gate.rollbackPlan(plan.id)
        assertThat(rollbackResult.isSuccess).isTrue()
        assertThat(gate.isPlanApplied(plan.id)).isFalse()
        assertThat(currentProject.durationUs).isEqualTo(initialProject.durationUs)

        // Second rollback on the same plan must fail with NoSuchElementException
        val secondRollback = gate.rollbackPlan(plan.id)
        assertThat(secondRollback.isFailure).isTrue()
        assertThat(secondRollback.exceptionOrNull()).isInstanceOf(NoSuchElementException::class.java)

        // Rollback on an unknown plan must fail with NoSuchElementException
        val unknownRollback = gate.rollbackPlan("unknown-plan-999")
        assertThat(unknownRollback.isFailure).isTrue()
        assertThat(unknownRollback.exceptionOrNull()).isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun planExecutionEngine_edgeCases_extremeBoundaries() {
        val initialProject = createMultiTrackProject()

        // 1. Empty action list -> returns timeline unchanged
        val noOpResult = PlanExecutionEngine.executeActions(initialProject, emptyList())
        assertThat(noOpResult.durationUs).isEqualTo(initialProject.durationUs)
        assertThat(noOpResult.tracks).isEqualTo(initialProject.tracks)

        // 2. Inverted cut (startTimeUs > endTimeUs) -> model rejects at construction with IllegalArgumentException
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CutSegmentAction(
                id = "inv-1",
                description = "Inverted cut",
                trackId = "track-video-1",
                startTimeUs = 8_000_000L,
                endTimeUs = 2_000_000L
            )
        }

        // 3. Zero duration cut (startTimeUs == endTimeUs) -> ignored
        val zeroCut = CutSegmentAction(
            id = "zero-1",
            description = "Zero duration cut",
            trackId = "track-video-1",
            startTimeUs = 5_000_000L,
            endTimeUs = 5_000_000L
        )
        val zeroResult = PlanExecutionEngine.executeActions(initialProject, listOf(zeroCut))
        assertThat(zeroResult.durationUs).isEqualTo(initialProject.durationUs)

        // 4. Cut completely outside timeline bounds (e.g. 80s .. 90s) -> ignored
        val outOfBoundsCut = CutSegmentAction(
            id = "oob-1",
            description = "Out of bounds cut",
            trackId = "track-video-1",
            startTimeUs = 80_000_000L,
            endTimeUs = 90_000_000L
        )
        val oobResult = PlanExecutionEngine.executeActions(initialProject, listOf(outOfBoundsCut))
        assertThat(oobResult.durationUs).isEqualTo(initialProject.durationUs)

        // 5. Cut completely engulfing an entire clip: [11s .. 23s] engulfs Clip B [12s .. 22s]
        val engulfCut = CutSegmentAction(
            id = "engulf-1",
            description = "Engulf Clip B",
            trackId = "track-video-1",
            startTimeUs = 11_000_000L,
            endTimeUs = 23_000_000L,
            ripple = true
        )
        val engulfResult = PlanExecutionEngine.executeActions(initialProject, listOf(engulfCut))
        val engulfVideoTrack = engulfResult.findTrackById("track-video-1")!!
        assertThat(engulfVideoTrack.validateNonOverlapping()).isTrue()
        // Clip B must be completely excised; only Clip A and Clip C remain
        assertThat(engulfVideoTrack.clips).hasSize(2)
        assertThat(engulfVideoTrack.clips[0].id).isEqualTo("clip-v-A")
        assertThat(engulfVideoTrack.clips[1].id).isEqualTo("clip-v-C")

        // 6. Contiguous adjacent cuts: [2s .. 4s] and [4s .. 6s]
        val contiguousCuts = listOf(
            CutSegmentAction(id = "contig-1", description = "Cut 2-4", trackId = "track-video-1", startTimeUs = 2_000_000L, endTimeUs = 4_000_000L),
            CutSegmentAction(id = "contig-2", description = "Cut 4-6", trackId = "track-video-1", startTimeUs = 4_000_000L, endTimeUs = 6_000_000L)
        )
        val contiguousResult = PlanExecutionEngine.executeActions(initialProject, contiguousCuts)
        val contigVideoTrack = contiguousResult.findTrackById("track-video-1")!!
        assertThat(contigVideoTrack.validateNonOverlapping()).isTrue()
        // Clip A should be split into [0s .. 2s] and [2s .. 6s] (was 6s..10s shifted by 4s)
        assertThat(contigVideoTrack.clips[0].timelineStartUs).isEqualTo(0L)
        assertThat(contigVideoTrack.clips[0].timelineDurationUs).isEqualTo(2_000_000L)
        assertThat(contigVideoTrack.clips[1].timelineStartUs).isEqualTo(2_000_000L)
        assertThat(contigVideoTrack.clips[1].timelineDurationUs).isEqualTo(4_000_000L)

        // 7. Non-ripple cut leaves a gap without shifting downstream clips
        val nonRippleCut = CutSegmentAction(
            id = "non-ripple-1",
            description = "Cut without ripple",
            trackId = "track-video-1",
            startTimeUs = 3_000_000L,
            endTimeUs = 5_000_000L,
            ripple = false
        )
        val nonRippleResult = PlanExecutionEngine.executeActions(initialProject, listOf(nonRippleCut))
        val nrVideoTrack = nonRippleResult.findTrackById("track-video-1")!!
        assertThat(nrVideoTrack.validateNonOverlapping()).isTrue()
        // Clip A is split into [0s..3s] and [5s..10s]
        assertThat(nrVideoTrack.clips[0].timelineStartUs).isEqualTo(0L)
        assertThat(nrVideoTrack.clips[0].timelineDurationUs).isEqualTo(3_000_000L)
        assertThat(nrVideoTrack.clips[1].timelineStartUs).isEqualTo(5_000_000L)
        assertThat(nrVideoTrack.clips[1].timelineDurationUs).isEqualTo(5_000_000L)
        // Clip B start is NOT shifted (remains at 12s)
        assertThat(nrVideoTrack.clips[2].timelineStartUs).isEqualTo(12_000_000L)
    }

    @Test
    fun planExecutionEngine_speedAndTrimActions_directMutation() {
        val initialProject = createMultiTrackProject()

        val speedAction = AdjustSpeedAction(
            id = "spd-1",
            description = "Speed up Clip A to 2x",
            trackId = "track-video-1",
            clipId = "clip-v-A",
            startTimeUs = 0L,
            endTimeUs = 10_000_000L,
            currentSpeedMultiplier = 1.0f,
            targetSpeedMultiplier = 2.0f
        )
        val trimAction = TrimClipAction(
            id = "trim-1",
            description = "Trim Clip B source in to 2s",
            trackId = "track-video-1",
            clipId = "clip-v-B",
            startTimeUs = 12_000_000L,
            endTimeUs = 22_000_000L,
            newSourceInUs = 12_000_000L,
            newSourceOutUs = 18_000_000L
        )

        val mutated = PlanExecutionEngine.executeActions(initialProject, listOf(speedAction, trimAction))
        val track = mutated.findTrackById("track-video-1")!!

        val clipA = track.clips.first { it.id == "clip-v-A" }
        assertThat(clipA.speedMultiplier).isEqualTo(2.0f)
        assertThat(clipA.timelineDurationUs).isEqualTo(5_000_000L) // 10s / 2 = 5s

        val clipB = track.clips.first { it.id == "clip-v-B" }
        assertThat(clipB.sourceInUs).isEqualTo(12_000_000L)
        assertThat(clipB.sourceOutUs).isEqualTo(18_000_000L)
        assertThat(clipB.timelineDurationUs).isEqualTo(6_000_000L)
    }
}
