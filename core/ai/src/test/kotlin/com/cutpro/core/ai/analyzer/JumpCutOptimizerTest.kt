package com.cutpro.core.ai.analyzer

import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.TimelineClip
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JumpCutOptimizerTest {

    private val dummySource = MediaSourceInfo(
        uriString = "content://media/external/video/1",
        fileName = "video1.mp4",
        mimeType = "video/mp4",
        durationUs = 60_000_000L,
        width = 1920,
        height = 1080
    )

    private fun createTestClip(
        timelineStartUs: Long = 0L,
        sourceInUs: Long = 0L,
        sourceOutUs: Long = 10_000_000L,
        speedMultiplier: Float = 1.0f
    ): TimelineClip {
        val durationUs = ((sourceOutUs - sourceInUs) / speedMultiplier).toLong()
        return TimelineClip(
            id = "clip-1",
            trackId = "track-video",
            mediaSource = dummySource,
            timelineStartUs = timelineStartUs,
            timelineDurationUs = durationUs,
            sourceInUs = sourceInUs,
            sourceOutUs = sourceOutUs,
            speedMultiplier = speedMultiplier
        )
    }

    @Test
    fun optimizeJumpCuts_preservesPreAndPostRollBuffers() {
        val optimizer = JumpCutOptimizer(
            JumpCutConfig(
                postSpeechSafetyBufferUs = 80_000L,
                preSpeechSafetyBufferUs = 80_000L,
                minCutDurationUs = 200_000L
            )
        )
        val clip = createTestClip(sourceInUs = 0L, sourceOutUs = 10_000_000L)
        val rawSilences = listOf(
            SilenceInterval(startUs = 2_000_000L, endUs = 3_000_000L, averageDb = -45f, peakDb = -42f)
        )

        val suggestions = optimizer.optimizeSilencesForClip(clip, rawSilences)

        assertThat(suggestions).hasSize(1)
        val suggestion = suggestions.first()
        // Cut start = 2_000_000 + 80_000 = 2_080_000
        // Cut end = 3_000_000 - 80_000 = 2_920_000
        // Excised = 840_000 (840ms)
        assertThat(suggestion.cutStartTimelineUs).isEqualTo(2_080_000L)
        assertThat(suggestion.cutEndTimelineUs).isEqualTo(2_920_000L)
        assertThat(suggestion.excisedDurationUs).isEqualTo(840_000L)
        assertThat(suggestion.postSpeechBufferUs).isEqualTo(80_000L)
        assertThat(suggestion.preSpeechBufferUs).isEqualTo(80_000L)
    }

    @Test
    fun optimizeJumpCuts_gapBelow200msAfterBuffering_rejected() {
        val optimizer = JumpCutOptimizer(
            JumpCutConfig(
                postSpeechSafetyBufferUs = 80_000L,
                preSpeechSafetyBufferUs = 80_000L,
                minCutDurationUs = 200_000L
            )
        )
        val clip = createTestClip(sourceInUs = 0L, sourceOutUs = 10_000_000L)
        // 300ms silence with 2x80ms buffers leaves 140ms < 200ms
        val rawSilences = listOf(
            SilenceInterval(startUs = 2_000_000L, endUs = 2_300_000L, averageDb = -45f, peakDb = -42f)
        )

        val suggestions = optimizer.optimizeSilencesForClip(clip, rawSilences)

        assertThat(suggestions).isEmpty()
    }

    @Test
    fun optimizeJumpCuts_leadingAndTrailingSilences_handledCorrectly() {
        val optimizer = JumpCutOptimizer(
            JumpCutConfig(
                postSpeechSafetyBufferUs = 80_000L,
                preSpeechSafetyBufferUs = 80_000L,
                minCutDurationUs = 200_000L
            )
        )
        val clip = createTestClip(sourceInUs = 0L, sourceOutUs = 5_000_000L)
        val rawSilences = listOf(
            // Leading silence (starts at 0)
            SilenceInterval(startUs = 0L, endUs = 1_000_000L, averageDb = -50f, peakDb = -48f),
            // Trailing silence (ends at clip end)
            SilenceInterval(startUs = 4_000_000L, endUs = 5_000_000L, averageDb = -50f, peakDb = -48f)
        )

        val suggestions = optimizer.optimizeSilencesForClip(clip, rawSilences)

        assertThat(suggestions).hasSize(2)

        val leadingCut = suggestions[0]
        assertThat(leadingCut.cutStartTimelineUs).isEqualTo(0L)
        assertThat(leadingCut.cutEndTimelineUs).isEqualTo(920_000L) // 1_000_000 - 80_000
        assertThat(leadingCut.postSpeechBufferUs).isEqualTo(0L)
        assertThat(leadingCut.preSpeechBufferUs).isEqualTo(80_000L)

        val trailingCut = suggestions[1]
        assertThat(trailingCut.cutStartTimelineUs).isEqualTo(4_080_000L) // 4_000_000 + 80_000
        assertThat(trailingCut.cutEndTimelineUs).isEqualTo(5_000_000L)
        assertThat(trailingCut.postSpeechBufferUs).isEqualTo(80_000L)
        assertThat(trailingCut.preSpeechBufferUs).isEqualTo(0L)
    }

    @Test
    fun optimizeJumpCuts_speedMultiplierScaling() {
        val optimizer = JumpCutOptimizer()
        // Speed multiplier 2.0x, source duration 10s, timeline duration 5s
        val clip = createTestClip(
            timelineStartUs = 1_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            speedMultiplier = 2.0f
        )
        val rawSilences = listOf(
            SilenceInterval(startUs = 2_000_000L, endUs = 4_000_000L, averageDb = -50f, peakDb = -48f)
        )

        val suggestions = optimizer.optimizeSilencesForClip(clip, rawSilences)

        assertThat(suggestions).hasSize(1)
        val suggestion = suggestions.first()

        // Source cut: 2_080_000L to 3_920_000L (delta = 1_840_000L)
        assertThat(suggestion.cutStartSourceUs).isEqualTo(2_080_000L)
        assertThat(suggestion.cutEndSourceUs).isEqualTo(3_920_000L)

        // Timeline cut scaled by 2.0x:
        // Start: 1_000_000 + 2_080_000 / 2 = 2_040_000L
        // End: 1_000_000 + 3_920_000 / 2 = 2_960_000L
        // Timeline duration: 920_000L
        assertThat(suggestion.cutStartTimelineUs).isEqualTo(2_040_000L)
        assertThat(suggestion.cutEndTimelineUs).isEqualTo(2_960_000L)
        assertThat(suggestion.excisedDurationUs).isEqualTo(920_000L)
    }
}
