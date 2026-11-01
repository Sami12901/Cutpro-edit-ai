package com.cutpro.core.ai.assistant

import com.cutpro.core.ai.model.AnalysisOptions
import com.cutpro.core.model.TrackType
import java.util.Locale
import java.util.regex.Pattern

enum class EditIntent {
    REMOVE_SILENCE,
    OPTIMIZE_JUMP_CUTS,
    ANALYZE_PACING,
    ADJUST_SPEED,
    APPLY_TEMPLATE,
    MUTE_NOISE,
    GENERATE_CAPTIONS,
    UNKNOWN
}

data class ParsedCommand(
    val rawPrompt: String,
    val intent: EditIntent,
    val confidence: Float,
    val options: AnalysisOptions,
    val speedMultiplier: Float? = null,
    val templateId: String? = null,
    val targetTrackType: TrackType? = null
)

/**
 * Deterministic offline rule-based NLP prompt parser.
 * Maps natural language editing queries to structured EditIntents and AnalysisOptions
 * without invoking external cloud or network APIs.
 */
object DeterministicNlpParser {

    private val SILENCE_PATTERNS = listOf(
        Pattern.compile(".*(remove|cut|delete|strip|trim)\\s+(all\\s+)?(silence|silences|dead\\s*air|pauses?).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*silence\\s+(removal|detector|cleaning).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*cut\\s+quiet\\s+(parts?|sections?).*", Pattern.CASE_INSENSITIVE)
    )

    private val JUMP_CUT_PATTERNS = listOf(
        Pattern.compile(".*(jump\\s*cut|tighten\\s+cuts?|tighten\\s+edits?|crisp\\s+cuts?).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*punch\\s+up\\s+(pacing|cuts?).*", Pattern.CASE_INSENSITIVE)
    )

    private val PACING_PATTERNS = listOf(
        Pattern.compile(".*(talking\\s*head|pacing|long\\s+shots?|long\\s+takes?|unbroken\\s+shots?).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(break\\s+up|flag)\\s+long\\s+takes?.*", Pattern.CASE_INSENSITIVE)
    )

    private val SPEED_PATTERNS = listOf(
        Pattern.compile(".*(speed\\s*up|slow\\s*down|change\\s+speed|set\\s+speed|playback\\s+.*speed|playback\\s+speed|double\\s+speed|half\\s+speed).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(\\d+(\\.\\d+)?)\\s*x\\s+speed.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*speed\\s+(\\d+(\\.\\d+)?)\\s*x.*", Pattern.CASE_INSENSITIVE)
    )

    private val TEMPLATE_PATTERNS = listOf(
        Pattern.compile(".*(apply|insert|add|use)\\s+(template|title|lower\\s+third|caption|intro|outro|cta).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(minimal\\s+modern|kinetic|glitch|pop\\s+bounce|terminal).*", Pattern.CASE_INSENSITIVE)
    )

    private val NOISE_PATTERNS = listOf(
        Pattern.compile(".*(mute|suppress|reduce|clean|remove)\\s+(noise|background\\s+noise|buzz|hum|hiss).*", Pattern.CASE_INSENSITIVE)
    )

    private val CAPTION_PATTERNS = listOf(
        Pattern.compile(".*(generate|create|transcribe|add)\\s+(subtitles?|captions?|transcript).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*speech\\s+to\\s+text.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*transcribe.*", Pattern.CASE_INSENSITIVE)
    )

    private val DURATION_SECONDS_PATTERN = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(s|sec|secs|seconds?)", Pattern.CASE_INSENSITIVE)
    private val DURATION_MILLIS_PATTERN = Pattern.compile("(\\d+)\\s*(ms|millis|milliseconds?)", Pattern.CASE_INSENSITIVE)
    private val SPEED_VALUE_PATTERN = Pattern.compile("(\\d+(\\.\\d+)?)\\s*x", Pattern.CASE_INSENSITIVE)
    private val DB_PATTERN = Pattern.compile("(-\\d+(\\.\\d+)?)\\s*db", Pattern.CASE_INSENSITIVE)

