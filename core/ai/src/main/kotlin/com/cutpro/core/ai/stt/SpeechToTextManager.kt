package com.cutpro.core.ai.stt

import com.cutpro.core.ai.stt.guard.MemoryCheckResult
import com.cutpro.core.ai.stt.guard.MemoryGuard
import com.cutpro.core.ai.stt.model.SttError
import com.cutpro.core.ai.stt.model.SttException
import com.cutpro.core.ai.stt.model.SttProgress
import com.cutpro.core.ai.stt.model.SttTranscriptionRequest
import com.cutpro.core.ai.stt.provider.DeterministicMockSttProvider
import com.cutpro.core.ai.stt.provider.SpeechToTextProviderRegistry
import com.cutpro.core.ai.stt.subtitles.SttSubtitlesAdapter
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SpeechToTextManager
 * High-level coordinator that validates system memory via MemoryGuard, checks provider/model
 * readiness, routes transcription requests, and converts results to domain subtitles.
 */
class SpeechToTextManager(
    val memoryGuard: MemoryGuard,
    val registry: SpeechToTextProviderRegistry = SpeechToTextProviderRegistry(),
    val fallbackProvider: SpeechToTextProvider = DeterministicMockSttProvider(memoryGuard)
) {
    init {
        registry.register(fallbackProvider)
    }

    private val _activeProgress = MutableStateFlow<SttProgress>(SttProgress.Idle)
    val activeProgress: StateFlow<SttProgress> = _activeProgress.asStateFlow()

    suspend fun transcribeToCues(
        request: SttTranscriptionRequest,
        preferredProviderId: String? = null
    ): Result<List<CaptionCue>> {
        val provider = preferredProviderId?.let { registry.get(it) }
            ?: registry.getAvailableForLanguage(request.language).firstOrNull()
            ?: fallbackProvider

        // 1. Language support validation
        if (request.language !in provider.supportedLanguages) {
            val err = SttError.UnsupportedLanguage(request.language, provider.displayName)
            _activeProgress.value = SttProgress.Failed(err)
            return Result.failure(SttException(err))
        }

        // 2. Low-RAM Device Guard
        val requiredMemory = provider.getEstimatedMemoryRequirementBytes(request.language)
        val memCheck = memoryGuard.checkMemorySufficient(maxOf(requiredMemory, MemoryGuard.MIN_REQUIRED_RAM_BYTES))
        if (memCheck is MemoryCheckResult.Insufficient) {
            val err = SttError.InsufficientMemory(memCheck.availableBytes, memCheck.requiredBytes)
            _activeProgress.value = SttProgress.Failed(err)
            return Result.failure(SttException(err))
        }

        // 3. Model availability check
        if (!provider.isModelAvailable(request.language)) {
            val err = SttError.ModelNotDownloaded(
                language = request.language,
                modelIdentifier = "${provider.id}-${request.language.code}"
            )
            _activeProgress.value = SttProgress.Failed(err)
            return Result.failure(SttException(err))
        }

        return provider.transcribe(request)
    }

    suspend fun transcribeToCaptionTrack(
        request: SttTranscriptionRequest,
        trackName: String = "Captions (${request.language.displayName})",
        preferredProviderId: String? = null
    ): Result<CaptionTrack> {
        return transcribeToCues(request, preferredProviderId).map { cues ->
            SttSubtitlesAdapter.toCaptionTrack(cues, request.language, trackName)
        }
    }
}
