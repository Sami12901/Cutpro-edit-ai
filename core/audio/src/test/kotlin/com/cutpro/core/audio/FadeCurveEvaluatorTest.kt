package com.cutpro.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FadeCurveEvaluatorTest {

    @Test
    fun fadeIn_endpoints_areExact() {
        for (curve in FadeCurveType.values()) {
            assertThat(FadeCurveEvaluator.evaluateFadeIn(0.0f, curve)).isEqualTo(0.0f)
            assertThat(FadeCurveEvaluator.evaluateFadeIn(1.0f, curve)).isEqualTo(1.0f)
        }
    }

    @Test
    fun fadeOut_endpoints_areExact() {
        for (curve in FadeCurveType.values()) {
            assertThat(FadeCurveEvaluator.evaluateFadeOut(0.0f, curve)).isEqualTo(1.0f)
            assertThat(FadeCurveEvaluator.evaluateFadeOut(1.0f, curve)).isEqualTo(0.0f)
        }
    }

    @Test
    fun fadeIn_monotonicity() {
        for (curve in FadeCurveType.values()) {
            var prev = 0.0f
            for (step in 1..100) {
                val t = step / 100.0f
                val current = FadeCurveEvaluator.evaluateFadeIn(t, curve)
                assertThat(current).isAtLeast(prev)
                prev = current
            }
        }
    }

    @Test
    fun scurve_symmetryAtMidpoint() {
        val mid = FadeCurveEvaluator.evaluateFadeIn(0.5f, FadeCurveType.SCURVE)
        assertThat(mid).isEqualTo(0.5f)

        // Point reflection symmetry: g(t) + g(1 - t) == 1.0
        val t1 = 0.25f
        val g1 = FadeCurveEvaluator.evaluateFadeIn(t1, FadeCurveType.SCURVE)
        val g2 = FadeCurveEvaluator.evaluateFadeIn(1.0f - t1, FadeCurveType.SCURVE)
        assertThat(g1 + g2).isWithin(1e-6f).of(1.0f)
    }

    @Test
    fun logarithmic_curveCharacteristics() {
        // Fast rise at start: log10(1 + 9 * 0.5) = log10(5.5) ~ 0.74036
        val mid = FadeCurveEvaluator.evaluateFadeIn(0.5f, FadeCurveType.LOGARITHMIC)
        assertThat(mid).isWithin(0.01f).of(0.74f)
    }

    @Test
    fun exponential_curveCharacteristics() {
        // Slow emergence from silence: (10^0.5 - 1) / 9 ~ 0.24025
        val mid = FadeCurveEvaluator.evaluateFadeIn(0.5f, FadeCurveType.EXPONENTIAL)
        assertThat(mid).isWithin(0.01f).of(0.24f)
    }
}
