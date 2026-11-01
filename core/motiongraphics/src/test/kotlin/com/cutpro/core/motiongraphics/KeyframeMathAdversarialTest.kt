package com.cutpro.core.motiongraphics

import com.cutpro.core.motiongraphics.evaluator.AnalyticalSpringSolver
import com.cutpro.core.motiongraphics.evaluator.CubicBezierSolver
import com.cutpro.core.motiongraphics.evaluator.LinearRgbColorInterpolator
import com.cutpro.core.motiongraphics.model.AnimationLoopMode
import com.cutpro.core.motiongraphics.model.EasingType
import com.cutpro.core.motiongraphics.model.Keyframe
import com.cutpro.core.motiongraphics.model.PropertyTimeline
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.measureNanoTime

class KeyframeMathAdversarialTest {

    // =========================================================================
    // 1. SPRING SOLVER ADVERSARIAL STRESS
    // =========================================================================

    @Test
    fun spring_extremeParameters_noNanOrInfinity() {
        val stiffnessValues = floatArrayOf(1e-3f, 0.01f, 0.1f, 1.0f, 100.0f, 10_000.0f, 100_000.0f)
        val dampingValues = floatArrayOf(0.0f, 0.01f, 0.1f, 0.5f, 0.99f, 1.0f, 1.01f, 2.0f, 5.0f, 10.0f)
        val massValues = floatArrayOf(1e-3f, 0.01f, 0.1f, 1.0f, 10.0f, 100.0f)
        val fractions = floatArrayOf(0.0f, 1e-6f, 0.001f, 0.1f, 0.5f, 0.999f, 1.0f, 2.0f)

        for (stiffness in stiffnessValues) {
            for (damping in dampingValues) {
                for (mass in massValues) {
                    for (fraction in fractions) {
                        val result = AnalyticalSpringSolver.solve(
                            stiffness = stiffness,
                            dampingRatio = damping,
                            fraction = fraction,
                            durationSec = 1.0f,
                            mass = mass
                        )
                        assertWithMessage("Spring result NaN for k=$stiffness, z=$damping, m=$mass, f=$fraction")
                            .that(result.isNaN())
                            .isFalse()
                        assertWithMessage("Spring result Infinite for k=$stiffness, z=$damping, m=$mass, f=$fraction")
                            .that(result.isInfinite())
                            .isFalse()
                    }
                }
            }
        }
    }

    @Test
    fun spring_criticalDampingBoundary_continuityAndMonotonicity() {
        val stiffness = 300.0f
        val mass = 1.0f
        val epsilons = floatArrayOf(1e-6f, 5e-6f, 1e-5f, 5e-5f, 1e-4f)

        // Monotonic convergence at exact zeta = 1.0
        var prevVal = -0.0001f
        var maxVal = 0.0f
        for (step in 0..1000) {
            val t = step / 1000.0f * 2.0f // 0 to 2 seconds
            val value = AnalyticalSpringSolver.evaluateAtSeconds(
                stiffness = stiffness,
                dampingRatio = 1.0f,
                t = t,
                mass = mass
            )
            assertThat(value).isAtLeast(prevVal)
            prevVal = value
            if (value > maxVal) maxVal = value
        }
        assertThat(maxVal).isAtMost(1.0001f)

        // Continuity across the critical damping boundary
        for (eps in epsilons) {
            val zetaSub = 1.0f - eps
            val zetaSuper = 1.0f + eps
            for (step in 1..100) {
                val t = step / 100.0f * 1.5f
                val valSub = AnalyticalSpringSolver.evaluateAtSeconds(stiffness, zetaSub, t, mass)
                val valCrit = AnalyticalSpringSolver.evaluateAtSeconds(stiffness, 1.0f, t, mass)
                val valSuper = AnalyticalSpringSolver.evaluateAtSeconds(stiffness, zetaSuper, t, mass)

                assertWithMessage("Discontinuity between zeta=$zetaSub and zeta=1.0 at t=$t")
                    .that(abs(valSub - valCrit))
                    .isLessThan(5e-3f)
                assertWithMessage("Discontinuity between zeta=$zetaSuper and zeta=1.0 at t=$t")
                    .that(abs(valSuper - valCrit))
                    .isLessThan(5e-3f)
            }
        }
    }

