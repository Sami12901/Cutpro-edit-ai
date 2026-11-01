package com.cutpro.core.model

import java.util.UUID

object ClipTrimHelper {

    fun trimIn(
        clip: TimelineClip,
        requestedNewInUs: Long,
        minClipDurationUs: Long = 100_000L
    ): TimelineClip {
        val minSourceDurationUs = (minClipDurationUs * clip.speedMultiplier).toLong()
        val minAllowedInUs = maxOf(0L, clip.sourceInUs - (clip.timelineStartUs * clip.speedMultiplier).toLong())
        val maxAllowedInUs = clip.sourceOutUs - minSourceDurationUs
        val clampedInUs = requestedNewInUs.coerceIn(minAllowedInUs, maxAllowedInUs)

        val deltaSourceUs = clampedInUs - clip.sourceInUs
        val deltaTimelineUs = (deltaSourceUs / clip.speedMultiplier).toLong()

        val newTimelineStartUs = maxOf(0L, clip.timelineStartUs + deltaTimelineUs)
        val newSourceTrimDurationUs = clip.sourceOutUs - clampedInUs
        val newTimelineDurationUs = (newSourceTrimDurationUs / clip.speedMultiplier).toLong()

        return clip.copy(
            sourceInUs = clampedInUs,
            timelineStartUs = newTimelineStartUs,
            timelineDurationUs = newTimelineDurationUs
        )
    }

    fun trimOut(
        clip: TimelineClip,
        requestedNewOutUs: Long,
        minClipDurationUs: Long = 100_000L
    ): TimelineClip {
        val minSourceDurationUs = (minClipDurationUs * clip.speedMultiplier).toLong()
        val clampedOutUs = requestedNewOutUs.coerceIn(
            clip.sourceInUs + minSourceDurationUs,
            clip.mediaSource.durationUs
        )

        val newSourceTrimDurationUs = clampedOutUs - clip.sourceInUs
        val newTimelineDurationUs = (newSourceTrimDurationUs / clip.speedMultiplier).toLong()

        return clip.copy(
            sourceOutUs = clampedOutUs,
            timelineDurationUs = newTimelineDurationUs
        )
    }

    fun split(
        clip: TimelineClip,
        splitPlayheadUs: Long,
        minClipDurationUs: Long = 100_000L
    ): Pair<TimelineClip, TimelineClip> {
        require(splitPlayheadUs >= clip.timelineStartUs + minClipDurationUs) {
            "splitPlayheadUs ($splitPlayheadUs) is too close to clip start (${clip.timelineStartUs})"
        }
        require(splitPlayheadUs <= clip.timelineEndUs - minClipDurationUs) {
            "splitPlayheadUs ($splitPlayheadUs) is too close to clip end (${clip.timelineEndUs})"
        }

        val timelineOffsetUs = splitPlayheadUs - clip.timelineStartUs
        val sourceOffsetUs = (timelineOffsetUs * clip.speedMultiplier).toLong()
        val splitSourceUs = clip.sourceInUs + sourceOffsetUs

        val leftClip = clip.copy(
            id = UUID.randomUUID().toString(),
            sourceOutUs = splitSourceUs,
            timelineDurationUs = timelineOffsetUs,
            audioProperties = clip.audioProperties.copy(fadeOutDurationUs = 0L)
        )

        val rightTimelineDurationUs = clip.timelineDurationUs - timelineOffsetUs
        val rightClip = clip.copy(
            id = UUID.randomUUID().toString(),
            timelineStartUs = splitPlayheadUs,
            timelineDurationUs = rightTimelineDurationUs,
            sourceInUs = splitSourceUs,
            audioProperties = clip.audioProperties.copy(fadeInDurationUs = 0L)
        )

        return Pair(leftClip, rightClip)
    }

    fun adjustSpeed(
        clip: TimelineClip,
        newSpeedMultiplier: Float
    ): TimelineClip {
        require(newSpeedMultiplier in 0.1f..10.0f) { "Speed multiplier must be in [0.1, 10.0]" }
        val newTimelineDurationUs = ((clip.sourceOutUs - clip.sourceInUs) / newSpeedMultiplier).toLong()
        return clip.copy(
            speedMultiplier = newSpeedMultiplier,
            timelineDurationUs = newTimelineDurationUs
        )
    }

    fun reposition(
        clip: TimelineClip,
        newTimelineStartUs: Long
    ): TimelineClip {
        require(newTimelineStartUs >= 0L) { "newTimelineStartUs cannot be negative" }
        return clip.copy(timelineStartUs = newTimelineStartUs)
    }
}
