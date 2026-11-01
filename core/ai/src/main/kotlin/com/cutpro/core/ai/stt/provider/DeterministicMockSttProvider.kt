package com.cutpro.core.ai.stt.provider

import com.cutpro.core.ai.stt.SpeechToTextProvider
import com.cutpro.core.ai.stt.bengali.BengaliScriptHelper
import com.cutpro.core.ai.stt.guard.MemoryCheckResult
import com.cutpro.core.ai.stt.guard.MemoryGuard
import com.cutpro.core.ai.stt.model.SttError
import com.cutpro.core.ai.stt.model.SttException
import com.cutpro.core.ai.stt.model.SttProgress
import com.cutpro.core.ai.stt.model.SttTranscriptionRequest
import com.cutpro.core.ai.stt.model.SupportedLanguage
import com.cutpro.core.motiongraphics.schema.ZeroEmojiValidator
import com.cutpro.core.subtitles.model.CaptionCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Offline deterministic mock STT provider for English and Bengali.
 * Provides reliable fallback and offline verification without external network calls.
 */
class DeterministicMockSttProvider(
    private val memoryGuard: MemoryGuard? = null,
    val simulateDelayMs: Long = 0L,
    var simulateLowMemory: Boolean = false,
    var simulateModelMissing: Boolean = false,
    var customEnglishCorpus: List<String>? = null,
    var customBengaliCorpus: List<String>? = null
) : SpeechToTextProvider {

    override val id: String = "mock-deterministic-stt"
    override val displayName: String = "Built-in Deterministic Simulation"
    override val supportedLanguages: Set<SupportedLanguage> = setOf(
        SupportedLanguage.ENGLISH,
        SupportedLanguage.BENGALI
    )

    private val _progress = MutableStateFlow<SttProgress>(SttProgress.Idle)
    override val progress: StateFlow<SttProgress> = _progress.asStateFlow()

    override suspend fun isModelAvailable(language: SupportedLanguage): Boolean {
        if (simulateModelMissing) return false
        return language in supportedLanguages
    }

    override suspend fun getEstimatedMemoryRequirementBytes(language: SupportedLanguage): Long {
        return 50L * 1024L * 1024L // 50MB lightweight footprint
    }

    override suspend fun initialize(language: SupportedLanguage): Result<Unit> {
        if (language !in supportedLanguages) {
            return Result.failure(SttException(SttError.UnsupportedLanguage(language, displayName)))
        }
        _progress.value = SttProgress.Initializing("Initializing deterministic speech recognition engine")
        if (simulateDelayMs > 0) delay(simulateDelayMs / 4)
        _progress.value = SttProgress.Idle
        return Result.success(Unit)
    }

    override suspend fun transcribe(request: SttTranscriptionRequest): Result<List<CaptionCue>> {
        val startTimeMs = System.currentTimeMillis()
        try {
            // 1. Language validation
            if (request.language !in supportedLanguages) {
                val err = SttError.UnsupportedLanguage(request.language, displayName)
                _progress.value = SttProgress.Failed(err)
                return Result.failure(SttException(err))
            }

            // 2. Memory guard check
            if (simulateLowMemory) {
                val err = SttError.InsufficientMemory(
                    availableBytes = 200L * 1024L * 1024L,
                    requiredBytes = MemoryGuard.MIN_REQUIRED_RAM_BYTES
                )
                _progress.value = SttProgress.Failed(err)
                return Result.failure(SttException(err))
            }

            memoryGuard?.let { guard ->
                val check = guard.checkMemorySufficient(MemoryGuard.MIN_REQUIRED_RAM_BYTES)
                if (check is MemoryCheckResult.Insufficient) {
                    val err = SttError.InsufficientMemory(check.availableBytes, check.requiredBytes)
                    _progress.value = SttProgress.Failed(err)
                    return Result.failure(SttException(err))
                }
            }

            // 3. Model availability check
            if (simulateModelMissing || !isModelAvailable(request.language)) {
                val err = SttError.ModelNotDownloaded(request.language, "$id-${request.language.code}")
                _progress.value = SttProgress.Failed(err)
                return Result.failure(SttException(err))
            }

            // 4. Progress phase: Audio Preparation
            _progress.value = SttProgress.PreparingAudio(0.5f)
            if (simulateDelayMs > 0) delay(simulateDelayMs / 4)
            _progress.value = SttProgress.PreparingAudio(1.0f)

            // 5. Select corpus sentences
            val rawSentences = when (request.language) {
                SupportedLanguage.ENGLISH -> customEnglishCorpus ?: DEFAULT_ENGLISH_SENTENCES
                SupportedLanguage.BENGALI -> customBengaliCorpus ?: DEFAULT_BENGALI_SENTENCES
            }

            val cues = mutableListOf<CaptionCue>()
            var timelineCursorUs = request.timelineStartOffsetUs
            var totalWords = 0

            _progress.value = SttProgress.Transcribing(progress = 0.5f, currentWordCount = 0)
            if (simulateDelayMs > 0) delay(simulateDelayMs / 2)

            for (sentence in rawSentences) {
                ZeroEmojiValidator.validateNoEmoji(sentence, "transcribed_sentence")

                val words = BengaliScriptHelper.splitIntoWords(sentence)
                if (words.isEmpty()) continue

                val chunks = chunkWordsIntoCues(words, request.maxWordsPerCue, request.maxCharsPerCue)
                for (chunkWords in chunks) {
                    val chunkText = chunkWords.joinToString(" ")
                    val cueDurationUs = calculateCueDurationUs(chunkWords, request.minCueDurationUs, request.maxCueDurationUs)
                    val cueStartUs = timelineCursorUs
                    val cueEndUs = cueStartUs + cueDurationUs

                    val timedWords = if (request.generateWordTimings) {
                        BengaliScriptHelper.alignWordsToCueDuration(chunkWords, cueStartUs, cueEndUs)
                    } else {
                        emptyList()
                    }

                    cues.add(
                        CaptionCue(
                            startTimeUs = cueStartUs,
                            endTimeUs = cueEndUs,
                            text = chunkText,
                            words = timedWords
                        )
                    )

                    totalWords += chunkWords.size
                    timelineCursorUs = cueEndUs + 250_000L
                }
            }

            val elapsedMs = System.currentTimeMillis() - startTimeMs
            _progress.value = SttProgress.Completed(
                cueCount = cues.size,
                wordCount = totalWords,
                elapsedMillis = elapsedMs
            )
            return Result.success(cues)
        } catch (e: CancellationException) {
            _progress.value = SttProgress.Failed(SttError.Cancelled)
            throw e
        } catch (e: Exception) {
            val err = SttError.TranscriptionError("Deterministic transcription failure: ${e.message}", e)
            _progress.value = SttProgress.Failed(err)
            return Result.failure(SttException(err))
        }
    }

    override suspend fun release() {
        _progress.value = SttProgress.Idle
    }

    private fun chunkWordsIntoCues(words: List<String>, maxWords: Int, maxChars: Int): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var cur = mutableListOf<String>()
        var curCharCount = 0

        for (w in words) {
            val nextCharCount = if (cur.isEmpty()) w.length else curCharCount + 1 + w.length
            if ((cur.size >= maxWords || nextCharCount > maxChars) && cur.isNotEmpty()) {
                result.add(cur)
                cur = mutableListOf(w)
                curCharCount = w.length
            } else {
                cur.add(w)
                curCharCount = nextCharCount
            }
        }
        if (cur.isNotEmpty()) {
            result.add(cur)
        }
        return result
    }

    private fun calculateCueDurationUs(words: List<String>, minUs: Long, maxUs: Long): Long {
        val estimatedUs = words.size * 350_000L
        return estimatedUs.coerceIn(minUs, maxUs)
    }

    companion object {
        val DEFAULT_ENGLISH_SENTENCES = listOf(
            "Welcome to CutPro video editor.",
            "Professional multitrack editing made simple and powerful.",
            "Offline native Android performance with zero latency."
        )

        val DEFAULT_BENGALI_SENTENCES = listOf(
            "কাটপ্রো ভিডিও এডিটরে স্বাগতম।",
            "সহজে পেশাদার মাল্টিট্র্যাক ভিডিও তৈরি করুন।",
            "সম্পূর্ণ অফলাইন এবং দ্রুতগতির ভিডিও এডিটিং।"
        )
    }
}