    @Test
    fun spring_longDuration100Hours_numericalStability() {
        val duration100HoursSec = 100.0f * 3600.0f // 360,000 seconds
        val testTimes = floatArrayOf(
            3600.0f,       // 1 hour
            36000.0f,      // 10 hours
            duration100HoursSec * 0.5f, // 50 hours
            duration100HoursSec         // 100 hours
        )

        val configurations = listOf(
            Triple(280.0f, 0.55f, 1.0f), // Underdamped
            Triple(300.0f, 1.0f, 1.0f),  // Critically damped
            Triple(450.0f, 1.5f, 1.0f),  // Overdamped
            Triple(1e5f, 2.0f, 0.001f),  // High stiffness low mass overdamped
            Triple(0.01f, 1.0f, 100.0f)  // Low stiffness high mass critically damped
        )

        for ((stiffness, damping, mass) in configurations) {
            for (t in testTimes) {
                val value = AnalyticalSpringSolver.evaluateAtSeconds(stiffness, damping, t, mass)
                assertThat(value.isNaN()).isFalse()
                assertThat(value.isInfinite()).isFalse()

                // Any damped spring with damping > 0 must settle to 1.0 after 1 hour or more
                if (damping >= 0.5f && (stiffness / mass) >= 1.0f) {
                    assertThat(value).isWithin(1e-4f).of(1.0f)
                }
            }
        }

        // Test undamped spring (damping = 0) at 100 hours: bounded within [0, 2]
        val undampedVal = AnalyticalSpringSolver.evaluateAtSeconds(
            stiffness = 100.0f,
            dampingRatio = 0.0f,
            t = duration100HoursSec,
            mass = 1.0f
        )
        assertThat(undampedVal.isNaN()).isFalse()
        assertThat(undampedVal).isAtLeast(-0.01f)
        assertThat(undampedVal).isAtMost(2.01f)
    }

    @Test
    fun spring_microsecondSampling_120fpsVs24fps_bitExactParity() {
        // Analytical evaluation must produce bit-exact identical values at common timestamps,
        // confirming immunity to framerate-dependent Euler/Verlet numerical drift.
        val stiffness = 320.0f
        val damping = 0.65f
        val mass = 1.2f
        val durationUs = 2_000_000L // 2 seconds

        // 24fps frame step: 1,000,000 / 24 = 41,666.666... us.
        // 120fps frame step: 1,000,000 / 120 = 8,333.333... us.
        // Exact frame alignment occurs every 5 frames of 120fps (since 120 = 5 * 24).
        // Using integer microsecond timestamps: t_k = (k * 1_000_000L) / 24L.
        for (k in 0..48) { // 48 frames at 24fps = 2 seconds
            val timestamp24 = (k * 1_000_000L) / 24L
            val frame120 = k * 5
            val timestamp120 = (frame120 * 1_000_000L) / 120L

            assertThat(timestamp120).isEqualTo(timestamp24)

            val val24 = AnalyticalSpringSolver.solveAtTimestamp(
                stiffness = stiffness,
                dampingRatio = damping,
                timestampUs = timestamp24,
                startUs = 0L,
                durationUs = durationUs,
                mass = mass
            )
            val val120 = AnalyticalSpringSolver.solveAtTimestamp(
                stiffness = stiffness,
                dampingRatio = damping,
                timestampUs = timestamp120,
                startUs = 0L,
                durationUs = durationUs,
                mass = mass
            )

            // Bit-exact comparison
            val bits24 = java.lang.Float.floatToRawIntBits(val24)
            val bits120 = java.lang.Float.floatToRawIntBits(val120)
            assertWithMessage("Bit mismatch at frame $k (timestamp $timestamp24 us)")
                .that(bits120)
                .isEqualTo(bits24)
        }
    }

    // =========================================================================
    // 2. BEZIER SOLVER ADVERSARIAL STRESS
    // =========================================================================

    @Test
    fun bezier_degenerateControlPoints_zeroAndOne() {
        // Degenerate case 1: x1 = 0, x2 = 0 (X(u) = u^3, dX/du -> 0 at u = 0)
        for (step in 0..20) {
            val targetX = step / 20.0f
            val y = CubicBezierSolver.solve(0.0f, 0.0f, 0.0f, 1.0f, targetX)
            assertThat(y.isNaN()).isFalse()
            assertThat(y).isAtLeast(0.0f)
            assertThat(y).isAtMost(1.0f)
        }

        // Degenerate case 2: x1 = 1, x2 = 1 (X(u) = 1 - (1-u)^3, dX/du -> 0 at u = 1)
        for (step in 0..20) {
            val targetX = step / 20.0f
            val y = CubicBezierSolver.solve(1.0f, 0.0f, 1.0f, 1.0f, targetX)
            assertThat(y.isNaN()).isFalse()
            assertThat(y).isAtLeast(0.0f)
            assertThat(y).isAtMost(1.0f)
        }

        // Boundary points
        assertThat(CubicBezierSolver.solve(0.0f, 0.0f, 0.0f, 1.0f, 0.0f)).isEqualTo(0.0f)
        assertThat(CubicBezierSolver.solve(0.0f, 0.0f, 0.0f, 1.0f, 1.0f)).isEqualTo(1.0f)
        assertThat(CubicBezierSolver.solve(1.0f, 0.0f, 1.0f, 1.0f, 0.0f)).isEqualTo(0.0f)
        assertThat(CubicBezierSolver.solve(1.0f, 0.0f, 1.0f, 1.0f, 1.0f)).isEqualTo(1.0f)
    }

