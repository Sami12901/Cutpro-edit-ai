package com.cutpro.core.playback

import com.cutpro.core.model.TimelineClip

data class PlayerTarget(
    val mediaItemIndex: Int,
    val positionMs: Long,
    val clip: TimelineClip?,
    val isGap: Boolean,
    val speedMultiplier: Float
)

class TimelinePositionMapper {

    private var activeClips: List<TimelineClip> = emptyList()

    fun updateClips(clips: List<TimelineClip>) {
        this.activeClips = clips.sortedBy { it.timelineStartUs }
    }

    /**
     * Maps CutPro timeline microsecond timestamp to ExoPlayer (mediaItemIndex, positionMs).
     */
    fun timelineToPlayer(timelineUs: Long): PlayerTarget {
        if (activeClips.isEmpty()) {
            return PlayerTarget(
                mediaItemIndex = 0,
                positionMs = (timelineUs / 1000L).coerceAtLeast(0L),
                clip = null,
                isGap = true,
                speedMultiplier = 1.0f
            )
        }

        // Before first clip
        val firstClip = activeClips.first()
        if (timelineUs < firstClip.timelineStartUs) {
            return PlayerTarget(
                mediaItemIndex = 0,
                positionMs = 0L,
                clip = null,
                isGap = true,
                speedMultiplier = 1.0f
            )
        }

        // Search for active clip or gap
        for (index in activeClips.indices) {
            val clip = activeClips[index]
            if (timelineUs in clip.timelineStartUs until clip.timelineEndUs) {
                val offsetTimelineUs = timelineUs - clip.timelineStartUs
                val offsetSourceUs = (offsetTimelineUs * clip.speedMultiplier).toLong()
                val windowPositionMs = offsetSourceUs / 1000L
                return PlayerTarget(
                    mediaItemIndex = index,
                    positionMs = windowPositionMs.coerceAtLeast(0L),
                    clip = clip,
                    isGap = false,
                    speedMultiplier = clip.speedMultiplier
                )
            }

            if (index < activeClips.size - 1) {
                val nextClip = activeClips[index + 1]
                if (timelineUs in clip.timelineEndUs until nextClip.timelineStartUs) {
                    return PlayerTarget(
                        mediaItemIndex = index + 1,
                        positionMs = 0L,
                        clip = null,
                        isGap = true,
                        speedMultiplier = 1.0f
                    )
                }
            }
        }

        // Beyond last clip
        val lastIndex = activeClips.size - 1
        val lastClip = activeClips[lastIndex]
        val maxWindowMs = lastClip.sourceTrimDurationUs / 1000L
        return PlayerTarget(
            mediaItemIndex = lastIndex,
            positionMs = maxWindowMs,
            clip = lastClip,
            isGap = false,
            speedMultiplier = lastClip.speedMultiplier
        )
    }

    /**
     * Maps ExoPlayer (mediaItemIndex, positionMs) back to CutPro timeline microsecond timestamp.
     */
    fun playerToTimeline(mediaItemIndex: Int, positionMs: Long): Long {
        if (activeClips.isEmpty() || mediaItemIndex !in activeClips.indices) {
            return (positionMs * 1000L).coerceAtLeast(0L)
        }

        val clip = activeClips[mediaItemIndex]
        val offsetSourceUs = positionMs * 1000L
        val offsetTimelineUs = if (clip.speedMultiplier > 0f) {
            (offsetSourceUs / clip.speedMultiplier).toLong()
        } else {
            offsetSourceUs
        }
        val timelineUs = clip.timelineStartUs + offsetTimelineUs

        return timelineUs.coerceIn(clip.timelineStartUs, clip.timelineEndUs)
    }
}
