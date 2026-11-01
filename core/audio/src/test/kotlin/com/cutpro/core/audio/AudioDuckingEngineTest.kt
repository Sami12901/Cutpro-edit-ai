package com.cutpro.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioDuckingEngineTest {

    @Test
    fun idleState_maintainsUnityGain() {
        val engine = AudioDuckingEngine()
        val gain = engine.process(ptsUs = 0L, speechActive = false)
        assertThat(gain).isEqualTo(1.0f)
        assertThat(engine.state).isEqualTo(DuckingState.IDLE)
    }

    @Test
    fun attackPhase_rampsDownToTargetDuckedGain() {
        val engine = AudioDuckingEngine(attackTimeUs = 150_000L, duckingDepthDb = -12.0f)

        // Trigger speech at 1,000,000 us
        val startGain = engine.process(ptsUs = 1_000_000L, speechActive = true)
        assertThat(engine.state).isEqualTo(DuckingState.ATTACK)

        // Mid attack (75ms into 150ms attack)
        val midGain = engine.process(ptsUs = 1_075_000L, speechActive = true)
        assertThat(midGain).isLessThan(1.0f)
        assertThat(midGain).isGreaterThan(engine.targetGain)

        // End of attack (150ms)
        val endGain = engine.process(ptsUs = 1_150_000L, speechActive = true)
        assertThat(endGain).isWithin(0.001f).of(engine.targetGain)
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)
    }

    @Test
    fun holdPhase_sustainsDuckedGainAcrossSpeechPausesUnder300ms() {
        val engine = AudioDuckingEngine(
            attackTimeUs = 150_000L,
            holdTimeUs = 300_000L,
            releaseTimeUs = 400_000L
        )

        engine.process(ptsUs = 0L, speechActive = true)
        engine.process(ptsUs = 150_000L, speechActive = true) // Reaches HOLD

        // Speech stops at 200,000 us
        engine.process(ptsUs = 200_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)

        // Pause of 200ms (at 400,000 us, < 300ms hold)
        val gainDuringPause = engine.process(ptsUs = 400_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.HOLD)
        assertThat(gainDuringPause).isWithin(0.001f).of(engine.targetGain)

        // After 350ms pause (at 550,000 us >= 200,000 + 300,000)
        engine.process(ptsUs = 550_000L, speechActive = false)
        assertThat(engine.state).isEqualTo(DuckingState.RELEASE)
    }

    @Test
    fun midReleaseInterruption_rampsDownSmoothlyWithoutPop() {
        val engine = AudioDuckingEngine(
            attackTimeUs = 150_000L,
            holdTimeUs = 300_000L,
            releaseTimeUs = 400_000L
        )

        // Transition to RELEASE
        engine.process(ptsUs = 0L, speechActive = true)
        engine.process(ptsUs = 150_000L, speechActive = true) // In HOLD
        engine.process(ptsUs = 150_000L, speechActive = false)
        engine.process(ptsUs = 450_000L, speechActive = false) // Hold expires -> RELEASE

        // 200ms into 400ms release (gain is intermediate, ~0.62)
        val intermediateGain = engine.process(ptsUs = 650_000L, speechActive = false)
        assertThat(intermediateGain).isGreaterThan(engine.targetGain)
        assertThat(intermediateGain).isLessThan(1.0f)
        assertThat(engine.state).isEqualTo(DuckingState.RELEASE)

        // Speech interrupts at 650,000 us
        val interruptGain = engine.process(ptsUs = 650_000L, speechActive = true)
        assertThat(engine.state).isEqualTo(DuckingState.ATTACK)
        // Continuity: gain must match intermediate gain exactly (zero pop!)
        assertThat(interruptGain).isWithin(0.001f).of(intermediateGain)

        // Ramps downward from intermediate gain
        val nextGain = engine.process(ptsUs = 700_000L, speechActive = true)
        assertThat(nextGain).isLessThan(interruptGain)
        assertThat(nextGain).isAtLeast(engine.targetGain)
    }

    @Test
    fun timelineDuckingSchedule_mergesMicroGaps() {
        val raw = listOf(
            SpeechInterval(0L, 1_000_000L),
            SpeechInterval(1_030_000L, 2_000_000L) // 30ms micro-gap (< 50ms)
        )
        val merged = TimelineDuckingSchedule.mergeIntervals(raw)
        assertThat(merged).hasSize(1)
        assertThat(merged[0].startUs).isEqualTo(0L)
        assertThat(merged[0].endUs).isEqualTo(2_000_000L)
    }

    @Test
    fun rmsSpeechDetector_detectsSpeechAboveThreshold() {
        val detector = RmsSpeechDetector(speechThresholdDb = -32.0f, hysteresisDb = 3.0f)

        // Quiet signal (amplitude 100 out of 32768, ~ -50 dBFS)
        val quietPcm = ShortArray(480) { 100 }
        val isQuiet = detector.processFrame(quietPcm)
        assertThat(isQuiet).isFalse()

        // Loud signal (amplitude 20000 out of 32768, ~ -4.3 dBFS)
        val loudPcm = ShortArray(480) { 20000 }
        val isLoud = detector.processFrame(loudPcm)
        assertThat(isLoud).isTrue()
    }
}
