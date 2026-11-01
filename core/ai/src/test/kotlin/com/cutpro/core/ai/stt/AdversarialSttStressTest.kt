package com.cutpro.core.ai.stt

import com.cutpro.core.ai.stt.bengali.BengaliScriptHelper
import com.cutpro.core.ai.stt.guard.MemoryCheckResult
import com.cutpro.core.ai.stt.guard.MemoryGuard
import com.cutpro.core.ai.stt.guard.MockMemoryGuard
import com.cutpro.core.ai.stt.model.AudioSource
import com.cutpro.core.ai.stt.model.SttError
import com.cutpro.core.ai.stt.model.SttException
import com.cutpro.core.ai.stt.model.SttProgress
import com.cutpro.core.ai.stt.model.SttTranscriptionRequest
import com.cutpro.core.ai.stt.model.SupportedLanguage
import com.cutpro.core.ai.stt.provider.DeterministicMockSttProvider
import com.cutpro.core.motiongraphics.schema.ZeroEmojiValidator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/**
 * Adversarial stress testing for Speech-to-Text, MemoryGuard boundary verification,
 * and Bengali Unicode script processing with proportional duration alignment.
 */
class AdversarialSttStressTest {

    @Test
    fun memoryGuard_exactBoundaryAt500MB_524287999Fails_524288000Passes() = runTest {
        val boundaryRequiredBytes = MemoryGuard.MIN_REQUIRED_RAM_BYTES
        assertThat(boundaryRequiredBytes).isEqualTo(524_288_000L) // 500 * 1024 * 1024

        // 1. Exactly 1 byte BELOW the 500MB threshold (524_287_999L): MUST FAIL
        val belowThresholdBytes = 524_287_999L
        val lowGuard = MockMemoryGuard(availableBytes = belowThresholdBytes)
        val checkBelow = lowGuard.checkMemorySufficient(boundaryRequiredBytes)
        assertThat(checkBelow).isInstanceOf(MemoryCheckResult.Insufficient::class.java)
        val belowDetails = checkBelow as MemoryCheckResult.Insufficient
        assertThat(belowDetails.availableBytes).isEqualTo(belowThresholdBytes)
        assertThat(belowDetails.requiredBytes).isEqualTo(boundaryRequiredBytes)

        val lowManager = SpeechToTextManager(lowGuard)
        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("dummy.wav")),
            language = SupportedLanguage.ENGLISH
        )
        val lowResult = lowManager.transcribeToCues(request)
        assertThat(lowResult.isFailure).isTrue()
        val lowException = lowResult.exceptionOrNull() as? SttException
        assertThat(lowException).isNotNull()
        assertThat(lowException!!.error).isInstanceOf(SttError.InsufficientMemory::class.java)
        val lowError = lowException.error as SttError.InsufficientMemory
        assertThat(lowError.availableBytes).isEqualTo(belowThresholdBytes)
        assertThat(lowError.requiredBytes).isEqualTo(boundaryRequiredBytes)
        assertThat(lowManager.activeProgress.value).isInstanceOf(SttProgress.Failed::class.java)

        // 2. Exactly AT the 500MB threshold (524_288_000L): MUST SUCCEED
        val atThresholdBytes = 524_288_000L
        val atGuard = MockMemoryGuard(availableBytes = atThresholdBytes)
        val checkAt = atGuard.checkMemorySufficient(boundaryRequiredBytes)
        assertThat(checkAt).isEqualTo(MemoryCheckResult.Sufficient)

        val atManager = SpeechToTextManager(atGuard)
        val atResult = atManager.transcribeToCues(request)
        assertThat(atResult.isSuccess).isTrue()
        assertThat(atResult.getOrThrow()).isNotEmpty()

        // 3. Exactly 1 byte ABOVE the 500MB threshold (524_288_001L): MUST SUCCEED
        val aboveThresholdBytes = 524_288_001L
        val aboveGuard = MockMemoryGuard(availableBytes = aboveThresholdBytes)
        val checkAbove = aboveGuard.checkMemorySufficient(boundaryRequiredBytes)
        assertThat(checkAbove).isEqualTo(MemoryCheckResult.Sufficient)

        val aboveManager = SpeechToTextManager(aboveGuard)
        val aboveResult = aboveManager.transcribeToCues(request)
        assertThat(aboveResult.isSuccess).isTrue()

        // 4. Low memory flag active regardless of high available bytes: MUST FAIL
        val flagGuard = MockMemoryGuard(
            availableBytes = 2_000_000_000L, // 2 GB available
            isLowMemoryState = true          // System reported low memory flag
        )
        val checkFlag = flagGuard.checkMemorySufficient(boundaryRequiredBytes)
        assertThat(checkFlag).isInstanceOf(MemoryCheckResult.Insufficient::class.java)

        val flagManager = SpeechToTextManager(flagGuard)
        val flagResult = flagManager.transcribeToCues(request)
        assertThat(flagResult.isFailure).isTrue()
    }

    @Test
    fun bengaliUnicode_complexConjunctsAndDiacritics() {
        // Complex conjuncts (যুক্তবর্ণ): ক্ষ, জ্ঞ, ্ত, ্র, ঙ্ক, ঙ্গ, ঞ্চ, ঞ্জ, ণ্ট, ণ্ড, ন্থ, ন্দ, ব্দ, ল্ক, ষ্ক, ষ্ট, ষ্ণ, স্প, স্ক, স্ত্র, হ্ম, ব্ল
        val complexConjunctSentence = "যুক্তরাষ্ট্রের ক্ষিপ্র স্প্রিন্টার ব্রাহ্মণবাড়িয়ার চত্বরে বিজ্ঞানের সূক্ষ্ম স্তম্ভ প্রতিষ্ঠা করলেন।"
        val diacriticRichSentence = "ঐতিহাসিক ভৌগোলিক দৃষ্টিকোণ থেকে গুরুত্বপূর্ণ।"

        // 1. Detection of pure and complex Bengali text
        assertThat(BengaliScriptHelper.isBengaliText(complexConjunctSentence)).isTrue()
        assertThat(BengaliScriptHelper.isBengaliText(diacriticRichSentence)).isTrue()

        // 2. Word splitting preserves conjunct integrity (no splitting inside hasant sequences)
        val words = BengaliScriptHelper.splitIntoWords(complexConjunctSentence)
        assertThat(words).hasSize(10)
        assertThat(words[0]).isEqualTo("যুক্তরাষ্ট্রের") // ক্ত + ষ্ট্র
        assertThat(words[1]).isEqualTo("ক্ষিপ্র")        // ক্ষ + প্র
        assertThat(words[2]).isEqualTo("স্প্রিন্টার")     // স্প্র + ন্ট
        assertThat(words[3]).isEqualTo("ব্রাহ্মণবাড়িয়ার") // ব্রা + হ্ম
        assertThat(words[4]).isEqualTo("চত্বরে")        // ত্ব
        assertThat(words[5]).isEqualTo("বিজ্ঞানের")      // জ্ঞ
        assertThat(words[6]).isEqualTo("সূক্ষ্ম")         // ক্ষ্ম
        assertThat(words[7]).isEqualTo("স্তম্ভ")         // স্ত + ম্ভ
        assertThat(words[8]).isEqualTo("প্রতিষ্ঠা")       // প্র + ষ্ঠ
        assertThat(words[9]).isEqualTo("করলেন।")

        // 3. Ensure zero emoji in all Bengali tokens
        for (word in words) {
            assertThat(ZeroEmojiValidator.containsEmoji(word)).isFalse()
        }
    }

    @Test
    fun bengaliPunctuation_dandaDoubleDandaAndSentenceSplitting() {
        // Bengali Danda (। \u0964) and Double Danda (॥ \u0965)
        val multiSentenceText = "কাটপ্রো একটি আধুনিক ভিডিও এডিটর। এটি অফলাইনে কাজ করে॥ আপনি কি এটি ব্যবহার করছেন? অবশ্যই করবেন!"
        val sentences = BengaliScriptHelper.splitIntoSentences(multiSentenceText)

        assertThat(sentences).hasSize(4)
        assertThat(sentences[0]).isEqualTo("কাটপ্রো একটি আধুনিক ভিডিও এডিটর।")
        assertThat(sentences[1]).isEqualTo("এটি অফলাইনে কাজ করে॥")
        assertThat(sentences[2]).isEqualTo("আপনি কি এটি ব্যবহার করছেন?")
        assertThat(sentences[3]).isEqualTo("অবশ্যই করবেন!")

        // Code point validation for Danda and Double Danda
        assertThat(BengaliScriptHelper.isBengaliCodePoint(0x0964)).isTrue() // ।
        assertThat(BengaliScriptHelper.isBengaliCodePoint(0x0965)).isTrue() // ॥
        assertThat(BengaliScriptHelper.isBengaliCodePoint(0x09CD)).isTrue() // ্ (hasant)
    }

    @Test
    fun bengaliScript_mixedEnglishAndBengaliTokens() {
        val mixedText = "CutPro দিয়ে সহজেই 4K 60fps HDR ভিডিও তৈরি ও এডিটিং করুন।"

        val words = BengaliScriptHelper.splitIntoWords(mixedText)
        assertThat(words).contains("CutPro")
        assertThat(words).contains("4K")
        assertThat(words).contains("60fps")
        assertThat(words).contains("HDR")
        assertThat(words).contains("ভিডিও")
        assertThat(words).contains("তৈরি")

        // Alignment with mixed tokens preserves sequence and timing
        val cueStartUs = 2_000_000L
        val cueEndUs = 6_000_000L
        val timedWords = BengaliScriptHelper.alignWordsToCueDuration(words, cueStartUs, cueEndUs)

        assertThat(timedWords).hasSize(words.size)
        assertThat(timedWords.first().word).isEqualTo("CutPro")
        assertThat(timedWords.first().startOffsetUs).isEqualTo(cueStartUs)
        assertThat(timedWords.last().word).isEqualTo("করুন।")
        assertThat(timedWords.last().endOffsetUs).isEqualTo(cueEndUs)

        // Microsecond continuity across mixed tokens
        for (i in 1 until timedWords.size) {
            assertThat(timedWords[i].startOffsetUs).isEqualTo(timedWords[i - 1].endOffsetUs)
        }
    }

    @Test
    fun syllableGraphemeWeightedTiming_proportionalDurationDistribution() {
        // Words with drastically varying lengths:
        // "ও" (1 char)
        // "এবং" (3 chars)
        // "ভিডিও" (5 chars)
        // "আন্তর্জাতিক" (12 chars)
        // "যুক্তরাষ্ট্রভিত্তিক" (19 chars)
        val testWords = listOf("ও", "এবং", "ভিডিও", "আন্তর্জাতিক", "যুক্তরাষ্ট্রভিত্তিক")
        val cueStartUs = 10_000_000L
        val cueEndUs = 20_000_000L // 10.0s total duration

        val timedWords = BengaliScriptHelper.alignWordsToCueDuration(testWords, cueStartUs, cueEndUs)

        assertThat(timedWords).hasSize(5)

        val duration1 = timedWords[0].endOffsetUs - timedWords[0].startOffsetUs // "ও"
        val duration2 = timedWords[1].endOffsetUs - timedWords[1].startOffsetUs // "এবং"
        val duration3 = timedWords[2].endOffsetUs - timedWords[2].startOffsetUs // "ভিডিও"
        val duration4 = timedWords[3].endOffsetUs - timedWords[3].startOffsetUs // "আন্তর্জাতিক"
        val duration5 = timedWords[4].endOffsetUs - timedWords[4].startOffsetUs // "যুক্তরাষ্ট্রভিত্তিক"

        // Strictly increasing durations based on length weight
        assertThat(duration2).isGreaterThan(duration1)
        assertThat(duration3).isGreaterThan(duration2)
        assertThat(duration4).isGreaterThan(duration3)
        assertThat(duration5).isGreaterThan(duration4)

        // "যুক্তরাষ্ট্রভিত্তিক" (19 chars) must have over 10x the duration of "ও" (1 char)
        assertThat(duration5).isGreaterThan(duration1 * 10)

        // Perfect boundary alignment with zero microsecond drift
        assertThat(timedWords.first().startOffsetUs).isEqualTo(cueStartUs)
        assertThat(timedWords.last().endOffsetUs).isEqualTo(cueEndUs)

        // Seamless continuity
        for (i in 1 until timedWords.size) {
            assertThat(timedWords[i].startOffsetUs).isEqualTo(timedWords[i - 1].endOffsetUs)
        }
    }

    @Test
    fun syllableGraphemeWeightedTiming_primeNumberDuration_zeroRoundingLoss() {
        // Stress test with prime number duration to verify zero rounding error
        val words = listOf("প্রথম", "দ্বিতীয়", "তৃতীয়", "চতুর্থ", "পঞ্চম", "ষষ্ঠ", "সপ্তম")
        val cueStartUs = 1_333_337L
        val cueEndUs = 9_876_543L // Total: 8_543_206L us

        val timedWords = BengaliScriptHelper.alignWordsToCueDuration(words, cueStartUs, cueEndUs)

        assertThat(timedWords).hasSize(7)
        assertThat(timedWords.first().startOffsetUs).isEqualTo(cueStartUs)
        assertThat(timedWords.last().endOffsetUs).isEqualTo(cueEndUs)

        val computedTotalDuration = timedWords.sumOf { it.endOffsetUs - it.startOffsetUs }
        val expectedTotalDuration = cueEndUs - cueStartUs
        assertThat(computedTotalDuration).isEqualTo(expectedTotalDuration)
    }

    @Test
    fun deterministicMockStt_bengaliCorpus_zeroEmojiVerified() = runTest {
        val guard = MockMemoryGuard()
        val provider = DeterministicMockSttProvider(memoryGuard = guard)

        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("bengali_input.wav")),
            language = SupportedLanguage.BENGALI,
            generateWordTimings = true
        )

        val result = provider.transcribe(request)
        assertThat(result.isSuccess).isTrue()

        val cues = result.getOrThrow()
        assertThat(cues).isNotEmpty()

        for (cue in cues) {
            assertThat(ZeroEmojiValidator.containsEmoji(cue.text)).isFalse()
            for (w in cue.words) {
                assertThat(ZeroEmojiValidator.containsEmoji(w.word)).isFalse()
            }
        }
    }
}
