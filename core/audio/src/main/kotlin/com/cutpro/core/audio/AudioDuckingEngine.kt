package com.cutpro.core.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

enum class DuckingState {
    IDLE,
    ATTACK,
    HOLD,
    RELEASE
}

data class SpeechInterval(val startUs: Long, val endUs: Long)

/**
 * Deterministic timeline ducking schedule for pre-computed speech intervals.
 */
class TimelineDuckingSchedule(
    intervals: List<SpeechInterval>,
    private val attackTimeUs: Long = 150_000L,
    private val holdTimeUs: Long = 300_000L,
    private val releaseTimeUs: Long = 400_000L,
    private val duckingDepthDb: Float = -12.0f
) {
    val targetGain: Float = 10.0.pow(duckingDepthDb / 20.0).toFloat()
    val mergedIntervals: List<SpeechInterval> = mergeIntervals(intervals)

    companion object {
        fun mergeIntervals(raw: List<SpeechInterval>): List<SpeechInterval> {
            if (raw.isEmpty()) return emptyList()
            val sorted = raw.sortedBy { it.startUs }
            val merged = mutableListOf<SpeechInterval>()
            var current = sorted[0]
            for (i in 1 until sorted.size) {
                val next = sorted[i]
                if (next.startUs <= current.endUs + 50_000L) { // Merge micro-gaps < 50ms
                    current = SpeechInterval(current.startUs, maxOf(current.endUs, next.endUs))
                } else {
                    merged.add(current)
                    current = next
                }
            }
            merged.add(current)
            return merged
        }
    }

    /**
     * Evaluates deterministic ducking gain multiplier at presentation timestamp [ptsUs].
     */
    fun evaluateGainAt(ptsUs: Long): Float {
        for (interval in mergedIntervals) {
            val attackStart = interval.startUs
            val attackEnd = attackStart + attackTimeUs
            val speechEnd = interval.endUs
            val holdEnd = speechEnd + holdTimeUs
            val releaseEnd = holdEnd + releaseTimeUs

            if (ptsUs < attackStart) {
                continue
            } else if (ptsUs in attackStart..attackEnd) {
                // Attack phase
                val tau = if (attackTimeUs > 0L) {
                    (ptsUs - attackStart).toFloat() / attackTimeUs.toFloat()
                } else {
                    1.0f
                }
                val smoothTau = 3f * tau * tau - 2f * tau * tau * tau
                return 1.0f + (targetGain - 1.0f) * smoothTau
            } else if (ptsUs in attackEnd..holdEnd) {
                // Hold phase
                return targetGain
            } else if (ptsUs in holdEnd..releaseEnd) {
                // Release phase
                val tau = if (releaseTimeUs > 0L) {
                    (ptsUs - holdEnd).toFloat() / releaseTimeUs.toFloat()
                } else {
                    1.0f
                }
                val smoothTau = 3f * tau * tau - 2f * tau * tau * tau
                return targetGain + (1.0f - targetGain) * smoothTau
            }
        }
        return 1.0f
    }
}

/**
 * Sliding-window RMS speech energy detector with hysteresis.
 */
class RmsSpeechDetector(
    val speechThresholdDb: Float = -32.0f,
    val hysteresisDb: Float = 3.0f
) {
    private var isSpeechActive: Boolean = false

    fun processFrame(pcmSamples: ShortArray): Boolean {
        if (pcmSamples.isEmpty()) return isSpeechActive
        var sumSquares = 0.0
        for (s in pcmSamples) {
            val norm = s.toDouble() / 32768.0
            sumSquares += norm * norm
        }
        val rms = sqrt(sumSquares / pcmSamples.size)
        val rmsDb = if (rms > 1e-5) 20.0 * log10(rms) else -100.0

        isSpeechActive = if (isSpeechActive) {
            rmsDb > (speechThresholdDb - hysteresisDb)
        } else {
            rmsDb > speechThresholdDb
        }
        return isSpeechActive
    }

    fun processFrameFloat(pcmSamples: FloatArray): Boolean {
        if (pcmSamples.isEmpty()) return isSpeechActive
        var sumSquares = 0.0
        for (s in pcmSamples) {
            val sample = s.toDouble()
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / pcmSamples.size)
        val rmsDb = if (rms > 1e-5) 20.0 * log10(rms) else -100.0

        isSpeechActive = if (isSpeechActive) {
            rmsDb > (speechThresholdDb - hysteresisDb)
        } else {
            rmsDb > speechThresholdDb
        }
        return isSpeechActive
    }

    fun reset() {
        isSpeechActive = false
    }
}

