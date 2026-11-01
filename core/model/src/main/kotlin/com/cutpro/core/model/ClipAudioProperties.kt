package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AudioRole {
    DIALOGUE,
    BACKGROUND_MUSIC,
    SOUND_EFFECTS,
    VOICEOVER
}

@Serializable
data class DuckingConfig(
    val isEnabled: Boolean = false,
    val duckingDepthDb: Float = -12.0f,
    val attackTimeMs: Int = 150,
    val holdTimeMs: Int = 300,
    val releaseTimeMs: Int = 400,
    val speechThresholdDb: Float = -32.0f
) {
    init {
        require(duckingDepthDb <= 0.0f) { "duckingDepthDb ($duckingDepthDb) must be <= 0 dB" }
        require(attackTimeMs >= 0) { "attackTimeMs must be >= 0" }
        require(holdTimeMs >= 0) { "holdTimeMs must be >= 0" }
        require(releaseTimeMs >= 0) { "releaseTimeMs must be >= 0" }
    }
}

@Serializable
data class ClipAudioProperties(
    val volume: Float = 1.0f,        // 0.0 to 2.0 (200% boost)
    val pan: Float = 0.0f,           // -1.0 to 1.0 (Center = 0.0)
    val isMuted: Boolean = false,
    val fadeInDurationUs: Long = 0L,
    val fadeOutDurationUs: Long = 0L,
    val role: AudioRole = AudioRole.BACKGROUND_MUSIC,
    val duckingConfig: DuckingConfig = DuckingConfig()
) {
    init {
        require(volume in 0.0f..2.0f) { "volume ($volume) must be in [0.0, 2.0]" }
        require(pan in -1.0f..1.0f) { "pan ($pan) must be in [-1.0, 1.0]" }
        require(fadeInDurationUs >= 0L) { "fadeInDurationUs ($fadeInDurationUs) must be >= 0" }
        require(fadeOutDurationUs >= 0L) { "fadeOutDurationUs ($fadeOutDurationUs) must be >= 0" }
    }

    /**
     * Calculates effective fade-in and fade-out durations for a clip duration.
     * If the sum of requested fades exceeds clip duration, proportionally scales them down.
     */
    fun calculateEffectiveFades(clipDurationUs: Long): Pair<Long, Long> {
        require(clipDurationUs >= 0L) { "clipDurationUs cannot be negative" }
        val totalFade = fadeInDurationUs + fadeOutDurationUs
        if (totalFade == 0L || totalFade <= clipDurationUs) {
            return Pair(fadeInDurationUs, fadeOutDurationUs)
        }
        val scale = clipDurationUs.toDouble() / totalFade.toDouble()
        val effectiveIn = (fadeInDurationUs * scale).toLong()
        val effectiveOut = (fadeOutDurationUs * scale).toLong()
        return Pair(effectiveIn, effectiveOut)
    }
}
