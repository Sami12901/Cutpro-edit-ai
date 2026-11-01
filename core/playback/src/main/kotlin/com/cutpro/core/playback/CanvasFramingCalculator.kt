package com.cutpro.core.playback

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ClipTransform
import kotlin.math.abs

data class ViewportGeometry(
    val canvasBounds: Rect,
    val letterboxOffset: Offset,
    val previewWidth: Int,
    val previewHeight: Int,
    val encoderWidth: Int,
    val encoderHeight: Int,
    val actionSafeRect: Rect,
    val titleSafeRect: Rect
)

object CanvasFramingCalculator {

    /**
     * Calculates framed canvas viewport geometry inside parent container bounds.
     */
    fun computeViewportGeometry(
        containerWidth: Float,
        containerHeight: Float,
        aspectRatio: CanvasAspectRatio
    ): ViewportGeometry {
        require(containerWidth > 0f && containerHeight > 0f) { "Container dimensions must be positive" }

        val containerRatio = containerWidth / containerHeight
        val targetRatio = aspectRatio.aspectRatioFloat

        val canvasWidth: Float
        val canvasHeight: Float

        if (containerRatio > targetRatio) {
            // Container is wider -> Pillarbox (vertical black bars)
            canvasHeight = containerHeight
            canvasWidth = canvasHeight * targetRatio
        } else {
            // Container is taller -> Letterbox (horizontal black bars)
            canvasWidth = containerWidth
            canvasHeight = canvasWidth / targetRatio
        }

        val offsetX = (containerWidth - canvasWidth) / 2f
        val offsetY = (containerHeight - canvasHeight) / 2f
        val canvasBounds = Rect(offsetX, offsetY, offsetX + canvasWidth, offsetY + canvasHeight)

        // Multiples of 2 (preview/YUV420p) and multiples of 16 (macroblocks)
        val safeHeight = canvasHeight.toInt().coerceAtLeast(1)
        val (prevW, prevH) = aspectRatio.calculateDimensions(safeHeight, alignMultipleOf = 2)
        val (encW, encH) = aspectRatio.calculateMacroblockDimensions(safeHeight, macroblockSize = 16)

        // Safe Zones: Action Safe 93% (3.5% margin), Title Safe 90% (5% margin)
        val actionInsetX = canvasWidth * 0.035f
        val actionInsetY = canvasHeight * 0.035f
        val actionSafe = Rect(
            offsetX + actionInsetX,
            offsetY + actionInsetY,
            offsetX + canvasWidth - actionInsetX,
            offsetY + canvasHeight - actionInsetY
        )

        val titleInsetX = canvasWidth * 0.05f
        val titleInsetY = canvasHeight * 0.05f
        val titleSafe = Rect(
            offsetX + titleInsetX,
            offsetY + titleInsetY,
            offsetX + canvasWidth - titleInsetX,
            offsetY + canvasHeight - titleInsetY
        )

        return ViewportGeometry(
            canvasBounds = canvasBounds,
            letterboxOffset = Offset(offsetX, offsetY),
            previewWidth = prevW,
            previewHeight = prevH,
            encoderWidth = encW,
            encoderHeight = encH,
            actionSafeRect = actionSafe,
            titleSafeRect = titleSafe
        )
    }

    /**
     * Computes render scale and translation for a clip given its FitMode and Transform.
     */
    fun computeClipTransform(
        canvasWidth: Float,
        canvasHeight: Float,
        sourceWidth: Float,
        sourceHeight: Float,
        fitMode: ClipFitMode,
        transform: ClipTransform
    ): Triple<Float, Float, Offset> {
        val baseScaleX: Float
        val baseScaleY: Float

        when (fitMode) {
            ClipFitMode.FIT -> {
                val s = minOf(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
                baseScaleX = s
                baseScaleY = s
            }
            ClipFitMode.FILL -> {
                val s = maxOf(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
                baseScaleX = s
                baseScaleY = s
            }
            ClipFitMode.STRETCH, ClipFitMode.CUSTOM -> {
                baseScaleX = canvasWidth / sourceWidth
                baseScaleY = canvasHeight / sourceHeight
            }
        }

        val finalScaleX = baseScaleX * transform.scaleX
        val finalScaleY = baseScaleY * transform.scaleY

        val transPx = Offset(
            x = transform.translationX * (canvasWidth / 2f),
            y = transform.translationY * (canvasHeight / 2f)
        )

        return Triple(finalScaleX, finalScaleY, transPx)
    }

    /**
     * Maps touch delta into normalized canvas translation delta [-1.0, 1.0].
     */
    fun screenDeltaToNormalizedTranslation(
        deltaX: Float,
        deltaY: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ): Offset {
        return Offset(
            x = (2f * deltaX) / canvasWidth,
            y = (2f * deltaY) / canvasHeight
        )
    }

    /**
     * Snaps rotation angle to cardinal angles (0 deg, 90 deg, 180 deg, 270 deg) if within threshold.
     */
    fun snapRotation(angleDegrees: Float, thresholdDegrees: Float = 3.0f): Float {
        val normalized = ((angleDegrees % 360f) + 360f) % 360f
        val cardinalAngles = floatArrayOf(0f, 90f, 180f, 270f, 360f)
        for (target in cardinalAngles) {
            if (abs(normalized - target) <= thresholdDegrees) {
                return if (target == 360f) 0f else target
            }
        }
        return normalized
    }
}
