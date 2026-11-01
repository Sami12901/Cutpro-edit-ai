package com.cutpro.core.model

import kotlinx.serialization.Serializable

/**
 * Supported canvas aspect ratios with macroblock-aligned dimension calculations.
 */
@Serializable
enum class CanvasAspectRatio(val widthRatio: Int, val heightRatio: Int, val label: String) {
    RATIO_16_9(16, 9, "16:9 Landscape"),
    RATIO_9_16(9, 16, "9:16 Vertical"),
    RATIO_1_1(1, 1, "1:1 Square"),
    RATIO_4_5(4, 5, "4:5 Portrait"),
    RATIO_21_9(21, 9, "21:9 Ultrawide");

    val aspectRatioFloat: Float get() = widthRatio.toFloat() / heightRatio.toFloat()

    /**
     * Calculates width and height for a target height, guaranteeing that both dimensions
     * are multiples of [alignMultipleOf] (defaults to 2 for YUV420p even chroma alignment).
     */
    fun calculateDimensions(targetHeight: Int, alignMultipleOf: Int = 2): Pair<Int, Int> {
        require(targetHeight > 0) { "targetHeight ($targetHeight) must be positive" }
        require(alignMultipleOf > 0 && alignMultipleOf % 2 == 0) {
            "alignMultipleOf ($alignMultipleOf) must be a positive even integer"
        }
        val alignedHeight = ((targetHeight / alignMultipleOf) * alignMultipleOf).coerceAtLeast(alignMultipleOf)
        val rawWidth = (alignedHeight.toLong() * widthRatio / heightRatio).toInt()
        val alignedWidth = ((rawWidth / alignMultipleOf) * alignMultipleOf).coerceAtLeast(alignMultipleOf)
        return Pair(alignedWidth, alignedHeight)
    }

    /**
     * Calculates macroblock-aligned dimensions (multiples of 16) for hardware video encoders.
     */
    fun calculateMacroblockDimensions(targetHeight: Int, macroblockSize: Int = 16): Pair<Int, Int> {
        require(targetHeight > 0) { "targetHeight ($targetHeight) must be positive" }
        require(macroblockSize > 0 && macroblockSize % 16 == 0) {
            "macroblockSize ($macroblockSize) must be a multiple of 16"
        }
        val alignedHeight = (((targetHeight + macroblockSize / 2) / macroblockSize) * macroblockSize).coerceAtLeast(macroblockSize)
        val rawWidth = (alignedHeight.toLong() * widthRatio / heightRatio).toInt()
        val alignedWidth = (((rawWidth + macroblockSize / 2) / macroblockSize) * macroblockSize).coerceAtLeast(macroblockSize)
        return Pair(alignedWidth, alignedHeight)
    }
}
