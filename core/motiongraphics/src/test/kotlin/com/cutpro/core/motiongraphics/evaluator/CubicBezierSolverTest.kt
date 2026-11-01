package com.cutpro.core.motiongraphics.evaluator

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CubicBezierSolverTest {

    @Test
    fun linearBezier_returnsLinearProgress() {
        val y0 = CubicBezierSolver.solve(0.0f, 0.0f, 1.0f, 1.0f, 0.0f)
        val yMid = CubicBezierSolver.solve(0.0f, 0.0f, 1.0f, 1.0f, 0.5f)
        val y1 = CubicBezierSolver.solve(0.0f, 0.0f, 1.0f, 1.0f, 1.0f)

        assertThat(y0).isEqualTo(0.0f)
        assertThat(yMid).isWithin(1e-4f).of(0.5f)
        assertThat(y1).isEqualTo(1.0f)
    }

    @Test
    fun easeOutBezier_progressesFasterAtBeginning() {
        // Standard EaseOut: (0.0, 0.0, 0.58, 1.0)
        val yQuarter = CubicBezierSolver.solve(0.0f, 0.0f, 0.58f, 1.0f, 0.25f)
        assertThat(yQuarter).isGreaterThan(0.25f)
    }

    @Test
    fun easeInBezier_progressesSlowerAtBeginning() {
        // Standard EaseIn: (0.42, 0.0, 1.0, 1.0)
        val yQuarter = CubicBezierSolver.solve(0.42f, 0.0f, 1.0f, 1.0f, 0.25f)
        assertThat(yQuarter).isLessThan(0.25f)
    }

    @Test
    fun steepCurve_bisectionFallback_convergesAccurately() {
        // Steep curve with nearly vertical tangent at beginning
        val y = CubicBezierSolver.solve(0.001f, 0.999f, 0.001f, 0.999f, 0.5f)
        assertThat(y).isGreaterThan(0.9f)
    }

    @Test
    fun boundaryValues_clampedCleanly() {
        assertThat(CubicBezierSolver.solve(0.25f, 0.1f, 0.25f, 1.0f, -0.5f)).isEqualTo(0.0f)
        assertThat(CubicBezierSolver.solve(0.25f, 0.1f, 0.25f, 1.0f, 1.5f)).isEqualTo(1.0f)
    }

    @Test
    fun invalidControlPoints_throwException() {
        assertThrows(IllegalArgumentException::class.java) {
            CubicBezierSolver.solve(-0.1f, 0.0f, 0.5f, 1.0f, 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CubicBezierSolver.solve(0.0f, 0.0f, 1.1f, 1.0f, 0.5f)
        }
    }
}
