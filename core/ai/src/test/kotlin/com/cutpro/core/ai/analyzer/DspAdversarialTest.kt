package com.cutpro.core.ai.analyzer

import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Adversarial and stress test harness for DSP Audio Analyzers in :core:ai.
 * Strictly zero emojis.
 */
class DspAdversarialTest {

    private val testMediaSource = MediaSourceInfo(
        uriString = "content://media/external/video/dsp_test",
        fileName = "dsp_test.mp4",
        mimeType = "video/mp4",
        durationUs = 60_000_000L,
        width = 1920,
        height = 1080
    )

    private fun createClip(
        id: String,
        trackId: String,
        timelineStartUs: Long,
        durationUs: Long,
        sourceInUs: Long = 0L,
        sourceOutUs: Long = durationUs,
        speedMultiplier: Float = 1.0f
    ): TimelineClip {
        return TimelineClip(
            id = id,
            trackId = trackId,
            mediaSource = testMediaSource,
            timelineStartUs = timelineStartUs,
            timelineDurationUs = durationUs,
            sourceInUs = sourceInUs,
            sourceOutUs = sourceOutUs,
            speedMultiplier = speedMultiplier,
            fitMode = ClipFitMode.FIT,
            transform = ClipTransform()
        )
    }

    // =========================================================================
    // 1. AudioRmsCalculator Stress Tests
    // =========================================================================

