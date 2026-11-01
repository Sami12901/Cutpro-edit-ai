package com.cutpro.core.ai.stt

import com.cutpro.core.ai.stt.guard.MockMemoryGuard
import com.cutpro.core.ai.stt.model.AudioSource
import com.cutpro.core.ai.stt.model.SttError
import com.cutpro.core.ai.stt.model.SttException
import com.cutpro.core.ai.stt.model.SttTranscriptionRequest
import com.cutpro.core.ai.stt.model.SupportedLanguage
import com.cutpro.core.ai.stt.provider.DeterministicMockSttProvider
import com.cutpro.core.ai.stt.subtitles.SttSubtitlesAdapter
import com.cutpro.core.subtitles.parser.SrtParser
import com.cutpro.core.subtitles.parser.WebVttParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class SpeechToTextManagerTest {

    @Test
    fun supportedLanguage_localeMapping() {
        assertThat(SupportedLanguage.fromLocale("en_US")).isEqualTo(SupportedLanguage.ENGLISH)
        assertThat(SupportedLanguage.fromLocale("en_GB")).isEqualTo(SupportedLanguage.ENGLISH)
        assertThat(SupportedLanguage.fromLocale("bn_BD")).isEqualTo(SupportedLanguage.BENGALI)
        assertThat(SupportedLanguage.fromLocale("bn_IN")).isEqualTo(SupportedLanguage.BENGALI)
        assertThat(SupportedLanguage.fromLocale("fr_FR")).isNull()
    }

    @Test
    fun manager_sufficientRam_executesSuccessfully() = runTest {
        val memoryGuard = MockMemoryGuard(availableBytes = 1024L * 1024L * 1024L) // 1 GB
        val manager = SpeechToTextManager(memoryGuard)

        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("dummy.wav")),
            language = SupportedLanguage.ENGLISH
        )

        val result = manager.transcribeToCues(request)
        assertThat(result.isSuccess).isTrue()
        val cues = result.getOrThrow()
        assertThat(cues).isNotEmpty()
        assertThat(cues.first().words).isNotEmpty()
    }

    @Test
    fun manager_lowRamGuardBlocksExecution_withInsufficientMemoryError() = runTest {
        // 350 MB available (< 500 MB required)
        val lowRamBytes = 350L * 1024L * 1024L
        val memoryGuard = MockMemoryGuard(availableBytes = lowRamBytes)
        val manager = SpeechToTextManager(memoryGuard)

        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("dummy.wav")),
            language = SupportedLanguage.ENGLISH
        )

        val result = manager.transcribeToCues(request)
        assertThat(result.isFailure).isTrue()

        val exception = result.exceptionOrNull() as? SttException
        assertThat(exception).isNotNull()
        assertThat(exception!!.error).isInstanceOf(SttError.InsufficientMemory::class.java)

        val error = exception.error as SttError.InsufficientMemory
        assertThat(error.availableBytes).isEqualTo(lowRamBytes)
    }

    @Test
    fun manager_modelMissing_failsGracefully() = runTest {
        val memoryGuard = MockMemoryGuard(availableBytes = 1024L * 1024L * 1024L)
        val mockProvider = DeterministicMockSttProvider(
            memoryGuard = memoryGuard,
            simulateModelMissing = true
        )
        val manager = SpeechToTextManager(memoryGuard, fallbackProvider = mockProvider)

        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("dummy.wav")),
            language = SupportedLanguage.BENGALI
        )

        val result = manager.transcribeToCues(request)
        assertThat(result.isFailure).isTrue()

        val exception = result.exceptionOrNull() as? SttException
        assertThat(exception).isNotNull()
        assertThat(exception!!.error).isInstanceOf(SttError.ModelNotDownloaded::class.java)
    }

    @Test
    fun transcribe_bengali_generatesTimedCues() = runTest {
        val memoryGuard = MockMemoryGuard()
        val manager = SpeechToTextManager(memoryGuard)

        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("bengali_audio.wav")),
            language = SupportedLanguage.BENGALI,
            generateWordTimings = true
        )

        val result = manager.transcribeToCues(request)
        assertThat(result.isSuccess).isTrue()

        val cues = result.getOrThrow()
        assertThat(cues).isNotEmpty()
        val firstCue = cues.first()
        assertThat(firstCue.text).contains("কাটপ্রো")
        assertThat(firstCue.words).isNotEmpty()
    }

    @Test
    fun transcribeToCaptionTrack_createsValidTrack() = runTest {
        val memoryGuard = MockMemoryGuard()
        val manager = SpeechToTextManager(memoryGuard)

        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("audio.wav")),
            language = SupportedLanguage.ENGLISH
        )

        val result = manager.transcribeToCaptionTrack(request, trackName = "Main Voiceover")
        assertThat(result.isSuccess).isTrue()

        val track = result.getOrThrow()
        assertThat(track.name).isEqualTo("Main Voiceover")
        assertThat(track.languageCode).isEqualTo("en")
        assertThat(track.cues).isNotEmpty()
    }

    @Test
    fun srtAndVttExport_roundtripFidelity() = runTest {
        val memoryGuard = MockMemoryGuard()
        val manager = SpeechToTextManager(memoryGuard)

        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("audio.wav")),
            language = SupportedLanguage.BENGALI
        )

        val cues = manager.transcribeToCues(request).getOrThrow()

        // 1. SRT export & parse
        val srt = SttSubtitlesAdapter.exportToSrt(cues)
        assertThat(srt).isNotEmpty()
        val parsedSrt = SrtParser.parse(srt)
        assertThat(parsedSrt).hasSize(cues.size)
        assertThat(parsedSrt[0].text).isEqualTo(cues[0].text)

        // 2. WebVTT export & parse
        val vtt = SttSubtitlesAdapter.exportToWebVtt(cues)
        assertThat(vtt).contains("WEBVTT")
        val parsedVtt = WebVttParser.parse(vtt)
        assertThat(parsedVtt).hasSize(cues.size)
        assertThat(parsedVtt[0].text).isEqualTo(cues[0].text)
    }

    @Test
    fun karaokeWordHighlighting_findsActiveWordAtTimestamp() = runTest {
        val memoryGuard = MockMemoryGuard()
        val manager = SpeechToTextManager(memoryGuard)

        val request = SttTranscriptionRequest(
            audioSource = AudioSource.FileAudio(File("audio.wav")),
            language = SupportedLanguage.ENGLISH,
            timelineStartOffsetUs = 0L
        )

        val cues = manager.transcribeToCues(request).getOrThrow()
        val firstWord = cues[0].words[0]
        val midWordPtsUs = (firstWord.startOffsetUs + firstWord.endOffsetUs) / 2

        val activeWord = SttSubtitlesAdapter.findActiveKaraokeWord(cues, midWordPtsUs)
        assertThat(activeWord).isEqualTo(firstWord.word)
    }
}
