package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ColorAdjustments(
    val exposure: Float = 0.0f,     // [-2.0, 2.0]
    val brightness: Float = 0.0f,   // [-1.0, 1.0]
    val contrast: Float = 1.0f,     // [0.0, 2.0]
    val saturation: Float = 1.0f,   // [0.0, 2.0]
    val temperature: Float = 0.0f,  // [-1.0, 1.0] (cool blue to warm amber)
    val tint: Float = 0.0f,         // [-1.0, 1.0] (green to magenta)
    val vignette: Float = 0.0f,     // [0.0, 1.0]
    val sharpen: Float = 0.0f,      // [0.0, 1.0]
    val highlights: Float = 0.0f,   // [-1.0, 1.0]
    val shadows: Float = 0.0f       // [-1.0, 1.0]
) {
    init {
        require(exposure in -2.0f..2.0f) { "exposure ($exposure) must be in [-2.0, 2.0]" }
        require(brightness in -1.0f..1.0f) { "brightness ($brightness) must be in [-1.0, 1.0]" }
        require(contrast in 0.0f..2.0f) { "contrast ($contrast) must be in [0.0, 2.0]" }
        require(saturation in 0.0f..2.0f) { "saturation ($saturation) must be in [0.0, 2.0]" }
        require(temperature in -1.0f..1.0f) { "temperature ($temperature) must be in [-1.0, 1.0]" }
        require(tint in -1.0f..1.0f) { "tint ($tint) must be in [-1.0, 1.0]" }
        require(vignette in 0.0f..1.0f) { "vignette ($vignette) must be in [0.0, 1.0]" }
        require(sharpen in 0.0f..1.0f) { "sharpen ($sharpen) must be in [0.0, 1.0]" }
        require(highlights in -1.0f..1.0f) { "highlights ($highlights) must be in [-1.0, 1.0]" }
        require(shadows in -1.0f..1.0f) { "shadows ($shadows) must be in [-1.0, 1.0]" }
    }

    val isIdentity: Boolean
        get() = exposure == 0.0f && brightness == 0.0f && contrast == 1.0f &&
                saturation == 1.0f && temperature == 0.0f && tint == 0.0f &&
                vignette == 0.0f && sharpen == 0.0f && highlights == 0.0f && shadows == 0.0f
}