    fun parse(prompt: String): ParsedCommand {
        val trimmed = prompt.trim()
        val normalized = trimmed.lowercase(Locale.ROOT)

        var intent = EditIntent.UNKNOWN
        var confidence = 0.0f

        when {
            matchesAny(trimmed, SILENCE_PATTERNS) -> {
                intent = EditIntent.REMOVE_SILENCE
                confidence = 0.95f
            }
            matchesAny(trimmed, JUMP_CUT_PATTERNS) -> {
                intent = EditIntent.OPTIMIZE_JUMP_CUTS
                confidence = 0.90f
            }
            matchesAny(trimmed, PACING_PATTERNS) -> {
                intent = EditIntent.ANALYZE_PACING
                confidence = 0.88f
            }
            matchesAny(trimmed, SPEED_PATTERNS) -> {
                intent = EditIntent.ADJUST_SPEED
                confidence = 0.92f
            }
            matchesAny(trimmed, TEMPLATE_PATTERNS) -> {
                intent = EditIntent.APPLY_TEMPLATE
                confidence = 0.85f
            }
            matchesAny(trimmed, NOISE_PATTERNS) -> {
                intent = EditIntent.MUTE_NOISE
                confidence = 0.90f
            }
            matchesAny(trimmed, CAPTION_PATTERNS) -> {
                intent = EditIntent.GENERATE_CAPTIONS
                confidence = 0.92f
            }
        }

        val minSilenceUs = extractDurationUs(trimmed) ?: when {
            normalized.contains("aggressive") -> 300_000L
            normalized.contains("gentle") || normalized.contains("conservative") -> 800_000L
            else -> 500_000L
        }

        val thresholdDb = extractDb(trimmed) ?: when {
            normalized.contains("aggressive") -> -35.0f
            normalized.contains("gentle") || normalized.contains("conservative") -> -45.0f
            else -> -40.0f
        }

        val speedMultiplier = extractSpeed(trimmed)

        val languageCode = when {
            normalized.contains("bengali") || normalized.contains("bangla") -> "bn"
            normalized.contains("english") -> "en"
            else -> null
        }

        val templateId = extractTemplateId(normalized)

        val options = AnalysisOptions(
            silenceThresholdDb = thresholdDb,
            minSilenceDurationUs = minSilenceUs,
            languageCode = languageCode
        )

        return ParsedCommand(
            rawPrompt = prompt,
            intent = intent,
            confidence = confidence,
            options = options,
            speedMultiplier = speedMultiplier,
            templateId = templateId
        )
    }

    private fun matchesAny(input: String, patterns: List<Pattern>): Boolean {
        return patterns.any { it.matcher(input).matches() }
    }

    private fun extractDurationUs(input: String): Long? {
        val msMatcher = DURATION_MILLIS_PATTERN.matcher(input)
        if (msMatcher.find()) {
            val ms = msMatcher.group(1)?.toLongOrNull() ?: return null
            return ms * 1_000L
        }
        val secMatcher = DURATION_SECONDS_PATTERN.matcher(input)
        if (secMatcher.find()) {
            val sec = secMatcher.group(1)?.toDoubleOrNull() ?: return null
            return (sec * 1_000_000L).toLong()
        }
        return null
    }

    private fun extractDb(input: String): Float? {
        val dbMatcher = DB_PATTERN.matcher(input)
        if (dbMatcher.find()) {
            return dbMatcher.group(1)?.toFloatOrNull()
        }
        return null
    }

    private fun extractSpeed(input: String): Float? {
        val matcher = SPEED_VALUE_PATTERN.matcher(input)
        if (matcher.find()) {
            return matcher.group(1)?.toFloatOrNull()
        }
        if (input.contains("double speed", ignoreCase = true)) return 2.0f
        if (input.contains("half speed", ignoreCase = true)) return 0.5f
        return null
    }

    private fun extractTemplateId(normalized: String): String? {
        return when {
            normalized.contains("minimal modern") -> "minimal_modern_title"
            normalized.contains("cinematic split") -> "cinematic_split_fade"
            normalized.contains("glitch") -> "bold_glitch_title"
            normalized.contains("pop bounce") -> "pop_bounce_kinetic"
            normalized.contains("terminal") || normalized.contains("typewriter") -> "terminal_typewriter"
            normalized.contains("lower third") -> "news_broadcast_bar"
            else -> null
        }
    }
}
