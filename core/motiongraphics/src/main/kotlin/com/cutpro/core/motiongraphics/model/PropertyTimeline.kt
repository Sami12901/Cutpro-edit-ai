package com.cutpro.core.motiongraphics.model

import com.cutpro.core.motiongraphics.evaluator.EasingEvaluator
import com.cutpro.core.motiongraphics.evaluator.LinearRgbColorInterpolator
import java.util.Collections

class PropertyTimeline<T>(
    initialKeyframes: List<Keyframe<T>> = emptyList(),
    val defaultValue: T,
    var loopMode: AnimationLoopMode = AnimationLoopMode.ONCE,
    private val interpolator: (start: T, end: T, fraction: Float) -> T
) {
    private val _keyframes = mutableListOf<Keyframe<T>>()
    val keyframes: List<Keyframe<T>>
        get() = Collections.unmodifiableList(_keyframes)

    init {
        for (kf in initialKeyframes) {
            addKeyframe(kf)
        }
    }

    fun addKeyframe(keyframe: Keyframe<T>) {
        val idx = _keyframes.binarySearch { it.timestampUs.compareTo(keyframe.timestampUs) }
        if (idx >= 0) {
            _keyframes[idx] = keyframe
        } else {
            val insertIdx = -(idx + 1)
            _keyframes.add(insertIdx, keyframe)
        }
    }

    fun removeKeyframeAt(index: Int): Boolean {
        if (index in _keyframes.indices) {
            _keyframes.removeAt(index)
            return true
        }
        return false
    }

    fun removeKeyframeAtTimestamp(timestampUs: Long): Boolean {
        val idx = _keyframes.binarySearch { it.timestampUs.compareTo(timestampUs) }
        if (idx >= 0) {
            _keyframes.removeAt(idx)
            return true
        }
        return false
    }

    fun clearKeyframes() {
        _keyframes.clear()
    }

    fun evaluateAt(timestampUs: Long): T {
        if (_keyframes.isEmpty()) return defaultValue
        if (_keyframes.size == 1) return _keyframes[0].value

        val firstTime = _keyframes.first().timestampUs
        val lastTime = _keyframes.last().timestampUs
        val duration = lastTime - firstTime
        if (duration <= 0L) return _keyframes[0].value

        val effectiveTime = when (loopMode) {
            AnimationLoopMode.ONCE -> timestampUs
            AnimationLoopMode.LOOP -> {
                if (timestampUs < firstTime) timestampUs
                else firstTime + ((timestampUs - firstTime) % duration)
            }
            AnimationLoopMode.PING_PONG -> {
                if (timestampUs < firstTime) timestampUs
                else {
                    val cycle = (timestampUs - firstTime) / duration
                    val rem = (timestampUs - firstTime) % duration
                    if (cycle % 2L == 0L) firstTime + rem
                    else lastTime - rem
                }
            }
        }

        // Boundary clamping
        if (effectiveTime <= firstTime) return _keyframes.first().value
        if (effectiveTime >= lastTime) return _keyframes.last().value

        // Binary search to find segment
        val idx = _keyframes.binarySearch { it.timestampUs.compareTo(effectiveTime) }
        if (idx >= 0) {
            return _keyframes[idx].value
        }

        val nextIdx = -(idx + 1)
        val prevIdx = nextIdx - 1
        val k0 = _keyframes[prevIdx]
        val k1 = _keyframes[nextIdx]

        val segDuration = (k1.timestampUs - k0.timestampUs).toFloat()
        val segElapsed = (effectiveTime - k0.timestampUs).toFloat()
        val tau = (segElapsed / segDuration).coerceIn(0.0f, 1.0f)

        val durationSec = (k1.timestampUs - k0.timestampUs) / 1_000_000.0f
        val easedFraction = EasingEvaluator.evaluate(
            type = k0.easing,
            fraction = tau,
            bezierParams = k0.bezierParams,
            springParams = k0.springParams,
            durationSec = durationSec
        )

        return interpolator(k0.value, k1.value, easedFraction)
    }

    companion object {
        fun floatTimeline(
            initialKeyframes: List<Keyframe<Float>> = emptyList(),
            defaultValue: Float = 0.0f,
            loopMode: AnimationLoopMode = AnimationLoopMode.ONCE
        ): PropertyTimeline<Float> {
            return PropertyTimeline(
                initialKeyframes = initialKeyframes,
                defaultValue = defaultValue,
                loopMode = loopMode,
                interpolator = { start, end, fraction ->
                    start + (end - start) * fraction
                }
            )
        }

        fun colorTimeline(
            initialKeyframes: List<Keyframe<Int>> = emptyList(),
            defaultValue: Int = 0xFFFFFFFF.toInt(),
            loopMode: AnimationLoopMode = AnimationLoopMode.ONCE
        ): PropertyTimeline<Int> {
            return PropertyTimeline(
                initialKeyframes = initialKeyframes,
                defaultValue = defaultValue,
                loopMode = loopMode,
                interpolator = { start, end, fraction ->
                    LinearRgbColorInterpolator.interpolate(start, end, fraction)
                }
            )
        }
    }
}
