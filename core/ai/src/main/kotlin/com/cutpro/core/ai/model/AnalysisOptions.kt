package com.cutpro.core.ai.model

data class AnalysisOptions(
    val silenceThresholdDb: Float = -40.0f,
    val minSilenceDurationUs: Long = 500_000L,
    val preRollSafetyUs: Long = 80_000L,
    val postRollSafetyUs: Long = 80_000L,
    val maxUnbrokenShotDurationUs: Long = 8_000_000L,
    val targetTrackId: String? = null,
    val languageCode: String? = null
) {
    init {
        require(silenceThresholdDb in -100.0f..0.0f) { "silenceThresholdDb must be in [-100.0, 0.0] dBFS" }
        require(minSilenceDurationUs > 0L) { "minSilenceDurationUs must be > 0" }
        require(maxUnbrokenShotDurationUs > 0L) { "maxUnbrokenShotDurationUs must be > 0" }
    }

    companion object {
        val DEFAULT = AnalysisOptions()
        val AGGRESSIVE = AnalysisOptions(
            silenceThresholdDb = -35.0f,
            minSilenceDurationUs = 300_000L,
            preRollSafetyUs = 40_000L,
            postRollSafetyUs = 40_000L
        )
        val CONSERVATIVE = AnalysisOptions(
            silenceThresholdDb = -45.0f,
            minSilenceDurationUs = 800_000L,
            preRollSafetyUs = 100_000L,
            postRollSafetyUs = 100_000L
        )
    }
}
