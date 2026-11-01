package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExportResolution(val width: Int, val height: Int, val displayName: String) {
    RES_720P(1280, 720, "720p HD"),
    RES_1080P(1920, 1080, "1080p Full HD");

    fun getDimensionsForAspectRatio(aspectRatio: CanvasAspectRatio): Pair<Int, Int> {
        return aspectRatio.calculateDimensions(this.height, alignMultipleOf = 16)
    }
}