    @Test
    fun bezier_zeroDerivativeInflectionPoint_bisectionFallback() {
        // Curve with dX/du = 0 at inflection point u = 0.5:
        // x1 = 1.0, x2 = 0.0.
        // X(u) = 3(1)u(1-u)^2 + 3(0)u^2(1-u) + u^3 = 3u(1 - 2u + u^2) + u^3 = 3u - 6u^2 + 4u^3.
        // dX/du = 12u^2 - 12u + 3 = 3(4u^2 - 4u + 1) = 3(2u - 1)^2.
        // At u = 0.5: dX/du = 0.0 exactly!
        // At targetX = 0.5: X(0.5) = 3(0.5) - 6(0.25) + 4(0.125) = 1.5 - 1.5 + 0.5 = 0.5.
        // With y1 = 0.0, y2 = 1.0: Y(u) = 3(0)u(1-u)^2 + 3(1)u^2(1-u) + u^3 = 3u^2 - 2u^3.
        // Y(0.5) = 3(0.25) - 2(0.125) = 0.75 - 0.25 = 0.5.
        val yMid = CubicBezierSolver.solve(
            x1 = 1.0f,
            y1 = 0.0f,
            x2 = 0.0f,
            y2 = 1.0f,
            targetX = 0.5f
        )
        assertThat(yMid.isNaN()).isFalse()
        assertThat(yMid).isWithin(1e-3f).of(0.5f)

        // Verify across entire sweep that no targetX causes NaN or divergence
        for (step in 0..50) {
            val targetX = step / 50.0f
            val y = CubicBezierSolver.solve(1.0f, 0.0f, 0.0f, 1.0f, targetX)
            assertThat(y.isNaN()).isFalse()
            assertThat(y).isAtLeast(0.0f)
            assertThat(y).isAtMost(1.0f)
        }
    }

    @Test
    fun bezier_outOfBoundsQueries_clampedSafely() {
        val negativeQueries = floatArrayOf(-1e-6f, -0.01f, -1.0f, -100.0f, Float.NEGATIVE_INFINITY)
        val overflowQueries = floatArrayOf(1.00001f, 1.1f, 2.0f, 500.0f, Float.POSITIVE_INFINITY)

        for (targetX in negativeQueries) {
            val y = CubicBezierSolver.solve(0.25f, 0.1f, 0.25f, 1.0f, targetX)
            assertThat(y).isEqualTo(0.0f)
        }

        for (targetX in overflowQueries) {
            val y = CubicBezierSolver.solve(0.25f, 0.1f, 0.25f, 1.0f, targetX)
            assertThat(y).isEqualTo(1.0f)
        }
    }

    @Test
    fun bezier_overshootYCoordinates_evaluatedWithoutClamping() {
        // Bezier curves in CSS animation can overshoot Y < 0 or Y > 1 for spring-like anticipation
        val yOvershoot = CubicBezierSolver.solve(
            x1 = 0.25f,
            y1 = -0.5f,
            x2 = 0.75f,
            y2 = 1.5f,
            targetX = 0.2f
        )
        assertThat(yOvershoot).isLessThan(0.0f)

        val yPeak = CubicBezierSolver.solve(
            x1 = 0.25f,
            y1 = -0.5f,
            x2 = 0.75f,
            y2 = 1.5f,
            targetX = 0.8f
        )
        assertThat(yPeak).isGreaterThan(1.0f)
    }

    // =========================================================================
    // 3. COLOR INTERPOLATOR ADVERSARIAL STRESS
    // =========================================================================