    @Test
    fun testRmsPcm16_emptyArray_returnsZeroRmsAndSilenceFloor() {
        val empty = ShortArray(0)
        val rms = AudioRmsCalculator.calculateRmsPcm16(empty)
        val db = AudioRmsCalculator.rmsToDb(rms)
        val chunkDb = AudioRmsCalculator.calculatePcm16ChunkDb(empty)

        assertThat(rms).isEqualTo(0.0f)
        assertThat(db).isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)
        assertThat(chunkDb).isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)
    }

    @Test
    fun testRmsPcmFloat_emptyArray_returnsZeroRmsAndSilenceFloor() {
        val empty = FloatArray(0)
        val rms = AudioRmsCalculator.calculateRmsPcmFloat(empty)
        val db = AudioRmsCalculator.rmsToDb(rms)
        val chunkDb = AudioRmsCalculator.calculatePcmFloatChunkDb(empty)

        assertThat(rms).isEqualTo(0.0f)
        assertThat(db).isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)
        assertThat(chunkDb).isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)
    }

    @Test
    fun testRmsPcm16_fullScaleSignal_reachesZeroDbFs() {
        val maxPos = ShortArray(1000) { Short.MAX_VALUE }
        val rmsPos = AudioRmsCalculator.calculateRmsPcm16(maxPos)
        val dbPos = AudioRmsCalculator.rmsToDb(rmsPos)

        assertThat(rmsPos).isWithin(0.001f).of(1.0f)
        assertThat(dbPos).isWithin(0.01f).of(0.0f)

        val maxNeg = ShortArray(1000) { (-32768).toShort() }
        val rmsNeg = AudioRmsCalculator.calculateRmsPcm16(maxNeg)
        val dbNeg = AudioRmsCalculator.rmsToDb(rmsNeg)

        assertThat(rmsNeg).isWithin(0.001f).of(1.0f)
        assertThat(dbNeg).isWithin(0.01f).of(0.0f)

        val square = ShortArray(1000) { if (it % 2 == 0) Short.MAX_VALUE else (-32768).toShort() }
        val rmsSquare = AudioRmsCalculator.calculateRmsPcm16(square)
        val dbSquare = AudioRmsCalculator.rmsToDb(rmsSquare)

        assertThat(rmsSquare).isWithin(0.001f).of(1.0f)
        assertThat(dbSquare).isWithin(0.01f).of(0.0f)
    }

    @Test
    fun testRmsPcmFloat_fullScaleSignal_reachesZeroDbFs() {
        val maxFloat = FloatArray(1000) { 1.0f }
        val rms = AudioRmsCalculator.calculateRmsPcmFloat(maxFloat)
        val db = AudioRmsCalculator.rmsToDb(rms)

        assertThat(rms).isWithin(0.001f).of(1.0f)
        assertThat(db).isWithin(0.01f).of(0.0f)

        val negFloat = FloatArray(1000) { -1.0f }
        val rmsNeg = AudioRmsCalculator.calculateRmsPcmFloat(negFloat)
        val dbNeg = AudioRmsCalculator.rmsToDb(rmsNeg)

        assertThat(rmsNeg).isWithin(0.001f).of(1.0f)
        assertThat(dbNeg).isWithin(0.01f).of(0.0f)
    }

    @Test
    fun testRms_silenceFloorAndSubEpsilonClamping() {
        val zero16 = ShortArray(1000) { 0 }
        assertThat(AudioRmsCalculator.calculatePcm16ChunkDb(zero16))
            .isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)

        val zeroFloat = FloatArray(1000) { 0.0f }
        assertThat(AudioRmsCalculator.calculatePcmFloatChunkDb(zeroFloat))
            .isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)

        // Sub-epsilon float values (1e-6 < EPSILON 1e-5)
        val subEpsilon = FloatArray(1000) { 1e-6f }
        assertThat(AudioRmsCalculator.calculatePcmFloatChunkDb(subEpsilon))
            .isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)

        // Negative RMS input to rmsToDb
        assertThat(AudioRmsCalculator.rmsToDb(-1.0f))
            .isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)
    }

    @Test
    fun testRmsPcmFloat_nanSamples_returnsSilenceFloor() {
        val nanSamples = FloatArray(100) { Float.NaN }
        val rms = AudioRmsCalculator.calculateRmsPcmFloat(nanSamples)
        assertThat(rms.isNaN()).isTrue()

        val db = AudioRmsCalculator.rmsToDb(rms)
        assertThat(db).isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)

        val chunkDb = AudioRmsCalculator.calculatePcmFloatChunkDb(nanSamples)
        assertThat(chunkDb).isEqualTo(AudioRmsCalculator.SILENCE_DB_FLOOR)
    }

    @Test
    fun testRmsPcmFloat_infiniteSamples_handledOrProducesInfinity() {
        val infSamples = FloatArray(100) { Float.POSITIVE_INFINITY }
        val rms = AudioRmsCalculator.calculateRmsPcmFloat(infSamples)
        val db = AudioRmsCalculator.rmsToDb(rms)

        // Verifying behavior: positive infinity propagates to float infinity
        assertThat(rms.isInfinite()).isTrue()
        assertThat(db.isInfinite()).isTrue()
    }

    @Test
    fun testRms_boundarySlicing_startIndexAndLength() {
        val samples = ShortArray(100) { 1000 }
        // Length 0 returns 0.0
        assertThat(AudioRmsCalculator.calculateRmsPcm16(samples, startIndex = 0, length = 0))
            .isEqualTo(0.0f)

        // StartIndex at or beyond size returns 0.0
        assertThat(AudioRmsCalculator.calculateRmsPcm16(samples, startIndex = 100, length = 10))
            .isEqualTo(0.0f)
        assertThat(AudioRmsCalculator.calculateRmsPcm16(samples, startIndex = 150, length = 10))
            .isEqualTo(0.0f)

        // Length exceeding available elements is capped to available
        val rmsCapped = AudioRmsCalculator.calculateRmsPcm16(samples, startIndex = 90, length = 50)
        val rmsExpected = AudioRmsCalculator.calculateRmsPcm16(samples, startIndex = 90, length = 10)
        assertThat(rmsCapped).isEqualTo(rmsExpected)
    }

    @Test
    fun testRmsPcm16_largeBuffer_numericalStability() {
        // 480,000 samples = 10 seconds of 48kHz audio at full amplitude
        val largeBuffer = ShortArray(480_000) { Short.MAX_VALUE }
        val rms = AudioRmsCalculator.calculateRmsPcm16(largeBuffer)
        val db = AudioRmsCalculator.rmsToDb(rms)

        assertThat(rms).isWithin(0.001f).of(1.0f)
        assertThat(db).isWithin(0.01f).of(0.0f)
    }

    // =========================================================================
    // 2. AudioRmsSilenceDetector Stress Tests
    // =========================================================================

    @Test
    fun testSilenceDetector_20msChunking_sampleRateParity() {
        val rates = listOf(48000, 44100, 16000, 8000)
        for (rate in rates) {
            val detector = AudioRmsSilenceDetector(
                SilenceDetectionConfig(
                    silenceThresholdDb = -40.0f,
                    minSilenceDurationUs = 500_000L,
                    chunkDurationUs = 20_000L
                )
            )
            // 1 second of silence
            val samples = ShortArray(rate) { 0 }
            val silences = detector.detectSilencesPcm16(samples, rate, channelCount = 1)

            assertThat(silences).hasSize(1)
            val silence = silences.first()
            assertThat(silence.startUs).isEqualTo(0L)
            // Duration should be within 1 chunk (20ms) of 1,000,000us
            assertThat(silence.durationUs).isWithin(20_000L).of(1_000_000L)
        }
    }

    @Test
    fun testSilenceDetector_hysteresis_preventsChatterAtThreshold() {
        val sampleRate = 48000
        val thresholdDb = -40.0f
        val hysteresisDb = 3.0f
        val detector = AudioRmsSilenceDetector(
            SilenceDetectionConfig(
                silenceThresholdDb = thresholdDb,
                hysteresisDb = hysteresisDb,
                minSilenceDurationUs = 500_000L
            )
        )

        // Total 2 seconds (100 chunks of 20ms)
        // Alternate between -41 dBFS (enters silence) and -38 dBFS (below exit threshold -37 dBFS)
        // With 3dB hysteresis, once in silence, -38 dBFS should NOT cause an exit!
        val chunkCount = 100
        val samplesPerChunk = 960
        val samples = ShortArray(chunkCount * samplesPerChunk) { i ->
            val chunkIdx = i / samplesPerChunk
            val targetDb = if (chunkIdx % 2 == 0) -41.0 else -38.0
            val amp = 10.0.pow(targetDb / 20.0) * 32767.0
            (sin(2.0 * PI * 440.0 * i / sampleRate) * amp).toInt().toShort()
        }

        val silences = detector.detectSilencesPcm16(samples, sampleRate, channelCount = 1)

        // Hysteresis must keep the detector in silence without chattering
        assertThat(silences).hasSize(1)
        assertThat(silences.first().durationUs).isAtLeast(1_900_000L)
    }

    @Test
    fun testSilenceDetector_hysteresisExit_triggersWhenAboveExitThreshold() {
        val sampleRate = 48000
        val thresholdDb = -40.0f
        val hysteresisDb = 3.0f
        val detector = AudioRmsSilenceDetector(
            SilenceDetectionConfig(
                silenceThresholdDb = thresholdDb,
                hysteresisDb = hysteresisDb,
                minSilenceDurationUs = 500_000L
            )
        )

        // 1 second silence (-50 dBFS) followed by 1 second speech (-20 dBFS, > -37 dBFS exit threshold)
        val chunkCount = 100
        val samplesPerChunk = 960
        val samples = ShortArray(chunkCount * samplesPerChunk) { i ->
            val chunkIdx = i / samplesPerChunk
            val targetDb = if (chunkIdx < 50) -50.0 else -20.0
            val amp = 10.0.pow(targetDb / 20.0) * 32767.0
            (sin(2.0 * PI * 440.0 * i / sampleRate) * amp).toInt().toShort()
        }

        val silences = detector.detectSilencesPcm16(samples, sampleRate, channelCount = 1)

        assertThat(silences).hasSize(1)
        val silence = silences.first()
        assertThat(silence.startUs).isEqualTo(0L)
        // Must exit at ~1.0 second
        assertThat(silence.endUs).isWithin(40_000L).of(1_000_000L)
    }

    @Test
    fun testSilenceDetector_minSilenceDurationFiltering_480msVs520ms() {
        val sampleRate = 48000
        val samplesPerChunk = 960 // 20ms
        val detector = AudioRmsSilenceDetector(
            SilenceDetectionConfig(minSilenceDurationUs = 500_000L)
        )

        // Case A: 24 chunks of silence = 480ms < 500ms
        val samples480ms = ShortArray(24 * samplesPerChunk) { 0 }
        val silences480ms = detector.detectSilencesPcm16(samples480ms, sampleRate, 1)
        assertThat(silences480ms).isEmpty()

        // Case B: 26 chunks of silence = 520ms >= 500ms
        val samples520ms = ShortArray(26 * samplesPerChunk) { 0 }
        val silences520ms = detector.detectSilencesPcm16(samples520ms, sampleRate, 1)
        assertThat(silences520ms).hasSize(1)
        assertThat(silences520ms.first().durationUs).isAtLeast(500_000L)
    }

    @Test
    fun testSilenceDetector_adaptiveNoiseFloor_percentileCalculation() {
        val sampleRate = 48000
        val samplesPerChunk = 960
        val totalChunks = 100

        // 80 chunks of quiet noise (-45 dBFS), 20 chunks of speech (-15 dBFS)
        val samples = ShortArray(totalChunks * samplesPerChunk) { i ->
            val chunkIdx = i / samplesPerChunk
            val targetDb = if (chunkIdx < 80) -45.0 else -15.0
            val amp = 10.0.pow(targetDb / 20.0) * 32767.0
            (sin(2.0 * PI * 440.0 * i / sampleRate) * amp).toInt().toShort()
        }

        val detector = AudioRmsSilenceDetector(
            SilenceDetectionConfig(
                adaptiveNoiseFloor = true,
                minSilenceDurationUs = 500_000L
            )
        )

        val silences = detector.detectSilencesPcm16(samples, sampleRate, 1)

        // In quiet noise (-45 dBFS), adaptive threshold is around -45 + 6 = -39 dBFS
        // The first 80 chunks (-45 dBFS) are below -39 dBFS, detected as silence!
        assertThat(silences).isNotEmpty()
        val silence = silences.first()
        assertThat(silence.durationUs).isAtLeast(1_500_000L)
    }

    @Test
    fun testSilenceDetector_stereo_interleavedChannelsEnergy() {
        val sampleRate = 48000
        val samplesPerChunk = 960 * 2 // 2 channels
        val totalChunks = 30 // 600ms

        // Left channel silent (0), Right channel loud speech (-15 dBFS)
        val samples = ShortArray(totalChunks * samplesPerChunk) { i ->
            val isRightChannel = i % 2 == 1
            if (isRightChannel) {
                val amp = 10.0.pow(-15.0 / 20.0) * 32767.0
                (sin(2.0 * PI * 440.0 * (i / 2) / sampleRate) * amp).toInt().toShort()
            } else {
                0
            }
        }

        val detector = AudioRmsSilenceDetector(
            SilenceDetectionConfig(
                silenceThresholdDb = -40.0f,
                minSilenceDurationUs = 500_000L
            )
        )

        val silences = detector.detectSilencesPcm16(samples, sampleRate, channelCount = 2)

        // Because right channel is loud, total RMS energy is around -18 dBFS (> -40 dBFS)
        // Therefore, it must NOT be flagged as silence!
        assertThat(silences).isEmpty()
    }

    // =========================================================================
    // 3. JumpCutOptimizer Stress Tests
    // =========================================================================

    @Test
    fun testJumpCut_80msSafetyBuffers_middleSilence() {
        val optimizer = JumpCutOptimizer(
            JumpCutConfig(
                postSpeechSafetyBufferUs = 80_000L,
                preSpeechSafetyBufferUs = 80_000L,
                minCutDurationUs = 200_000L
            )
        )
        val clip = createClip("c1", "t1", timelineStartUs = 0L, durationUs = 10_000_000L)
        val rawSilences = listOf(
            SilenceInterval(startUs = 2_000_000L, endUs = 4_000_000L, averageDb = -50f, peakDb = -45f)
        )

        val suggestions = optimizer.optimizeSilencesForClip(clip, rawSilences)

        assertThat(suggestions).hasSize(1)
        val cut = suggestions.first()
        // Cut start = 2.0s + 80ms = 2.080s
        // Cut end = 4.0s - 80ms = 3.920s
        // Excised = 1.840s
        assertThat(cut.cutStartTimelineUs).isEqualTo(2_080_000L)
        assertThat(cut.cutEndTimelineUs).isEqualTo(3_920_000L)
        assertThat(cut.excisedDurationUs).isEqualTo(1_840_000L)
        assertThat(cut.postSpeechBufferUs).isEqualTo(80_000L)
        assertThat(cut.preSpeechBufferUs).isEqualTo(80_000L)
    }

    @Test
    fun testJumpCut_leadingSilence_omitsPostSpeechSafetyBuffer() {
        val optimizer = JumpCutOptimizer()
        val clip = createClip("c1", "t1", timelineStartUs = 0L, durationUs = 5_000_000L)
        // Silence from 0s to 1.5s
        val rawSilences = listOf(
            SilenceInterval(startUs = 0L, endUs = 1_500_000L, averageDb = -50f, peakDb = -48f)
        )

        val suggestions = optimizer.optimizeSilencesForClip(clip, rawSilences)

        assertThat(suggestions).hasSize(1)
        val cut = suggestions.first()
        // Leading silence starts at 0 without post-speech buffer
        assertThat(cut.cutStartTimelineUs).isEqualTo(0L)
        assertThat(cut.cutEndTimelineUs).isEqualTo(1_420_000L) // 1.5s - 80ms
        assertThat(cut.postSpeechBufferUs).isEqualTo(0L)
        assertThat(cut.preSpeechBufferUs).isEqualTo(80_000L)
    }

    @Test
    fun testJumpCut_trailingSilence_omitsPreSpeechSafetyBuffer() {
        val optimizer = JumpCutOptimizer()
        val clip = createClip("c1", "t1", timelineStartUs = 0L, durationUs = 5_000_000L)
        // Silence from 3.5s to 5.0s (clip end)
        val rawSilences = listOf(
            SilenceInterval(startUs = 3_500_000L, endUs = 5_000_000L, averageDb = -50f, peakDb = -48f)
        )

        val suggestions = optimizer.optimizeSilencesForClip(clip, rawSilences)

        assertThat(suggestions).hasSize(1)
        val cut = suggestions.first()
        // Trailing silence ends at clip end without pre-speech buffer
        assertThat(cut.cutStartTimelineUs).isEqualTo(3_580_000L) // 3.5s + 80ms
        assertThat(cut.cutEndTimelineUs).isEqualTo(5_000_000L)
        assertThat(cut.postSpeechBufferUs).isEqualTo(80_000L)
        assertThat(cut.preSpeechBufferUs).isEqualTo(0L)
    }

    @Test
    fun testJumpCut_minimumCutDuration_rejectionBoundary() {
        val optimizer = JumpCutOptimizer(
            JumpCutConfig(
                postSpeechSafetyBufferUs = 80_000L,
                preSpeechSafetyBufferUs = 80_000L,
                minCutDurationUs = 200_000L
            )
        )
        val clip = createClip("c1", "t1", timelineStartUs = 0L, durationUs = 10_000_000L)

        // 350ms silence: 350ms - 160ms buffer = 190ms residual (< 200ms) -> REJECTED
        val raw350ms = listOf(
            SilenceInterval(startUs = 1_000_000L, endUs = 1_350_000L, averageDb = -50f, peakDb = -45f)
        )
        assertThat(optimizer.optimizeSilencesForClip(clip, raw350ms)).isEmpty()

        // 370ms silence: 370ms - 160ms buffer = 210ms residual (>= 200ms) -> ACCEPTED
        val raw370ms = listOf(
            SilenceInterval(startUs = 1_000_000L, endUs = 1_370_000L, averageDb = -50f, peakDb = -45f)
        )
        val result370ms = optimizer.optimizeSilencesForClip(clip, raw370ms)
        assertThat(result370ms).hasSize(1)
        assertThat(result370ms.first().excisedDurationUs).isEqualTo(210_000L)
    }

    @Test
    fun testJumpCut_speedMultiplier_timestampMapping() {
        val optimizer = JumpCutOptimizer()
        // Speed multiplier 2.0x, source duration 10s, timeline duration 5s
        val clip2x = createClip(
            id = "c1",
            trackId = "t1",
            timelineStartUs = 1_000_000L,
            durationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            speedMultiplier = 2.0f
        )
        val rawSilences = listOf(
            SilenceInterval(startUs = 2_000_000L, endUs = 4_000_000L, averageDb = -50f, peakDb = -45f)
        )

        val suggestions2x = optimizer.optimizeSilencesForClip(clip2x, rawSilences)
        assertThat(suggestions2x).hasSize(1)
        val cut2x = suggestions2x.first()

        // Source cut: 2_080_000 to 3_920_000 (delta = 1_840_000us)
        // Timeline cut: 1_000_000 + 2_080_000/2 = 2_040_000us
        // Timeline cut end: 1_000_000 + 3_920_000/2 = 2_960_000us
        // Timeline duration: 920_000us
        assertThat(cut2x.cutStartTimelineUs).isEqualTo(2_040_000L)
        assertThat(cut2x.cutEndTimelineUs).isEqualTo(2_960_000L)
        assertThat(cut2x.excisedDurationUs).isEqualTo(920_000L)

        // Speed multiplier 0.5x, source duration 10s, timeline duration 20s
        val clipHalf = createClip(
            id = "c2",
            trackId = "t1",
            timelineStartUs = 0L,
            durationUs = 20_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            speedMultiplier = 0.5f
        )
        val suggestionsHalf = optimizer.optimizeSilencesForClip(clipHalf, rawSilences)
        assertThat(suggestionsHalf).hasSize(1)
        val cutHalf = suggestionsHalf.first()

        // Timeline cut: 0 + 2_080_000 / 0.5 = 4_160_000us
        // Timeline cut end: 0 + 3_920_000 / 0.5 = 7_840_000us
        // Timeline duration: 3_680_000us
        assertThat(cutHalf.cutStartTimelineUs).isEqualTo(4_160_000L)
        assertThat(cutHalf.cutEndTimelineUs).isEqualTo(7_840_000L)
        assertThat(cutHalf.excisedDurationUs).isEqualTo(3_680_000L)
    }

    @Test
    fun testJumpCut_silenceOutsideClipBounds_gracefulClamping() {
        val optimizer = JumpCutOptimizer()
        val clip = createClip("c1", "t1", timelineStartUs = 0L, durationUs = 5_000_000L)

        // Silence beyond clip duration
        val beyondEnd = listOf(
            SilenceInterval(startUs = 6_000_000L, endUs = 8_000_000L, averageDb = -50f, peakDb = -48f)
        )
        assertThat(optimizer.optimizeSilencesForClip(clip, beyondEnd)).isEmpty()

        // Silence with negative start (should clamp to 0)
        val negativeStart = listOf(
            SilenceInterval(startUs = 0L, endUs = 1_000_000L, averageDb = -50f, peakDb = -48f)
        )
        val resultNegative = optimizer.optimizeSilencesForClip(clip, negativeStart)
        assertThat(resultNegative).hasSize(1)
        assertThat(resultNegative.first().cutStartTimelineUs).isEqualTo(0L)
    }

    // =========================================================================
    // 4. TalkingHeadPacingAnalyzer Stress Tests
    // =========================================================================

    @Test
    fun testPacing_shotBoundary_8sExactVsAbove8s() {
        val analyzer = TalkingHeadPacingAnalyzer(
            PacingAnalyzerConfig(maxContiguousDurationUs = 8_000_000L)
        )

        // Case A: Exactly 8.000000s -> strictly <= 8s -> 0 suggestions
        val track8s = TimelineTrack(
            id = "t1",
            name = "V1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(createClip("c8s", "t1", 0L, 8_000_000L))
        )
        val project8s = ProjectManifest(title = "P8", tracks = listOf(track8s), durationUs = 8_000_000L)
        assertThat(analyzer.analyzePacing(project8s)).isEmpty()

        // Case B: 7.999999s -> < 8s -> 0 suggestions
        val track799s = TimelineTrack(
            id = "t1",
            name = "V1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(createClip("c799", "t1", 0L, 7_999_999L))
        )
        val project799s = ProjectManifest(title = "P799", tracks = listOf(track799s), durationUs = 7_999_999L)
        assertThat(analyzer.analyzePacing(project799s)).isEmpty()

        // Case C: 8.000001s -> > 8s -> generates suggestions
        val track801s = TimelineTrack(
            id = "t1",
            name = "V1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(createClip("c801", "t1", 0L, 8_000_001L))
        )
        val project801s = ProjectManifest(title = "P801", tracks = listOf(track801s), durationUs = 8_000_001L)
        val suggestions801 = analyzer.analyzePacing(project801s)
        assertThat(suggestions801).hasSize(2)
    }

    @Test
    fun testPacing_pushInZoomParameters_andZeroEmojis() {
        val analyzer = TalkingHeadPacingAnalyzer(
            PacingAnalyzerConfig(
                maxContiguousDurationUs = 8_000_000L,
                defaultZoomScale = 1.15f
            )
        )
        val track = TimelineTrack(
            id = "t1",
            name = "V1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(createClip("c1", "t1", 0L, 10_000_000L))
        )
        val project = ProjectManifest(title = "P10", tracks = listOf(track), durationUs = 10_000_000L)
        val suggestions = analyzer.analyzePacing(project)

        assertThat(suggestions).hasSize(2)

        val pushIn = suggestions.first { it.suggestionType == PacingSuggestionType.PUSH_IN_ZOOM }
        assertThat(pushIn.proposedTransform?.scaleX).isEqualTo(1.15f)
        assertThat(pushIn.proposedTransform?.scaleY).isEqualTo(1.15f)
        assertThat(pushIn.proposedSplitUs).isEqualTo(5_000_000L) // Midpoint
        assertThat(pushIn.durationUs).isEqualTo(10_000_000L)

        // Verify rationale contains zero emojis
        val hasEmoji = pushIn.rationale.any { it.isSurrogate() || it.code in 0x2600..0x27BF }
        assertThat(hasEmoji).isFalse()

        val broll = suggestions.first { it.suggestionType == PacingSuggestionType.B_ROLL_INSERT }
        val brollHasEmoji = broll.rationale.any { it.isSurrogate() || it.code in 0x2600..0x27BF }
        assertThat(brollHasEmoji).isFalse()
    }

    @Test
    fun testPacing_multipleUpperTrackOcclusions_suppressWarnings() {
        val analyzer = TalkingHeadPacingAnalyzer(
            PacingAnalyzerConfig(maxContiguousDurationUs = 8_000_000L)
        )

        // Base talking head: 16.0s (0 to 16s)
        val baseTrack = TimelineTrack(
            id = "track-base",
            name = "Base Video",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(createClip("clip-base", "track-base", 0L, 16_000_000L))
        )

        // Overlay Track 1: clip from 3.0s to 6.0s
        val overlayTrack1 = TimelineTrack(
            id = "track-overlay-1",
            name = "Overlay 1",
            type = TrackType.OVERLAY,
            zIndex = 1,
            clips = listOf(createClip("clip-ov1", "track-overlay-1", 3_000_000L, 3_000_000L))
        )

        // Overlay Track 2: clip from 10.0s to 13.0s
        val overlayTrack2 = TimelineTrack(
            id = "track-overlay-2",
            name = "Overlay 2",
            type = TrackType.OVERLAY,
            zIndex = 2,
            clips = listOf(createClip("clip-ov2", "track-overlay-2", 10_000_000L, 3_000_000L))
        )

        val project = ProjectManifest(
            title = "Multi-Overlay Project",
            tracks = listOf(baseTrack, overlayTrack1, overlayTrack2),
            durationUs = 16_000_000L
        )

        // Unoccluded segments are:
        // [0s, 3s] = 3.0s (<= 8s)
        // [6s, 10s] = 4.0s (<= 8s)
        // [13s, 16s] = 3.0s (<= 8s)
        // All segments <= 8s, so pacing warning must be suppressed!
        val suggestions = analyzer.analyzePacing(project)
        assertThat(suggestions).isEmpty()
    }

    @Test
    fun testPacing_multipleUpperTrackOcclusions_leavingWindowAbove8s_generatesSuggestion() {
        val analyzer = TalkingHeadPacingAnalyzer(
            PacingAnalyzerConfig(maxContiguousDurationUs = 8_000_000L)
        )

        // Base talking head: 20.0s (0 to 20s)
        val baseTrack = TimelineTrack(
            id = "track-base",
            name = "Base Video",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(createClip("clip-base", "track-base", 0L, 20_000_000L))
        )

        // Overlay track: clip from 2.0s to 4.0s (duration 2s)
        val overlayTrack = TimelineTrack(
            id = "track-ov",
            name = "Overlay",
            type = TrackType.OVERLAY,
            zIndex = 1,
            clips = listOf(createClip("clip-ov", "track-ov", 2_000_000L, 2_000_000L))
        )

        val project = ProjectManifest(
            title = "Partial Occlusion",
            tracks = listOf(baseTrack, overlayTrack),
            durationUs = 20_000_000L
        )

        // Windows:
        // [0s, 2s] = 2.0s (<= 8s) -> ignored
        // [4s, 20s] = 16.0s (> 8s) -> generates suggestions!
        val suggestions = analyzer.analyzePacing(project)
        assertThat(suggestions).hasSize(2)

        val pushIn = suggestions.first { it.suggestionType == PacingSuggestionType.PUSH_IN_ZOOM }
        assertThat(pushIn.segmentStartUs).isEqualTo(4_000_000L)
        assertThat(pushIn.segmentEndUs).isEqualTo(20_000_000L)
        assertThat(pushIn.durationUs).isEqualTo(16_000_000L)
        assertThat(pushIn.proposedSplitUs).isEqualTo(12_000_000L) // Midpoint of 4s..20s
    }

    @Test
    fun testPacing_highZIndexVideoTrack_suppressesWarning() {
        val analyzer = TalkingHeadPacingAnalyzer(
            PacingAnalyzerConfig(maxContiguousDurationUs = 8_000_000L)
        )

        // Base talking head: 12.0s on TrackType.VIDEO, zIndex 0
        val baseTrack = TimelineTrack(
            id = "track-base",
            name = "Base Video",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(createClip("clip-base", "track-base", 0L, 12_000_000L))
        )

        // Upper visual video track: TrackType.VIDEO, zIndex 1 with clip from 4.0s to 8.0s
        val upperVideoTrack = TimelineTrack(
            id = "track-upper",
            name = "Cutaway Video",
            type = TrackType.VIDEO,
            zIndex = 1,
            clips = listOf(createClip("clip-upper", "track-upper", 4_000_000L, 4_000_000L))
        )

        val project = ProjectManifest(
            title = "High Z-Index Cutaway",
            tracks = listOf(baseTrack, upperVideoTrack),
            durationUs = 12_000_000L
        )

        // Unoccluded segments on base track: [0s, 4s] (4s <= 8s), [8s, 12s] (4s <= 8s)
        // High z-index video clip properly suppresses false positive warning on base track!
        val suggestions = analyzer.analyzePacing(project)
        assertThat(suggestions).isEmpty()
    }
}
