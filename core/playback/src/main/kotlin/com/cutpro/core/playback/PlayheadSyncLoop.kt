package com.cutpro.core.playback

import android.view.Choreographer
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayheadSyncLoop(
    private val player: ExoPlayer,
    private val mapper: TimelinePositionMapper,
    private val playheadState: MutableStateFlow<Long>,
    private val isPlayingState: MutableStateFlow<Boolean>,
    private val totalDurationUsProvider: () -> Long,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher? = null
) {
    private var syncJob: Job? = null
    private var isScrubbing: Boolean = false

    fun onScrubbingChanged(scrubbing: Boolean) {
        isScrubbing = scrubbing
    }

    fun start() {
        if (syncJob?.isActive == true) return
        val targetDispatcher = dispatcher ?: runCatching { Dispatchers.Main.immediate }.getOrDefault(Dispatchers.Default)
        syncJob = scope.launch(targetDispatcher) {
            val choreographer = runCatching { Choreographer.getInstance() }.getOrNull()

            if (choreographer != null) {
                val frameCallback = object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (!isActive) return

                        syncFrame()

                        if (player.isPlaying) {
                            choreographer.postFrameCallback(this)
                        }
                    }
                }
                choreographer.postFrameCallback(frameCallback)
            } else {
                // Headless / unit-test fallback loop (60fps ~ 16ms cadence)
                while (isActive && player.isPlaying) {
                    syncFrame()
                    delay(16)
                }
            }
        }
    }

    private fun syncFrame() {
        if (player.isPlaying && !isScrubbing) {
            val currentItem = player.currentMediaItemIndex
            val currentPosMs = player.currentPosition
            val timelineUs = mapper.playerToTimeline(currentItem, currentPosMs)
            val totalDuration = totalDurationUsProvider()

            if (totalDuration > 0L && timelineUs >= totalDuration) {
                player.pause()
                playheadState.value = totalDuration
                isPlayingState.value = false
            } else {
                playheadState.value = timelineUs
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }
}
