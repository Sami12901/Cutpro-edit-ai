package com.cutpro.core.ai.prompt

import com.cutpro.core.common.timecode.TimecodeHelper
import com.cutpro.core.model.ProjectManifest

enum class PromptObjectivePreset(val displayName: String, val description: String) {
    VIRAL_SHORT_REEL(
        "Viral Shorts / Reels (High Energy)",
        "Punchy hook in first 2 seconds, aggressive jump cuts on silences, kinetic subtitles, and auto zoom punch-ins."
    ),
    YOUTUBE_EXPLAINER(
        "YouTube Explainer / Deep Dive",
        "Paced narrative structure, chapter titles, lower thirds, and B-roll overlay recommendations."
    ),
    BENGALI_PODCAST_HIGHLIGHT(
        "Bengali Podcast / Interview Highlight",
        "Bengali language subtitle synchronization, dialogue highlight clips, and background audio ducking."
    ),
    FAST_PACED_VLOG(
        "Fast-Paced Vlog & Montage",
        "Rhythmic cuts, montage speed curves (Montage/Hero), and cinematic color grading."
    ),
    CUSTOM(
        "Custom Editing Prompt",
        "User-defined creative editing instructions powered by full CutPro DSL specification."
    )
}

data class TranscriptSegment(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val text: String,
    val speaker: String? = null,
    val language: String = "en"
)

data class GeneratedPromptResult(
    val preset: PromptObjectivePreset,
    val fullPrompt: String,
    val estimatedWordCount: Int,
    val segmentCount: Int
)

object TranscriptToPromptEngine {

    fun generatePrompt(
        project: ProjectManifest,
        segments: List<TranscriptSegment>,
        preset: PromptObjectivePreset,
        customInstructions: String = ""
    ): GeneratedPromptResult {
        val sb = StringBuilder()

        val formattedDuration = TimecodeHelper.formatSmpte(project.durationUs, 30)
        val aspectRatioStr = project.aspectRatio.name.replace("RATIO_", "").replace("_", ":")

        sb.appendLine("### CutPro AI Video Editor Prompt")
        sb.appendLine("Role: You are an expert professional video editor assistant. Your task is to analyze the video transcript and metadata below, then generate an executable CutPro DSL (CutPro Script v2) editing plan.")
        sb.appendLine()
        sb.appendLine("#### Project Metadata:")
        sb.appendLine("- Title: ${project.title}")
        sb.appendLine("- Aspect Ratio: $aspectRatioStr (${project.aspectRatio.widthRatio}:${project.aspectRatio.heightRatio})")
        sb.appendLine("- Total Duration: $formattedDuration (${project.durationUs / 1_000_000}s)")
        sb.appendLine("- Objective: ${preset.displayName}")
        sb.appendLine("- Objective Description: ${preset.description}")
        sb.appendLine()

        if (customInstructions.isNotBlank()) {
            sb.appendLine("#### User Custom Instructions:")
            sb.appendLine(customInstructions.trim())
            sb.appendLine()
        }

        sb.appendLine("#### Dialogue & Spoken Audio Transcript:")
        if (segments.isEmpty()) {
            sb.appendLine("(No spoken dialogue detected. Proceed with visual pacing and rhythm cuts.)")
        } else {
            for ((index, segment) in segments.withIndex()) {
                val startSmpte = TimecodeHelper.formatSmpte(segment.startTimeUs, 30)
                val endSmpte = TimecodeHelper.formatSmpte(segment.endTimeUs, 30)
                val speakerLabel = segment.speaker?.let { "[$it] " } ?: ""
                sb.appendLine("${index + 1}. [$startSmpte -> $endSmpte] $speakerLabel\"${segment.text}\" (lang: ${segment.language})")
            }
        }
        sb.appendLine()

        sb.appendLine("#### CutPro DSL v2 Syntax Specification:")
        sb.appendLine("Generate executable CutPro DSL commands adhering to these exact signatures:")
        sb.appendLine("1. SPLIT(track=\"main\", time=\"MM:SS.mmm\") - Splits clip at specified time.")
        sb.appendLine("2. DELETE_RANGE(track=\"main\", start=\"MM:SS.mmm\", end=\"MM:SS.mmm\", ripple=true) - Deletes section and ripples remaining clips.")
        sb.appendLine("3. APPLY_SPEED_RAMP(clip=\"<clip_id_or_index>\", curve=\"HERO|MONTAGE|BULLET|JUMP_CUT\") - Applies speed ramping curve.")
        sb.appendLine("4. ADD_CAPTION(text=\"...\", start=\"MM:SS.mmm\", end=\"MM:SS.mmm\", style=\"HIGHLIGHT_PILL|BOUNCING_POP|GLOW_OUTLINE|TYPEWRITER\", lang=\"en|bn\")")
        sb.appendLine("5. ADD_OVERLAY(src=\"asset://b_roll\", start=\"MM:SS.mmm\", duration=\"MM:SS.mmm\", scale=1.15, blend=\"SCREEN|OVERLAY|NORMAL\")")
        sb.appendLine("6. APPLY_ZOOM_PUNCH(time=\"MM:SS.mmm\", factor=1.20) - Punches in 20% on the speaker for emphasis.")
        sb.appendLine("7. DUCK_AUDIO(track=\"bg_music\", start=\"MM:SS.mmm\", end=\"MM:SS.mmm\", db=-18) - Ducks music under dialogue.")
        sb.appendLine("8. APPLY_COLOR_GRADE(clip=\"<clip_id>\", contrast=1.2, saturation=1.2, temperature=0.1, vignette=0.2)")
        sb.appendLine("9. APPLY_FILTER(clip=\"<clip_id>\", filter=\"CINEMATIC_TEAL_ORANGE|MOODY_DARK|VIBRANT_GOLD\")")
        sb.appendLine()

        sb.appendLine("#### Output Requirements:")
        sb.appendLine("Output ONLY the executable CutPro DSL code inside a single code block. Do not include markdown preamble or conversational explanations.")
        sb.appendLine("Example output format:")
        sb.appendLine("```cutpro")
        sb.appendLine("// CutPro v2 Auto Edit Plan")
        sb.appendLine("DELETE_RANGE(track=\"main\", start=\"00:03.200\", end=\"00:04.800\", ripple=true)")
        sb.appendLine("APPLY_ZOOM_PUNCH(time=\"00:04.800\", factor=1.15)")
        sb.appendLine("ADD_CAPTION(text=\"স্বাগতম সবাইকে!\", start=\"00:00.000\", end=\"00:03.200\", style=\"HIGHLIGHT_PILL\", lang=\"bn\")")
        sb.appendLine("```")

        val fullText = sb.toString()
        val wordCount = fullText.split("\\s+".toRegex()).count { it.isNotBlank() }

        return GeneratedPromptResult(
            preset = preset,
            fullPrompt = fullText,
            estimatedWordCount = wordCount,
            segmentCount = segments.size
        )
    }
}