/**
 * Real-time 4-state ducking state machine with mid-release interruption handling.
 */
class AudioDuckingEngine(
    val attackTimeUs: Long = 150_000L,
    val holdTimeUs: Long = 300_000L,
    val releaseTimeUs: Long = 400_000L,
    val duckingDepthDb: Float = -12.0f
) {
    val targetGain: Float = 10.0.pow(duckingDepthDb / 20.0).toFloat()

    var state: DuckingState = DuckingState.IDLE
        private set

    var currentGain: Float = 1.0f
        private set

    private var phaseStartUs: Long = 0L
    private var startGain: Float = 1.0f
    private var lastSpeechActiveUs: Long = 0L

    fun reset() {
        state = DuckingState.IDLE
        currentGain = 1.0f
        phaseStartUs = 0L
        startGain = 1.0f
        lastSpeechActiveUs = 0L
    }

    /**
     * Updates ducking state and computes attenuation gain for timestamp [ptsUs]
     * given whether speech is currently active.
     */
    fun process(ptsUs: Long, speechActive: Boolean): Float {
        if (speechActive) {
            lastSpeechActiveUs = ptsUs
        }

        when (state) {
            DuckingState.IDLE -> {
                if (speechActive) {
                    state = DuckingState.ATTACK
                    phaseStartUs = ptsUs
                    startGain = currentGain
                    currentGain = calculateAttackGain(ptsUs)
                } else {
                    currentGain = 1.0f
                }
            }
            DuckingState.ATTACK -> {
                currentGain = calculateAttackGain(ptsUs)
                val elapsed = ptsUs - phaseStartUs
                if (elapsed >= attackTimeUs || attackTimeUs == 0L) {
                    state = DuckingState.HOLD
                    currentGain = targetGain
                }
            }
            DuckingState.HOLD -> {
                currentGain = targetGain
                if (speechActive) {
                    lastSpeechActiveUs = ptsUs
                } else {
                    val silenceDuration = ptsUs - lastSpeechActiveUs
                    if (silenceDuration >= holdTimeUs) {
                        state = DuckingState.RELEASE
                        phaseStartUs = ptsUs
                        startGain = currentGain
                    }
                }
            }
            DuckingState.RELEASE -> {
                if (speechActive) {
                    // Mid-release interruption: smoothly ramp down from current gain
                    state = DuckingState.ATTACK
                    phaseStartUs = ptsUs
                    startGain = currentGain
                    currentGain = calculateAttackGain(ptsUs)
                } else {
                    currentGain = calculateReleaseGain(ptsUs)
                    val elapsed = ptsUs - phaseStartUs
                    if (elapsed >= releaseTimeUs || releaseTimeUs == 0L) {
                        state = DuckingState.IDLE
                        currentGain = 1.0f
                    }
                }
            }
        }

        return currentGain
    }

    private fun calculateAttackGain(ptsUs: Long): Float {
        if (attackTimeUs <= 0L) return targetGain
        val elapsed = (ptsUs - phaseStartUs).coerceIn(0L, attackTimeUs)
        val tau = elapsed.toFloat() / attackTimeUs.toFloat()
        val smoothTau = 3f * tau * tau - 2f * tau * tau * tau
        return startGain + (targetGain - startGain) * smoothTau
    }

    private fun calculateReleaseGain(ptsUs: Long): Float {
        if (releaseTimeUs <= 0L) return 1.0f
        val elapsed = (ptsUs - phaseStartUs).coerceIn(0L, releaseTimeUs)
        val tau = elapsed.toFloat() / releaseTimeUs.toFloat()
        val smoothTau = 3f * tau * tau - 2f * tau * tau * tau
        return startGain + (1.0f - startGain) * smoothTau
    }
}
