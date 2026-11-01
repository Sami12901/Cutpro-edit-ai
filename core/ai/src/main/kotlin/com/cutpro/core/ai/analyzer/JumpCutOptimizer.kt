package com.cutpro.core.ai.analyzer

import com.cutpro.core.model.TimelineClip
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class JumpCutConfig(
    val silenceConfig: SilenceDetectionConfig = SilenceDetectionConfig(),
    val postSpeechSafetyBufferUs: Long = 80_000L,
    val preSpeechSafetyBufferUs: Long = 80_000L,
    val minCutDurationUs: Long = 200_000L
) {
    init {
        require(postSpeechSafetyBufferUs >= 0L) { "postSpeechSafetyBufferUs must be >= 0" }
        require(preSpeechSafetyBufferUs >= 0L) { "preSpeechSafetyBufferUs must be >= 0" }
        require(minCutDurationUs > 0L) { "minCutDurationUs must be > 0" }
    }
}

@Serializable
data class JumpCutSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val clipId: String,
    val trackId: String,
    val rawSilenceStartUs: Long,
    val rawSilenceEndUs: Long,
    val cutStartTimelineUs: Long,
    val cutEndTimelineUs: Long,
    val cutStartSourceUs: Long,
    val cutEndSourceUs: Long,
    val postSpeechBufferUs: Long,
    val preSpeechBufferUs: Long,
    val excisedDurationUs: Long,
    val description: String
) {
    init {
        require(cutEndTimelineUs >= cutStartTimelineUs) { "cutEndTimelineUs must be >= cutStartTimelineUs" }
        require(excisedDurationUs == cutEndTimelineUs - cutStartTimelineUs) {
            "excisedDurationUs must match cutEndTimelineUs - cutStartTimelineUs"
        }
    }
}

/**
 * AI-02: JumpCutOptimizer
 * Suggests non-destructive cuts between speech pauses with speech-edge safety buffers
 * (80ms pre/post) to preserve consonant attacks and decaying fricatives without clipping speech.
 */
class JumpCutOptimizer(
    val config: JumpCutConfig = JumpCutConfig()
) {
    private val silenceDetector = AudioRmsSilenceDetector(config.silenceConfig)

    fun optimizeFromClipPcm16(
        clip: TimelineClip,
        pcmSamples: ShortArray,
        sampleRate: Int,
        channelCount: Int
    ): List<JumpCutSuggestion> {
        val rawSilences = silenceDetector.detectSilencesPcm16(pcmSamples, sampleRate, channelCount, startPtsUs = 0L)
        return optimizeSilencesForClip(clip, rawSilences)
    }

    fun optimizeSilencesForClip(
        clip: TimelineClip,
        rawSilences: List<SilenceInterval>
    ): List<JumpCutSuggestion> {
        val suggestions = mutableListOf<JumpCutSuggestion>()

        val clipSourceDurationUs = clip.sourceOutUs - clip.sourceInUs
        val postBufferUs = config.postSpeechSafetyBufferUs
        val preBufferUs = config.preSpeechSafetyBufferUs

        for (silence in rawSilences) {
            val effectiveSilenceStartSourceUs = silence.startUs.coerceIn(0L, clipSourceDurationUs)
            val effectiveSilenceEndSourceUs = silence.endUs.coerceIn(0L, clipSourceDurationUs)

            if (effectiveSilenceEndSourceUs <= effectiveSilenceStartSourceUs) continue

            val isLeadingSilence = effectiveSilenceStartSourceUs == 0L
            val isTrailingSilence = effectiveSilenceEndSourceUs >= clipSourceDurationUs

            val cutStartSourceRelUs = if (isLeadingSilence) {
                0L
            } else {
                effectiveSilenceStartSourceUs + postBufferUs
            }

            val cutEndSourceRelUs = if (isTrailingSilence) {
                clipSourceDurationUs
            } else {
                effectiveSilenceEndSourceUs - preBufferUs
            }

            val excisedDurationUs = cutEndSourceRelUs - cutStartSourceRelUs
            if (excisedDurationUs < config.minCutDurationUs) {
                continue
            }

            val absoluteSourceCutStartUs = clip.sourceInUs + cutStartSourceRelUs
            val absoluteSourceCutEndUs = clip.sourceInUs + cutEndSourceRelUs

            val timelineCutStartUs = clip.timelineStartUs + (cutStartSourceRelUs / clip.speedMultiplier).toLong()
            val timelineCutEndUs = clip.timelineStartUs + (cutEndSourceRelUs / clip.speedMultiplier).toLong()

            val cutTimelineDurationUs = timelineCutEndUs - timelineCutStartUs

            suggestions.add(
                JumpCutSuggestion(
                    clipId = clip.id,
                    trackId = clip.trackId,
                    rawSilenceStartUs = silence.startUs,
                    rawSilenceEndUs = silence.endUs,
                    cutStartTimelineUs = timelineCutStartUs,
                    cutEndTimelineUs = timelineCutEndUs,
                    cutStartSourceUs = absoluteSourceCutStartUs,
                    cutEndSourceUs = absoluteSourceCutEndUs,
                    postSpeechBufferUs = if (isLeadingSilence) 0L else postBufferUs,
                    preSpeechBufferUs = if (isTrailingSilence) 0L else preBufferUs,
                    excisedDurationUs = cutTimelineDurationUs,
                    description = "Excise ${cutTimelineDurationUs / 1000L}ms pause between speech segments"
                )
            )
        }

        return suggestions
    }
}
