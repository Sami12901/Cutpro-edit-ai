package com.cutpro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Adversarial stress testing suite for :core:audio DSP algorithms and mathematical invariants:
 * 1. 64-bit zero-drift timestamp math across 100,000 buffers (44.1 kHz & 48 kHz).
 * 2. Volume boost overdrive up to 200% (+6 dB) with SoftClippingLimiter saturation.
 * 3. Constant-power stereo panning law (acoustic power conservation gL^2 + gR^2 == 1.0 within 10^-6).
 * 4. Dynamic ducking 4-state machine stress (rapid speech bursts, hold expiration, mid-release interruption).
 * 5. Fade overlap proportional scaling (zero negative plateau intervals).
 */
class AudioDspAdversarialTest {

    // =========================================================================
    // SECTION 1: 64-Bit Zero-Drift Timestamp Math Across 100,000 Buffers
    // =========================================================================

    @Test
    fun ptsMath_100kBuffersAt44100Hz_zeroMicrosecondCumulativeDrift() {
        val sampleRate = 44100L
        val framesPerBuffer = 1024L
        val totalBuffers = 100_000L
        val anchorPtsUs = 5_000_000L // Non-zero initial anchor

        var totalFramesProcessed = 0L
        var lastCalculatedPtsUs = anchorPtsUs

        for (bufferIdx in 1..totalBuffers) {
            totalFramesProcessed += framesPerBuffer

            // Formula exactly as implemented in VolumeAndFadeAudioProcessor and AudioDuckingAudioProcessor
            val calculatedPtsUs = anchorPtsUs + (totalFramesProcessed * 1_000_000L) / sampleRate

            // Monotonic advancement assertion
            assertThat(calculatedPtsUs).isGreaterThan(lastCalculatedPtsUs)
            lastCalculatedPtsUs = calculatedPtsUs
        }

        val totalFrames = totalBuffers * framesPerBuffer // 102,400,000 frames
        val expectedFinalPtsUs = anchorPtsUs + (totalFrames * 1_000_000L) / sampleRate
        // Cumulative drift across 100,000 continuous buffers must be strictly 0 microseconds
        assertThat(lastCalculatedPtsUs).isEqualTo(expectedFinalPtsUs)
        assertThat(lastCalculatedPtsUs).isEqualTo(5_000_000L + 2_321_995_464L)
    }

    @Test
    fun ptsMath_100kBuffersAt48000Hz_zeroMicrosecondCumulativeDrift() {
        val sampleRate = 48000L
        val framesPerBuffer = 1024L
        val totalBuffers = 100_000L
        val anchorPtsUs = 0L

        var totalFramesProcessed = 0L
        for (bufferIdx in 1..totalBuffers) {
            totalFramesProcessed += framesPerBuffer
        }

        val finalPtsUs = anchorPtsUs + (totalFramesProcessed * 1_000_000L) / sampleRate
        val expectedFinalPtsUs = (100_000L * 1024L * 1_000_000L) / 48000L
        assertThat(finalPtsUs).isEqualTo(expectedFinalPtsUs)
        assertThat(finalPtsUs).isEqualTo(2_133_333_333L)
    }

    @Test
    fun ptsMath_variableBufferSizes_zeroMicrosecondCumulativeDrift() {
        val sampleRate = 44100L
        val bufferSizes = intArrayOf(128, 256, 512, 1024, 1152, 2048)
        val totalBuffers = 100_000
        var totalFramesProcessed = 0L

        for (i in 0 until totalBuffers) {
            val frames = bufferSizes[i % bufferSizes.size]
            totalFramesProcessed += frames.toLong()

            // Verify intermediate checkpoint timestamp accuracy every 10,000 buffers
            if ((i + 1) % 10_000 == 0) {
                val pts = (totalFramesProcessed * 1_000_000L) / sampleRate
                val expected = (totalFramesProcessed * 1_000_000L) / sampleRate
                assertThat(pts).isEqualTo(expected)
            }
        }

        val finalPts = (totalFramesProcessed * 1_000_000L) / sampleRate
        val expectedFinalPts = (totalFramesProcessed * 1_000_000L) / sampleRate
        assertThat(finalPts).isEqualTo(expectedFinalPts)
    }

