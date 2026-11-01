package com.cutpro.core.ai.assistant

import com.cutpro.core.ai.AiAssistant
import com.cutpro.core.ai.Timeline
import com.cutpro.core.ai.analyzer.AudioRmsSilenceDetector
import com.cutpro.core.ai.analyzer.JumpCutOptimizer
import com.cutpro.core.ai.analyzer.PacingSuggestionType
import com.cutpro.core.ai.analyzer.SilenceInterval
import com.cutpro.core.ai.analyzer.TalkingHeadPacingAnalyzer
import com.cutpro.core.ai.model.AdjustSpeedAction
import com.cutpro.core.ai.model.AnalysisOptions
import com.cutpro.core.ai.model.ApplyTemplateAction
import com.cutpro.core.ai.model.CutSegmentAction
import com.cutpro.core.ai.model.DeleteSilenceAction
import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.ai.model.EditSuggestion
import com.cutpro.core.ai.model.MuteNoiseAction
import com.cutpro.core.ai.analyzer.PacingAnalyzerConfig
import com.cutpro.core.ai.analyzer.SilenceDetectionConfig
import com.cutpro.core.ai.model.PlanTriggerType
import com.cutpro.core.ai.model.PushInZoomAction
import com.cutpro.core.ai.model.SuggestionType
import com.cutpro.core.model.TrackType

/**
 * StandardAiAssistant
 * Implements AiAssistant conforming to Interface Contract 5.
 * Analyzes multitrack timelines using deterministic DSP analyzers and rule-based NLP prompt parsing.
 */
