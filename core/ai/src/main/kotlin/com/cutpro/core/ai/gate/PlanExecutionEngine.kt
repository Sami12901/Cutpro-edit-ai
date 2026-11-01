package com.cutpro.core.ai.gate

import com.cutpro.core.ai.model.AddCaptionAction
import com.cutpro.core.ai.model.AddOverlayAction
import com.cutpro.core.ai.model.AdjustSpeedAction
import com.cutpro.core.ai.model.ApplyColorGradeAction
import com.cutpro.core.ai.model.ApplyFilterAction
import com.cutpro.core.ai.model.ApplySpeedCurveAction
import com.cutpro.core.ai.model.CutSegmentAction
import com.cutpro.core.ai.model.DeleteSilenceAction
import com.cutpro.core.ai.model.DuckAudioAction
import com.cutpro.core.ai.model.EditAction
import com.cutpro.core.ai.model.MuteNoiseAction
import com.cutpro.core.ai.model.PushInZoomAction
import com.cutpro.core.ai.model.TrimClipAction
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.ClipTrimHelper
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.timeline.TimelineOperations
import java.util.UUID
import kotlin.math.pow

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
            var track = currentProject.findTrackById(trackId)
            if (track == null) {
                track = createTrackForActions(trackId, trackActions)
                currentProject = currentProject.copy(tracks = currentProject.tracks + track)
            }
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

        // 6. Apply Color Grading
        val colorGradeActions = actions.filterIsInstance<ApplyColorGradeAction>()
        for (cg in colorGradeActions) {
            currentClips = currentClips.map { clip ->
                if (clip.id == cg.clipId || (clip.timelineStartUs <= cg.startTimeUs && clip.timelineEndUs >= cg.endTimeUs)) {
                    clip.copy(colorAdjustments = cg.adjustments)
                } else {
                    clip
                }
            }
        }

        // 7. Apply LUT Filters
        val filterActions = actions.filterIsInstance<ApplyFilterAction>()
        for (fa in filterActions) {
            currentClips = currentClips.map { clip ->
                if (clip.id == fa.clipId || (clip.timelineStartUs <= fa.startTimeUs && clip.timelineEndUs >= fa.endTimeUs)) {
                    clip.copy(filterType = fa.filterType)
                } else {
                    clip
                }
            }
        }

        // 8. Apply Speed Curves
        val speedCurveActions = actions.filterIsInstance<ApplySpeedCurveAction>()
        for (sca in speedCurveActions) {
            currentClips = currentClips.map { clip ->
                if (clip.id == sca.clipId) {
                    clip.copy(speedCurve = sca.curve)
                } else {
                    clip
                }
            }
        }

        // 9. Add Overlay clips
        val overlayActions = actions.filterIsInstance<AddOverlayAction>()
        for (oa in overlayActions) {
            val durationUs = oa.endTimeUs - oa.startTimeUs
            val isImg = oa.assetUriString.endsWith(".png", true) ||
                    oa.assetUriString.endsWith(".jpg", true) ||
                    oa.assetUriString.endsWith(".jpeg", true) ||
                    oa.assetUriString.endsWith(".webp", true)
            val mime = if (isImg) "image/png" else "video/mp4"
            val overlayClip = TimelineClip(
                id = UUID.randomUUID().toString(),
                trackId = track.id,
                mediaSource = MediaSourceInfo(
                    uriString = oa.assetUriString,
                    fileName = oa.assetUriString.substringAfterLast('/'),
                    mimeType = mime,
                    durationUs = durationUs.coerceAtLeast(1_000_000L)
                ),
                timelineStartUs = oa.startTimeUs,
                timelineDurationUs = durationUs,
                sourceInUs = 0L,
                sourceOutUs = durationUs,
                transform = ClipTransform(
                    scaleX = oa.scale,
                    scaleY = oa.scale,
                    translationX = oa.translationX,
                    translationY = oa.translationY,
                    blendMode = oa.blendMode
                ),
                customTitle = "Overlay"
            )
            currentClips = currentClips + overlayClip
        }

        // 10. Add Caption clips
        val captionActions = actions.filterIsInstance<AddCaptionAction>()
        for (ca in captionActions) {
            val durationUs = ca.endTimeUs - ca.startTimeUs
            val captionClip = TimelineClip(
                id = UUID.randomUUID().toString(),
                trackId = track.id,
                mediaSource = MediaSourceInfo(
                    uriString = "cutpro://caption/${UUID.randomUUID()}",
                    fileName = "caption.txt",
                    mimeType = "text/plain",
                    durationUs = durationUs.coerceAtLeast(1_000_000L)
                ),
                timelineStartUs = ca.startTimeUs,
                timelineDurationUs = durationUs,
                sourceInUs = 0L,
                sourceOutUs = durationUs,
                customTitle = ca.text
            )
            currentClips = currentClips + captionClip
        }

        // 11. Duck Audio
        val duckActions = actions.filterIsInstance<DuckAudioAction>()
        for (da in duckActions) {
            val factor = (10.0.pow(da.targetVolumeDb / 20.0)).toFloat().coerceIn(0.01f, 1.0f)
            currentClips = currentClips.map { clip ->
                if (clip.timelineStartUs < da.endTimeUs && clip.timelineEndUs > da.startTimeUs) {
                    clip.copy(audioProperties = clip.audioProperties.copy(volume = factor))
                } else {
                    clip
                }
            }
        }

        return track.copy(clips = currentClips.sortedBy { it.timelineStartUs })
    }

    private fun createTrackForActions(
        trackId: String,
        actions: List<EditAction>
    ): TimelineTrack {
        val trackType = when {
            actions.any { it is AddCaptionAction } -> TrackType.CAPTION
            actions.any { it is AddOverlayAction } -> TrackType.OVERLAY
            actions.any { it is DuckAudioAction } -> TrackType.AUDIO
            else -> TrackType.VIDEO
        }
        val trackName = when (trackType) {
            TrackType.CAPTION -> "Captions"
            TrackType.OVERLAY -> "Overlay"
            TrackType.AUDIO -> "Audio"
            else -> "Video Track"
        }
        return TimelineTrack(id = trackId, name = trackName, type = trackType)
    }

    private fun applySingleCut(
        clips: List<TimelineClip>,
        cut: CutSegmentAction
    ): List<TimelineClip> {
        val cutStart = cut.startTimeUs
        val cutEnd = cut.endTimeUs
        if (cutEnd < cutStart) return clips

        if (cutEnd == cutStart) {
            // Split point action
            return clips.flatMap { clip ->
                if (cutStart > clip.timelineStartUs && cutStart < clip.timelineEndUs) {
                    val leftDurationUs = cutStart - clip.timelineStartUs
                    val leftSourceTrimUs = (leftDurationUs * clip.speedMultiplier).toLong()
                    val leftClip = clip.copy(
                        id = UUID.randomUUID().toString(),
                        timelineDurationUs = leftDurationUs,
                        sourceOutUs = clip.sourceInUs + leftSourceTrimUs
                    )
                    val rightDurationUs = clip.timelineEndUs - cutStart
                    val rightClip = clip.copy(
                        id = UUID.randomUUID().toString(),
                        timelineStartUs = cutStart,
                        timelineDurationUs = rightDurationUs,
                        sourceInUs = leftClip.sourceOutUs,
                        sourceOutUs = clip.sourceOutUs
                    )
                    listOf(leftClip, rightClip)
                } else {
                    listOf(clip)
                }
            }
        }

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
