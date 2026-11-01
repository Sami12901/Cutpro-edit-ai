package com.cutpro.core.ai.analyzer

import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TalkingHeadPacingAnalyzerTest {

    private val dummySource = MediaSourceInfo(
        uriString = "content://media/external/video/talking_head",
        fileName = "talking_head.mp4",
        mimeType = "video/mp4",
        durationUs = 60_000_000L,
        width = 1920,
        height = 1080
    )

    private fun createVideoTrack(trackId: String, durationUs: Long): TimelineTrack {
        val clip = TimelineClip(
            id = "clip-$trackId",
            trackId = trackId,
            mediaSource = dummySource,
            timelineStartUs = 0L,
            timelineDurationUs = durationUs,
            sourceInUs = 0L,
            sourceOutUs = durationUs
        )
        return TimelineTrack(
            id = trackId,
            name = "Video 1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
    }

    @Test
    fun analyzePacing_clipDuration8sOrLess_zeroSuggestions() {
        val track = createVideoTrack("track-1", 7_990_000L) // 7.99 seconds
        val project = ProjectManifest(
            title = "Short Shot Project",
            tracks = listOf(track),
            durationUs = 7_990_000L
        )
        val analyzer = TalkingHeadPacingAnalyzer(
            PacingAnalyzerConfig(maxContiguousDurationUs = 8_000_000L)
        )

        val suggestions = analyzer.analyzePacing(project)

        assertThat(suggestions).isEmpty()
    }

    @Test
    fun analyzePacing_clipDurationAbove8s_generatesPushInZoomAndBroll() {
        val track = createVideoTrack("track-1", 12_000_000L) // 12.0 seconds
        val project = ProjectManifest(
            title = "Long Shot Project",
            tracks = listOf(track),
            durationUs = 12_000_000L
        )
        val analyzer = TalkingHeadPacingAnalyzer(
            PacingAnalyzerConfig(
                maxContiguousDurationUs = 8_000_000L,
                defaultZoomScale = 1.15f
            )
        )

        val suggestions = analyzer.analyzePacing(project)

        assertThat(suggestions).hasSize(2)

        val pushIn = suggestions.first { it.suggestionType == PacingSuggestionType.PUSH_IN_ZOOM }
        assertThat(pushIn.proposedTransform?.scaleX).isEqualTo(1.15f)
        assertThat(pushIn.proposedSplitUs).isEqualTo(6_000_000L) // Midpoint
        assertThat(pushIn.durationUs).isEqualTo(12_000_000L)

        val broll = suggestions.first { it.suggestionType == PacingSuggestionType.B_ROLL_INSERT }
        assertThat(broll.proposedSplitUs).isEqualTo(6_000_000L)
    }

    @Test
    fun analyzePacing_overlayTrackOcclusion_suppressesAlert() {
        // 12s base clip, with an overlay clip between 4.0s and 8.0s
        // Unoccluded segments are: [0s, 4s] (4s duration <= 8s) and [8s, 12s] (4s duration <= 8s)
        val baseTrack = createVideoTrack("track-base", 12_000_000L)

        val overlayClip = TimelineClip(
            id = "clip-overlay-1",
            trackId = "track-overlay",
            mediaSource = dummySource,
            timelineStartUs = 4_000_000L,
            timelineDurationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L
        )
        val overlayTrack = TimelineTrack(
            id = "track-overlay",
            name = "Overlay",
            type = TrackType.OVERLAY,
            zIndex = 1,
            clips = listOf(overlayClip)
        )

        val project = ProjectManifest(
            title = "Occluded Project",
            tracks = listOf(baseTrack, overlayTrack),
            durationUs = 12_000_000L
        )

        val analyzer = TalkingHeadPacingAnalyzer(
            PacingAnalyzerConfig(
                maxContiguousDurationUs = 8_000_000L,
                considerOverlayOcclusion = true
            )
        )

        val suggestions = analyzer.analyzePacing(project)

        assertThat(suggestions).isEmpty()
    }

    @Test
    fun analyzePacing_naturalSpeechPause_snapsSplitPointToPause() {
        val track = createVideoTrack("track-1", 12_000_000L) // 12 seconds
        val project = ProjectManifest(
            title = "Speech Pauses Project",
            tracks = listOf(track),
            durationUs = 12_000_000L
        )

        // Silence at 5.0s to 5.8s (midpoint is 6.0s, silence center is 5.4s)
        val silences = mapOf(
            "clip-track-1" to listOf(
                SilenceInterval(startUs = 5_000_000L, endUs = 5_800_000L, averageDb = -50f, peakDb = -48f)
            )
        )

        val analyzer = TalkingHeadPacingAnalyzer()
        val suggestions = analyzer.analyzePacing(project, silences)

        assertThat(suggestions).isNotEmpty()
        val pushIn = suggestions.first { it.suggestionType == PacingSuggestionType.PUSH_IN_ZOOM }
        assertThat(pushIn.proposedSplitUs).isEqualTo(5_400_000L) // Center of speech pause
    }
}
