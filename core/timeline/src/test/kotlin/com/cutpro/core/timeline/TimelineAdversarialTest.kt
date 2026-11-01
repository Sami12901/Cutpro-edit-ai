package com.cutpro.core.timeline

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipTrimHelper
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import kotlin.math.round

/**
 * Adversarial and stress test suite for SnappingEngine and TimelineOperations in :core:timeline.
 * Tests mathematical boundaries, tie-breaking, microsecond precision, zoom scaling,
 * trimming clamps, and split continuity invariants.
 */
class TimelineAdversarialTest {

    private lateinit var snappingEngine: SnappingEngine
    private lateinit var timelineEngine: StandardTimelineEngine
    private lateinit var sampleMedia: MediaSourceInfo
    private lateinit var standardClip: TimelineClip

    @Before
    fun setUp() {
        snappingEngine = SnappingEngine(defaultSnapThresholdDp = 16f)
        timelineEngine = StandardTimelineEngine(snappingEngine)

        sampleMedia = MediaSourceInfo(
            uriString = "content://media/adversarial_sample.mp4",
            fileName = "adversarial_sample.mp4",
            mimeType = "video/mp4",
            durationUs = 20_000_000L // 20 seconds
        )

        standardClip = TimelineClip(
            id = "clip_adv_1",
            trackId = "track_v1",
            mediaSource = sampleMedia,
            timelineStartUs = 2_000_000L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 1_000_000L,
            sourceOutUs = 6_000_000L,
            speedMultiplier = 1.0f
        )
    }

    // =========================================================================
    // 1. SnappingEngine - Zoom Scaling & Display Density Stress Matrix
    // =========================================================================

    @Test
    fun snapping_zoomAndDensityMatrix_exactBoundaryEpsilon() {
        val zoomLevels = listOf(0.25f, 0.5f, 1.0f, 2.0f, 2.75f, 3.5f, 4.0f)
        val displayDensities = listOf(160.0f, 240.0f, 320.0f, 440.0f, 560.0f, 640.0f)
        val snapThresholdDp = 16.0f
        val candidate = 5_000_000L
        val candidateList = listOf(candidate)

        for (zoom in zoomLevels) {
            val pixelsPerSecond = 100.0f * zoom
            for (density in displayDensities) {
                val dpToPixels = snapThresholdDp.toDouble() * (density.toDouble() / 160.0)
                val expectedThresholdUs = round((dpToPixels / pixelsPerSecond.toDouble()) * 1_000_000.0).toLong()

                // Exact threshold boundary (positive offset) -> MUST snap
                val snapAtThreshold = snappingEngine.findSnapPoint(
                    movingEdgeUs = candidate + expectedThresholdUs,
                    candidatePointsUs = candidateList,
                    pixelsPerSecond = pixelsPerSecond,
                    densityDpi = density,
                    snapThresholdDp = snapThresholdDp
                )
                assertThat(snapAtThreshold).isEqualTo(candidate)

                // 1 us inside threshold -> MUST snap
                val snapInside = snappingEngine.findSnapPoint(
                    movingEdgeUs = candidate + expectedThresholdUs - 1L,
                    candidatePointsUs = candidateList,
                    pixelsPerSecond = pixelsPerSecond,
                    densityDpi = density,
                    snapThresholdDp = snapThresholdDp
                )
                assertThat(snapInside).isEqualTo(candidate)

                // 1 us outside threshold -> MUST NOT snap (returns null)
                val snapOutside = snappingEngine.findSnapPoint(
                    movingEdgeUs = candidate + expectedThresholdUs + 1L,
                    candidatePointsUs = candidateList,
                    pixelsPerSecond = pixelsPerSecond,
                    densityDpi = density,
                    snapThresholdDp = snapThresholdDp
                )
                assertThat(snapOutside).isNull()

                // Exact negative threshold boundary -> MUST snap
                val snapAtNegativeThreshold = snappingEngine.findSnapPoint(
                    movingEdgeUs = candidate - expectedThresholdUs,
                    candidatePointsUs = candidateList,
                    pixelsPerSecond = pixelsPerSecond,
                    densityDpi = density,
                    snapThresholdDp = snapThresholdDp
                )
                assertThat(snapAtNegativeThreshold).isEqualTo(candidate)

                // 1 us inside negative threshold -> MUST snap
                val snapNegativeInside = snappingEngine.findSnapPoint(
                    movingEdgeUs = candidate - expectedThresholdUs + 1L,
                    candidatePointsUs = candidateList,
                    pixelsPerSecond = pixelsPerSecond,
                    densityDpi = density,
                    snapThresholdDp = snapThresholdDp
                )
                assertThat(snapNegativeInside).isEqualTo(candidate)

                // 1 us outside negative threshold -> MUST NOT snap
                val snapNegativeOutside = snappingEngine.findSnapPoint(
                    movingEdgeUs = candidate - expectedThresholdUs - 1L,
                    candidatePointsUs = candidateList,
                    pixelsPerSecond = pixelsPerSecond,
                    densityDpi = density,
                    snapThresholdDp = snapThresholdDp
                )
                assertThat(snapNegativeOutside).isNull()
            }
        }
    }

