package com.cutpro.core.playback

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveSeekDispatcher(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val mapper: TimelinePositionMapper
) {
    private val seekLock = Any()
    private val scrubbingEpoch = AtomicLong(0L)

    // Conflated flow keeps only the latest seek target during high-frequency dragging
    private val seekRequests = MutableSharedFlow<SeekCommand>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    data class SeekCommand(
        val targetTimelineUs: Long,
        val isScrubbing: Boolean = true,
        val epoch: Long = 0L
    )

    val seekJob = scope.launch {
        seekRequests.collectLatest { command ->
            if (command.epoch == scrubbingEpoch.get()) {
                synchronized(seekLock) {
                    if (command.epoch == scrubbingEpoch.get()) {
                        executeSeek(command.targetTimelineUs, command.isScrubbing)
                    }
                }
            }
        }
    }

    fun cancel() {
        seekJob.cancel()
    }

    fun dispatchSeek(targetTimelineUs: Long, isScrubbing: Boolean) {
        if (!isScrubbing) {
            // Touch release: invalidate pending scrubbing seeks, reset buffer,
            // and execute EXACT seek synchronously on the caller thread.
            scrubbingEpoch.incrementAndGet()
            seekRequests.resetReplayCache()
            synchronized(seekLock) {
                executeSeek(targetTimelineUs, isScrubbing = false)
            }
        } else {
            // Dragging: queue into conflated flow tagged with current epoch
            seekRequests.tryEmit(
                SeekCommand(
                    targetTimelineUs = targetTimelineUs,
                    isScrubbing = true,
                    epoch = scrubbingEpoch.get()
                )
            )
        }
    }

    private fun executeSeek(targetTimelineUs: Long, isScrubbing: Boolean) {
        val target = mapper.timelineToPlayer(targetTimelineUs)

        // Select seek parameter based on interaction state
        val seekParams = if (isScrubbing) {
            SeekParameters.CLOSEST_SYNC // Fluid keyframe jump (<15ms)
        } else {
            SeekParameters.EXACT // Frame-accurate render on release
        }

        player.setSeekParameters(seekParams)

        if (target.clip != null && player.playbackParameters.speed != target.speedMultiplier) {
            player.setPlaybackSpeed(target.speedMultiplier)
        }

        player.seekTo(target.mediaItemIndex, target.positionMs)
    }
}
