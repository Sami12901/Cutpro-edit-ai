package com.cutpro.core.ai.gate

import com.cutpro.core.ai.model.AdjustSpeedAction
import com.cutpro.core.ai.model.CutSegmentAction
import com.cutpro.core.ai.model.DeleteSilenceAction
import com.cutpro.core.ai.model.EditAction
import com.cutpro.core.ai.model.MuteNoiseAction
import com.cutpro.core.ai.model.PushInZoomAction
import com.cutpro.core.ai.model.TrimClipAction
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.ClipTrimHelper
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.timeline.TimelineOperations
import java.util.UUID

/**
 * Executes EditActions atomically on a ProjectManifest with reverse-chronological (back-to-front)
 * ripple processing to preserve non-overlapping track invariants and prevent cascading coordinate drift.
 */
object PlanExecutionEngine {

    fun executeActions(
        timeline: ProjectManifest,
        actions: List<EditAction>
    ): ProjectManifest {
        if (actions.isEmpty()) return timeline

        var currentProject = timeline
        val actionsByTrack = actions.groupBy { it.trackId }

        for ((trackId, trackActions) in actionsByTrack) {
            val track = currentProject.findTrackById(trackId) ?: continue
            val updatedTrack = applyTrackActions(track, trackActions)
            currentProject = currentProject.withUpdatedTrack(updatedTrack)
        }

        val recalculatedDuration = TimelineOperations.recalculateDurationUs(currentProject)
        return currentProject.copy(
            durationUs = recalculatedDuration,
            modifiedAt = System.currentTimeMillis()
        )
    }

    private fun applyTrackActions(
        track: TimelineTrack,
        actions: List<EditAction>
    ): TimelineTrack {
        var currentClips = track.clips

        // 1. Process cuts and silences in reverse chronological order (back-to-front)
        val cutActions = actions.filterIsInstance<CutSegmentAction>() +
            actions.filterIsInstance<DeleteSilenceAction>().map { silence ->
                CutSegmentAction(
                    id = silence.id,
                    description = silence.description,
                    trackId = silence.trackId,
                    clipId = silence.clipId,
                    startTimeUs = silence.startTimeUs,
                    endTimeUs = silence.endTimeUs,
                    ripple = silence.ripple
                )
            }

        val sortedCuts = cutActions.sortedByDescending { it.startTimeUs }
        for (cut in sortedCuts) {
            currentClips = applySingleCut(currentClips, cut)
        }

        // 2. Process push-in zooms (splits clip and applies scale)
        val pushInActions = actions.filterIsInstance<PushInZoomAction>().sortedByDescending { it.splitPointUs }
        for (pushIn in pushInActions) {
            currentClips = applyPushInZoom(currentClips, pushIn)
        }

        // 3. Apply speed adjustments
        val speedActions = actions.filterIsInstance<AdjustSpeedAction>()
        for (speedAction in speedActions) {
            currentClips = currentClips.map { clip ->
                if (clip.id == speedAction.clipId) {
                    ClipTrimHelper.adjustSpeed(clip, speedAction.targetSpeedMultiplier)
                } else {
                    clip
                }
            }
        }

        // 4. Apply mutes
        val muteActions = actions.filterIsInstance<MuteNoiseAction>()
        for (muteAction in muteActions) {
            currentClips = currentClips.map { clip ->
                if (clip.id == muteAction.clipId ||
                    (clip.timelineStartUs <= muteAction.startTimeUs && clip.timelineEndUs >= muteAction.endTimeUs)
                ) {
                    val updatedAudio = clip.audioProperties.copy(volume = muteAction.targetVolume)
                    clip.copy(audioProperties = updatedAudio)
                } else {
                    clip
                }
            }
        }

        // 5. Apply direct trim clip actions
        val trimActions = actions.filterIsInstance<TrimClipAction>()
        for (trim in trimActions) {
            currentClips = currentClips.map { clip ->
                if (clip.id == trim.clipId) {
                    val newIn = trim.newSourceInUs ?: clip.sourceInUs
                    val newOut = trim.newSourceOutUs ?: clip.sourceOutUs
                    val newDurationUs = ((newOut - newIn) / clip.speedMultiplier).toLong()
                    clip.copy(
                        sourceInUs = newIn,
                        sourceOutUs = newOut,
                        timelineDurationUs = newDurationUs
                    )
                } else {
                    clip
                }
            }
        }

        return track.copy(clips = currentClips.sortedBy { it.timelineStartUs })
    }

