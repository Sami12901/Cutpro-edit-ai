package com.cutpro.core.ai.stt.model

import com.cutpro.core.common.error.AppError
import java.io.File

sealed class SttProgress {
    data object Idle : SttProgress()
    data class Initializing(val message: String = "Initializing speech recognition engine") : SttProgress()
    data class PreparingAudio(val progress: Float) : SttProgress()
    data class Transcribing(
        val progress: Float,
        val processedAudioDurationUs: Long = 0L,
        val totalAudioDurationUs: Long = 0L,
        val currentWordCount: Int = 0
    ) : SttProgress()
    data class Completed(
        val cueCount: Int,
        val wordCount: Int,
        val elapsedMillis: Long
    ) : SttProgress()
    data class Failed(val error: SttError) : SttProgress()
}

sealed class SttError(
    open val message: String,
    open val cause: Throwable? = null
) {

    data class InsufficientMemory(
        val availableBytes: Long,
        val requiredBytes: Long
    ) : SttError(
        "Insufficient system memory for speech recognition: available ${availableBytes / (1024 * 1024)}MB, required ${requiredBytes / (1024 * 1024)}MB"
    )

    data class ModelNotDownloaded(
        val language: SupportedLanguage,
        val modelIdentifier: String
    ) : SttError(
        "Local STT model '$modelIdentifier' for language '${language.displayName}' is not installed or downloaded"
    )

    data class UnsupportedLanguage(
        val language: SupportedLanguage,
        val providerName: String
    ) : SttError(
        "Language '${language.displayName}' is not supported by provider '$providerName'"
    )

    data class AudioSourceError(
        val sourceDescription: String,
        override val cause: Throwable? = null
    ) : SttError("Failed to read audio source: $sourceDescription", cause)

    data class TranscriptionError(
        val reason: String,
        override val cause: Throwable? = null
    ) : SttError("Speech recognition transcription failed: $reason", cause)

    data object Cancelled : SttError("Speech recognition cancelled by user")
}

class SttException(val error: SttError) : Exception(error.message, error.cause)

sealed interface AudioSource {
    data class FileAudio(val file: File) : AudioSource
    data class UriAudio(val uriString: String) : AudioSource
    data class PcmBuffer(
        val pcmData: ShortArray,
        val sampleRate: Int = 16000,
        val channels: Int = 1
    ) : AudioSource
}

data class SttTranscriptionRequest(
    val audioSource: AudioSource,
    val language: SupportedLanguage,
    val timelineStartOffsetUs: Long = 0L,
    val maxWordsPerCue: Int = 8,
    val maxCharsPerCue: Int = 42,
    val maxCueDurationUs: Long = 4_000_000L,
    val minCueDurationUs: Long = 800_000L,
    val generateWordTimings: Boolean = true
) {
    init {
        require(maxWordsPerCue > 0) { "maxWordsPerCue must be > 0" }
        require(maxCharsPerCue > 0) { "maxCharsPerCue must be > 0" }
        require(maxCueDurationUs >= minCueDurationUs) { "maxCueDurationUs must be >= minCueDurationUs" }
        require(timelineStartOffsetUs >= 0L) { "timelineStartOffsetUs must be >= 0" }
    }
}
