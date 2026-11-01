package com.cutpro.core.audio

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

enum class SoftClipMode {
    THRESHOLDED_TANH,
    POLYNOMIAL_SPLINE,
    PURE_TANH,
    HARD_CLIP
}

object SoftClippingLimiter {

    /**
     * Applies soft saturation limiting to an audio sample.
     * Guaranteed output range is [-1.0, 1.0].
     *
     * @param x Input audio sample.
     * @param mode Limiting mode: THRESHOLDED_TANH, POLYNOMIAL_SPLINE, PURE_TANH, HARD_CLIP.
     * @param threshold Linear threshold below which the signal is 100% bit-exact transparent (default 0.75).
     * @return Bounded sample in [-1.0, 1.0].
     */
    fun softClip(
        x: Float,
        mode: SoftClipMode = SoftClipMode.THRESHOLDED_TANH,
        threshold: Float = 0.75f
    ): Float {
        if (x.isNaN() || x.isInfinite()) return 0.0f
        return when (mode) {
            SoftClipMode.THRESHOLDED_TANH -> {
                val ax = abs(x)
                if (ax <= threshold) {
                    x
                } else {
                    val headRoom = 1.0f - threshold
                    val u = (ax - threshold) / headRoom
                    val sat = threshold + headRoom * tanh(u.toDouble()).toFloat()
                    sign(x) * sat.coerceIn(0.0f, 1.0f)
                }
            }
            SoftClipMode.POLYNOMIAL_SPLINE -> {
                val ax = abs(x)
                if (ax <= threshold) {
                    x
                } else {
                    val headRoom = 1.0f - threshold
                    val u = (ax - threshold) / headRoom
                    if (u >= 2.0f) {
                        sign(x) * 1.0f
                    } else {
                        val sat = threshold + headRoom * (u - (u * u) / 4.0f)
                        sign(x) * sat.coerceIn(0.0f, 1.0f)
                    }
                }
            }
            SoftClipMode.PURE_TANH -> {
                tanh(x.toDouble()).toFloat().coerceIn(-1.0f, 1.0f)
            }
            SoftClipMode.HARD_CLIP -> {
                x.coerceIn(-1.0f, 1.0f)
            }
        }
    }
}