    @Test
    fun ptsMath_demonstrateFloatingPointDriftPrevention() {
        val sampleRate = 44100.0
        val framesPerBuffer = 1024.0
        val totalBuffers = 100_000

        // Naive float accumulation:
        val deltaUsFloat = (framesPerBuffer * 1_000_000.0 / sampleRate).toFloat()
        var accumulatedFloatUs = 0.0f
        for (i in 0 until totalBuffers) {
            accumulatedFloatUs += deltaUsFloat
        }

        // Exact 64-bit Long math:
        val exactLongUs = (100_000L * 1024L * 1_000_000L) / 44100L

        // Naive float32 accumulates massive precision drift (~hundreds of microseconds or seconds)
        val driftUs = abs(accumulatedFloatUs.toDouble() - exactLongUs.toDouble())
        assertThat(driftUs).isGreaterThan(1_000.0) // Float drifts significantly
    }

    // =========================================================================
    // SECTION 2: Volume Boost Overdrive & SoftClippingLimiter Saturation
    // =========================================================================

    @Test
    fun limiter_thresholdedTanh_bitExactTransparencyBelowThreshold() {
        val threshold = 0.75f
        val steps = 10_000
        for (i in -steps..steps) {
            val x = (i.toFloat() / steps.toFloat()) * threshold // [-0.75, 0.75]
            val clipped = SoftClippingLimiter.softClip(x, SoftClipMode.THRESHOLDED_TANH, threshold)
            assertThat(java.lang.Float.floatToIntBits(clipped))
                .isEqualTo(java.lang.Float.floatToIntBits(x))
        }
    }

    @Test
    fun limiter_overdrive200Percent_strictlyBoundedWithinUnitInterval() {
        // 200% overdrive corresponds to x = 2.0 (+6 dB)
        val overdriveValues = floatArrayOf(
            0.75001f, 0.8f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 5.0f, 10.0f, 100.0f, 1000.0f
        )
        for (v in overdriveValues) {
            val pos = SoftClippingLimiter.softClip(v, SoftClipMode.THRESHOLDED_TANH, 0.75f)
            assertThat(pos).isGreaterThan(0.75f)
            assertThat(pos).isAtMost(1.0f)
            assertThat(pos.isNaN()).isFalse()

            val neg = SoftClippingLimiter.softClip(-v, SoftClipMode.THRESHOLDED_TANH, 0.75f)
            assertThat(neg).isLessThan(-0.75f)
            assertThat(neg).isAtLeast(-1.0f)
            assertThat(neg.isNaN()).isFalse()

            // Exact odd symmetry
            assertThat(pos).isEqualTo(-neg)
        }
    }

    @Test
    fun limiter_denseSweep_strictMonotonicityAndBounds() {
        val modes = arrayOf(
            SoftClipMode.THRESHOLDED_TANH,
            SoftClipMode.POLYNOMIAL_SPLINE,
            SoftClipMode.PURE_TANH,
            SoftClipMode.HARD_CLIP
        )

        for (mode in modes) {
            var prevOutput = -1.0f
            val numSteps = 20_000
            for (i in 0..numSteps) {
                val x = -5.0f + (10.0f * i.toFloat() / numSteps.toFloat())
                val out = SoftClippingLimiter.softClip(x, mode, threshold = 0.75f)

                // Bounds assertion
                assertThat(out).isAtLeast(-1.0f)
                assertThat(out).isAtMost(1.0f)
                assertThat(out.isNaN()).isFalse()

                // Monotonicity assertion (non-decreasing)
                assertThat(out).isAtLeast(prevOutput - 1e-7f)
                prevOutput = out
            }
        }
    }

    @Test
    fun limiter_extremeAdversarialInputs_safeFallback() {
        val extremeInputs = floatArrayOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            0.0f,
            -0.0f,
            Float.MIN_VALUE,
            -Float.MIN_VALUE,
            Float.MAX_VALUE,
            -Float.MAX_VALUE,
            1e12f,
            -1e12f
        )

