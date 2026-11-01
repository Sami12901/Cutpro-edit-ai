package com.cutpro.core.timeline

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class TimelineOperationsTest {

    private lateinit var project: ProjectManifest
    private lateinit var clip1: TimelineClip
    private lateinit var clip2: TimelineClip
    private val engine = StandardTimelineEngine()

    @Before
    fun setUp() {
        val media = MediaSourceInfo(
            uriString = "content://media/sample.mp4",
            fileName = "sample.mp4",
            mimeType = "video/mp4",
            durationUs = 10_000_000L
        )
        clip1 = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = media,
            timelineStartUs = 0L,
            timelineDurationUs = 3_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 3_000_000L
        )
        clip2 = TimelineClip(
            id = "c2",
            trackId = "v1",
            mediaSource = media,
            timelineStartUs = 3_000_000L,
            timelineDurationUs = 4_000_000L,
            sourceInUs = 3_000_000L,
            sourceOutUs = 7_000_000L
        )
        val track = TimelineTrack(
            id = "v1",
            name = "Video Track 1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip1, clip2)
        )
        project = ProjectManifest(
            id = "proj_1",
            title = "Test",
            aspectRatio = CanvasAspectRatio.RATIO_16_9,
            defaultResolution = ExportResolution.RES_1080P,
            defaultFrameRate = ExportFrameRate.FPS_30,
            durationUs = 7_000_000L,
            tracks = listOf(track)
        )
    }

    @Test
    fun trimIn_advancesInPointAndStartUs() {
        val trimmed = engine.trimIn(clip1, newInUs = 1_000_000L)
        assertThat(trimmed.sourceInUs).isEqualTo(1_000_000L)
        assertThat(trimmed.timelineStartUs).isEqualTo(1_000_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(2_000_000L)
    }

    @Test
    fun trimOut_shortensOutPointAndDuration() {
        val trimmed = engine.trimOut(clip1, newOutUs = 2_000_000L)
        assertThat(trimmed.sourceOutUs).isEqualTo(2_000_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(2_000_000L)
    }

    @Test
    fun splitAt_createsContiguousClips() {
        val (left, right) = engine.splitAt(clip1, splitPlayheadUs = 1_500_000L)
        assertThat(left.timelineStartUs).isEqualTo(0L)
        assertThat(left.timelineDurationUs).isEqualTo(1_500_000L)
        assertThat(left.sourceOutUs).isEqualTo(1_500_000L)

        assertThat(right.timelineStartUs).isEqualTo(1_500_000L)
        assertThat(right.timelineDurationUs).isEqualTo(1_500_000L)
        assertThat(right.sourceInUs).isEqualTo(1_500_000L)

        assertThat(left.timelineEndUs).isEqualTo(right.timelineStartUs)
        assertThat(left.timelineDurationUs + right.timelineDurationUs).isEqualTo(clip1.timelineDurationUs)
    }

    @Test
    fun reposition_changesStartPreservingDuration() {
        val moved = engine.reposition(clip1, newTimelineStartUs = 10_000_000L)
        assertThat(moved.timelineStartUs).isEqualTo(10_000_000L)
        assertThat(moved.timelineDurationUs).isEqualTo(clip1.timelineDurationUs)
    }

    @Test
    fun adjustSpeed_scalesDuration() {
        val spedUp = engine.adjustSpeed(clip1, speedMultiplier = 2.0f)
        assertThat(spedUp.timelineDurationUs).isEqualTo(1_500_000L)

        val slowedDown = engine.adjustSpeed(clip1, speedMultiplier = 0.5f)
        assertThat(slowedDown.timelineDurationUs).isEqualTo(6_000_000L)
    }

    @Test
    fun collectCandidateSnapPoints_gathersBoundariesAndPlayhead() {
        val candidates = TimelineOperations.collectCandidateSnapPoints(
            project = project,
            playheadUs = 2_500_000L
        )
        assertThat(candidates).contains(0L)
        assertThat(candidates).contains(2_500_000L)
        assertThat(candidates).contains(3_000_000L)
        assertThat(candidates).contains(7_000_000L)
    }

    @Test
    fun trackOverlap_detectsCollisions() {
        val track = project.tracks.first()
        val hasOverlap = TimelineOperations.hasTrackOverlap(
            track = track,
            startUs = 1_000_000L,
            durationUs = 3_000_000L
        )
        assertThat(hasOverlap).isTrue()

        val noOverlap = TimelineOperations.hasTrackOverlap(
            track = track,
            startUs = 8_000_000L,
            durationUs = 1_000_000L
        )
        assertThat(noOverlap).isFalse()
    }
}
