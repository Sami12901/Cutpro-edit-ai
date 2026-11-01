package com.cutpro.core.motiongraphics.model

import android.graphics.RectF

sealed class RenderLayerState {
    abstract val id: String
    abstract val transform: TrackItemTransform
    abstract val isVisible: Boolean

    data class TextLayer(
        override val id: String,
        override val transform: TrackItemTransform = TrackItemTransform.IDENTITY,
        override val isVisible: Boolean = true,
        val text: String,
        val fontScale: Float = 1.0f,
        val textColor: Int = 0xFFFFFFFF.toInt(),
        val strokeColor: Int = 0x00000000,
        val strokeWidth: Float = 0.0f,
        val letterSpacing: Float = 0.0f,
        val clipRect: RectF? = null,
        val alignment: TextAlignment = TextAlignment.CENTER
    ) : RenderLayerState()

    data class RectLayer(
        override val id: String,
        override val transform: TrackItemTransform = TrackItemTransform.IDENTITY,
        override val isVisible: Boolean = true,
        val bounds: RectF,
        val fillColor: Int = 0x00000000,
        val strokeColor: Int = 0x00000000,
        val strokeWidth: Float = 0.0f,
        val cornerRadius: Float = 0.0f
    ) : RenderLayerState()

    data class CircleLayer(
        override val id: String,
        override val transform: TrackItemTransform = TrackItemTransform.IDENTITY,
        override val isVisible: Boolean = true,
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val fillColor: Int = 0x00000000,
        val strokeColor: Int = 0x00000000,
        val strokeWidth: Float = 0.0f
    ) : RenderLayerState()

    data class ArcLayer(
        override val id: String,
        override val transform: TrackItemTransform = TrackItemTransform.IDENTITY,
        override val isVisible: Boolean = true,
        val bounds: RectF,
        val startAngle: Float,
        val sweepAngle: Float,
        val strokeColor: Int,
        val strokeWidth: Float
    ) : RenderLayerState()
}

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

data class RenderState(
    val timestampUs: Long,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val layers: List<RenderLayerState> = emptyList(),
    val masterOpacity: Float = 1.0f
)
