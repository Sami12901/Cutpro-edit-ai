package com.cutpro.core.playback

import androidx.media3.exoplayer.ExoPlayer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class PlayheadSyncLoopTest {

    @Test
    fun syncLoop_updatesPlayheadWhilePlaying() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.isPlaying).thenReturn(true)
        `when`(player.currentMediaItemIndex).thenReturn(0)
        `when`(player.currentPosition).thenReturn(1500L)

        val mapper = TimelinePositionMapper()
        val playheadState = MutableStateFlow(0L)
        val isPlayingState = MutableStateFlow(true)

        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val syncLoop = PlayheadSyncLoop(
            player = player,
            mapper = mapper,
            playheadState = playheadState,
            isPlayingState = isPlayingState,
            totalDurationUsProvider = { 10_000_000L },
            scope = this,
            dispatcher = testDispatcher
        )

        syncLoop.start()
        advanceTimeBy(32)
        runCurrent()

        // 1500ms -> 1_500_000 us
        assertThat(playheadState.value).isEqualTo(1_500_000L)
        syncLoop.stop()
    }

    @Test
    fun syncLoop_reachesDuration_pausesPlayer() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.isPlaying).thenReturn(true)
        `when`(player.currentMediaItemIndex).thenReturn(0)
        `when`(player.currentPosition).thenReturn(10_000L) // 10s = duration

        val mapper = TimelinePositionMapper()
        val playheadState = MutableStateFlow(0L)
        val isPlayingState = MutableStateFlow(true)

        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val syncLoop = PlayheadSyncLoop(
            player = player,
            mapper = mapper,
            playheadState = playheadState,
            isPlayingState = isPlayingState,
            totalDurationUsProvider = { 10_000_000L },
            scope = this,
            dispatcher = testDispatcher
        )

        syncLoop.start()
        advanceTimeBy(32)
        runCurrent()

        verify(player, org.mockito.Mockito.atLeastOnce()).pause()
        assertThat(playheadState.value).isEqualTo(10_000_000L)
        assertThat(isPlayingState.value).isFalse()
        syncLoop.stop()
    }
}
