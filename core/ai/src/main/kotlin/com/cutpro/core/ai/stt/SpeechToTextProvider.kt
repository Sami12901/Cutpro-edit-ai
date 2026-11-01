package com.cutpro.core.ai.stt

import com.cutpro.core.ai.stt.model.SttProgress
import com.cutpro.core.ai.stt.model.SttTranscriptionRequest
import com.cutpro.core.ai.stt.model.SupportedLanguage
import com.cutpro.core.subtitles.model.CaptionCue
import kotlinx.coroutines.flow.StateFlow

/**
 * AI-05: SpeechToTextProvider Contract
 * Extensible interface for on-device and local speech recognition engines.
 */
interface SpeechToTextProvider {
    val id: String
    val displayName: String
    val supportedLanguages: Set<SupportedLanguage>
    val progress: StateFlow<SttProgress>

    suspend fun isModelAvailable(language: SupportedLanguage): Boolean
    suspend fun getEstimatedMemoryRequirementBytes(language: SupportedLanguage): Long
    suspend fun initialize(language: SupportedLanguage): Result<Unit>
    suspend fun transcribe(request: SttTranscriptionRequest): Result<List<CaptionCue>>
    suspend fun release()
}
