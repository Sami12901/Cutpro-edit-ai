package com.cutpro.core.motiongraphics.model

data class BezierParams(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
) {
    init {
        require(x1 in 0.0f..1.0f) { "Bezier control point x1 ($x1) must be in [0, 1]" }
        require(x2 in 0.0f..1.0f) { "Bezier control point x2 ($x2) must be in [0, 1]" }
    }

    companion object {
        val DEFAULT = BezierParams(0.25f, 0.1f, 0.25f, 1.0f)
        val EASE = BezierParams(0.25f, 0.1f, 0.25f, 1.0f)
        val EASE_IN = BezierParams(0.42f, 0.0f, 1.0f, 1.0f)
        val EASE_OUT = BezierParams(0.0f, 0.0f, 0.58f, 1.0f)
        val EASE_IN_OUT = BezierParams(0.42f, 0.0f, 0.58f, 1.0f)
    }
}

data class SpringParams(
    val stiffness: Float = 300.0f,
    val dampingRatio: Float = 0.7f,
    val mass: Float = 1.0f
) {
    init {
        require(stiffness > 0f) { "Spring stiffness ($stiffness) must be positive" }
        require(dampingRatio >= 0f) { "Spring dampingRatio ($dampingRatio) must be non-negative" }
        require(mass > 0f) { "Spring mass ($mass) must be positive" }
    }

    companion object {
        val BOUNCY = SpringParams(stiffness = 280.0f, dampingRatio = 0.55f)
        val SMOOTH = SpringParams(stiffness = 300.0f, dampingRatio = 0.75f)
        val STIFF = SpringParams(stiffness = 450.0f, dampingRatio = 0.85f)
    }
}

data class Keyframe<T>(
    var timestampUs: Long,
    var value: T,
    var easing: EasingType = EasingType.LINEAR,
    var bezierParams: BezierParams? = null,
    var springParams: SpringParams? = null
) {
    init {
        require(timestampUs >= 0L) { "Keyframe timestampUs ($timestampUs) cannot be negative" }
    }
}