    // =========================================================================
    // 2. SnappingEngine - Multi-Target Tie-Breaking & Extreme Values
    // =========================================================================

    @Test
    fun snapping_multiTarget_closestCandidateWinsDeterministically() {
        val candidates = listOf(1_000_000L, 2_000_000L, 3_000_000L)
        val pps = 100.0f
        val dpi = 160.0f
        // threshold is 160_000 us

        // Position 1_050_000 is 50_000 us from 1_000_000 and 950_000 us from 2_000_000
        val snap1 = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_050_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = pps,
            densityDpi = dpi
        )
        assertThat(snap1).isEqualTo(1_000_000L)

        // Position 1_950_000 is 50_000 us from 2_000_000
        val snap2 = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_950_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = pps,
            densityDpi = dpi
        )
        assertThat(snap2).isEqualTo(2_000_000L)

        // Position 2_050_000 is 50_000 us from 2_000_000
        val snap3 = snappingEngine.findSnapPoint(
            movingEdgeUs = 2_050_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = pps,
            densityDpi = dpi
        )
        assertThat(snap3).isEqualTo(2_000_000L)

        // Position 2_950_000 is 50_000 us from 3_000_000
        val snap4 = snappingEngine.findSnapPoint(
            movingEdgeUs = 2_950_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = pps,
            densityDpi = dpi
        )
        assertThat(snap4).isEqualTo(3_000_000L)
    }

    @Test
    fun snapping_equidistantCandidates_deterministicFirstCandidateSelection() {
        // Two candidates equidistant from 1_500_000L with threshold large enough
        val candidatesForward = listOf(1_000_000L, 2_000_000L)
        val snapForward = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_500_000L,
            candidatePointsUs = candidatesForward,
            pixelsPerSecond = 20.0f, // threshold = 800_000 us
            densityDpi = 160.0f
        )
        // First encountered in iteration wins
        assertThat(snapForward).isEqualTo(1_000_000L)

        val candidatesReverse = listOf(2_000_000L, 1_000_000L)
        val snapReverse = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_500_000L,
            candidatePointsUs = candidatesReverse,
            pixelsPerSecond = 20.0f,
            densityDpi = 160.0f
        )
        assertThat(snapReverse).isEqualTo(2_000_000L)
    }

    @Test
    fun snapping_duplicateCandidates_resolvesDeterministically() {
        val candidates = listOf(1_000_000L, 1_000_000L, 2_000_000L)
        val snap = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_020_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f
        )
        assertThat(snap).isEqualTo(1_000_000L)
    }

    @Test
    fun snapping_zeroThreshold_onlySnapsOnExactZeroDistance() {
        val candidates = listOf(1_000_000L, 2_000_000L)
        val snapExact = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_000_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f,
            snapThresholdDp = 0.0f
        )
        assertThat(snapExact).isEqualTo(1_000_000L)

        val snapNear = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_000_001L,
            candidatePointsUs = candidates,
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f,
            snapThresholdDp = 0.0f
        )
        assertThat(snapNear).isNull()
    }

    @Test
    fun snapping_emptyCandidatePoints_returnsNull() {
        val snap = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_000_000L,
            candidatePointsUs = emptyList(),
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f
        )
        assertThat(snap).isNull()
    }

    @Test
    fun snapping_negativeMovingEdge_fuzzTesting() {
        // Negative edge near 0 within threshold snaps to 0
        val snapNearZero = snappingEngine.findSnapPoint(
            movingEdgeUs = -50_000L,
            candidatePointsUs = listOf(0L, 2_000_000L),
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f // threshold 160_000 us
        )
        assertThat(snapNearZero).isEqualTo(0L)

        // Negative edge far beyond threshold does not snap
        val snapFarZero = snappingEngine.findSnapPoint(
            movingEdgeUs = -500_000L,
            candidatePointsUs = listOf(0L, 2_000_000L),
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f
        )
        assertThat(snapFarZero).isNull()

        // Negative candidate points
        val negativeCandidates = listOf(-1_000_000L, 0L, 1_000_000L)
        val snapNegative = snappingEngine.findSnapPoint(
            movingEdgeUs = -990_000L,
            candidatePointsUs = negativeCandidates,
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f
        )
        assertThat(snapNegative).isEqualTo(-1_000_000L)
    }

    @Test
    fun snapping_extremeZoomLevelsAndLargeTimestamps() {
        // Deep zoom in (50,000 px/sec) -> threshold is very narrow (~320 us)
        val narrowSnap = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_000_100L,
            candidatePointsUs = listOf(1_000_000L),
            pixelsPerSecond = 50_000.0f,
            densityDpi = 160.0f
        )
        assertThat(narrowSnap).isEqualTo(1_000_000L)

        val narrowMiss = snappingEngine.findSnapPoint(
            movingEdgeUs = 1_000_500L,
            candidatePointsUs = listOf(1_000_000L),
            pixelsPerSecond = 50_000.0f,
            densityDpi = 160.0f
        )
        assertThat(narrowMiss).isNull()

        // Extreme large timestamp (24-hour project = 86_400_000_000 us)
        val dayUs = 86_400_000_000L
        val largeSnap = snappingEngine.findSnapPoint(
            movingEdgeUs = dayUs + 50_000L,
            candidatePointsUs = listOf(dayUs),
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f
        )
        assertThat(largeSnap).isEqualTo(dayUs)
    }

    // =========================================================================
    // 3. TimelineOperations / ClipTrimHelper - Trimming Boundary Invariants
    // =========================================================================

    @Test
    fun trimIn_clampedAtZeroTimelineStart_cannotExpandPastOrigin() {
        // Clip starts at timelineStartUs = 500_000L, sourceInUs = 1_000_000L
        val clip = standardClip.copy(
            timelineStartUs = 500_000L,
            sourceInUs = 1_000_000L,
            sourceOutUs = 5_000_000L,
            timelineDurationUs = 4_000_000L
        )

        // Requesting trimIn to -5_000_000L (or 0L source)
        val trimmed = timelineEngine.trimIn(clip, newInUs = -1_000_000L)

        // Timeline start cannot be negative; clamped at 0L
        assertThat(trimmed.timelineStartUs).isAtLeast(0L)
        assertThat(trimmed.timelineStartUs).isEqualTo(0L)
        // Moving timelineStartUs from 500_000 to 0 reduces sourceInUs by 500_000
        assertThat(trimmed.sourceInUs).isEqualTo(500_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(4_500_000L)
        assertThat(trimmed.timelineEndUs).isEqualTo(4_500_000L)
    }

    @Test
    fun trimIn_clipAlreadyAtTimelineStartZero_cannotMoveEarlier() {
        // Clip starts at 0L on timeline
        val clip = standardClip.copy(
            timelineStartUs = 0L,
            sourceInUs = 3_000_000L,
            sourceOutUs = 8_000_000L,
            timelineDurationUs = 5_000_000L
        )

        val trimmed = timelineEngine.trimIn(clip, newInUs = 1_000_000L)

        // Since timelineStartUs was 0, it cannot expand earlier
        assertThat(trimmed.timelineStartUs).isEqualTo(0L)
        assertThat(trimmed.sourceInUs).isEqualTo(3_000_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(5_000_000L)
    }

    @Test
    fun trimIn_exceedingSourceOut_enforcesMinClipDuration() {
        // Requesting trimIn beyond sourceOutUs (collapsing clip)
        val trimmed = timelineEngine.trimIn(
            clip = standardClip,
            newInUs = standardClip.sourceOutUs + 10_000_000L,
            minClipDurationUs = 100_000L
        )

        // Must enforce minimum duration
        assertThat(trimmed.timelineDurationUs).isAtLeast(100_000L)
        assertThat(trimmed.sourceInUs).isEqualTo(standardClip.sourceOutUs - 100_000L)
    }

    @Test
    fun trimIn_withSpeedScaling_preservesMicrosecondMath() {
        val clip = standardClip.copy(
            timelineStartUs = 1_000_000L,
            sourceInUs = 2_000_000L,
            sourceOutUs = 6_000_000L,
            speedMultiplier = 2.0f,
            timelineDurationUs = 2_000_000L // 4s source / 2x speed = 2s
        )

        // Trim in from source 2s to 3s (delta source = +1s, delta timeline = +0.5s)
        val trimmed = timelineEngine.trimIn(clip, newInUs = 3_000_000L)

        assertThat(trimmed.sourceInUs).isEqualTo(3_000_000L)
        assertThat(trimmed.timelineStartUs).isEqualTo(1_500_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(1_500_000L) // 3s source / 2x = 1.5s
    }

    @Test
    fun trimOut_clampedToSourceMediaDuration() {
        // Media duration is 20_000_000L
        val trimmed = timelineEngine.trimOut(
            clip = standardClip,
            newOutUs = 50_000_000L
        )

        // Must not exceed physical media duration
        assertThat(trimmed.sourceOutUs).isAtMost(sampleMedia.durationUs)
        assertThat(trimmed.sourceOutUs).isEqualTo(20_000_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(19_000_000L) // (20M - 1M) / 1.0
    }

    @Test
    fun trimOut_collapsingTowardsInPoint_enforcesMinClipDuration() {
        // Requesting trimOut smaller than sourceInUs + minClipDurationUs
        val trimmed = timelineEngine.trimOut(
            clip = standardClip,
            newOutUs = standardClip.sourceInUs - 500_000L,
            minClipDurationUs = 100_000L
        )

        // Must enforce minimum duration
        assertThat(trimmed.timelineDurationUs).isAtLeast(100_000L)
        assertThat(trimmed.sourceOutUs).isEqualTo(standardClip.sourceInUs + 100_000L)
    }

    @Test
    fun trimOut_withSpeedScaling_preservesMicrosecondMath() {
        val clip = standardClip.copy(
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            speedMultiplier = 0.5f,
            timelineDurationUs = 20_000_000L
        )

        // Trim out to 5_000_000L source -> timeline duration should become 10_000_000L
        val trimmed = timelineEngine.trimOut(clip, newOutUs = 5_000_000L)

        assertThat(trimmed.sourceOutUs).isEqualTo(5_000_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(10_000_000L)
    }

    // =========================================================================
    // 4. TimelineOperations / ClipTrimHelper - Split Adversarial Invariants
    // =========================================================================

    @Test
    fun splitAt_exactClipStart_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.splitAt(standardClip, splitPlayheadUs = standardClip.timelineStartUs)
        }
    }

    @Test
    fun splitAt_exactClipEnd_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.splitAt(standardClip, splitPlayheadUs = standardClip.timelineEndUs)
        }
    }

    @Test
    fun splitAt_outsideClipBeforeStart_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.splitAt(standardClip, splitPlayheadUs = standardClip.timelineStartUs - 500_000L)
        }
    }

    @Test
    fun splitAt_outsideClipAfterEnd_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.splitAt(standardClip, splitPlayheadUs = standardClip.timelineEndUs + 500_000L)
        }
    }

    @Test
    fun splitAt_withinMinDurationBuffer_throwsIllegalArgumentException() {
        val minDuration = 100_000L
        // 1 us too close to start
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.splitAt(
                standardClip,
                splitPlayheadUs = standardClip.timelineStartUs + minDuration - 1L,
                minClipDurationUs = minDuration
            )
        }

        // 1 us too close to end
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.splitAt(
                standardClip,
                splitPlayheadUs = standardClip.timelineEndUs - minDuration + 1L,
                minClipDurationUs = minDuration
            )
        }
    }

    @Test
    fun splitAt_exactMinDurationBoundaries_succeeds() {
        val minDuration = 100_000L

        // Exactly at start + minDuration
        val (left1, right1) = timelineEngine.splitAt(
            standardClip,
            splitPlayheadUs = standardClip.timelineStartUs + minDuration,
            minClipDurationUs = minDuration
        )
        assertThat(left1.timelineDurationUs).isEqualTo(minDuration)
        assertThat(left1.timelineEndUs).isEqualTo(right1.timelineStartUs)

        // Exactly at end - minDuration
        val (left2, right2) = timelineEngine.splitAt(
            standardClip,
            splitPlayheadUs = standardClip.timelineEndUs - minDuration,
            minClipDurationUs = minDuration
        )
        assertThat(right2.timelineDurationUs).isEqualTo(minDuration)
        assertThat(left2.timelineEndUs).isEqualTo(right2.timelineStartUs)
    }

    @Test
    fun splitAt_continuousIntervalGuaranteesAcrossMultiplePositions() {
        val splitOffsets = listOf(200_000L, 1_000_000L, 2_500_000L, 3_800_000L, 4_900_000L)

        for (offset in splitOffsets) {
            val playhead = standardClip.timelineStartUs + offset
            val (left, right) = timelineEngine.splitAt(standardClip, splitPlayheadUs = playhead)

            // Strict continuity: no gap, no overlap
            assertThat(left.timelineEndUs).isEqualTo(right.timelineStartUs)
            assertThat(left.timelineStartUs).isEqualTo(standardClip.timelineStartUs)
            assertThat(right.timelineEndUs).isEqualTo(standardClip.timelineEndUs)
            assertThat(left.timelineDurationUs + right.timelineDurationUs).isEqualTo(standardClip.timelineDurationUs)

            // Source continuity
            assertThat(left.sourceInUs).isEqualTo(standardClip.sourceInUs)
            assertThat(left.sourceOutUs).isEqualTo(right.sourceInUs)
            assertThat(right.sourceOutUs).isEqualTo(standardClip.sourceOutUs)

            // Unique IDs generated
            assertThat(left.id).isNotEqualTo(right.id)
            assertThat(left.id).isNotEqualTo(standardClip.id)
            assertThat(right.id).isNotEqualTo(standardClip.id)

            // Audio fade invariants (internal split edge removes fades)
            assertThat(left.audioProperties.fadeOutDurationUs).isEqualTo(0L)
            assertThat(right.audioProperties.fadeInDurationUs).isEqualTo(0L)
        }
    }

    @Test
    fun splitAt_withSpeedMultipliers_preservesSourceAndTimelineSync() {
        val speeds = listOf(0.25f, 0.5f, 1.5f, 2.0f, 4.0f)

        for (speed in speeds) {
            val sourceDuration = 10_000_000L
            val clip = standardClip.copy(
                sourceInUs = 0L,
                sourceOutUs = sourceDuration,
                speedMultiplier = speed,
                timelineStartUs = 1_000_000L,
                timelineDurationUs = (sourceDuration / speed).toLong()
            )

            val splitPoint = clip.timelineStartUs + (clip.timelineDurationUs / 2)
            val (left, right) = timelineEngine.splitAt(clip, splitPlayheadUs = splitPoint)

            assertThat(left.timelineEndUs).isEqualTo(right.timelineStartUs)
            assertThat(left.sourceOutUs).isEqualTo(right.sourceInUs)
            assertThat(left.timelineDurationUs + right.timelineDurationUs).isEqualTo(clip.timelineDurationUs)
        }
    }

    // =========================================================================
    // 5. TimelineOperations - Speed Changes & Precision Invariants
    // =========================================================================

    @Test
    fun adjustSpeed_acrossRange_calculatesCorrectTimelineDurations() {
        val baseClip = standardClip.copy(
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L, // 10 seconds source
            timelineDurationUs = 10_000_000L
        )

        val testCases = mapOf(
            0.25f to 40_000_000L,
            0.5f to 20_000_000L,
            0.75f to 13_333_333L,
            1.0f to 10_000_000L,
            1.25f to 8_000_000L,
            1.5f to 6_666_666L,
            2.0f to 5_000_000L,
            2.5f to 4_000_000L,
            3.0f to 3_333_333L,
            4.0f to 2_500_000L
        )

        for ((multiplier, expectedDurationUs) in testCases) {
            val sped = timelineEngine.adjustSpeed(baseClip, multiplier)
            assertThat(sped.speedMultiplier).isEqualTo(multiplier)
            assertThat(sped.timelineDurationUs).isEqualTo(expectedDurationUs)
        }
    }

    @Test
    fun adjustSpeed_outOfRangeMultipliers_throwsIllegalArgumentException() {
        // Below 0.1f minimum
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.adjustSpeed(standardClip, 0.05f)
        }

        // Above 10.0f maximum
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.adjustSpeed(standardClip, 15.0f)
        }

        // Negative speed
        assertThrows(IllegalArgumentException::class.java) {
            timelineEngine.adjustSpeed(standardClip, -1.0f)
        }
    }

    // =========================================================================
    // 6. TimelineOperations - Candidate Snap Points & Track Overlap Stress
    // =========================================================================

    @Test
    fun collectCandidateSnapPoints_emptyProjectAndNegativePlayhead() {
        val emptyProject = ProjectManifest(
            id = "empty_proj",
            title = "Empty",
            aspectRatio = CanvasAspectRatio.RATIO_16_9,
            defaultResolution = ExportResolution.RES_1080P,
            defaultFrameRate = ExportFrameRate.FPS_30,
            durationUs = 0L,
            tracks = emptyList()
        )

        // Negative playhead should be ignored
        val pointsNegativePlayhead = TimelineOperations.collectCandidateSnapPoints(
            project = emptyProject,
            playheadUs = -500_000L
        )
        assertThat(pointsNegativePlayhead).containsExactly(0L)

        // Positive playhead included
        val pointsPositive = TimelineOperations.collectCandidateSnapPoints(
            project = emptyProject,
            playheadUs = 1_500_000L
        )
        assertThat(pointsPositive).containsExactly(0L, 1_500_000L).inOrder()
    }

    @Test
    fun collectCandidateSnapPoints_hiddenTracksAndClipExclusion() {
        val clipA = standardClip.copy(
            id = "cA",
            trackId = "t_vis",
            timelineStartUs = 1_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
            timelineDurationUs = 2_000_000L
        )
        val clipB = standardClip.copy(
            id = "cB",
            trackId = "t_vis",
            timelineStartUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
            timelineDurationUs = 2_000_000L
        )
        val hiddenClip = standardClip.copy(
            id = "cH",
            trackId = "t_hid",
            timelineStartUs = 8_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
            timelineDurationUs = 2_000_000L
        )

        val visibleTrack = TimelineTrack(
            id = "t_vis",
            name = "Visible Track",
            type = TrackType.VIDEO,
            zIndex = 0,
            isVisible = true,
            clips = listOf(clipA, clipB)
        )
        val hiddenTrack = TimelineTrack(
            id = "t_hid",
            name = "Hidden Track",
            type = TrackType.VIDEO,
            zIndex = 1,
            isVisible = false,
            clips = listOf(hiddenClip)
        )

        val project = ProjectManifest(
            id = "p1",
            title = "Test",
            aspectRatio = CanvasAspectRatio.RATIO_16_9,
            defaultResolution = ExportResolution.RES_1080P,
            defaultFrameRate = ExportFrameRate.FPS_30,
            durationUs = 10_000_000L,
            tracks = listOf(visibleTrack, hiddenTrack)
        )

        // Excluding clipA: should include 0L, duration 10M, and clipB boundaries (4M, 6M)
        // Hidden track clipH (8M, 10M) must NOT be included from the hidden track
        val candidates = TimelineOperations.collectCandidateSnapPoints(
            project = project,
            playheadUs = null,
            excludeClipId = "cA"
        )

        assertThat(candidates).contains(0L)
        assertThat(candidates).contains(4_000_000L)
        assertThat(candidates).contains(6_000_000L)
        assertThat(candidates).contains(10_000_000L)
        assertThat(candidates).doesNotContain(1_000_000L) // clipA start excluded
        assertThat(candidates).doesNotContain(3_000_000L) // clipA end excluded
        assertThat(candidates).doesNotContain(8_000_000L) // hidden track excluded
        assertThat(candidates).isInOrder()
    }

    @Test
    fun hasTrackOverlap_boundaryPrecisionAndTouchCases() {
        val clip = standardClip.copy(
            id = "c1",
            trackId = "t1",
            timelineStartUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 3_000_000L,
            timelineDurationUs = 3_000_000L // [2_000_000, 5_000_000]
        )
        val track = TimelineTrack(
            id = "t1",
            name = "Video",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )

        // Exact touching edge on left: ends at 2_000_000L -> NO overlap
        assertThat(TimelineOperations.hasTrackOverlap(track, startUs = 0L, durationUs = 2_000_000L)).isFalse()

        // Exact touching edge on right: starts at 5_000_000L -> NO overlap
        assertThat(TimelineOperations.hasTrackOverlap(track, startUs = 5_000_000L, durationUs = 2_000_000L)).isFalse()

        // 1 microsecond overlap on left: ends at 2_000_001L -> MUST detect overlap
        assertThat(TimelineOperations.hasTrackOverlap(track, startUs = 0L, durationUs = 2_000_001L)).isTrue()

        // 1 microsecond overlap on right: starts at 4_999_999L -> MUST detect overlap
        assertThat(TimelineOperations.hasTrackOverlap(track, startUs = 4_999_999L, durationUs = 2_000_000L)).isTrue()

        // Fully enclosed within existing clip -> MUST detect overlap
        assertThat(TimelineOperations.hasTrackOverlap(track, startUs = 3_000_000L, durationUs = 1_000_000L)).isTrue()

        // Fully enclosing existing clip -> MUST detect overlap
        assertThat(TimelineOperations.hasTrackOverlap(track, startUs = 1_000_000L, durationUs = 5_000_000L)).isTrue()

        // Exclude clip ID from check -> NO overlap with self
        assertThat(
            TimelineOperations.hasTrackOverlap(
                track = track,
                startUs = 2_000_000L,
                durationUs = 3_000_000L,
                excludeClipId = "c1"
            )
        ).isFalse()
    }

    // =========================================================================
    // 7. TimelineOperations - Project Manifest Mutation Invariance
    // =========================================================================

    @Test
    fun projectMutations_addReplaceRemoveMaintainDurationAndSorting() {
        val mainClip = standardClip.copy(
            id = "clip_adv_1",
            trackId = "t_main",
            timelineStartUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L,
            timelineDurationUs = 5_000_000L // [2_000_000, 7_000_000]
        )
        val track = TimelineTrack(
            id = "t_main",
            name = "Main",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(mainClip)
        )
        val initialProject = ProjectManifest(
            id = "proj_mut",
            title = "Mutations",
            aspectRatio = CanvasAspectRatio.RATIO_16_9,
            defaultResolution = ExportResolution.RES_1080P,
            defaultFrameRate = ExportFrameRate.FPS_30,
            durationUs = 7_000_000L,
            tracks = listOf(track)
        )

        // 1. Add clip extending duration
        val clip2 = standardClip.copy(
            id = "c2",
            trackId = "t_main",
            timelineStartUs = 8_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
            timelineDurationUs = 4_000_000L // ends at 12_000_000L
        )
        val withClip2 = TimelineOperations.addClipToProject(initialProject, "t_main", clip2)
        assertThat(withClip2.durationUs).isEqualTo(12_000_000L)
        assertThat(withClip2.tracks.first().clips.map { it.id }).containsExactly("clip_adv_1", "c2").inOrder()

        // 2. Add earlier clip, verify sorting preserved
        val clip0 = standardClip.copy(
            id = "c0",
            trackId = "t_main",
            timelineStartUs = 0L,
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
            timelineDurationUs = 1_000_000L
        )
        val withClip0 = TimelineOperations.addClipToProject(withClip2, "t_main", clip0)
        assertThat(withClip0.durationUs).isEqualTo(12_000_000L)
        assertThat(withClip0.tracks.first().clips.map { it.id }).containsExactly("c0", "clip_adv_1", "c2").inOrder()

        // 3. Replace clip with shorter duration
        val clip2Short = clip2.copy(
            sourceOutUs = 2_000_000L,
            timelineDurationUs = 2_000_000L
        ) // ends at 10_000_000L
        val withReplaced = TimelineOperations.replaceClipInProject(withClip0, "t_main", clip2Short)
        assertThat(withReplaced.durationUs).isEqualTo(10_000_000L)

        // 4. Remove tail clip -> duration shrinks to previous tail (standardClip ending at 7_000_000L)
        val withoutTail = TimelineOperations.removeClipFromProject(withReplaced, "c2")
        assertThat(withoutTail.durationUs).isEqualTo(7_000_000L)

        // 5. Remove all remaining clips -> duration becomes 0L
        val withoutStandard = TimelineOperations.removeClipFromProject(withoutTail, "clip_adv_1")
        val emptyClipsProject = TimelineOperations.removeClipFromProject(withoutStandard, "c0")
        assertThat(emptyClipsProject.durationUs).isEqualTo(0L)
        assertThat(emptyClipsProject.tracks.first().clips).isEmpty()
    }
}
