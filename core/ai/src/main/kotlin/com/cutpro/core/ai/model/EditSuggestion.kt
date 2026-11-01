package com.cutpro.core.ai.model

import java.util.UUID

enum class SuggestionType {
    SILENCE_REMOVAL,
    JUMP_CUT_OPTIMIZATION,
    PACING_ADJUSTMENT,
    SPEED_CHANGE,
    TEMPLATE_INSERTION,
    NOISE_SUPPRESSION
}

data class EditSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val type: SuggestionType,
    val description: String,
    val rationale: String,
    val trackId: String,
    val clipId: String? = null,
    val startTimeUs: Long,
    val endTimeUs: Long,
    val confidence: Float,
    val proposedAction: EditAction
) {
    init {
        require(confidence in 0.0f..1.0f) { "confidence must be in [0.0, 1.0]" }
    }
}
