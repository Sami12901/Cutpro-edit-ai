package com.cutpro.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SoftClippingLimiterTest {

    @Test
    fun thresholdedTanh_belowThreshold_isBitExactTransparent() {
        val testValues = floatArrayOf(-0.75f, -0.5f, 0.0f, 0.25f, 0.75f)
        for (v in testValues) {
            val clipped = SoftClippingLimiter.softClip(v, SoftClipMode.THRESHOLDED_TANH, threshold = 0.75f)
            assertThat(clipped).isEqualTo(v)
        }
    }

    @Test
    fun thresholdedTanh_overdrive_boundedStrictlyWithinOne() {
        val overdrivenValues = floatArrayOf(1.0f, 1.5f, 2.0f, 5.0f, 100.0f)
        for (v in overdrivenValues) {
            val pos = SoftClippingLimiter.softClip(v, SoftClipMode.THRESHOLDED_TANH, threshold = 0.75f)
            assertThat(pos).isAtMost(1.0f)
            assertThat(pos).isGreaterThan(0.75f)

            val neg = SoftClippingLimiter.softClip(-v, SoftClipMode.THRESHOLDED_TANH, threshold = 0.75f)
            assertThat(neg).isAtLeast(-1.0f)
            assertThat(neg).isLessThan(-0.75f)
        }
    }

    @Test
    fun symmetry_holdsAcrossAllModes() {
        val modes = SoftClipMode.values()
        val values = floatArrayOf(0.2f, 0.8f, 1.4f, 2.0f)
        for (mode in modes) {
            for (v in values) {
                val pos = SoftClippingLimiter.softClip(v, mode)
                val neg = SoftClippingLimiter.softClip(-v, mode)
                assertThat(pos).isWithin(1e-6f).of(-neg)
            }
        }
    }

    @Test
    fun polynomialSpline_flatCeilingAtTwoHeadrooms() {
        // T = 0.75, headRoom = 0.25, u = 2.0 occurs at x = 0.75 + 2 * 0.25 = 1.25
        val atClamp = SoftClippingLimiter.softClip(1.25f, SoftClipMode.POLYNOMIAL_SPLINE, threshold = 0.75f)
        assertThat(atClamp).isEqualTo(1.0f)

        val pastClamp = SoftClippingLimiter.softClip(2.0f, SoftClipMode.POLYNOMIAL_SPLINE, threshold = 0.75f)
        assertThat(pastClamp).isEqualTo(1.0f)
    }

    @Test
    fun nanAndInfinity_coercedToZero() {
        assertThat(SoftClippingLimiter.softClip(Float.NaN)).isEqualTo(0.0f)
        assertThat(SoftClippingLimiter.softClip(Float.POSITIVE_INFINITY)).isEqualTo(0.0f)
        assertThat(SoftClippingLimiter.softClip(Float.NEGATIVE_INFINITY)).isEqualTo(0.0f)
    }
}
