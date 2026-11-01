package com.cutpro.core.motiongraphics.evaluator

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

object AnalyticalSpringSolver {

    fun solve(
        stiffness: Float,
        dampingRatio: Float,
        fraction: Float,
        durationSec: Float = 1.0f,
        mass: Float = 1.0f
    ): Float {
        require(stiffness > 0f) { "Spring stiffness ($stiffness) must be positive" }
        require(dampingRatio >= 0f) { "Spring damping ratio ($dampingRatio) must be non-negative" }
        require(mass > 0f) { "Spring mass ($mass) must be positive" }

        if (durationSec <= 0f || fraction <= 0f) return 0.0f
        if (fraction >= 1.0f && dampingRatio >= 1.0f) return 1.0f

        val t = fraction * durationSec
        return evaluateAtSeconds(stiffness, dampingRatio, t, mass)
    }

    fun solveAtTimestamp(
        stiffness: Float,
        dampingRatio: Float,
        timestampUs: Long,
        startUs: Long,
        durationUs: Long,
        mass: Float = 1.0f
    ): Float {
        if (durationUs <= 0L) return 1.0f
        if (timestampUs <= startUs) return 0.0f
        val elapsedUs = timestampUs - startUs
        val t = elapsedUs / 1_000_000.0f
        return evaluateAtSeconds(stiffness, dampingRatio, t, mass)
    }

    fun evaluateAtSeconds(
        stiffness: Float,
        dampingRatio: Float,
        t: Float,
        mass: Float = 1.0f
    ): Float {
        if (t <= 0f) return 0.0f

        val omega0 = sqrt(stiffness / mass)
        val zeta = dampingRatio

        return when {
            abs(zeta - 1.0f) < 1e-5f -> {
                // Critically damped
                val decay = exp(-omega0 * t)
                1.0f - decay * (1.0f + omega0 * t)
            }
            zeta < 1.0f -> {
                // Underdamped
                val omegaD = omega0 * sqrt(1.0f - zeta * zeta)
                val decay = exp(-zeta * omega0 * t)
                val sinTerm = (zeta * omega0 / omegaD) * sin(omegaD * t)
                val cosTerm = cos(omegaD * t)
                1.0f - decay * (cosTerm + sinTerm)
            }
            else -> {
                // Overdamped
                val gamma = omega0 * sqrt(zeta * zeta - 1.0f)
                val c1 = (zeta * omega0 + gamma) / (2.0f * gamma)
                val c2 = (zeta * omega0 - gamma) / (2.0f * gamma)
                val r1 = -zeta * omega0 + gamma
                val r2 = -zeta * omega0 - gamma
                1.0f - (c1 * exp(r1 * t) - c2 * exp(r2 * t))
            }
        }
    }
}
