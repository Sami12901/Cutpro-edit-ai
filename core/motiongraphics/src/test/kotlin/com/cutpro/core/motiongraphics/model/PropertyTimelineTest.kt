package com.cutpro.core.motiongraphics.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PropertyTimelineTest {

    @Test
    fun emptyTimeline_returnsDefaultValue() {
        val timeline = PropertyTimeline.floatTimeline(defaultValue = 42.0f)
        assertThat(timeline.evaluateAt(1_000_000L)).isEqualTo(42.0f)
    }

    @Test
    fun singleKeyframe_returnsConstantValue() {
        val timeline = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(Keyframe(1_000_000L, 50.0f)),
            defaultValue = 0.0f
        )
        assertThat(timeline.evaluateAt(500_000L)).isEqualTo(50.0f)
        assertThat(timeline.evaluateAt(2_000_000L)).isEqualTo(50.0f)
    }

    @Test
    fun boundaryClamping_clampsBeforeFirstAndAfterLast() {
        val timeline = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(
                Keyframe(1_000_000L, 10.0f),
                Keyframe(2_000_000L, 20.0f)
            ),
            defaultValue = 0.0f
        )

        assertThat(timeline.evaluateAt(500_000L)).isEqualTo(10.0f)
        assertThat(timeline.evaluateAt(2_500_000L)).isEqualTo(20.0f)
    }

    @Test
    fun linearInterpolation_evaluatesMidpoint() {
        val timeline = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(
                Keyframe(1_000_000L, 10.0f, EasingType.LINEAR),
                Keyframe(2_000_000L, 20.0f, EasingType.LINEAR)
            ),
            defaultValue = 0.0f
        )

        val mid = timeline.evaluateAt(1_500_000L)
        assertThat(mid).isWithin(1e-4f).of(15.0f)
    }

    @Test
    fun loopMode_repeatsKeyframes() {
        val timeline = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(
                Keyframe(0L, 0.0f, EasingType.LINEAR),
                Keyframe(1_000_000L, 100.0f, EasingType.LINEAR)
            ),
            defaultValue = 0.0f,
            loopMode = AnimationLoopMode.LOOP
        )

        // At 1.5s, cycle should be at 0.5s (50.0)
        val valLoop = timeline.evaluateAt(1_500_000L)
        assertThat(valLoop).isWithin(1e-4f).of(50.0f)
    }

    @Test
    fun pingPongMode_reversesKeyframes() {
        val timeline = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(
                Keyframe(0L, 0.0f, EasingType.LINEAR),
                Keyframe(1_000_000L, 100.0f, EasingType.LINEAR)
            ),
            defaultValue = 0.0f,
            loopMode = AnimationLoopMode.PING_PONG
        )

        // First cycle (0..1s): 0 -> 100
        // Second cycle (1..2s): 100 -> 0. At 1.5s, should be 50.0
        val valPingPong = timeline.evaluateAt(1_500_000L)
        assertThat(valPingPong).isWithin(1e-4f).of(50.0f)
    }

    @Test
    fun addKeyframe_maintainsTemporalOrder() {
        val timeline = PropertyTimeline.floatTimeline(defaultValue = 0f)
        timeline.addKeyframe(Keyframe(2_000_000L, 20f))
        timeline.addKeyframe(Keyframe(500_000L, 5f))
        timeline.addKeyframe(Keyframe(1_000_000L, 10f))

        val times = timeline.keyframes.map { it.timestampUs }
        assertThat(times).containsExactly(500_000L, 1_000_000L, 2_000_000L).inOrder()
    }

    @Test
    fun removeKeyframe_removesCorrectly() {
        val timeline = PropertyTimeline.floatTimeline(
            initialKeyframes = listOf(
                Keyframe(500_000L, 5f),
                Keyframe(1_000_000L, 10f)
            ),
            defaultValue = 0f
        )
        val removed = timeline.removeKeyframeAtTimestamp(500_000L)
        assertThat(removed).isTrue()
        assertThat(timeline.keyframes).hasSize(1)
        assertThat(timeline.keyframes[0].timestampUs).isEqualTo(1_000_000L)
    }
}
