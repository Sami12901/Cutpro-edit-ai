package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ClipTransform(
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val translationX: Float = 0.0f, // Normalized offset [-1.0, 1.0] relative to canvas
    val translationY: Float = 0.0f, // Normalized offset [-1.0, 1.0] relative to canvas
    val rotationDegrees: Float = 0.0f,
    val opacity: Float = 1.0f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val mask: ClipMask? = null,
    val kenBurns: KenBurnsEffect? = null
) {
    init {
        require(scaleX != 0f) { "scaleX cannot be 0" }
        require(scaleY != 0f) { "scaleY cannot be 0" }
        require(opacity in 0.0f..1.0f) { "opacity ($opacity) must be in [0.0, 1.0]" }
        require(translationX in -5.0f..5.0f) { "translationX ($translationX) exceeds allowed bounds [-5.0, 5.0]" }
        require(translationY in -5.0f..5.0f) { "translationY ($translationY) exceeds allowed bounds [-5.0, 5.0]" }
    }

    val isIdentity: Boolean
        get() = scaleX == 1.0f && scaleY == 1.0f &&
                translationX == 0.0f && translationY == 0.0f &&
                rotationDegrees == 0.0f && opacity == 1.0f &&
                blendMode == BlendMode.NORMAL && mask == null && kenBurns == null

    fun withScale(uniformScale: Float): ClipTransform {
        require(uniformScale != 0f) { "uniformScale cannot be 0" }
        return copy(scaleX = uniformScale, scaleY = uniformScale)
    }

    fun withTranslation(x: Float, y: Float): ClipTransform {
        return copy(translationX = x, translationY = y)
    }

    fun withRotation(degrees: Float): ClipTransform {
        val normalized = ((degrees % 360f) + 360f) % 360f
        return copy(rotationDegrees = normalized)
    }

    fun withOpacity(newOpacity: Float): ClipTransform {
        return copy(opacity = newOpacity.coerceIn(0.0f, 1.0f))
    }
}
