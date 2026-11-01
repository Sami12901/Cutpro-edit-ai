package com.cutpro.core.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PreviewPlaybackCoordinatorTest {

    @Test
    fun transportControls_interactWithPlayerCorrectly() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)

        val context = mock(android.content.Context::class.java)
        val coordinator = PreviewPlaybackCoordinator(
            context = context,
            coroutineScope = this,
            customPlayer = player
        )

        coordinator.play()
        verify(player).play()

        coordinator.pause()
        verify(player).pause()

        coordinator.seekTo(targetUs = 3_000_000L, isScrubbing = false)
        assertThat(coordinator.playheadUs.value).isEqualTo(3_000_000L)
        coordinator.release()
    }

    @Test
    fun setTimeline_updatesDurationAndClips() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)

        val context = mock(android.content.Context::class.java)
        val coordinator = PreviewPlaybackCoordinator(
            context = context,
            coroutineScope = this,
            customPlayer = player
        )

        val sampleMedia = MediaSourceInfo(
            uriString = "content://media/clip1.mp4",
            fileName = "clip1.mp4",
            mimeType = "video/mp4",
            durationUs = 10_000_000L
        )
        val clip = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaSource = sampleMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L
        )
        val track = TimelineTrack(
            id = "t1",
            name = "Video Track",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
        val manifest = ProjectManifest(
            id = "p1",
            title = "Test Project",
            durationUs = 5_000_000L,
            tracks = listOf(track)
        )

        coordinator.setTimeline(manifest)

        assertThat(coordinator.durationUs.value).isEqualTo(5_000_000L)
        assertThat(coordinator.mapper.playerToTimeline(0, 0L)).isEqualTo(0L)
        coordinator.release()
    }
}
