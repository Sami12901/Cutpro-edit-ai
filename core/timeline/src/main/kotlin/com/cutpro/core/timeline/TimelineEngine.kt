package com.cutpro.core.timeline

import com.cutpro.core.model.ClipTrimHelper
import com.cutpro.core.model.TimelineClip

/**
 * Pure Kotlin timeline engine contract for non-destructive editing operations.
 */
interface TimelineEngine {
    fun trimIn(clip: TimelineClip, newInUs: Long, minClipDurationUs: Long = 100_000L): TimelineClip
    fun trimOut(clip: TimelineClip, newOutUs: Long, minClipDurationUs: Long = 100_000L): TimelineClip
    fun splitAt(clip: TimelineClip, splitPlayheadUs: Long, minClipDurationUs: Long = 100_000L): Pair<TimelineClip, TimelineClip>
    fun adjustSpeed(clip: TimelineClip, speedMultiplier: Float): TimelineClip
    fun reposition(clip: TimelineClip, newTimelineStartUs: Long): TimelineClip
    fun findSnapPoint(
        movingEdgeUs: Long,
        candidatePointsUs: List<Long>,
        pixelsPerSec: Float,
        densityDpi: Float,
        thresholdDp: Float = 16f
    ): Long?
}

/**
 * Standard production implementation of TimelineEngine delegating to ClipTrimHelper and SnappingEngine.
 */
class StandardTimelineEngine(
    private val snappingEngine: SnappingEngine = SnappingEngine()
) : TimelineEngine {

    override fun trimIn(clip: TimelineClip, newInUs: Long, minClipDurationUs: Long): TimelineClip =
        ClipTrimHelper.trimIn(clip, newInUs, minClipDurationUs)

    override fun trimOut(clip: TimelineClip, newOutUs: Long, minClipDurationUs: Long): TimelineClip =
        ClipTrimHelper.trimOut(clip, newOutUs, minClipDurationUs)

    override fun splitAt(clip: TimelineClip, splitPlayheadUs: Long, minClipDurationUs: Long): Pair<TimelineClip, TimelineClip> =
        ClipTrimHelper.split(clip, splitPlayheadUs, minClipDurationUs)

    override fun adjustSpeed(clip: TimelineClip, speedMultiplier: Float): TimelineClip =
        ClipTrimHelper.adjustSpeed(clip, speedMultiplier)

    override fun reposition(clip: TimelineClip, newTimelineStartUs: Long): TimelineClip =
        ClipTrimHelper.reposition(clip, newTimelineStartUs)

    override fun findSnapPoint(
        movingEdgeUs: Long,
        candidatePointsUs: List<Long>,
        pixelsPerSec: Float,
        densityDpi: Float,
        thresholdDp: Float
    ): Long? = snappingEngine.findSnapPoint(
        movingEdgeUs = movingEdgeUs,
        candidatePointsUs = candidatePointsUs,
        pixelsPerSecond = pixelsPerSec,
        densityDpi = densityDpi,
        snapThresholdDp = thresholdDp
    )
}
