package com.cutpro.core.ai.analyzer

import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class PacingSuggestionType {
    PUSH_IN_ZOOM,
    B_ROLL_INSERT,
    PACING_SPLIT
}

@Serializable
data class PacingAnalyzerConfig(
    val maxContiguousDurationUs: Long = 8_000_000L,
    val defaultZoomScale: Float = 1.15f,
    val minBrollDurationUs: Long = 2_500_000L,
    val considerOverlayOcclusion: Boolean = true
) {
    init {
        require(maxContiguousDurationUs > 0L) { "maxContiguousDurationUs must be > 0" }
        require(defaultZoomScale in 1.05f..2.0f) { "defaultZoomScale must be in [1.05, 2.0]" }
    }
}

@Serializable
data class PacingSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val clipId: String,
    val trackId: String,
    val segmentStartUs: Long,
    val segmentEndUs: Long,
    val durationUs: Long,
    val suggestionType: PacingSuggestionType,
    val proposedSplitUs: Long,
    val proposedTransform: ClipTransform? = null,
    val rationale: String
) {
    init {
        require(segmentEndUs >= segmentStartUs) { "segmentEndUs must be >= segmentStartUs" }
        require(durationUs == segmentEndUs - segmentStartUs) { "durationUs must match segment bounds" }
        require(proposedSplitUs in segmentStartUs..segmentEndUs) {
            "proposedSplitUs ($proposedSplitUs) outside segment [$segmentStartUs, $segmentEndUs]"
        }
    }
}

/**
 * AI-03: TalkingHeadPacingAnalyzer
 * Analyzes video tracks for unbroken talking-head segments exceeding 8 seconds,
 * evaluates overlay track occlusions, and proposes non-destructive visual variations
 * (1.15x push-in zoom, B-roll insert, pacing split).
 */
class TalkingHeadPacingAnalyzer(
    val config: PacingAnalyzerConfig = PacingAnalyzerConfig()
) {
    fun analyzePacing(
        project: ProjectManifest,
        knownSilencesByClipId: Map<String, List<SilenceInterval>> = emptyMap()
    ): List<PacingSuggestion> {
        val suggestions = mutableListOf<PacingSuggestion>()

        val videoTracks = project.tracks.filter { it.type == TrackType.VIDEO && it.isVisible }
        val overlayTracks = project.tracks.filter {
            (it.type == TrackType.OVERLAY || it.type == TrackType.VIDEO) && it.isVisible
        }

        for (track in videoTracks) {
            for (clip in track.clips) {
                if (clip.timelineDurationUs <= config.maxContiguousDurationUs) continue

                val unoccludedWindows = if (config.considerOverlayOcclusion) {
                    computeUnoccludedWindows(clip, overlayTracks.filter { it.id != track.id })
                } else {
                    listOf(Pair(clip.timelineStartUs, clip.timelineEndUs))
                }

                for ((windowStartUs, windowEndUs) in unoccludedWindows) {
                    val windowDurationUs = windowEndUs - windowStartUs
                    if (windowDurationUs <= config.maxContiguousDurationUs) continue

                    val midpointUs = windowStartUs + windowDurationUs / 2
                    val silences = knownSilencesByClipId[clip.id] ?: emptyList()
                    val candidateSilence = silences
                        .map { silence ->
                            val sStart = clip.sourceToTimelineTimestamp(
                                (silence.startUs + clip.sourceInUs).coerceIn(clip.sourceInUs, clip.sourceOutUs)
                            )
                            val sEnd = clip.sourceToTimelineTimestamp(
                                (silence.endUs + clip.sourceInUs).coerceIn(clip.sourceInUs, clip.sourceOutUs)
                            )
                            Pair(sStart, sEnd)
                        }
                        .filter { (sStart, sEnd) ->
                            sStart >= windowStartUs + 2_000_000L && sEnd <= windowEndUs - 2_000_000L
                        }
                        .minByOrNull { (sStart, sEnd) -> kotlin.math.abs((sStart + sEnd) / 2 - midpointUs) }

                    val splitPointUs = candidateSilence?.let { (sStart, sEnd) -> (sStart + sEnd) / 2 } ?: midpointUs

                    // 1. Digital punch-in zoom suggestion (1.15x)
                    suggestions.add(
                        PacingSuggestion(
                            clipId = clip.id,
                            trackId = track.id,
                            segmentStartUs = windowStartUs,
                            segmentEndUs = windowEndUs,
                            durationUs = windowDurationUs,
                            suggestionType = PacingSuggestionType.PUSH_IN_ZOOM,
                            proposedSplitUs = splitPointUs,
                            proposedTransform = ClipTransform(scaleX = config.defaultZoomScale, scaleY = config.defaultZoomScale),
                            rationale = "Contiguous shot exceeds ${windowDurationUs / 1_000_000L}s. Apply digital punch-in zoom (${config.defaultZoomScale}x) to restore visual energy."
                        )
                    )

                    // 2. Secondary B-roll cutaway suggestion
                    suggestions.add(
                        PacingSuggestion(
                            clipId = clip.id,
                            trackId = track.id,
                            segmentStartUs = windowStartUs,
                            segmentEndUs = windowEndUs,
                            durationUs = windowDurationUs,
                            suggestionType = PacingSuggestionType.B_ROLL_INSERT,
                            proposedSplitUs = splitPointUs,
                            proposedTransform = null,
                            rationale = "Contiguous shot exceeds ${windowDurationUs / 1_000_000L}s. Insert cutaway B-roll at speech pause."
                        )
                    )
                }
            }
        }

        return suggestions
    }

    private fun computeUnoccludedWindows(
        baseClip: TimelineClip,
        overlayTracks: List<TimelineTrack>
    ): List<Pair<Long, Long>> {
        val occludingClips = overlayTracks.flatMap { it.clips }
            .filter { it.timelineEndUs > baseClip.timelineStartUs && it.timelineStartUs < baseClip.timelineEndUs }
            .sortedBy { it.timelineStartUs }

        if (occludingClips.isEmpty()) {
            return listOf(Pair(baseClip.timelineStartUs, baseClip.timelineEndUs))
        }

        val result = mutableListOf<Pair<Long, Long>>()
        var cursorUs = baseClip.timelineStartUs

        for (occ in occludingClips) {
            val occStart = occ.timelineStartUs.coerceAtLeast(baseClip.timelineStartUs)
            val occEnd = occ.timelineEndUs.coerceAtMost(baseClip.timelineEndUs)

            if (occStart > cursorUs) {
                result.add(Pair(cursorUs, occStart))
            }
            cursorUs = maxOf(cursorUs, occEnd)
        }

        if (cursorUs < baseClip.timelineEndUs) {
            result.add(Pair(cursorUs, baseClip.timelineEndUs))
        }

        return result
    }
}
