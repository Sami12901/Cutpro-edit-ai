package com.cutpro.core.playback

import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.TimelineClip
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimelinePositionMapperTest {

    private val sampleMedia = MediaSourceInfo(
        uriString = "content://media/video1.mp4",
        fileName = "video1.mp4",
        mimeType = "video/mp4",
        durationUs = 10_000_000L
    )

    @Test
    fun emptyClips_returnsZeroWindowFallback() {
        val mapper = TimelinePositionMapper()
        val target = mapper.timelineToPlayer(5_000_000L)

        assertThat(target.mediaItemIndex).isEqualTo(0)
        assertThat(target.positionMs).isEqualTo(5000L)
        assertThat(target.isGap).isTrue()

        val timelineUs = mapper.playerToTimeline(0, 5000L)
        assertThat(timelineUs).isEqualTo(5_000_000L)
    }

    @Test
    fun singleClip_standardSpeed_bidirectionalMapping() {
        val mapper = TimelinePositionMapper()
        val clip = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 2_000_000L,
            timelineDurationUs = 4_000_000L,
            sourceInUs = 1_000_000L,
            sourceOutUs = 5_000_000L,
            speedMultiplier = 1.0f
        )
        mapper.updateClips(listOf(clip))

        // Query at 3.5s timeline (1.5s into clip)
        val target = mapper.timelineToPlayer(3_500_000L)
        assertThat(target.mediaItemIndex).isEqualTo(0)
        assertThat(target.isGap).isFalse()
        assertThat(target.positionMs).isEqualTo(1500L)

        // Reverse map 1500ms window position back to timeline
        val mappedTimelineUs = mapper.playerToTimeline(0, 1500L)
        assertThat(mappedTimelineUs).isEqualTo(3_500_000L)
    }

    @Test
    fun clipWithDoubleSpeed_mapsCorrectly() {
        val mapper = TimelinePositionMapper()
        // 2x speed: 2s timeline duration consumes 4s source media
        val clip = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
            speedMultiplier = 2.0f
        )
        mapper.updateClips(listOf(clip))

        // At 1s on timeline, source offset is 1s * 2.0 = 2s (2000ms window position)
        val target = mapper.timelineToPlayer(1_000_000L)
        assertThat(target.positionMs).isEqualTo(2000L)

        // Reverse map 2000ms window position back to timeline
        val timelineUs = mapper.playerToTimeline(0, 2000L)
        assertThat(timelineUs).isEqualTo(1_000_000L)
    }

    @Test
    fun gapBetweenClips_identifiedProperly() {
        val mapper = TimelinePositionMapper()
        val clip1 = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L
        )
        val clip2 = TimelineClip(
            id = "c2",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 4_000_000L, // 2s gap from 2s to 4s
            timelineDurationUs = 3_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 3_000_000L
        )
        mapper.updateClips(listOf(clip1, clip2))

        // Query inside gap at 3.0s
        val target = mapper.timelineToPlayer(3_000_000L)
        assertThat(target.isGap).isTrue()
        assertThat(target.mediaItemIndex).isEqualTo(1)
        assertThat(target.positionMs).isEqualTo(0L)
    }
}