    @Test
    fun color_boundaryColors_fullAlphaAndZeroAlpha() {
        val transparentBlack = 0x00000000
        val opaqueWhite = 0xFFFFFFFF.toInt()
        val pureRed = 0xFFFF0000.toInt()
        val pureGreen = 0xFF00FF00.toInt()
        val pureBlue = 0xFF0000FF.toInt()

        // Exact boundary at t = 0.0
        assertThat(LinearRgbColorInterpolator.interpolate(transparentBlack, opaqueWhite, 0.0f))
            .isEqualTo(transparentBlack)
        // Exact boundary at t = 1.0
        assertThat(LinearRgbColorInterpolator.interpolate(transparentBlack, opaqueWhite, 1.0f))
            .isEqualTo(opaqueWhite)

        // Clamping on negative and overflowing fractions
        assertThat(LinearRgbColorInterpolator.interpolate(pureRed, pureBlue, -0.5f))
            .isEqualTo(pureRed)
        assertThat(LinearRgbColorInterpolator.interpolate(pureRed, pureBlue, 1.5f))
            .isEqualTo(pureBlue)

        // Fully transparent to fully opaque interpolation
        val midAlpha = LinearRgbColorInterpolator.interpolate(transparentBlack, opaqueWhite, 0.5f)
        val alpha = (midAlpha ushr 24) and 0xFF
        assertThat(alpha).isWithin(2).of(128)
    }

    @Test
    fun color_linearRgb_vs_naiveSrgb_producesBrighterMidpoint() {
        val pureRed = 0xFFFF0000.toInt()
        val pureGreen = 0xFF00FF00.toInt()

        val midColor = LinearRgbColorInterpolator.interpolate(pureRed, pureGreen, 0.5f)
        val r = (midColor ushr 16) and 0xFF
        val g = (midColor ushr 8) and 0xFF
        val b = midColor and 0xFF

        // In naive sRGB lerp: midpoint is (128, 128, 0) with channel sum = 256.
        // In linear space with gamma 2.2: 0.5^(1/2.2) * 255 ~= 186.
        // Channel sum is ~372, producing a vibrant, bright yellow rather than a dark muddy brown.
        assertThat(r).isAtLeast(180)
        assertThat(g).isAtLeast(180)
        assertThat(b).isEqualTo(0)
        assertThat(r + g).isGreaterThan(360)
    }

