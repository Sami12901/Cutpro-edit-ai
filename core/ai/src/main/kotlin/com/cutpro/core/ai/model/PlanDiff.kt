package com.cutpro.core.ai.model

data class ActionDiffSummary(
    val actionId: String,
    val actionType: String,
    val description: String,
    val isApproved: Boolean,
    val affectedTrackId: String,
    val startTimeUs: Long,
    val endTimeUs: Long,
    val durationDeltaUs: Long
)

data class PlanDiff(
    val planId: String,
    val beforeClipCount: Int,
    val afterClipCount: Int,
    val clipCountDelta: Int,
    val beforeDurationUs: Long,
    val afterDurationUs: Long,
    val durationDeltaUs: Long,
    val modifiedTrackIds: Set<String>,
    val approvedActionCount: Int,
    val rejectedActionCount: Int,
    val actionSummaries: List<ActionDiffSummary> = emptyList()
) {
    val totalTimeSavingsUs: Long get() = if (durationDeltaUs < 0) -durationDeltaUs else 0L
}
