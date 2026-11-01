package com.cutpro.core.motiongraphics.templates.subtitles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.cutpro.core.motiongraphics.model.RenderLayerState
import com.cutpro.core.motiongraphics.model.RenderState
import com.cutpro.core.motiongraphics.model.TrackItemTransform
import com.cutpro.core.motiongraphics.schema.ParameterDefinition
import com.cutpro.core.motiongraphics.schema.ParameterSchema
import com.cutpro.core.motiongraphics.schema.ParameterType
import com.cutpro.core.motiongraphics.templates.MotionGraphicsTemplate
import com.cutpro.core.motiongraphics.templates.TemplateCategory
import com.cutpro.core.motiongraphics.templates.TemplateDrawUtils

class BottomFadeContrastBoxTemplate : MotionGraphicsTemplate {
    override val id: String = "subtitle_bottom_fade"
    override val aliases: List<String> = listOf("subtitle_contrast_box", "sub_high_contrast_box", "high_contrast_box")
    override val displayName: String = "High-Contrast Box Caption"
    override val category: TemplateCategory = TemplateCategory.ANIMATED_SUBTITLES
    override val defaultDurationUs: Long = 3_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("subtitle_text", "Subtitle Text", ParameterType.STRING, "Clear speech in noisy environments", minLength = 1, maxLength = 90, disallowEmpty = true),
            ParameterDefinition("box_color", "Box Color", ParameterType.COLOR, 0xE6000000L),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("textColor", "Text Color Alias", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("font_size_sp", "Font Size SP", ParameterType.FLOAT, 18.0f, minValue = 12.0f, maxValue = 32.0f),
            ParameterDefinition("corner_radius", "Corner Radius", ParameterType.FLOAT, 4.0f, minValue = 0.0f, maxValue = 12.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val text = validParams["subtitle_text"].toString()
        val textColor = (validParams["text_color"] as Long).toInt()

        val alpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 200_000L -> timestampUs / 200_000.0f
            timestampUs > 2_800_000L -> (defaultDurationUs - timestampUs) / 200_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val layers = mutableListOf<RenderLayerState>()
        if (alpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "contrast_subtitle",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.85f,
                        opacity = alpha
                    ),
                    text = text,
                    textColor = TemplateDrawUtils.applyAlpha(textColor, alpha)
                )
            )
        }

        return RenderState(
            timestampUs = timestampUs,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            layers = layers,
            masterOpacity = alpha
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
        val alpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 200_000L -> timestampUs / 200_000.0f
            timestampUs > 2_800_000L -> (defaultDurationUs - timestampUs) / 200_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (alpha <= 0.0f) return

        val text = validParams["subtitle_text"].toString()
        val boxColor = validParams["box_color"] as Long
        val textColor = if (params.containsKey("textColor")) validParams["textColor"] as Long else validParams["text_color"] as Long
        val cornerRadius = (validParams["corner_radius"] as Float)

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.85f
        val textSize = viewportHeight * 0.045f

        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(textColor, alpha),
            textSize = textSize,
            isBold = true
        )

        val textBounds = TemplateDrawUtils.measureTextBounds(textPaint, text)
        val padH = textSize * 0.5f
        val padV = textSize * 0.3f
        val boxWidth = textBounds.width() + padH * 2.0f
        val boxHeight = textBounds.height() + padV * 2.0f

        val boxRect = RectF(
            cx - boxWidth * 0.5f,
            cy - boxHeight * 0.5f,
            cx + boxWidth * 0.5f,
            cy + boxHeight * 0.5f
        )
        val boxPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(boxColor, alpha))

        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, boxPaint)
        TemplateDrawUtils.drawCenteredText(canvas, text, cx, cy, textPaint)
    }
}