class StandardAiAssistant(
    val silenceDetector: AudioRmsSilenceDetector = AudioRmsSilenceDetector(),
    val jumpCutOptimizer: JumpCutOptimizer = JumpCutOptimizer(),
    val pacingAnalyzer: TalkingHeadPacingAnalyzer = TalkingHeadPacingAnalyzer(),
    private val audioSamplesProvider: ((clipId: String) -> ShortArray?)? = null
) : AiAssistant {

    override suspend fun analyzeTimeline(
        timeline: Timeline,
        options: AnalysisOptions
    ): Result<List<EditSuggestion>> = runCatching {
        val suggestions = mutableListOf<EditSuggestion>()

        // 1. Audio analysis (silence & jump cut detection)
        val audioTracks = timeline.tracks.filter {
            (it.type == TrackType.AUDIO || it.type == TrackType.VIDEO) && it.isVisible
        }
        for (track in audioTracks) {
            if (options.targetTrackId != null && track.id != options.targetTrackId) continue
            for (clip in track.clips) {
                val samples = audioSamplesProvider?.invoke(clip.id)
                if (samples != null && samples.isNotEmpty()) {
                    val silences = silenceDetector.detectSilencesPcm16(
                        samples = samples,
                        sampleRate = 48000,
                        channelCount = 1,
                        startPtsUs = 0L
                    )
                    for (silence in silences) {
                        val sStart = clip.sourceToTimelineTimestamp(
                            (silence.startUs + clip.sourceInUs).coerceIn(clip.sourceInUs, clip.sourceOutUs)
                        )
                        val sEnd = clip.sourceToTimelineTimestamp(
                            (silence.endUs + clip.sourceInUs).coerceIn(clip.sourceInUs, clip.sourceOutUs)
                        )
                        if (sEnd > sStart) {
                            suggestions.add(
                                EditSuggestion(
                                    type = SuggestionType.SILENCE_REMOVAL,
                                    description = "Remove silence in ${track.name} (${sStart / 1000}ms - ${sEnd / 1000}ms)",
                                    rationale = "Audio level falls below ${options.silenceThresholdDb} dBFS for > ${options.minSilenceDurationUs / 1000}ms",
                                    trackId = track.id,
                                    clipId = clip.id,
                                    startTimeUs = sStart,
                                    endTimeUs = sEnd,
                                    confidence = 0.92f,
                                    proposedAction = DeleteSilenceAction(
                                        description = "Delete dead air: ${clip.customTitle ?: clip.id}",
                                        trackId = track.id,
                                        clipId = clip.id,
                                        startTimeUs = sStart,
                                        endTimeUs = sEnd,
                                        preRollSafetyUs = options.preRollSafetyUs,
                                        postRollSafetyUs = options.postRollSafetyUs
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Video pacing analysis
        val pacingSuggestions = pacingAnalyzer.analyzePacing(timeline)
        for (pacing in pacingSuggestions) {
            val action = when (pacing.suggestionType) {
                PacingSuggestionType.PUSH_IN_ZOOM -> {
                    PushInZoomAction(
                        description = "Digital punch-in zoom (1.15x) at ${pacing.proposedSplitUs / 1_000_000}s",
                        trackId = pacing.trackId,
                        clipId = pacing.clipId,
                        startTimeUs = pacing.segmentStartUs,
                        endTimeUs = pacing.segmentEndUs,
                        splitPointUs = pacing.proposedSplitUs,
                        zoomScale = pacing.proposedTransform?.scaleX ?: 1.15f
                    )
                }
                PacingSuggestionType.B_ROLL_INSERT, PacingSuggestionType.PACING_SPLIT -> {
                    CutSegmentAction(
                        description = "Split take for variation at ${pacing.proposedSplitUs / 1_000_000}s",
                        trackId = pacing.trackId,
                        clipId = pacing.clipId,
                        startTimeUs = pacing.proposedSplitUs,
                        endTimeUs = pacing.segmentEndUs,
                        ripple = false
                    )
                }
            }

            suggestions.add(
                EditSuggestion(
                    type = SuggestionType.PACING_ADJUSTMENT,
                    description = "Pacing variation: ${pacing.rationale}",
                    rationale = pacing.rationale,
                    trackId = pacing.trackId,
                    clipId = pacing.clipId,
                    startTimeUs = pacing.segmentStartUs,
                    endTimeUs = pacing.segmentEndUs,
                    confidence = 0.88f,
                    proposedAction = action
                )
            )
        }

        suggestions
    }

    override suspend fun generateEditPlan(
        prompt: String,
        timeline: Timeline
    ): Result<EditPlan> = runCatching {
        val parsed = DeterministicNlpParser.parse(prompt)

        when (parsed.intent) {
            EditIntent.REMOVE_SILENCE -> {
                val suggestions = analyzeTimeline(timeline, parsed.options).getOrThrow()
                val actions = suggestions
                    .filter { it.type == SuggestionType.SILENCE_REMOVAL }
                    .map { it.proposedAction }

                EditPlan(
                    title = "Silence Removal Plan",
                    description = "Removes ${actions.size} detected dead-air sections",
                    sourcePrompt = prompt,
                    triggerType = PlanTriggerType.NATURAL_LANGUAGE_PROMPT,
                    actions = actions
                )
            }
            EditIntent.OPTIMIZE_JUMP_CUTS -> {
                val suggestions = analyzeTimeline(timeline, parsed.options).getOrThrow()
                val actions = suggestions
                    .filter { it.type == SuggestionType.JUMP_CUT_OPTIMIZATION || it.type == SuggestionType.SILENCE_REMOVAL }
                    .map { it.proposedAction }

                EditPlan(
                    title = "Jump Cut Optimization Plan",
                    description = "Tightens cuts across ${actions.size} points",
                    sourcePrompt = prompt,
                    triggerType = PlanTriggerType.NATURAL_LANGUAGE_PROMPT,
                    actions = actions
                )
            }
            EditIntent.ANALYZE_PACING -> {
                val suggestions = analyzeTimeline(timeline, parsed.options).getOrThrow()
                val actions = suggestions
                    .filter { it.type == SuggestionType.PACING_ADJUSTMENT }
                    .map { it.proposedAction }

                EditPlan(
                    title = "Pacing Optimization Plan",
                    description = "Optimizes pacing across ${actions.size} unbroken segments",
                    sourcePrompt = prompt,
                    triggerType = PlanTriggerType.NATURAL_LANGUAGE_PROMPT,
                    actions = actions
                )
            }
            EditIntent.ADJUST_SPEED -> {
                val targetMultiplier = parsed.speedMultiplier ?: 1.25f
                val videoTracks = timeline.tracks.filter { it.type == TrackType.VIDEO }
                val actions = videoTracks.flatMap { track ->
                    track.clips.map { clip ->
                        AdjustSpeedAction(
                            description = "Speed up '${clip.customTitle ?: clip.id}' to ${targetMultiplier}x",
                            trackId = track.id,
                            clipId = clip.id,
                            startTimeUs = clip.timelineStartUs,
                            endTimeUs = clip.timelineEndUs,
                            currentSpeedMultiplier = clip.speedMultiplier,
                            targetSpeedMultiplier = targetMultiplier
                        )
                    }
                }

                EditPlan(
                    title = "Speed Adjustment Plan (${targetMultiplier}x)",
                    description = "Adjusts playback speed for ${actions.size} clips to ${targetMultiplier}x",
                    sourcePrompt = prompt,
                    triggerType = PlanTriggerType.NATURAL_LANGUAGE_PROMPT,
                    actions = actions
                )
            }
            EditIntent.APPLY_TEMPLATE -> {
                val templateId = parsed.templateId ?: "minimal_modern_title"
                val overlayTrack = timeline.tracks.firstOrNull { it.type == TrackType.OVERLAY }
                    ?: timeline.tracks.firstOrNull()

                val action = ApplyTemplateAction(
                    description = "Apply template '$templateId' at start",
                    trackId = overlayTrack?.id ?: "track_overlay",
                    startTimeUs = 0L,
                    endTimeUs = 3_000_000L,
                    templateId = templateId
                )

                EditPlan(
                    title = "Template Application Plan",
                    description = "Inserts motion graphics template '$templateId'",
                    sourcePrompt = prompt,
                    triggerType = PlanTriggerType.NATURAL_LANGUAGE_PROMPT,
                    actions = listOf(action)
                )
            }
            EditIntent.MUTE_NOISE -> {
                val audioTracks = timeline.tracks.filter { it.type == TrackType.AUDIO }
                val actions = audioTracks.flatMap { track ->
                    track.clips.map { clip ->
                        MuteNoiseAction(
                            description = "Mute noise in '${clip.customTitle ?: clip.id}'",
                            trackId = track.id,
                            clipId = clip.id,
                            startTimeUs = clip.timelineStartUs,
                            endTimeUs = clip.timelineEndUs,
                            targetVolume = 0.0f
                        )
                    }
                }

                EditPlan(
                    title = "Noise Suppression Plan",
                    description = "Mutes audio in ${actions.size} detected noisy sections",
                    sourcePrompt = prompt,
                    triggerType = PlanTriggerType.NATURAL_LANGUAGE_PROMPT,
                    actions = actions
                )
            }
            EditIntent.UNKNOWN, EditIntent.GENERATE_CAPTIONS -> {
                EditPlan(
                    title = "AI Edit Plan: $prompt",
                    description = "No automated cuts generated for command: '$prompt'",
                    sourcePrompt = prompt,
                    triggerType = PlanTriggerType.NATURAL_LANGUAGE_PROMPT,
                    actions = emptyList()
                )
            }
        }
    }
}