    @Test
    fun color_longAndIntOverloads_produceIdenticalResults() {
        val testPairs = listOf(
            Pair(0x00000000, 0xFFFFFFFF.toInt()),
            Pair(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
            Pair(0x80123456.toInt(), 0x80FEDCBA.toInt()),
            Pair(0x33AABBCC.toInt(), 0xCC112233.toInt())
        )

        for ((c1, c2) in testPairs) {
            for (step in 0..10) {
                val frac = step / 10.0f
                val intResult = LinearRgbColorInterpolator.interpolate(c1, c2, frac)
                val expectedLong = intResult.toLong() and 0xFFFFFFFFL
                val longResult = LinearRgbColorInterpolator.interpolate(
                    c1.toLong() and 0xFFFFFFFFL,
                    c2.toLong() and 0xFFFFFFFFL,
                    frac
                )
                assertThat(longResult).isEqualTo(expectedLong)
            }
        }
    }

    // =========================================================================
    // 4. PROPERTY TIMELINE ADVERSARIAL STRESS
    // =========================================================================

    @Test
    fun timeline_benchmark10kKeyframes_binarySearchUnder5ms() {
        // Construct a massive timeline with 10,000 keyframes
        val keyframeCount = 10_000
        val keyframes = ArrayList<Keyframe<Float>>(keyframeCount)
        for (i in 0 until keyframeCount) {
            val tsUs = i * 100_000L // 100ms intervals = 1,000 seconds total
            val value = (i % 360).toFloat()
            keyframes.add(Keyframe(tsUs, value, EasingType.LINEAR))
        }

        val timeline = PropertyTimeline.floatTimeline(
            initialKeyframes = keyframes,
            defaultValue = 0.0f
        )
        assertThat(timeline.keyframes).hasSize(keyframeCount)

        // Warmup JIT
        for (i in 0 until 500) {
            timeline.evaluateAt((i * 123456L) % (keyframeCount * 100_000L))
        }

        // Benchmark 1,000 pseudo-random queries across the 10,000 keyframe timeline
        val random = Random(42)
        val queryTimestamps = LongArray(1000) {
            random.nextLong(0L, keyframeCount * 100_000L)
        }

        val durationNs = measureNanoTime {
            for (ts in queryTimestamps) {
                val evaluated = timeline.evaluateAt(ts)
                assertThat(evaluated.isNaN()).isFalse()
            }
        }

        val durationMs = durationNs / 1_000_000.0
        // Enforce binary search benchmark: 1,000 queries in a 10,000 element timeline must complete in < 5ms
        assertWithMessage("1,000 queries in 10,000 keyframes took $durationMs ms (must be < 5.0ms)")
            .that(durationMs)
            .isLessThan(5.0)
    }

    @Test
    fun timeline_disorderedInputKeyframes_automaticallySorted() {
        // Keyframes provided out of order
        val disordered = listOf(
            Keyframe(4_000_000L, 40.0f),
            Keyframe(1_000_000L, 10.0f),
            Keyframe(5_000_000L, 50.0f),
            Keyframe(2_000_000L, 20.0f),
            Keyframe(3_000_000L, 30.0f)
        )

        val timeline = PropertyTimeline.floatTimeline(initialKeyframes = disordered)
        val timestamps = timeline.keyframes.map { it.timestampUs }

        // Must be strictly ordered
        assertThat(timestamps).containsExactly(
            1_000_000L,
            2_000_000L,
            3_000_000L,
            4_000_000L,
            5_000_000L
        ).inOrder()

        // Interpolation works as expected
        val midVal = timeline.evaluateAt(2_500_000L)
        assertThat(midVal).isWithin(1e-4f).of(25.0f)
    }

    @Test
    fun timeline_duplicateTimestampKeyframes_replacesExisting() {
        val timeline = PropertyTimeline.floatTimeline()
        timeline.addKeyframe(Keyframe(1_000_000L, 10.0f))
        timeline.addKeyframe(Keyframe(2_000_000L, 20.0f))
        // Duplicate timestamp with different value
        timeline.addKeyframe(Keyframe(1_000_000L, 99.0f))

        assertThat(timeline.keyframes).hasSize(2)
        assertThat(timeline.evaluateAt(1_000_000L)).isEqualTo(99.0f)
    }

    @Test
    fun timeline_singleKeyframe_returnsConstantEverywhere() {
        val timeline = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(Keyframe(2_000_000L, 77.0f)),
            defaultValue = 0.0f
        )

        assertThat(timeline.evaluateAt(0L)).isEqualTo(77.0f)
        assertThat(timeline.evaluateAt(1_000_000L)).isEqualTo(77.0f)
        assertThat(timeline.evaluateAt(2_000_000L)).isEqualTo(77.0f)
        assertThat(timeline.evaluateAt(99_000_000L)).isEqualTo(77.0f)
    }

    @Test
    fun timeline_emptyKeyframes_returnsDefaultValue() {
        val timeline = PropertyTimeline.floatTimeline(defaultValue = 123.45f)
        assertThat(timeline.evaluateAt(0L)).isEqualTo(123.45f)
        assertThat(timeline.evaluateAt(10_000_000L)).isEqualTo(123.45f)
    }

    @Test
    fun timeline_negativeTimestamp_throwsExceptionOnKeyframeCreation() {
        assertThrows(IllegalArgumentException::class.java) {
            Keyframe(timestampUs = -1L, value = 5.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Keyframe(timestampUs = -1_000_000L, value = 5.0f)
        }
    }

    @Test
    fun timeline_pingPongAndLoopModes_across1000Cycles() {
        val timelineLoop = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(
                Keyframe(0L, 0.0f, EasingType.LINEAR),
                Keyframe(1_000_000L, 100.0f, EasingType.LINEAR) // 1-second period
            ),
            loopMode = AnimationLoopMode.LOOP
        )

        val timelinePingPong = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(
                Keyframe(0L, 0.0f, EasingType.LINEAR),
                Keyframe(1_000_000L, 100.0f, EasingType.LINEAR) // 1-second period
            ),
            loopMode = AnimationLoopMode.PING_PONG
        )

        val onePeriodUs = 1_000_000L

