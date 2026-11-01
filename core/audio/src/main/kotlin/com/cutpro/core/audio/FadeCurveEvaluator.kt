package com.cutpro.core.audio

import kotlin.math.log10
import kotlin.math.pow

object FadeCurveEvaluator {

    /**
     * Evaluates fade-in gain multiplier for progress in [0.0, 1.0].
     * Returns gain in [0.0, 1.0].
     */
    fun evaluateFadeIn(progress: Float, curve: FadeCurveType): Float {
        val t = progress.coerceIn(0.0f, 1.0f)
        return when (curve) {
            FadeCurveType.LINEAR -> t
            FadeCurveType.LOGARITHMIC -> log10(1.0 + 9.0 * t.toDouble()).toFloat()
            FadeCurveType.EXPONENTIAL -> ((10.0.pow(t.toDouble()) - 1.0) / 9.0).toFloat()
            FadeCurveType.SCURVE -> t * t * (3.0f - 2.0f * t)
        }
    }

    /**
     * Evaluates fade-out gain multiplier for progress in [0.0, 1.0].
     * At progress = 0.0, returns 1.0; at progress = 1.0, returns 0.0.
     */
    fun evaluateFadeOut(progress: Float, curve: FadeCurveType): Float {
        val t = progress.coerceIn(0.0f, 1.0f)
        return 1.0f - evaluateFadeIn(t, curve)
    }
}
