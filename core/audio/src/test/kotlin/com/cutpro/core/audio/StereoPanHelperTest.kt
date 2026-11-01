package com.cutpro.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sqrt

class StereoPanHelperTest {

    @Test
    fun centerPan_preservesUnitPower() {
        val (gL, gR) = StereoPanHelper.calculateGains(0.0f)
        val expected = (sqrt(2.0) / 2.0).toFloat()
        assertThat(gL).isWithin(1e-5f).of(expected)
        assertThat(gR).isWithin(1e-5f).of(expected)

        val power = gL * gL + gR * gR
        assertThat(power).isWithin(1e-5f).of(1.0f)
    }

    @Test
    fun hardLeftAndRight_endpoints() {
        val (leftL, leftR) = StereoPanHelper.calculateGains(-1.0f)
        assertThat(leftL).isWithin(1e-5f).of(1.0f)
        assertThat(leftR).isWithin(1e-5f).of(0.0f)

        val (rightL, rightR) = StereoPanHelper.calculateGains(1.0f)
        assertThat(rightL).isWithin(1e-5f).of(0.0f)
        assertThat(rightR).isWithin(1e-5f).of(1.0f)
    }

    @Test
    fun acousticPowerConservation_acrossEntireSoundstage() {
        val positions = floatArrayOf(-1.0f, -0.75f, -0.5f, -0.25f, 0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        for (p in positions) {
            val (gL, gR) = StereoPanHelper.calculateGains(p)
            val power = gL * gL + gR * gR
            assertThat(power).isWithin(1e-5f).of(1.0f)
        }
    }

    @Test
    fun outOfBoundsPan_isSafelyClamped() {
        val (minL, minR) = StereoPanHelper.calculateGains(-999.0f)
        val (hardL, _) = StereoPanHelper.calculateGains(-1.0f)
        assertThat(minL).isEqualTo(hardL)

        val (maxL, maxR) = StereoPanHelper.calculateGains(2.5f)
        val (_, hardR) = StereoPanHelper.calculateGains(1.0f)
        assertThat(maxR).isEqualTo(hardR)
    }

    @Test
    fun stereoBalance_atCenter_hasUnityGain() {
        val (balL, balR) = StereoPanHelper.calculateStereoBalanceGains(0.0f)
        assertThat(balL).isWithin(1e-5f).of(1.0f)
        assertThat(balR).isWithin(1e-5f).of(1.0f)
    }

    @Test
    fun panMonoToStereoPcm16_convertsCorrectly() {
        val mono = shortArrayOf(10000.toShort(), 20000.toShort(), (-10000).toShort())
        val stereo = ShortArray(6)
        val (gL, gR) = StereoPanHelper.calculateGains(0.0f)

        StereoPanHelper.panMonoToStereoPcm16(mono, stereo, gL, gR)

        // Left and Right should be equal for center pan
        assertThat(stereo[0]).isEqualTo(stereo[1])
        assertThat(stereo[2]).isEqualTo(stereo[3])
        assertThat(stereo[4]).isEqualTo(stereo[5])
    }

    @Test
    fun balanceStereoPcm16InPlace_hardLeftMutesRightChannel() {
        val stereo = shortArrayOf(10000.toShort(), 20000.toShort(), 30000.toShort(), 25000.toShort())
        val (gL, gR) = StereoPanHelper.calculateStereoBalanceGains(-1.0f)

        StereoPanHelper.balanceStereoPcm16InPlace(stereo, gL, gR)

        assertThat(stereo[1]).isEqualTo(0.toShort())
        assertThat(stereo[3]).isEqualTo(0.toShort())
        assertThat(stereo[0].toInt()).isGreaterThan(10000)
    }
}