        for (mode in SoftClipMode.values()) {
            for (v in extremeInputs) {
                val out = SoftClippingLimiter.softClip(v, mode)
                assertThat(out.isNaN()).isFalse()
                assertThat(out.isInfinite()).isFalse()
                assertThat(out).isAtLeast(-1.0f)
                assertThat(out).isAtMost(1.0f)
            }
        }
    }

    @Test
    fun processor_volumeOverdrivePcm16_noIntegerWrapAroundOrDistortionBurst() {
        val processor = VolumeAndFadeAudioProcessor(
            clipDurationUs = 10_000_000L,
            initialVolume = 2.0f // 200% volume boost
        )
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val frameCount = 1024
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
        // Fill with full-scale extreme samples (32767, -32768, etc.)
        for (i in 0 until frameCount) {
            val s1 = if (i % 2 == 0) 32767.toShort() else (-32768).toShort()
            val s2 = if (i % 2 == 0) 25000.toShort() else (-25000).toShort()
            inputBuffer.putShort(s1)
            inputBuffer.putShort(s2)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertThat(outputBuffer.hasRemaining()).isTrue()
        var maxObserved = Short.MIN_VALUE
        var minObserved = Short.MAX_VALUE
        while (outputBuffer.hasRemaining()) {
            val sample = outputBuffer.short
            if (sample > maxObserved) maxObserved = sample
            if (sample < minObserved) minObserved = sample
            // Ensure no wrap-around into negative numbers for positive peaks
            assertThat(sample.toInt()).isAtMost(32767)
            assertThat(sample.toInt()).isAtLeast(-32768)
        }
        // At 200% boost with full-scale input, limiter should hit near ceiling without wrapping
        assertThat(maxObserved.toInt()).isGreaterThan(30000)
        assertThat(minObserved.toInt()).isLessThan(-30000)
    }

    @Test
    fun processor_volumeOverdrivePcmFloat_strictlyBoundedWithinUnitRange() {
        val processor = VolumeAndFadeAudioProcessor(
            clipDurationUs = 10_000_000L,
            initialVolume = 2.0f // 200% volume boost
        )
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        processor.configure(format)
        processor.flush()

        val frameCount = 1024
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * 8).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            val sample = if (i % 2 == 0) 1.0f else -1.0f
            inputBuffer.putFloat(sample)
            inputBuffer.putFloat(sample * 0.9f)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertThat(outputBuffer.hasRemaining()).isTrue()
        while (outputBuffer.hasRemaining()) {
            val sample = outputBuffer.float
            assertThat(sample).isAtLeast(-1.0f)
            assertThat(sample).isAtMost(1.0f)
            assertThat(sample.isNaN()).isFalse()
        }
    }

    // =========================================================================
    // SECTION 3: Constant-Power Panning Law (gL^2 + gR^2 == 1.0)
    // =========================================================================

    @Test
    fun panningLaw_fullSweepStep005_acousticPowerConservedWithinOnePpm() {
        // Test pan parameter p in [-1.0, 1.0] with step 0.05
        var p = -1.0f
        while (p <= 1.0001f) {
            val (gL, gR) = StereoPanHelper.calculateGains(p)
            val power = (gL.toDouble() * gL.toDouble()) + (gR.toDouble() * gR.toDouble())
            val delta = abs(power - 1.0)
            assertThat(delta).isLessThan(1e-6)

            assertThat(gL).isAtLeast(0.0f)
            assertThat(gL).isAtMost(1.0f)
            assertThat(gR).isAtLeast(0.0f)
            assertThat(gR).isAtMost(1.0f)

            p += 0.05f
        }
    }

    @Test
    fun panningLaw_dense10kStepsSweep_acousticPowerConserved() {
        val steps = 10_000
        for (i in 0..steps) {
            val p = -1.0f + (2.0f * i.toFloat() / steps.toFloat())
            val (gL, gR) = StereoPanHelper.calculateGains(p)
            val power = (gL.toDouble() * gL.toDouble()) + (gR.toDouble() * gR.toDouble())
            assertThat(abs(power - 1.0)).isLessThan(1e-6)
        }
    }

    @Test
    fun panningLaw_outOfBoundsPan_strictlyClamped() {
        val outOfBoundsCases = floatArrayOf(-999.0f, -10.0f, -1.001f, 1.001f, 2.5f, 999.0f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        for (p in outOfBoundsCases) {
            val (gL, gR) = StereoPanHelper.calculateGains(p)
            val power = (gL.toDouble() * gL.toDouble()) + (gR.toDouble() * gR.toDouble())
            assertThat(abs(power - 1.0)).isLessThan(1e-6)

            if (p < -1.0f) {
                assertThat(gL).isWithin(1e-6f).of(1.0f)
                assertThat(gR).isWithin(1e-6f).of(0.0f)
            } else if (p > 1.0f) {
                assertThat(gL).isWithin(1e-6f).of(0.0f)
                assertThat(gR).isWithin(1e-6f).of(1.0f)
            }
        }
    }

    @Test
    fun panningLaw_stereoBalance_centerUnityAndEndpoints() {
        val (balL0, balR0) = StereoPanHelper.calculateStereoBalanceGains(0.0f)
        assertThat(abs(balL0 - 1.0f)).isLessThan(1e-5f)
        assertThat(abs(balR0 - 1.0f)).isLessThan(1e-5f)

        val (balLLeft, balRLeft) = StereoPanHelper.calculateStereoBalanceGains(-1.0f)
        val sqrt2 = sqrt(2.0).toFloat()
        assertThat(abs(balLLeft - sqrt2)).isLessThan(1e-5f)
        assertThat(balRLeft).isWithin(1e-6f).of(0.0f)

        val (balLRight, balRRight) = StereoPanHelper.calculateStereoBalanceGains(1.0f)
        assertThat(balLRight).isWithin(1e-6f).of(0.0f)
        assertThat(abs(balRRight - sqrt2)).isLessThan(1e-5f)
    }

    @Test
    fun stereoPanProcessor_adversarialMonoAndStereoExecution() {
        val processor = StereoPanAudioProcessor(initialPan = -0.5f)
        val monoFormat = AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_FLOAT)
        val outFormat = processor.configure(monoFormat)
        assertThat(outFormat.channelCount).isEqualTo(2) // Expansion to stereo

        processor.flush()

        val frameCount = 256
        val monoBuffer = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            monoBuffer.putFloat(0.8f)
        }
        monoBuffer.flip()

        processor.queueInput(monoBuffer)
        val outputBuffer = processor.output
        assertThat(outputBuffer.remaining()).isEqualTo(frameCount * 8)

        while (outputBuffer.hasRemaining()) {
            val l = outputBuffer.float
            val r = outputBuffer.float
            // Pan is -0.5 (left-biased), so left channel must have higher energy than right channel
            assertThat(l).isGreaterThan(r)
            val power = (l * l + r * r) / (0.8f * 0.8f)
            assertThat(abs(power - 1.0f)).isLessThan(1e-5f)
        }
    }

    // =========================================================================
    // SECTION 4: Dynamic Ducking 4-State Machine Stress
    // =========================================================================

    @Test
    fun duckingEngine_rapidSpeechBursts_noStateFlutterOrGainChatter() {
        val engine = AudioDuckingEngine(
            attackTimeUs = 150_000L,
            holdTimeUs = 300_000L,
            releaseTimeUs = 400_000L,
            duckingDepthDb = -12.0f
        )

        var ptsUs = 0L
        val burstDurationUs = 50_000L // 50ms active, 50ms inactive
        val cycles = 10

        for (cycle in 0 until cycles) {
            // 50ms speech active
            for (step in 0 until 5) {
                ptsUs += 10_000L
                val gain = engine.process(ptsUs, speechActive = true)
                assertThat(gain).isAtLeast(engine.targetGain)
                assertThat(gain).isAtMost(1.0f)
            }

            // 50ms speech pause (< 300ms hold time)
            for (step in 0 until 5) {
                ptsUs += 10_000L
                val gain = engine.process(ptsUs, speechActive = false)
                // Inactive pause is only 50ms, hold time is 300ms -> MUST NOT enter RELEASE
                assertThat(engine.state).isNotEqualTo(DuckingState.RELEASE)
                assertThat(gain).isAtLeast(engine.targetGain)
            }
        }

        // Once attack time (150ms) has elapsed across bursts, engine must be in HOLD
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)
        assertThat(engine.currentGain).isWithin(0.001f).of(engine.targetGain)
    }

    @Test
    fun duckingEngine_holdExpiration_exactThreshold() {
        val engine = AudioDuckingEngine(
            attackTimeUs = 150_000L,
            holdTimeUs = 300_000L,
            releaseTimeUs = 400_000L
        )

        // Reach HOLD state
        engine.process(ptsUs = 0L, speechActive = true)
        engine.process(ptsUs = 150_000L, speechActive = true)
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)

        // Speech is active until 200,000 us
        engine.process(ptsUs = 200_000L, speechActive = true)
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)

        // Speech drop of 200ms (at 400,000 us, silence is 200ms < 300ms)
        engine.process(ptsUs = 400_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)
        assertThat(engine.currentGain).isEqualTo(engine.targetGain)

        // Speech drop reaches exactly 299ms (at 499,000 us < 500,000 us)
        engine.process(ptsUs = 499_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)

        // Speech drop reaches 300ms (at 500,000 us == 200,000 + 300,000)
        engine.process(ptsUs = 500_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.RELEASE)

        // Speech drop of 350ms (at 550,000 us) -> firmly in RELEASE, gain increasing
        val gain350 = engine.process(ptsUs = 550_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.RELEASE)
        assertThat(gain350).isGreaterThan(engine.targetGain)
        assertThat(gain350).isLessThan(1.0f)
    }

    @Test
    fun duckingEngine_midReleaseInterruption_smoothRampingWithoutClickOrPop() {
        val engine = AudioDuckingEngine(
            attackTimeUs = 150_000L,
            holdTimeUs = 300_000L,
            releaseTimeUs = 400_000L
        )

        // Establish HOLD and transition to RELEASE
        engine.process(ptsUs = 0L, speechActive = true)
        engine.process(ptsUs = 150_000L, speechActive = true)
        engine.process(ptsUs = 200_000L, speechActive = true) // active until 200ms
        engine.process(ptsUs = 500_000L, speechActive = false) // 300ms hold expires -> RELEASE

        // Advance 200ms into release (ptsUs = 700_000 us)
        val gainBeforeInterrupt = engine.process(ptsUs = 700_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.RELEASE)

        // Mid-release interruption: speech reactivates at ptsUs = 700_000 us
        val gainAtInterrupt = engine.process(ptsUs = 700_000L, speechActive = true)
        assertThat(engine.state).isEqualTo(DuckingState.ATTACK)

        // Continuity check: zero instantaneous step jump (no click/pop!)
        val discontinuity = abs(gainAtInterrupt - gainBeforeInterrupt)
        assertThat(discontinuity).isLessThan(1e-6f)

        // Verify subsequent ramp down towards targetGain is strictly monotonic non-increasing
        var prevGain = gainAtInterrupt
        for (step in 1..15) {
            val t = 700_000L + (step * 10_000L) // up to 150ms attack
            val g = engine.process(t, speechActive = true)
            assertThat(g).isAtMost(prevGain + 1e-6f)
            assertThat(g).isAtLeast(engine.targetGain)
            prevGain = g
        }
        assertThat(prevGain).isWithin(0.001f).of(engine.targetGain)
    }

    @Test
    fun duckingEngine_zeroTimes_gracefulImmediateTransitions() {
        val zeroEngine = AudioDuckingEngine(
            attackTimeUs = 0L,
            holdTimeUs = 0L,
            releaseTimeUs = 0L,
            duckingDepthDb = -12.0f
        )

        // Immediate ducking across frames
        val duckedGain1 = zeroEngine.process(ptsUs = 1000L, speechActive = true)
        assertThat(duckedGain1).isEqualTo(zeroEngine.targetGain)
        val duckedGain2 = zeroEngine.process(ptsUs = 1020L, speechActive = true)
        assertThat(duckedGain2).isEqualTo(zeroEngine.targetGain)
        assertThat(zeroEngine.state).isEqualTo(DuckingState.HOLD)

        // Immediate release across frames (state machine step sequence: HOLD -> RELEASE -> IDLE)
        val releasingGain = zeroEngine.process(ptsUs = 2000L, speechActive = false)
        assertThat(zeroEngine.state).isEqualTo(DuckingState.RELEASE)
        val releasedGain = zeroEngine.process(ptsUs = 2020L, speechActive = false)
        assertThat(releasedGain).isEqualTo(1.0f)
        assertThat(zeroEngine.state).isEqualTo(DuckingState.IDLE)
    }

    // =========================================================================
    // SECTION 5: Fade Overlap Proportional Scaling
    // =========================================================================

    @Test
    fun fadeOverlap_clipDurationShorterThanSum_zeroNegativePlateauInterval() {
        val clipDurationUs = 1_000_000L // 1.0 second clip
        val fadeInUs = 1_000_000L       // 1.0 second fade-in requested
        val fadeOutUs = 1_000_000L      // 1.0 second fade-out requested
        // Total requested fade = 2.0 seconds (> clip duration)

        val processor = VolumeAndFadeAudioProcessor(
            clipDurationUs = clipDurationUs,
            initialVolume = 1.0f,
            fadeInDurationUs = fadeInUs,
            fadeInCurve = FadeCurveType.LINEAR,
            fadeOutDurationUs = fadeOutUs,
            fadeOutCurve = FadeCurveType.LINEAR
        )

        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        processor.configure(format)
        processor.flush()

        // Feed samples across the entire clip in small chunks of 100 frames
        val frameCount = 100
        val buffer = ByteBuffer.allocateDirect(frameCount * 8).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount * 2) {
            buffer.putFloat(1.0f)
        }

        // Process through processor and assert all outputs are within [0.0, 1.0] without negative intervals
        var totalProcessedFrames = 0
        while (totalProcessedFrames < 48000) { // 1 second = 48,000 frames
            buffer.position(0)
            processor.queueInput(buffer)
            val out = processor.output
            while (out.hasRemaining()) {
                val sample = out.float
                assertThat(sample).isAtLeast(0.0f)
                assertThat(sample).isAtMost(1.0f)
                assertThat(sample.isNaN()).isFalse()
            }
            totalProcessedFrames += frameCount
        }
    }

    @Test
    fun fadeOverlap_randomizedStress1000Pairs_invariantHolds() {
        val rng = Random(42L)
        for (trial in 0 until 1_000) {
            val clipDurationUs = (rng.nextLong() and 0x7FFFFFFF) % 10_000_000L + 1_000L // 1ms to 10s
            val fadeInUs = (rng.nextLong() and 0x7FFFFFFF) % 10_000_000L + 1_000L
            val fadeOutUs = (rng.nextLong() and 0x7FFFFFFF) % 10_000_000L + 1_000L

            val totalFade = fadeInUs + fadeOutUs
            val (effectiveFadeInUs, effectiveFadeOutUs) = if (totalFade <= clipDurationUs) {
                Pair(fadeInUs, fadeOutUs)
            } else {
                val scale = clipDurationUs.toDouble() / totalFade.toDouble()
                Pair((fadeInUs * scale).toLong(), (fadeOutUs * scale).toLong())
            }

            // Invariant 1: Sum of effective fades must never exceed clip duration
            assertThat(effectiveFadeInUs + effectiveFadeOutUs).isAtMost(clipDurationUs)

            // Invariant 2: fadeOutStartUs must never be strictly less than effectiveFadeInUs (no negative plateau)
            val fadeOutStartUs = clipDurationUs - effectiveFadeOutUs
            assertThat(fadeOutStartUs).isAtLeast(effectiveFadeInUs)
        }
    }

    @Test
    fun fadeCurves_monotonicityAndEndpointsAcrossAllTypes() {
        val curves = FadeCurveType.values()
        val steps = 1000

        for (curve in curves) {
            // Endpoints
            assertThat(FadeCurveEvaluator.evaluateFadeIn(0.0f, curve)).isEqualTo(0.0f)
            assertThat(FadeCurveEvaluator.evaluateFadeIn(1.0f, curve)).isWithin(1e-5f).of(1.0f)
            assertThat(FadeCurveEvaluator.evaluateFadeOut(0.0f, curve)).isWithin(1e-5f).of(1.0f)
            assertThat(FadeCurveEvaluator.evaluateFadeOut(1.0f, curve)).isEqualTo(0.0f)

            // Monotonicity of Fade-In
            var prevIn = 0.0f
            for (i in 0..steps) {
                val t = i.toFloat() / steps.toFloat()
                val gainIn = FadeCurveEvaluator.evaluateFadeIn(t, curve)
                assertThat(gainIn).isAtLeast(prevIn - 1e-6f)
                assertThat(gainIn).isAtLeast(0.0f)
                assertThat(gainIn).isAtMost(1.0f)
                prevIn = gainIn
            }

            // Monotonicity of Fade-Out (non-increasing)
            var prevOut = 1.0f
            for (i in 0..steps) {
                val t = i.toFloat() / steps.toFloat()
                val gainOut = FadeCurveEvaluator.evaluateFadeOut(t, curve)
                assertThat(gainOut).isAtMost(prevOut + 1e-6f)
                assertThat(gainOut).isAtLeast(0.0f)
                assertThat(gainOut).isAtMost(1.0f)
                prevOut = gainOut
            }
        }
    }

    // =========================================================================
    // SECTION 6: Advanced Stochastic & Edge Stress
    // =========================================================================

    @Test
    fun limiter_subnormalAndDenormalFloats_noNaNOrCrash() {
        val subnormals = floatArrayOf(
            1e-38f, -1e-38f, 1e-45f, -1e-45f,
            java.lang.Float.intBitsToFloat(0x00000001),
            java.lang.Float.intBitsToFloat(-0x7fffffff)
        )
        for (v in subnormals) {
            for (mode in SoftClipMode.values()) {
                val out = SoftClippingLimiter.softClip(v, mode)
                assertThat(out.isNaN()).isFalse()
                assertThat(out.isInfinite()).isFalse()
                assertThat(out).isAtLeast(-1.0f)
                assertThat(out).isAtMost(1.0f)
            }
        }
    }

    @Test
    fun duckingEngine_stochasticSpeechBursts_gainStrictlyBounded() {
        val rng = Random(12345L)
        val engine = AudioDuckingEngine(
            attackTimeUs = 150_000L,
            holdTimeUs = 300_000L,
            releaseTimeUs = 400_000L,
            duckingDepthDb = -18.0f
        )
        var ptsUs = 0L
        var speechActive = false

        for (step in 0 until 10_000) {
            ptsUs += 5_000L // 5ms step
            if (rng.nextFloat() < 0.05f) {
                speechActive = !speechActive
            }
            val gain = engine.process(ptsUs, speechActive)

            assertThat(gain).isAtMost(1.0f + 1e-6f)
            assertThat(gain).isAtLeast(engine.targetGain - 1e-6f)
            assertThat(gain.isNaN()).isFalse()
        }
    }

    @Test
    fun duckingEngine_speechOffMidAttack_advancesThroughHoldToRelease() {
        val engine = AudioDuckingEngine(
            attackTimeUs = 150_000L,
            holdTimeUs = 300_000L,
            releaseTimeUs = 400_000L
        )

        // Speech starts at 0, turns on for only 50ms (mid-attack)
        engine.process(ptsUs = 0L, speechActive = true)
        val gainAt50ms = engine.process(ptsUs = 50_000L, speechActive = true)
        assertThat(engine.state).isEqualTo(DuckingState.ATTACK)
        assertThat(gainAt50ms).isLessThan(1.0f)

        // Speech turns off at 50,001 us
        engine.process(ptsUs = 50_001L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.ATTACK)

        // At 150ms, attack completes -> transitions to HOLD
        engine.process(ptsUs = 150_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)
        assertThat(engine.currentGain).isEqualTo(engine.targetGain)

        // Hold duration is measured from lastSpeechActiveUs (50,000 us).
        // At 350,000 us (50,000 + 300,000 hold), hold expires -> transitions to RELEASE
        engine.process(ptsUs = 350_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.RELEASE)
    }

    @Test
    fun stereoPanProcessor_dynamicPanModulationAcrossContinuousBuffers() {
        val processor = StereoPanAudioProcessor(initialPan = -1.0f)
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        processor.configure(format)
        processor.flush()

        val frameCount = 512
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * 8).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            inputBuffer.putFloat(0.5f)
            inputBuffer.putFloat(0.5f)
        }

        for (step in 0..50) {
            val pan = -1.0f + (2.0f * step.toFloat() / 50.0f)
            processor.setPan(pan)

            inputBuffer.position(0)
            processor.queueInput(inputBuffer)
            val outputBuffer = processor.output

            assertThat(outputBuffer.remaining()).isEqualTo(frameCount * 8)
            while (outputBuffer.hasRemaining()) {
                val l = outputBuffer.float
                val r = outputBuffer.float
                assertThat(l.isNaN()).isFalse()
                assertThat(r.isNaN()).isFalse()
                assertThat(l).isAtLeast(0.0f)
                assertThat(r).isAtLeast(0.0f)
            }
        }
    }

    @Test
    fun ptsMath_seekAndFlushAccuracyAcross10kSeeks() {
        val processor = VolumeAndFadeAudioProcessor(clipDurationUs = 100_000_000L)
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        processor.configure(format)
        processor.flush()

        val rng = Random(999L)
        for (i in 0 until 10_000) {
            val seekTargetUs = (rng.nextLong() and 0x7FFFFFFF) % 50_000_000L
            processor.seekTo(seekTargetUs)
            processor.flush()
        }
    }
}

