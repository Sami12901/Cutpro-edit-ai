package com.cutpro.core.motiongraphics.evaluator

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AnalyticalSpringSolverTest {

    @Test
    fun underdamped_atOrigin_returnsZero() {
        val result = AnalyticalSpringSolver.solve(
            stiffness = 280.0f,
            dampingRatio = 0.55f,
            fraction = 0.0f,
            durationSec = 1.0f
        )
        assertThat(result).isEqualTo(0.0f)
    }

    @Test
    fun underdamped_producesOvershoot() {
        // Damping ratio 0.55 produces peak overshoot > 1.10
        var peak55 = 0.0f
        for (i in 0..100) {
            val frac = i / 100.0f
            val value = AnalyticalSpringSolver.solve(
                stiffness = 280.0f,
                dampingRatio = 0.55f,
                fraction = frac,
                durationSec = 1.0f
            )
            if (value > peak55) {
                peak55 = value
            }
        }
        assertThat(peak55).isGreaterThan(1.10f)

        // Damping ratio 0.40 produces peak overshoot > 1.20
        var peak40 = 0.0f
        for (i in 0..100) {
            val frac = i / 100.0f
            val value = AnalyticalSpringSolver.solve(
                stiffness = 400.0f,
                dampingRatio = 0.40f,
                fraction = frac,
                durationSec = 1.0f
            )
            if (value > peak40) {
                peak40 = value
            }
        }
        assertThat(peak40).isGreaterThan(1.20f)
    }

    @Test
    fun criticallyDamped_monotonicallyIncreasesWithoutOvershoot() {
        var prev = -0.001f
        var maxVal = 0.0f
        for (i in 0..100) {
            val frac = i / 100.0f
            val value = AnalyticalSpringSolver.solve(
                stiffness = 300.0f,
                dampingRatio = 1.0f,
                fraction = frac,
                durationSec = 1.0f
            )
            assertThat(value).isAtLeast(prev)
            prev = value
            if (value > maxVal) maxVal = value
        }
        assertThat(maxVal).isAtMost(1.001f)
    }

    @Test
    fun overdamped_monotonicallyIncreasesWithoutOvershoot() {
        var prev = -0.001f
        var maxVal = 0.0f
        for (i in 0..100) {
            val frac = i / 100.0f
            val value = AnalyticalSpringSolver.solve(
                stiffness = 200.0f,
                dampingRatio = 1.5f,
                fraction = frac,
                durationSec = 1.0f
            )
            assertThat(value).isAtLeast(prev)
            prev = value
            if (value > maxVal) maxVal = value
        }
        assertThat(maxVal).isAtMost(1.001f)
    }

    @Test
    fun timestampEvaluation_atStartAndEnd() {
        val atStart = AnalyticalSpringSolver.solveAtTimestamp(
            stiffness = 300f,
            dampingRatio = 0.7f,
            timestampUs = 0L,
            startUs = 0L,
            durationUs = 1_000_000L
        )
        assertThat(atStart).isEqualTo(0.0f)

        val beforeStart = AnalyticalSpringSolver.solveAtTimestamp(
            stiffness = 300f,
            dampingRatio = 0.7f,
            timestampUs = -100L,
            startUs = 0L,
            durationUs = 1_000_000L
        )
        assertThat(beforeStart).isEqualTo(0.0f)
    }

    @Test
    fun invalidParameters_throwException() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticalSpringSolver.solve(stiffness = -10f, dampingRatio = 0.5f, fraction = 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticalSpringSolver.solve(stiffness = 100f, dampingRatio = -0.5f, fraction = 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticalSpringSolver.solve(stiffness = 100f, dampingRatio = 0.5f, fraction = 0.5f, mass = 0f)
        }
    }
}
