package com.cutpro.core.ai.analyzer

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioRmsSilenceDetectorTest {

    @Test
    fun calculateRmsPcm16_allZero_returnsZeroAndDbFloor() {
        val samples = ShortArray(480) { 0 }
        val rms = AudioRmsCalculator.calculateRmsPcm16(samples)
        val db = AudioRmsCalculator.rmsToDb(rms)

        assertThat(rms).isEqualTo(0.0f)
        assertThat(db).isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)
    }

    @Test
    fun calculateRmsPcm16_fullScaleSquareWave_returnsZeroDbFs() {
        val samples = ShortArray(480) { if (it % 2 == 0) Short.MAX_VALUE else (-32768).toShort() }
        val rms = AudioRmsCalculator.calculateRmsPcm16(samples)
        val db = AudioRmsCalculator.rmsToDb(rms)

        assertThat(rms).isWithin(0.01f).of(1.0f)
        assertThat(db).isWithin(0.1f).of(0.0f)
    }

    @Test
    fun calculateRmsPcm16_fullScaleSineWave_returnsMinus3DbFs() {
        val sampleRate = 48000
        val freq = 1000.0
        val samples = ShortArray(sampleRate) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * 32767.0).toInt().toShort()
        }
        val rms = AudioRmsCalculator.calculateRmsPcm16(samples)
        val db = AudioRmsCalculator.rmsToDb(rms)

        assertThat(rms).isWithin(0.02f).of(0.707f)
        assertThat(db).isWithin(0.2f).of(-3.01f)
    }

    @Test
    fun calculateRmsPcmFloat_sweep_validBounds() {
        val samples = FloatArray(100) { 0.5f }
        val rms = AudioRmsCalculator.calculateRmsPcmFloat(samples)
        val db = AudioRmsCalculator.rmsToDb(rms)

        assertThat(rms).isWithin(0.001f).of(0.5f)
        assertThat(db).isWithin(0.1f).of(-6.02f)
    }

    @Test
    fun detectSilences_pureSilence_detectsSingleIntervalWithFullDuration() {
        val sampleRate = 48000
        val durationSec = 2
        val samples = ShortArray(sampleRate * durationSec) { 0 }
        val detector = AudioRmsSilenceDetector()

        val silences = detector.detectSilencesPcm16(samples, sampleRate, channelCount = 1)

        assertThat(silences).hasSize(1)
        val silence = silences.first()
        assertThat(silence.startUs).isEqualTo(0L)
        assertThat(silence.endUs).isEqualTo(2_000_000L)
        assertThat(silence.durationUs).isEqualTo(2_000_000L)
        assertThat(silence.averageDb).isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)
    }

    @Test
    fun detectSilences_speechPausesBelow500ms_ignored() {
        val sampleRate = 48000
        val speechFrames = sampleRate * 1 // 1 sec speech
        val pauseFrames = (sampleRate * 0.3).toInt() // 300ms pause (< 500ms)
        val totalFrames = speechFrames + pauseFrames + speechFrames

        val samples = ShortArray(totalFrames) { i ->
            if (i in speechFrames until (speechFrames + pauseFrames)) {
                0
            } else {
                (sin(2.0 * PI * 440.0 * i / sampleRate) * 16000.0).toInt().toShort()
            }
        }

        val detector = AudioRmsSilenceDetector(
            SilenceDetectionConfig(minSilenceDurationUs = 500_000L)
        )
        val silences = detector.detectSilencesPcm16(samples, sampleRate, channelCount = 1)

        assertThat(silences).isEmpty()
    }

    @Test
    fun detectSilences_speechPausesAbove500ms_flaggedAsSilence() {
        val sampleRate = 48000
        val speechFrames = sampleRate * 1 // 1s speech
        val pauseFrames = (sampleRate * 0.8).toInt() // 800ms pause (> 500ms)
        val totalFrames = speechFrames + pauseFrames + speechFrames

        val samples = ShortArray(totalFrames) { i ->
            if (i in speechFrames until (speechFrames + pauseFrames)) {
                0
            } else {
                (sin(2.0 * PI * 440.0 * i / sampleRate) * 16000.0).toInt().toShort()
            }
        }

        val detector = AudioRmsSilenceDetector(
            SilenceDetectionConfig(minSilenceDurationUs = 500_000L)
        )
        val silences = detector.detectSilencesPcm16(samples, sampleRate, channelCount = 1)

        assertThat(silences).hasSize(1)
        val silence = silences.first()
        assertThat(silence.durationUs).isAtLeast(700_000L)
    }

    @Test
    fun detectSilences_hysteresisPreventsFlutter() {
        val sampleRate = 48000
        val frameCount = sampleRate * 2
        val thresholdDb = -40.0f
        val hysteresisDb = 3.0f

        val samples = ShortArray(frameCount) { i ->
            val chunkIdx = i / 960
            val targetDb = if (chunkIdx % 2 == 0) -39.0 else -41.0
            val amp = Math.pow(10.0, targetDb / 20.0) * 32767.0
            (sin(2.0 * PI * 440.0 * i / sampleRate) * amp).toInt().toShort()
        }

        val detector = AudioRmsSilenceDetector(
            SilenceDetectionConfig(
                silenceThresholdDb = thresholdDb,
                hysteresisDb = hysteresisDb,
                minSilenceDurationUs = 500_000L
            )
        )
        val silences = detector.detectSilencesPcm16(samples, sampleRate, channelCount = 1)

        assertThat(silences.size).isAtMost(1)
    }

    @Test
    fun detectSilences_sampleRateParity_44100And48000() {
        val detector = AudioRmsSilenceDetector()
        val durationSec = 1

        val samples441 = ShortArray(44100 * durationSec) { 0 }
        val silences441 = detector.detectSilencesPcm16(samples441, 44100, 1)

        val samples480 = ShortArray(48000 * durationSec) { 0 }
        val silences480 = detector.detectSilencesPcm16(samples480, 48000, 1)

        assertThat(silences441).hasSize(1)
        assertThat(silences480).hasSize(1)
        assertThat(silences441.first().durationUs).isWithin(20_000L).of(1_000_000L)
        assertThat(silences480.first().durationUs).isWithin(20_000L).of(1_000_000L)
    }
}
