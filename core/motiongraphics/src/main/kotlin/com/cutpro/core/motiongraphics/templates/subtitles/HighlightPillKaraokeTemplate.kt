package com.cutpro.core.motiongraphics.templates.subtitles

import android.graphics.Canvas
import android.graphics.Paint
import com.cutpro.core.motiongraphics.evaluator.AnalyticalSpringSolver
import com.cutpro.core.motiongraphics.model.RenderLayerState
import com.cutpro.core.motiongraphics.model.RenderState
import com.cutpro.core.motiongraphics.model.TrackItemTransform
import com.cutpro.core.motiongraphics.schema.ParameterDefinition
import com.cutpro.core.motiongraphics.schema.ParameterSchema
import com.cutpro.core.motiongraphics.schema.ParameterType
import com.cutpro.core.motiongraphics.templates.MotionGraphicsTemplate
import com.cutpro.core.motiongraphics.templates.TemplateCategory
import com.cutpro.core.motiongraphics.templates.TemplateDrawUtils

class HighlightPillKaraokeTemplate : MotionGraphicsTemplate {
    override val id: String = "subtitle_highlight_pill"
    override val aliases: List<String> = listOf("subtitle_karaoke_highlight", "sub_karaoke_highlight", "karaoke_highlight")
    override val displayName: String = "Karaoke Active-Word Highlight"
    override val category: TemplateCategory = TemplateCategory.ANIMATED_SUBTITLES
    override val defaultDurationUs: Long = 3_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("caption_text", "Caption Text", ParameterType.STRING, "Edit videos like a pro", minLength = 1, maxLength = 80, disallowEmpty = true),
            ParameterDefinition("activeWordColor", "Active Word Color", ParameterType.COLOR, 0xFFFFD700L),
            ParameterDefinition("base_color", "Base Color", ParameterType.COLOR, 0xCCFFFFFFL),
            ParameterDefinition("active_scale", "Active Scale Factor", ParameterType.FLOAT, 1.15f, minValue = 1.0f, maxValue = 1.5f),
            ParameterDefinition("outline_color", "Outline Color", ParameterType.COLOR, 0xFF000000L),
            ParameterDefinition("outline_width", "Outline Width", ParameterType.FLOAT, 0.005f, minValue = 0.001f, maxValue = 0.015f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val text = validParams["caption_text"].toString()
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val count = words.size.coerceAtLeast(1)
        val slotDuration = defaultDurationUs / count
        val activeIndex = (timestampUs / slotDuration).toInt().coerceIn(0, count - 1)

        val activeColor = (validParams["activeWordColor"] as Long).toInt()
        val baseColor = (validParams["base_color"] as Long).toInt()
        val activeScale = validParams["active_scale"] as Float

        val layers = mutableListOf<RenderLayerState>()
        for (i in words.indices) {
            val isActive = (i == activeIndex)
            val color = if (isActive) activeColor else baseColor
            val scale = if (isActive) activeScale else 1.0f

            layers.add(
                RenderLayerState.TextLayer(
                    id = "karaoke_word_$i",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * (0.2f + i * 0.15f),
                        positionY = viewportHeight * 0.82f,
                        scaleX = scale,
                        scaleY = scale
                    ),
                    text = words[i],
                    textColor = color
                )
            )
        }

        return RenderState(
            timestampUs = timestampUs,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            layers = layers,
            masterOpacity = 1.0f
        )
    }

    override fun render(
        canvas: Canvas,
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        val validParams = schema.validate(params)
        val text = validParams["caption_text"].toString()
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return

        val count = words.size
        val slotDuration = defaultDurationUs / count
        val activeIndex = (timestampUs / slotDuration).toInt().coerceIn(0, count - 1)
        val slotElapsed = timestampUs % slotDuration

        val activeColor = validParams["activeWordColor"] as Long
        val baseColor = validParams["base_color"] as Long
        val activeScale = validParams["active_scale"] as Float
        val strokeColor = validParams["outline_color"] as Long
        val strokeWidthFrac = validParams["outline_width"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.82f
        val baseTextSize = viewportHeight * 0.05f

        val measurePaint = TemplateDrawUtils.createTextPaint(0xFFFFFFFF.toInt(), baseTextSize, true)
        val wordWidths = words.map { measurePaint.measureText(it) }
        val spaceWidth = measurePaint.measureText(" ")
        val totalWidth = wordWidths.sum() + spaceWidth * (count - 1)

        var curX = cx - totalWidth * 0.5f
        val springProgress = AnalyticalSpringSolver.solveAtTimestamp(400f, 0.6f, slotElapsed, 0L, 200_000L)

        for (i in words.indices) {
            val isActive = (i == activeIndex)
            val w = wordWidths[i]
            val wordCenterX = curX + w * 0.5f

            val currentScale = if (isActive) {
                1.0f + (activeScale - 1.0f) * springProgress
            } else {
                1.0f
            }
            val currentColor = if (isActive) activeColor else baseColor
            val currentSize = baseTextSize * currentScale

            val textPaint = TemplateDrawUtils.createTextPaint(
                color = currentColor.toInt(),
                textSize = currentSize,
                isBold = true
            )
            val strokePaint = TemplateDrawUtils.createStrokePaint(
                color = strokeColor.toInt(),
                strokeWidth = strokeWidthFrac * viewportHeight * currentScale,
                textSize = currentSize,
                isBold = true
            )

            TemplateDrawUtils.drawCenteredText(canvas, words[i], wordCenterX, cy, textPaint, strokePaint)
            curX += w + spaceWidth
        }
    }
}
