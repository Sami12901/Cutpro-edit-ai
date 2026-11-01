package com.cutpro.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreviewPlaybackCoordinator(
    context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
    customPlayer: ExoPlayer? = null
) : PlaybackCoordinator {

    override val player: ExoPlayer

    private val _playheadUs = MutableStateFlow(0L)
    override val playheadUs: StateFlow<Long> = _playheadUs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _durationUs = MutableStateFlow(0L)
    override val durationUs: StateFlow<Long> = _durationUs.asStateFlow()

    val mapper = TimelinePositionMapper()
    private val localJob = SupervisorJob()
    private val coordinatorScope = CoroutineScope(coroutineScope.coroutineContext + localJob)
    private val seekDispatcher: AdaptiveSeekDispatcher
    private val syncLoop: PlayheadSyncLoop

    init {
        if (customPlayer != null) {
            player = customPlayer
        } else {
            // Configure low-latency LoadControl optimized for scrubbing
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 1000,
                    /* maxBufferMs = */ 3000,
                    /* bufferForPlaybackMs = */ 250,
                    /* bufferForPlaybackAfterRebufferMs = */ 500
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

            player = ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .build()
        }

        seekDispatcher = AdaptiveSeekDispatcher(player, coordinatorScope, mapper)
        syncLoop = PlayheadSyncLoop(
            player = player,
            mapper = mapper,
            playheadState = _playheadUs,
            isPlayingState = _isPlaying,
            totalDurationUsProvider = { _durationUs.value },
            scope = coordinatorScope
        )

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    syncLoop.start()
                } else {
                    syncLoop.stop()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val target = mapper.timelineToPlayer(_playheadUs.value)
                if (target.clip != null && player.playbackParameters.speed != target.speedMultiplier) {
                    player.setPlaybackSpeed(target.speedMultiplier)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    syncLoop.stop()
                    _playheadUs.value = _durationUs.value
                }
            }
        })
    }

    override fun play() {
        if (_durationUs.value > 0L && _playheadUs.value >= _durationUs.value) {
            seekTo(0L, isScrubbing = false)
        }
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(targetUs: Long, isScrubbing: Boolean) {
        val maxDuration = _durationUs.value
        val clampedUs = if (maxDuration > 0L) targetUs.coerceIn(0L, maxDuration) else targetUs.coerceAtLeast(0L)
        _playheadUs.value = clampedUs
        syncLoop.onScrubbingChanged(isScrubbing)
        seekDispatcher.dispatchSeek(clampedUs, isScrubbing)
    }

    override fun setTimeline(project: ProjectManifest) {
        _durationUs.value = project.durationUs

        val v1Track = project.tracks.firstOrNull { it.type == TrackType.VIDEO }
        val clips = v1Track?.clips ?: emptyList()
        mapper.updateClips(clips)

        val mediaItems = clips.map { clip ->
            MediaItem.Builder()
                .setUri(clip.mediaSource.uriString)
                .setMediaId(clip.id)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(clip.sourceInUs)
                        .setEndPositionUs(clip.sourceOutUs)
                        .setStartsAtKeyFrame(false)
                        .build()
                )
                .build()
        }

        val currentTarget = mapper.timelineToPlayer(_playheadUs.value)
        if (mediaItems.isNotEmpty()) {
            player.setMediaItems(
                mediaItems,
                /* startIndex = */ currentTarget.mediaItemIndex,
                /* startPositionMs = */ currentTarget.positionMs
            )
            player.prepare()
        } else {
            player.clearMediaItems()
        }
    }

    override fun release() {
        seekDispatcher.cancel()
        syncLoop.stop()
        localJob.cancel()
        player.release()
    }
}
