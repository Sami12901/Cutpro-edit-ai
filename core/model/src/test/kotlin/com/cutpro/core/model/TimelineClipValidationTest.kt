package com.cutpro.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TimelineClipValidationTest {

    private val validMedia = MediaSourceInfo(
        uriString = "content://media/1",
        fileName = "video.mp4",
        mimeType = "video/mp4",
        durationUs = 10_000_000L
    )

    @Test
    fun validClip_initializesSuccessfully() {
        val clip = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaSource = validMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L
        )
        assertThat(clip.timelineEndUs).isEqualTo(5_000_000L)
        assertThat(clip.sourceTrimDurationUs).isEqualTo(5_000_000L)
    }

    @Test
    fun clip_negativeTimelineStart_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineClip(
                trackId = "t1",
                mediaSource = validMedia,
                timelineStartUs = -1L,
                timelineDurationUs = 5_000_000L,
                sourceInUs = 0L,
                sourceOutUs = 5_000_000L
            )
        }
    }

    @Test
    fun clip_sourceInGreaterThanSourceOut_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineClip(
                trackId = "t1",
                mediaSource = validMedia,
                timelineStartUs = 0L,
                timelineDurationUs = 5_000_000L,
                sourceInUs = 6_000_000L,
                sourceOutUs = 5_000_000L
            )
        }
    }

    @Test
    fun clip_sourceOutExceedsMediaDuration_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineClip(
                trackId = "t1",
                mediaSource = validMedia,
                timelineStartUs = 0L,
                timelineDurationUs = 15_000_000L,
                sourceInUs = 0L,
                sourceOutUs = 15_000_000L
            )
        }
    }

    @Test
    fun clip_speedDurationMismatch_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            // Speed 2.0x on 5s trim should yield ~2.5s timelineDurationUs, not 5s
            TimelineClip(
                trackId = "t1",
                mediaSource = validMedia,
                timelineStartUs = 0L,
                timelineDurationUs = 5_000_000L,
                sourceInUs = 0L,
                sourceOutUs = 5_000_000L,
                speedMultiplier = 2.0f
            )
        }
    }

    @Test
    fun clip_timelineToSourceTimestamp_calculatesAccurately() {
        val clip = TimelineClip(
            trackId = "t1",
            mediaSource = validMedia,
            timelineStartUs = 2_000_000L,
            timelineDurationUs = 2_500_000L,
            sourceInUs = 1_000_000L,
            sourceOutUs = 6_000_000L,
            speedMultiplier = 2.0f
        )
        // At 3s on timeline (1s into clip), source offset is 1s * 2.0 = 2s, source = 1s + 2s = 3s
        val sourcePts = clip.timelineToSourceTimestamp(3_000_000L)
        assertThat(sourcePts).isEqualTo(3_000_000L)
    }

    @Test
    fun clipAudioProperties_calculatesEffectiveFadesProportionally() {
        val audioProps = ClipAudioProperties(
            fadeInDurationUs = 3_000_000L,
            fadeOutDurationUs = 3_000_000L
        )
        // Clip duration is 4s (less than 6s total fade)
        val (effIn, effOut) = audioProps.calculateEffectiveFades(clipDurationUs = 4_000_000L)
        assertThat(effIn + effOut).isEqualTo(4_000_000L)
        assertThat(effIn).isEqualTo(2_000_000L)
        assertThat(effOut).isEqualTo(2_000_000L)
    }

    @Test
    fun clipAudioProperties_panOutOfBounds_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            ClipAudioProperties(pan = -1.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ClipAudioProperties(pan = 1.1f)
        }
    }

    @Test
    fun duckingConfig_invalidDepth_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            DuckingConfig(duckingDepthDb = 0.5f)
        }
    }

    @Test
    fun duckingConfig_defaults_areValid() {
        val config = DuckingConfig()
        assertThat(config.duckingDepthDb).isEqualTo(-12.0f)
        assertThat(config.attackTimeMs).isEqualTo(150)
        assertThat(config.holdTimeMs).isEqualTo(300)
        assertThat(config.releaseTimeMs).isEqualTo(400)
    }
}
