package com.cutpro.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ClipTrimHelperTest {

    private val sampleMedia = MediaSourceInfo(
        uriString = "content://media/picker/video/1",
        fileName = "video.mp4",
        mimeType = "video/mp4",
        durationUs = 20_000_000L, // 20s
        width = 1920,
        height = 1080
    )

    private val baseClip = TimelineClip(
        id = "clip-1",
        trackId = "track-v1",
        mediaSource = sampleMedia,
        timelineStartUs = 5_000_000L,     // starts at 5s
        timelineDurationUs = 10_000_000L, // duration 10s
        sourceInUs = 2_000_000L,          // source in at 2s
        sourceOutUs = 12_000_000L         // source out at 12s
    )

    @Test
    fun trimIn_advancesTimelineStart_keepsTimelineEndFixed() {
        val originalEnd = baseClip.timelineEndUs
        val trimmed = ClipTrimHelper.trimIn(baseClip, requestedNewInUs = 4_000_000L)

        assertThat(trimmed.sourceInUs).isEqualTo(4_000_000L)
        assertThat(trimmed.sourceOutUs).isEqualTo(12_000_000L)
        assertThat(trimmed.timelineStartUs).isEqualTo(7_000_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(8_000_000L)
        assertThat(trimmed.timelineEndUs).isEqualTo(originalEnd)
    }

    @Test
    fun trimIn_withSpeedMultiplier_scalesTimelineDeltaCorrectly() {
        val fastClip = baseClip.copy(
            speedMultiplier = 2.0f,
            timelineDurationUs = 5_000_000L // 10s source / 2.0x = 5s timeline
        )
        val originalEnd = fastClip.timelineEndUs
        val trimmed = ClipTrimHelper.trimIn(fastClip, requestedNewInUs = 4_000_000L)

        // 2s source delta / 2.0 = 1s timeline delta
        assertThat(trimmed.sourceInUs).isEqualTo(4_000_000L)
        assertThat(trimmed.timelineStartUs).isEqualTo(6_000_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(4_000_000L)
        assertThat(trimmed.timelineEndUs).isEqualTo(originalEnd)
    }

    @Test
    fun trimOut_adjustsDuration_keepsTimelineStartFixed() {
        val originalStart = baseClip.timelineStartUs
        val trimmed = ClipTrimHelper.trimOut(baseClip, requestedNewOutUs = 8_000_000L)

        assertThat(trimmed.sourceInUs).isEqualTo(2_000_000L)
        assertThat(trimmed.sourceOutUs).isEqualTo(8_000_000L)
        assertThat(trimmed.timelineStartUs).isEqualTo(originalStart)
        assertThat(trimmed.timelineDurationUs).isEqualTo(6_000_000L)
        assertThat(trimmed.timelineEndUs).isEqualTo(11_000_000L)
    }

    @Test
    fun split_createsTwoContiguousClips() {
        val splitPlayheadUs = 8_000_000L // 3s into timeline
        val (left, right) = ClipTrimHelper.split(baseClip, splitPlayheadUs)

        assertThat(left.timelineStartUs).isEqualTo(5_000_000L)
        assertThat(left.timelineDurationUs).isEqualTo(3_000_000L)
        assertThat(left.timelineEndUs).isEqualTo(splitPlayheadUs)
        assertThat(left.sourceInUs).isEqualTo(2_000_000L)
        assertThat(left.sourceOutUs).isEqualTo(5_000_000L)

        assertThat(right.timelineStartUs).isEqualTo(splitPlayheadUs)
        assertThat(right.timelineDurationUs).isEqualTo(7_000_000L)
        assertThat(right.timelineEndUs).isEqualTo(15_000_000L)
        assertThat(right.sourceInUs).isEqualTo(5_000_000L)
        assertThat(right.sourceOutUs).isEqualTo(12_000_000L)

        assertThat(left.timelineEndUs).isEqualTo(right.timelineStartUs)
        assertThat(left.sourceOutUs).isEqualTo(right.sourceInUs)
        assertThat(left.timelineDurationUs + right.timelineDurationUs).isEqualTo(baseClip.timelineDurationUs)
    }

    @Test
    fun split_tooCloseToBoundary_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            ClipTrimHelper.split(baseClip, baseClip.timelineStartUs + 50_000L) // 50ms from start
        }
        assertThrows(IllegalArgumentException::class.java) {
            ClipTrimHelper.split(baseClip, baseClip.timelineEndUs - 50_000L) // 50ms from end
        }
    }

    @Test
    fun trimIn_extendingLeftPastTimelineZero_clampsGracefullyAtTimelineZero() {
        val clipNearStart = baseClip.copy(
            timelineStartUs = 2_000_000L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 10_000_000L,
            sourceOutUs = 15_000_000L
        )

        // Requesting in-point at 0s would attempt a 10s expansion, but only 2s is available before timeline t=0
        val trimmed = ClipTrimHelper.trimIn(clipNearStart, requestedNewInUs = 0L)
        assertThat(trimmed.timelineStartUs).isEqualTo(0L)
        assertThat(trimmed.sourceInUs).isEqualTo(8_000_000L)
        assertThat(trimmed.sourceOutUs).isEqualTo(15_000_000L)
        assertThat(trimmed.timelineDurationUs).isEqualTo(7_000_000L)
        assertThat(trimmed.timelineEndUs).isEqualTo(7_000_000L)
    }

    @Test
    fun split_resetsBoundaryAudioFadesToPreventDips() {
        val clipWithFades = baseClip.copy(
            audioProperties = ClipAudioProperties(
                volume = 1.0f,
                fadeInDurationUs = 1_000_000L,
                fadeOutDurationUs = 2_000_000L
            )
        )

        val (left, right) = ClipTrimHelper.split(clipWithFades, 8_000_000L)

        // Left clip preserves original fadeIn, but clears fadeOut at the split cut
        assertThat(left.audioProperties.fadeInDurationUs).isEqualTo(1_000_000L)
        assertThat(left.audioProperties.fadeOutDurationUs).isEqualTo(0L)

        // Right clip clears fadeIn at the split cut, but preserves original fadeOut
        assertThat(right.audioProperties.fadeInDurationUs).isEqualTo(0L)
        assertThat(right.audioProperties.fadeOutDurationUs).isEqualTo(2_000_000L)
    }
}
