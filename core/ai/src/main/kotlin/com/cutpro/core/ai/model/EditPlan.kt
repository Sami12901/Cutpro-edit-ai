package com.cutpro.core.ai.model

import java.util.UUID

enum class PlanTriggerType {
    NATURAL_LANGUAGE_PROMPT,
    DSP_ANALYZER,
    MANUAL_INSPECTION
}

data class EditPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourcePrompt: String? = null,
    val triggerType: PlanTriggerType = PlanTriggerType.NATURAL_LANGUAGE_PROMPT,
    val actions: List<EditAction> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "EditPlan id cannot be blank" }
        require(title.isNotBlank()) { "EditPlan title cannot be blank" }
    }

    val actionCount: Int get() = actions.size
    val totalTimeSavedUs: Long get() = actions.filterIsInstance<DeleteSilenceAction>()
        .sumOf { (it.endTimeUs - it.startTimeUs).coerceAtLeast(0L) }
}