    private fun applySingleCut(
        clips: List<TimelineClip>,
        cut: CutSegmentAction
    ): List<TimelineClip> {
        val cutStart = cut.startTimeUs
        val cutEnd = cut.endTimeUs
        if (cutEnd <= cutStart) return clips

        val cutDuration = cutEnd - cutStart
        val resultClips = mutableListOf<TimelineClip>()

        for (clip in clips) {
            when {
                // Entirely before the cut
                clip.timelineEndUs <= cutStart -> {
                    resultClips.add(clip)
                }
                // Entirely after the cut
                clip.timelineStartUs >= cutEnd -> {
                    if (cut.ripple) {
                        val shifted = ClipTrimHelper.reposition(clip, clip.timelineStartUs - cutDuration)
                        resultClips.add(shifted)
                    } else {
                        resultClips.add(clip)
                    }
                }
                // Cut is strictly inside the clip
                cutStart > clip.timelineStartUs && cutEnd < clip.timelineEndUs -> {
                    val leftDurationUs = cutStart - clip.timelineStartUs
                    val leftSourceTrimUs = (leftDurationUs * clip.speedMultiplier).toLong()
                    val leftClip = clip.copy(
                        id = UUID.randomUUID().toString(),
                        timelineDurationUs = leftDurationUs,
                        sourceOutUs = clip.sourceInUs + leftSourceTrimUs
                    )
                    resultClips.add(leftClip)

                    val rightSourceTrimStart = (cutEnd - clip.timelineStartUs) * clip.speedMultiplier
                    val rightSourceInUs = clip.sourceInUs + rightSourceTrimStart.toLong()
                    val rightDurationUs = clip.timelineEndUs - cutEnd
                    val rightTimelineStartUs = if (cut.ripple) cutStart else cutEnd
                    val rightClip = clip.copy(
                        id = UUID.randomUUID().toString(),
                        timelineStartUs = rightTimelineStartUs,
                        timelineDurationUs = rightDurationUs,
                        sourceInUs = rightSourceInUs
                    )
                    resultClips.add(rightClip)
                }
                // Cut overlaps clip's in-point
                cutStart <= clip.timelineStartUs && cutEnd in (clip.timelineStartUs + 1) until clip.timelineEndUs -> {
                    val trimOffsetTimelineUs = cutEnd - clip.timelineStartUs
                    val trimOffsetSourceUs = (trimOffsetTimelineUs * clip.speedMultiplier).toLong()
                    val newTimelineStart = if (cut.ripple) cutStart else cutEnd
                    val newDurationUs = clip.timelineEndUs - cutEnd
                    val trimmedClip = clip.copy(
                        timelineStartUs = newTimelineStart,
                        timelineDurationUs = newDurationUs,
                        sourceInUs = clip.sourceInUs + trimOffsetSourceUs
                    )
                    resultClips.add(trimmedClip)
                }
                // Cut overlaps clip's out-point
                cutStart in (clip.timelineStartUs + 1) until clip.timelineEndUs && cutEnd >= clip.timelineEndUs -> {
                    val newDurationUs = cutStart - clip.timelineStartUs
                    val newSourceTrimUs = (newDurationUs * clip.speedMultiplier).toLong()
                    val trimmedClip = clip.copy(
                        timelineDurationUs = newDurationUs,
                        sourceOutUs = clip.sourceInUs + newSourceTrimUs
                    )
                    resultClips.add(trimmedClip)
                }
                // Cut covers entire clip
                cutStart <= clip.timelineStartUs && cutEnd >= clip.timelineEndUs -> {
                    // Excised
                }
            }
        }

        return resultClips.sortedBy { it.timelineStartUs }
    }

    private fun applyPushInZoom(
        clips: List<TimelineClip>,
        pushIn: PushInZoomAction
    ): List<TimelineClip> {
        val resultClips = mutableListOf<TimelineClip>()
        for (clip in clips) {
            if (pushIn.splitPointUs in (clip.timelineStartUs + 1) until clip.timelineEndUs &&
                (pushIn.clipId == clip.id || pushIn.clipId.isBlank())
            ) {
                val leftDurationUs = pushIn.splitPointUs - clip.timelineStartUs
                val leftSourceTrimUs = (leftDurationUs * clip.speedMultiplier).toLong()
                val leftClip = clip.copy(
                    id = UUID.randomUUID().toString(),
                    timelineDurationUs = leftDurationUs,
                    sourceOutUs = clip.sourceInUs + leftSourceTrimUs
                )
                resultClips.add(leftClip)

                val rightDurationUs = clip.timelineEndUs - pushIn.splitPointUs
                val rightSourceInUs = clip.sourceInUs + leftSourceTrimUs
                val rightClip = clip.copy(
                    id = UUID.randomUUID().toString(),
                    timelineStartUs = pushIn.splitPointUs,
                    timelineDurationUs = rightDurationUs,
                    sourceInUs = rightSourceInUs,
                    transform = clip.transform.copy(scaleX = pushIn.zoomScale, scaleY = pushIn.zoomScale)
                )
                resultClips.add(rightClip)
            } else {
                resultClips.add(clip)
            }
        }
        return resultClips.sortedBy { it.timelineStartUs }
    }
}
