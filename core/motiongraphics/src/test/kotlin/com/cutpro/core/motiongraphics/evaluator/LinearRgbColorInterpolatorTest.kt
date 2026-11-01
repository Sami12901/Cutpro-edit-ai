package com.cutpro.core.motiongraphics.evaluator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinearRgbColorInterpolatorTest {

    @Test
    fun blackToWhite_midpointHasGammaCorrectedLuminance() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val mid = LinearRgbColorInterpolator.interpolate(black, white, 0.5f)

        val alpha = (mid ushr 24) and 0xFF
        val red = (mid ushr 16) and 0xFF
        val green = (mid ushr 8) and 0xFF
        val blue = mid and 0xFF

        // Alpha is fully opaque
        assertThat(alpha).isEqualTo(255)

        // Linear RGB gamma 2.2 interpolation midpoint produces ~186 in sRGB, avoiding dark muddy 128
        assertThat(red).isGreaterThan(170)
        assertThat(green).isGreaterThan(170)
        assertThat(blue).isGreaterThan(170)
    }

    @Test
    fun boundaryFractions_returnExactColors() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()

        val at0 = LinearRgbColorInterpolator.interpolate(red, blue, 0.0f)
        val at1 = LinearRgbColorInterpolator.interpolate(red, blue, 1.0f)

        assertThat(at0).isEqualTo(red)
        assertThat(at1).isEqualTo(blue)
    }

    @Test
    fun alphaInterpolation_scalesLinearly() {
        val transRed = 0x00FF0000.toInt()
        val opaqueRed = 0xFFFF0000.toInt()

        val mid = LinearRgbColorInterpolator.interpolate(transRed, opaqueRed, 0.5f)
        val alpha = (mid ushr 24) and 0xFF
        assertThat(alpha).isWithin(2).of(128)
    }
}
