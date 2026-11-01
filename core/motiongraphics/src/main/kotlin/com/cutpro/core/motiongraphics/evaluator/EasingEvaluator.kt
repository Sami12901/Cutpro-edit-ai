package com.cutpro.core.motiongraphics.evaluator

import com.cutpro.core.motiongraphics.model.BezierParams
import com.cutpro.core.motiongraphics.model.EasingType
import com.cutpro.core.motiongraphics.model.SpringParams
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object EasingEvaluator {

    fun evaluate(
        type: EasingType,
        fraction: Float,
        bezierParams: BezierParams? = null,
        springParams: SpringParams? = null,
        durationSec: Float = 1.0f
    ): Float {
        val tau = max(0.0f, min(1.0f, fraction))
        return when (type) {
            EasingType.LINEAR -> tau
            EasingType.EASE_IN -> tau * tau * tau
            EasingType.EASE_OUT -> 1.0f - (1.0f - tau).pow(3)
            EasingType.EASE_IN_OUT -> {
                if (tau < 0.5f) {
                    4.0f * tau * tau * tau
                } else {
                    1.0f - (-2.0f * tau + 2.0f).pow(3) / 2.0f
                }
            }
            EasingType.CUBIC_BEZIER -> {
                val params = bezierParams ?: BezierParams.DEFAULT
                CubicBezierSolver.solve(params.x1, params.y1, params.x2, params.y2, tau)
            }
            EasingType.SPRING -> {
                val params = springParams ?: SpringParams.SMOOTH
                AnalyticalSpringSolver.solve(
                    stiffness = params.stiffness,
                    dampingRatio = params.dampingRatio,
                    fraction = tau,
                    durationSec = durationSec,
                    mass = params.mass
                )
            }
        }
    }
}
