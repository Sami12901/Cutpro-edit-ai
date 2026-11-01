package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class LutFilterType(val displayName: String) {
    NONE("Original"),
    CINEMATIC_TEAL_ORANGE("Teal & Orange"),
    MOODY_DARK("Moody Dark"),
    VIBRANT_GOLD("Vibrant Gold"),
    RETRO_FILM_1998("Retro Film 1998"),
    CLEAN_PORTRAIT("Clean Portrait"),
    BW_HIGH_CONTRAST("B&W High Contrast"),
    CYBERPUNK_NEON("Cyberpunk Neon"),
    VINTAGE_WARM("Vintage Warm")
}

@Serializable
enum class BlendMode(val displayName: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    SOFT_LIGHT("Soft Light"),
    ADD("Add / Linear Dodge")
}

@Serializable
enum class MaskType {
    LINEAR,
    MIRROR,
    RADIAL,
    RECTANGLE,
    HEART,
    STAR
}

@Serializable
data class ClipMask(
    val type: MaskType = MaskType.LINEAR,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDegrees: Float = 0.0f,
    val feather: Float = 0.05f,
    val isInverted: Boolean = false
) {
    init {
        require(feather in 0.0f..1.0f) { "feather ($feather) must be in [0.0, 1.0]" }
        require(scaleX > 0.0f && scaleY > 0.0f) { "Mask scales must be positive" }
    }
}

@Serializable
enum class SpeedCurvePreset {
    NONE,
    MONTAGE,
    HERO,
    BULLET,
    JUMP_CUT,
    FLASH_IN,
    FLASH_OUT,
    CUSTOM
}

@Serializable
data class SpeedCurvePoint(
    val normalizedTime: Float, // [0.0, 1.0]
    val speedMultiplier: Float // [0.1, 10.0]
) {
    init {
        require(normalizedTime in 0.0f..1.0f) { "normalizedTime ($normalizedTime) must be in [0.0, 1.0]" }
        require(speedMultiplier in 0.1f..10.0f) { "speedMultiplier ($speedMultiplier) must be in [0.1, 10.0]" }
    }
}

@Serializable
data class SpeedCurve(
    val preset: SpeedCurvePreset = SpeedCurvePreset.NONE,
    val controlPoints: List<SpeedCurvePoint> = emptyList()
) {
    init {
        if (controlPoints.isNotEmpty()) {
            require(controlPoints.first().normalizedTime == 0.0f) {
                "First curve point must start at normalizedTime 0.0"
            }
            require(controlPoints.last().normalizedTime == 1.0f) {
                "Last curve point must end at normalizedTime 1.0"
            }
        }
    }

    companion object {
        fun createPreset(preset: SpeedCurvePreset): SpeedCurve {
            val points = when (preset) {
                SpeedCurvePreset.NONE -> listOf(
                    SpeedCurvePoint(0.0f, 1.0f),
                    SpeedCurvePoint(1.0f, 1.0f)
                )
                SpeedCurvePreset.MONTAGE -> listOf(
                    SpeedCurvePoint(0.0f, 0.5f),
                    SpeedCurvePoint(0.3f, 2.5f),
                    SpeedCurvePoint(0.7f, 2.5f),
                    SpeedCurvePoint(1.0f, 0.5f)
                )
                SpeedCurvePreset.HERO -> listOf(
                    SpeedCurvePoint(0.0f, 1.0f),
                    SpeedCurvePoint(0.2f, 3.0f),
                    SpeedCurvePoint(0.6f, 0.3f),
                    SpeedCurvePoint(1.0f, 1.0f)
                )
                SpeedCurvePreset.BULLET -> listOf(
                    SpeedCurvePoint(0.0f, 4.0f),
                    SpeedCurvePoint(0.2f, 0.2f),
                    SpeedCurvePoint(0.8f, 0.2f),
                    SpeedCurvePoint(1.0f, 4.0f)
                )
                SpeedCurvePreset.JUMP_CUT -> listOf(
                    SpeedCurvePoint(0.0f, 2.0f),
                    SpeedCurvePoint(0.49f, 2.0f),
                    SpeedCurvePoint(0.51f, 0.5f),
                    SpeedCurvePoint(1.0f, 0.5f)
                )
                SpeedCurvePreset.FLASH_IN -> listOf(
                    SpeedCurvePoint(0.0f, 5.0f),
                    SpeedCurvePoint(0.3f, 1.0f),
                    SpeedCurvePoint(1.0f, 1.0f)
                )
                SpeedCurvePreset.FLASH_OUT -> listOf(
                    SpeedCurvePoint(0.0f, 1.0f),
                    SpeedCurvePoint(0.7f, 1.0f),
                    SpeedCurvePoint(1.0f, 5.0f)
                )
                SpeedCurvePreset.CUSTOM -> listOf(
                    SpeedCurvePoint(0.0f, 1.0f),
                    SpeedCurvePoint(1.0f, 1.0f)
                )
            }
            return SpeedCurve(preset = preset, controlPoints = points)
        }
    }
}

@Serializable
data class KenBurnsEffect(
    val startScale: Float = 1.0f,
    val endScale: Float = 1.25f,
    val startTranslationX: Float = 0.0f,
    val endTranslationX: Float = 0.05f,
    val startTranslationY: Float = 0.0f,
    val endTranslationY: Float = 0.0f
) {
    init {
        require(startScale > 0.0f && endScale > 0.0f) { "Scales must be positive" }
    }
}