        // Test over 1,000 cycles
        for (cycle in 0..1000) {
            val baseTime = cycle * onePeriodUs

            // Quarter point into cycle (+0.25s)
            val tQuarter = baseTime + 250_000L
            val loopVal = timelineLoop.evaluateAt(tQuarter)
            assertWithMessage("LOOP at cycle $cycle (+0.25s)")
                .that(loopVal)
                .isWithin(1e-3f)
                .of(25.0f)

            val pingPongVal = timelinePingPong.evaluateAt(tQuarter)
            if (cycle % 2 == 0) {
                // Forward phase: 0 -> 100
                assertWithMessage("PING_PONG even cycle $cycle (+0.25s)")
                    .that(pingPongVal)
                    .isWithin(1e-3f)
                    .of(25.0f)
            } else {
                // Reverse phase: 100 -> 0 (at 0.25s into reverse, value is 100 - 25 = 75)
                assertWithMessage("PING_PONG odd cycle $cycle (+0.25s)")
                    .that(pingPongVal)
                    .isWithin(1e-3f)
                    .of(75.0f)
            }

            // Midpoint (+0.5s): both LOOP and PING_PONG evaluate to 50.0
            val tHalf = baseTime + 500_000L
            assertThat(timelineLoop.evaluateAt(tHalf)).isWithin(1e-3f).of(50.0f)
            assertThat(timelinePingPong.evaluateAt(tHalf)).isWithin(1e-3f).of(50.0f)

            // Cycle boundaries
            val atStart = timelineLoop.evaluateAt(baseTime)
            assertThat(atStart).isWithin(1e-3f).of(0.0f)
        }
    }

    // =========================================================================
    // 5. EXTENDED ADVERSARIAL STRESS SUITE (CHALLENGER M4 ITERATION 2)
    // =========================================================================

    @Test
    fun spring_threeWaySamplingParity_120Hz_60Hz_24Hz_bitExactParity() {
        val stiffness = 350.0f
        val damping = 0.70f
        val mass = 1.0f
        val durationUs = 5_000_000L

        for (sec in 0..5) {
            val tsUs = sec * 1_000_000L
            val v120 = AnalyticalSpringSolver.solveAtTimestamp(stiffness, damping, tsUs, 0L, durationUs, mass)
            val v60 = AnalyticalSpringSolver.solveAtTimestamp(stiffness, damping, tsUs, 0L, durationUs, mass)
            val v24 = AnalyticalSpringSolver.solveAtTimestamp(stiffness, damping, tsUs, 0L, durationUs, mass)

            val bits120 = java.lang.Float.floatToRawIntBits(v120)
            val bits60 = java.lang.Float.floatToRawIntBits(v60)
            val bits24 = java.lang.Float.floatToRawIntBits(v24)

            assertWithMessage("Parity mismatch at second $sec between 120Hz and 60Hz").that(bits120).isEqualTo(bits60)
            assertWithMessage("Parity mismatch at second $sec between 60Hz and 24Hz").that(bits60).isEqualTo(bits24)
        }

        val arbitraryTimestamps = longArrayOf(125_000L, 250_000L, 500_000L, 750_000L, 1_250_000L, 2_500_000L, 3_750_000L)
        for (ts in arbitraryTimestamps) {
            val vA = AnalyticalSpringSolver.solveAtTimestamp(stiffness, damping, ts, 0L, durationUs, mass)
            val vB = AnalyticalSpringSolver.solveAtTimestamp(stiffness, damping, ts, 0L, durationUs, mass)
            assertThat(java.lang.Float.floatToRawIntBits(vA)).isEqualTo(java.lang.Float.floatToRawIntBits(vB))
        }
    }

    @Test
    fun spring_zeroIterativeDrift_independentOfEvaluationHistory() {
        val stiffness = 250.0f
        val damping = 0.6f
        val mass = 1.0f
        val targetTsUs = 1_543_210L
        val durationUs = 3_000_000L

        val baselineValue = AnalyticalSpringSolver.solveAtTimestamp(
            stiffness = stiffness,
            dampingRatio = damping,
            timestampUs = targetTsUs,
            startUs = 0L,
            durationUs = durationUs,
            mass = mass
        )

        var dummySum = 0.0f
        for (i in 0 until 50_000) {
            val intermediateTs = (i * 20L) % durationUs
            dummySum += AnalyticalSpringSolver.solveAtTimestamp(
                stiffness = stiffness,
                dampingRatio = damping,
                timestampUs = intermediateTs,
                startUs = 0L,
                durationUs = durationUs,
                mass = mass
            )
        }
        assertThat(dummySum.isNaN()).isFalse()

        val afterHistoryValue = AnalyticalSpringSolver.solveAtTimestamp(
            stiffness = stiffness,
            dampingRatio = damping,
            timestampUs = targetTsUs,
            startUs = 0L,
            durationUs = durationUs,
            mass = mass
        )

        assertThat(java.lang.Float.floatToRawIntBits(afterHistoryValue))
            .isEqualTo(java.lang.Float.floatToRawIntBits(baselineValue))
    }

    @Test
    fun spring_asymptoticConvergence_allDampingRegimesAtExtremeDurations() {
        val stiffness = 300.0f
        val mass = 1.0f
        val testTimes = floatArrayOf(
            3_600.0f,
            36_000.0f,
            180_000.0f,
            360_000.0f
        )

        val dampingRegimes = listOf(
            0.05f to "lightly underdamped",
            0.50f to "moderately underdamped",
            0.95f to "heavily underdamped",
            1.00f to "critically damped",
            1.05f to "lightly overdamped",
            2.00f to "moderately overdamped",
            10.0f to "heavily overdamped",
            50.0f to "extremely overdamped"
        )

        for ((damping, regimeName) in dampingRegimes) {
            for (t in testTimes) {
                val value = AnalyticalSpringSolver.evaluateAtSeconds(stiffness, damping, t, mass)
                assertWithMessage("Regime $regimeName (zeta=$damping) at t=${t}s produced NaN")
                    .that(value.isNaN()).isFalse()
                assertWithMessage("Regime $regimeName (zeta=$damping) at t=${t}s produced Inf")
                    .that(value.isInfinite()).isFalse()
                assertWithMessage("Regime $regimeName (zeta=$damping) did not converge to 1.0 at t=${t}s")
                    .that(value).isWithin(1e-4f).of(1.0f)
            }
        }
    }

    @Test
    fun spring_denseCriticalDampingBoundaryScan_continuousTransition() {
        val stiffness = 300.0f
        val mass = 1.0f
        val t = 0.5f

        val critVal = AnalyticalSpringSolver.evaluateAtSeconds(stiffness, 1.0f, t, mass)

        for (step in -100..100) {
            val delta = step * 5e-6f
            val zeta = 1.0f + delta
            val value = AnalyticalSpringSolver.evaluateAtSeconds(stiffness, zeta, t, mass)
            assertThat(value.isNaN()).isFalse()
            assertThat(value.isInfinite()).isFalse()
            assertThat(abs(value - critVal)).isLessThan(2e-4f)
        }
    }

    @Test
    fun bezier_denseControlPointGrid_exhaustiveAccuracy() {
        val xCoords = floatArrayOf(0.0f, 0.001f, 0.05f, 0.25f, 0.5f, 0.75f, 0.95f, 0.999f, 1.0f)
        val yCoords = floatArrayOf(-0.5f, 0.0f, 0.5f, 1.0f, 1.5f)

        var totalEvaluations = 0
        for (x1 in xCoords) {
            for (x2 in xCoords) {
                for (y1 in yCoords) {
                    for (y2 in yCoords) {
                        for (step in 0..10) {
                            val targetX = step / 10.0f
                            val y = CubicBezierSolver.solve(x1, y1, x2, y2, targetX)
                            assertWithMessage("Bezier failed for x1=$x1, x2=$x2, y1=$y1, y2=$y2, targetX=$targetX")
                                .that(y.isNaN()).isFalse()
                            assertWithMessage("Bezier inf for x1=$x1, x2=$x2, y1=$y1, y2=$y2, targetX=$targetX")
                                .that(y.isInfinite()).isFalse()
                            totalEvaluations++
                        }
                    }
                }
            }
        }
        assertThat(totalEvaluations).isGreaterThan(10_000)
    }

    @Test
    fun bezier_extremeSteepSlopes_bisectionAccuracy() {
        val ySteepStart = CubicBezierSolver.solve(
            x1 = 0.0001f,
            y1 = 0.9999f,
            x2 = 0.0001f,
            y2 = 0.9999f,
            targetX = 0.1f
        )
        assertThat(ySteepStart).isGreaterThan(0.8f)

        val ySteepEnd = CubicBezierSolver.solve(
            x1 = 0.9999f,
            y1 = 0.0001f,
            x2 = 0.9999f,
            y2 = 0.0001f,
            targetX = 0.9f
        )
        assertThat(ySteepEnd).isLessThan(0.2f)

        val yMidSteepStart = CubicBezierSolver.solve(0.0001f, 0.9999f, 0.0001f, 0.9999f, 0.5f)
        assertThat(yMidSteepStart).isGreaterThan(0.95f)

        val yMidSteepEnd = CubicBezierSolver.solve(0.9999f, 0.0001f, 0.9999f, 0.0001f, 0.5f)
        assertThat(yMidSteepEnd).isLessThan(0.05f)
    }


    @Test
    fun color_all256Levels_identityRoundTripAtBoundaries() {
        for (i in 0..255) {
            val color = (0xFF shl 24) or (i shl 16) or (i shl 8) or i
            val other = 0xFF123456.toInt()

            val atZero = LinearRgbColorInterpolator.interpolate(color, other, 0.0f)
            val atOne = LinearRgbColorInterpolator.interpolate(other, color, 1.0f)

            assertWithMessage("Level $i mismatch at t=0").that(atZero).isEqualTo(color)
            assertWithMessage("Level $i mismatch at t=1").that(atOne).isEqualTo(color)
        }
    }

    @Test
    fun color_radiometricAccuracy_complementaryPairsPreserveLuminance() {
        val red = 0xFFFF0000.toInt()
        val cyan = 0xFF00FFFF.toInt()
        val midRedCyan = LinearRgbColorInterpolator.interpolate(red, cyan, 0.5f)
        val rRC = (midRedCyan ushr 16) and 0xFF
        val gRC = (midRedCyan ushr 8) and 0xFF
        val bRC = midRedCyan and 0xFF
        assertThat(rRC).isWithin(3).of(186)
        assertThat(gRC).isWithin(3).of(186)
        assertThat(bRC).isWithin(3).of(186)

        val green = 0xFF00FF00.toInt()
        val magenta = 0xFFFF00FF.toInt()
        val midGreenMag = LinearRgbColorInterpolator.interpolate(green, magenta, 0.5f)
        val rGM = (midGreenMag ushr 16) and 0xFF
        val gGM = (midGreenMag ushr 8) and 0xFF
        val bGM = midGreenMag and 0xFF
        assertThat(rGM).isWithin(3).of(186)
        assertThat(gGM).isWithin(3).of(186)
        assertThat(bGM).isWithin(3).of(186)
    }

    @Test
    fun color_alphaBlending_strictlyMonotonic() {
        val transColor = 0x00FF0000
        val opaqueColor = 0xFFFF0000.toInt()

        var prevAlpha = -1
        for (step in 0..255) {
            val frac = step / 255.0f
            val interpolated = LinearRgbColorInterpolator.interpolate(transColor, opaqueColor, frac)
            val alpha = (interpolated ushr 24) and 0xFF
            assertThat(alpha).isAtLeast(prevAlpha)
            prevAlpha = alpha
        }
        assertThat(prevAlpha).isEqualTo(255)
    }

    @Test
    fun timeline_scaleStress_50kKeyframes_memoryAndLatency() {
        val count = 50_000
        val keyframes = ArrayList<Keyframe<Float>>(count)
        for (i in 0 until count) {
            val tsUs = i * 20_000L
            keyframes.add(Keyframe(tsUs, (i % 1000).toFloat(), EasingType.LINEAR))
        }

        val runtime = Runtime.getRuntime()
        runtime.gc()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        val timeline = PropertyTimeline.floatTimeline(initialKeyframes = keyframes)
        assertThat(timeline.keyframes).hasSize(count)

        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val deltaMb = (memAfter - memBefore) / (1024.0 * 1024.0)

        assertWithMessage("50k keyframes consumed $deltaMb MB (expected < 25 MB)")
            .that(deltaMb).isLessThan(25.0)

        for (i in 0..200) {
            timeline.evaluateAt(i * 123456L % (count * 20_000L))
        }

        val random = Random(12345)
        val queries = LongArray(5000) { random.nextLong(0L, count * 20_000L) }

        val elapsedNs = measureNanoTime {
            for (ts in queries) {
                val v = timeline.evaluateAt(ts)
                assertThat(v.isNaN()).isFalse()
            }
        }
        val elapsedMs = elapsedNs / 1_000_000.0
        assertWithMessage("5,000 queries in 50k keyframes took $elapsedMs ms (must be < 25.0ms)")
            .that(elapsedMs).isLessThan(25.0)
    }

    @Test
    fun timeline_denseSweep_60fpsPlaybackSimulation() {
        val count = 10_000
        val keyframes = ArrayList<Keyframe<Float>>(count)
        for (i in 0 until count) {
            keyframes.add(Keyframe(i * 100_000L, i.toFloat(), EasingType.LINEAR))
        }
        val timeline = PropertyTimeline.floatTimeline(initialKeyframes = keyframes)

        val frameStepUs = 16_666L
        val totalFrames = 600
        var prevValue = -1.0f

        val elapsedNs = measureNanoTime {
            for (f in 0 until totalFrames) {
                val ts = f * frameStepUs
                val value = timeline.evaluateAt(ts)
                assertThat(value).isAtLeast(prevValue)
                prevValue = value
            }
        }
        val elapsedMs = elapsedNs / 1_000_000.0
        assertThat(elapsedMs).isLessThan(10.0)
    }
}

