package com.cutpro.core.ai.gate

import com.cutpro.core.ai.Timeline
import com.cutpro.core.ai.model.ActionDiffSummary
import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.ai.model.PlanDiff

/**
 * Calculates pre-execution diffs and impact summaries for EditPlans.
 */
object PlanDiffCalculator {

    fun calculateDiff(
        plan: EditPlan,
        currentTimeline: Timeline,
        approvedItemIds: Set<String>? = null
    ): PlanDiff {
        val approvedActions = if (approvedItemIds != null) {
            plan.actions.filter { it.id in approvedItemIds }
        } else {
            plan.actions
        }

        val afterTimeline = PlanExecutionEngine.executeActions(currentTimeline, approvedActions)

        val beforeClipCount = currentTimeline.tracks.sumOf { it.clips.size }
        val afterClipCount = afterTimeline.tracks.sumOf { it.clips.size }
        val beforeDurationUs = currentTimeline.durationUs
        val afterDurationUs = afterTimeline.durationUs

        val modifiedTracks = mutableSetOf<String>()
        for (track in currentTimeline.tracks) {
            val afterTrack = afterTimeline.findTrackById(track.id)
            if (afterTrack == null || afterTrack.clips != track.clips || afterTrack.durationUs != track.durationUs) {
                modifiedTracks.add(track.id)
            }
        }

        val actionSummaries = plan.actions.map { action ->
            val isApproved = approvedItemIds?.contains(action.id) ?: true
            val singleActionAfter = if (isApproved) {
                PlanExecutionEngine.executeActions(currentTimeline, listOf(action))
            } else {
                currentTimeline
            }
            ActionDiffSummary(
                actionId = action.id,
                actionType = action::class.simpleName ?: "EditAction",
                description = action.description,
                isApproved = isApproved,
                affectedTrackId = action.trackId,
                startTimeUs = action.startTimeUs,
                endTimeUs = action.endTimeUs,
                durationDeltaUs = singleActionAfter.durationUs - currentTimeline.durationUs
            )
        }

        return PlanDiff(
            planId = plan.id,
            beforeClipCount = beforeClipCount,
            afterClipCount = afterClipCount,
            clipCountDelta = afterClipCount - beforeClipCount,
            beforeDurationUs = beforeDurationUs,
            afterDurationUs = afterDurationUs,
            durationDeltaUs = afterDurationUs - beforeDurationUs,
            modifiedTrackIds = modifiedTracks,
            approvedActionCount = approvedActions.size,
            rejectedActionCount = plan.actions.size - approvedActions.size,
            actionSummaries = actionSummaries
        )
    }
}
