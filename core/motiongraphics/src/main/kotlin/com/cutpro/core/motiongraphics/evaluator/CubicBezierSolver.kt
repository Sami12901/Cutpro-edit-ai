package com.cutpro.core.motiongraphics.evaluator

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object CubicBezierSolver {

    fun solve(x1: Float, y1: Float, x2: Float, y2: Float, targetX: Float): Float {
        require(x1 in 0.0f..1.0f && x2 in 0.0f..1.0f) {
            "Bezier control points x1 ($x1) and x2 ($x2) must be in [0, 1]"
        }
        val clampedX = max(0.0f, min(1.0f, targetX))
        if (clampedX <= 0.0f) return 0.0f
        if (clampedX >= 1.0f) return 1.0f

        val cx = 3.0f * x1
        val bx = 3.0f * (x2 - x1) - cx
        val ax = 1.0f - cx - bx

        var u = clampedX
        // Newton-Raphson iteration
        for (i in 0 until 8) {
            val xEst = ((ax * u + bx) * u + cx) * u
            val error = xEst - clampedX
            if (abs(error) < 1e-5f) break

            val dx = (3.0f * ax * u + 2.0f * bx) * u + cx
            if (abs(dx) < 1e-6f) break

            u -= error / dx
            u = max(0.0f, min(1.0f, u))
        }

        // Check if Newton-Raphson converged sufficiently
        val xEst = ((ax * u + bx) * u + cx) * u
        if (abs(xEst - clampedX) >= 1e-4f) {
            var low = 0.0f
            var high = 1.0f
            for (i in 0 until 16) {
                val mid = (low + high) * 0.5f
                val midX = ((ax * mid + bx) * mid + cx) * mid
                if (midX < clampedX) {
                    low = mid
                } else {
                    high = mid
                }
            }
            u = (low + high) * 0.5f
        }

        // Evaluate Y at solved u
        val cy = 3.0f * y1
        val by = 3.0f * (y2 - y1) - cy
        val ay = 1.0f - cy - by
        return ((ay * u + by) * u + cy) * u
    }
}
