package com.cutpro.core.audio

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object StereoPanHelper {
    private const val PI_OVER_4 = Math.PI / 4.0
    private const val SQRT_2 = 1.4142135623730951

    /**
     * Calculates left and right channel gains under the Constant-Power Panning Law.
     * Guaranteed acoustic power conservation: gL^2 + gR^2 = 1.0.
     *
     * @param pan Normalized pan parameter in [-1.0 (Left), +1.0 (Right)]. Clamped if out of range.
     * @return Pair(leftGain, rightGain).
     */
    fun calculateGains(pan: Float): Pair<Float, Float> {
        val p = pan.coerceIn(-1.0f, 1.0f).toDouble()
        val theta = (p + 1.0) * PI_OVER_4
        val leftGain = cos(theta).toFloat()
        val rightGain = sin(theta).toFloat()
        return Pair(leftGain, rightGain)
    }

    /**
     * Calculates left and right channel gains for an existing stereo track with center unity normalization.
     * At center (p = 0.0), both channels pass through with 1.0x gain.
     */
    fun calculateStereoBalanceGains(pan: Float): Pair<Float, Float> {
        val (baseL, baseR) = calculateGains(pan)
        return Pair((baseL * SQRT_2).toFloat(), (baseR * SQRT_2).toFloat())
    }

    /**
     * Applies constant-power panning to a mono 16-bit PCM buffer into an interleaved stereo 16-bit PCM buffer.
     */
    fun panMonoToStereoPcm16(
        inputMono: ShortArray,
        outputStereo: ShortArray,
        leftGain: Float,
        rightGain: Float
    ) {
        require(outputStereo.size >= inputMono.size * 2) { "Output buffer must hold 2x mono samples" }
        var outIdx = 0
        for (i in inputMono.indices) {
            val sample = inputMono[i].toFloat()
            val leftSample = (sample * leftGain).roundToInt().coerceIn(-32768, 32767)
            val rightSample = (sample * rightGain).roundToInt().coerceIn(-32768, 32767)
            outputStereo[outIdx++] = leftSample.toShort()
            outputStereo[outIdx++] = rightSample.toShort()
        }
    }

    /**
     * Applies constant-power stereo balance to an interleaved stereo 16-bit PCM buffer in-place.
     */
    fun balanceStereoPcm16InPlace(
        stereoBuffer: ShortArray,
        leftGain: Float,
        rightGain: Float
    ) {
        require(stereoBuffer.size % 2 == 0) { "Stereo buffer must contain even number of samples" }
        var i = 0
        while (i < stereoBuffer.size) {
            val left = stereoBuffer[i].toFloat()
            val right = stereoBuffer[i + 1].toFloat()
            val outL = (left * leftGain).roundToInt().coerceIn(-32768, 32767)
            val outR = (right * rightGain).roundToInt().coerceIn(-32768, 32767)
            stereoBuffer[i] = outL.toShort()
            stereoBuffer[i + 1] = outR.toShort()
            i += 2
        }
    }

    /**
     * Applies constant-power panning to a mono 32-bit float PCM buffer into an interleaved stereo 32-bit float buffer.
     */
    fun panMonoToStereoPcmFloat(
        inputMono: FloatArray,
        outputStereo: FloatArray,
        leftGain: Float,
        rightGain: Float
    ) {
        require(outputStereo.size >= inputMono.size * 2) { "Output buffer must hold 2x mono samples" }
        var outIdx = 0
        for (i in inputMono.indices) {
            val sample = inputMono[i]
            outputStereo[outIdx++] = sample * leftGain
            outputStereo[outIdx++] = sample * rightGain
        }
    }

    /**
     * Applies constant-power stereo balance to an interleaved stereo 32-bit float buffer in-place.
     */
    fun balanceStereoPcmFloatInPlace(
        stereoBuffer: FloatArray,
        leftGain: Float,
        rightGain: Float
    ) {
        require(stereoBuffer.size % 2 == 0) { "Stereo buffer must contain even number of samples" }
        var i = 0
        while (i < stereoBuffer.size) {
            stereoBuffer[i] = stereoBuffer[i] * leftGain
            stereoBuffer[i + 1] = stereoBuffer[i + 1] * rightGain
            i += 2
        }
    }
}
