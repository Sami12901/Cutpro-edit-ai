package com.cutpro.core.ai

import com.cutpro.core.ai.model.AnalysisOptions
import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.ai.model.EditSuggestion

/**
 * Interface Contract 5: AiAssistant
 * Analyzes timelines and generates structured edit plans from natural language prompts.
 */
interface AiAssistant {
    suspend fun analyzeTimeline(timeline: Timeline, options: AnalysisOptions): Result<List<EditSuggestion>>
    suspend fun generateEditPlan(prompt: String, timeline: Timeline): Result<EditPlan>
}
