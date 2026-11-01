package com.cutpro.core.playback

import androidx.media3.common.Player
import com.cutpro.core.model.ProjectManifest
import kotlinx.coroutines.flow.StateFlow

interface PlaybackCoordinator {
    val playheadUs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    val durationUs: StateFlow<Long>
    val player: Player? get() = null

    fun play()
    fun pause()
    fun seekTo(targetUs: Long, isScrubbing: Boolean)
    fun setTimeline(project: ProjectManifest)
    fun release()
}
