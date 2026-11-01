package com.cutpro.core.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveSeekDispatcherTest {

    @Test
    fun seekRelease_executesExactSeekImmediately() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        val mapper = TimelinePositionMapper()
        val dispatcher = AdaptiveSeekDispatcher(player, this, mapper)

        dispatcher.dispatchSeek(targetTimelineUs = 2_000_000L, isScrubbing = false)

        verify(player).setSeekParameters(SeekParameters.EXACT)
        verify(player).seekTo(0, 2000L)
        dispatcher.cancel()
    }

    @Test
    fun seekScrubbing_executesClosestSyncSeek() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        val mapper = TimelinePositionMapper()
        val dispatcher = AdaptiveSeekDispatcher(player, this, mapper)
        runCurrent()

        dispatcher.dispatchSeek(targetTimelineUs = 4_000_000L, isScrubbing = true)
        runCurrent()

        verify(player).setSeekParameters(SeekParameters.CLOSEST_SYNC)
        verify(player).seekTo(0, 4000L)
        dispatcher.cancel()
    }
}
