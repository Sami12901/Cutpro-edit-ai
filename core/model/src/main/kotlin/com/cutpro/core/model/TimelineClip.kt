package com.cutpro.core.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.abs

@Serializable
data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val mediaSource: MediaSourceInfo,
    val timelineStartUs: Long,
    val timelineDurationUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val speedMultiplier: Float = 1.0f,
    val fitMode: ClipFitMode = ClipFitMode.FIT,
    val transform: ClipTransform = ClipTransform(),
    val audioProperties: ClipAudioProperties = ClipAudioProperties(),
    val colorAdjustments: ColorAdjustments = ColorAdjustments(),
    val filterType: LutFilterType = LutFilterType.NONE,
    val speedCurve: SpeedCurve? = null,
    val customTitle: String? = null
) {
    val timelineEndUs: Long get() = timelineStartUs + timelineDurationUs
    val sourceTrimDurationUs: Long get() = sourceOutUs - sourceInUs

    init {
        require(id.isNotBlank()) { "Clip id cannot be blank" }
        require(trackId.isNotBlank()) { "trackId cannot be blank" }
        require(timelineStartUs >= 0L) { "timelineStartUs ($timelineStartUs) must be >= 0" }
        require(timelineDurationUs > 0L) { "timelineDurationUs ($timelineDurationUs) must be > 0" }
        require(sourceInUs >= 0L) { "sourceInUs ($sourceInUs) must be >= 0" }
        require(sourceOutUs > sourceInUs) { "sourceOutUs ($sourceOutUs) must be > sourceInUs ($sourceInUs)" }
        require(sourceOutUs <= mediaSource.durationUs) {
            "sourceOutUs ($sourceOutUs) exceeds mediaSource duration (${mediaSource.durationUs})"
        }
        require(speedMultiplier in 0.1f..10.0f) {
            "speedMultiplier ($speedMultiplier) must be in [0.1, 10.0]"
        }

        // Verify speed-to-duration relationship:
        // timelineDurationUs == (sourceOutUs - sourceInUs) / speedMultiplier within 1000us rounding margin
        val expectedTimelineDurationUs = ((sourceOutUs - sourceInUs) / speedMultiplier).toLong()
        require(abs(timelineDurationUs - expectedTimelineDurationUs) <= 1000L) {
            "timelineDurationUs ($timelineDurationUs) diverges from expected ($expectedTimelineDurationUs) for trim duration (${sourceOutUs - sourceInUs}) and speed ($speedMultiplier)"
        }
    }

    fun containsTimelineTimestamp(timestampUs: Long): Boolean {
        return timestampUs in timelineStartUs until timelineEndUs
    }

    fun timelineToSourceTimestamp(timestampUs: Long): Long {
        require(containsTimelineTimestamp(timestampUs)) {
            "Timestamp ($timestampUs) outside clip timeline bounds [$timelineStartUs, $timelineEndUs)"
        }
        val offsetTimelineUs = timestampUs - timelineStartUs
        val offsetSourceUs = (offsetTimelineUs * speedMultiplier).toLong()
        return sourceInUs + offsetSourceUs
    }

    fun sourceToTimelineTimestamp(sourceTimestampUs: Long): Long {
        require(sourceTimestampUs in sourceInUs..sourceOutUs) {
            "Source timestamp ($sourceTimestampUs) outside trim bounds [$sourceInUs, $sourceOutUs]"
        }
        val offsetSourceUs = sourceTimestampUs - sourceInUs
        val offsetTimelineUs = (offsetSourceUs / speedMultiplier).toLong()
        return timelineStartUs + offsetTimelineUs
    }
}
