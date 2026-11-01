package com.cutpro.core.motiongraphics.templates.kinetic

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.cutpro.core.motiongraphics.evaluator.EasingEvaluator
import com.cutpro.core.motiongraphics.evaluator.TypewriterSlicer
import com.cutpro.core.motiongraphics.model.EasingType
import com.cutpro.core.motiongraphics.model.RenderLayerState
import com.cutpro.core.motiongraphics.model.RenderState
import com.cutpro.core.motiongraphics.model.TrackItemTransform
import com.cutpro.core.motiongraphics.schema.ParameterDefinition
import com.cutpro.core.motiongraphics.schema.ParameterSchema
import com.cutpro.core.motiongraphics.schema.ParameterType
import com.cutpro.core.motiongraphics.templates.MotionGraphicsTemplate
import com.cutpro.core.motiongraphics.templates.TemplateCategory
import com.cutpro.core.motiongraphics.templates.TemplateDrawUtils

class StaggeredSlideTypewriterTemplate : MotionGraphicsTemplate {
    override val id: String = "kinetic_staggered_slide"
    override val aliases: List<String> = listOf("kinetic_typewriter_terminal", "typewriter_terminal")
    override val displayName: String = "Typewriter Terminal Console"
    override val category: TemplateCategory = TemplateCategory.KINETIC_TYPOGRAPHY
    override val defaultDurationUs: Long = 3_500_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("terminal_text", "Terminal Text", ParameterType.STRING, "SYSTEM INITIALIZED > OK", minLength = 1, maxLength = 60, disallowEmpty = true),
            ParameterDefinition("cursorChar", "Cursor Character", ParameterType.STRING, "|", minLength = 1, maxLength = 2),
            ParameterDefinition("font_color", "Font Color", ParameterType.COLOR, 0xFF00FF66L),
            ParameterDefinition("cursor_color", "Cursor Color", ParameterType.COLOR, 0xFF00FF66L),
            ParameterDefinition("typing_speed_cps", "Typing Speed CPS", ParameterType.FLOAT, 14.0f, minValue = 5.0f, maxValue = 35.0f),
            ParameterDefinition("cursor_blink_hz", "Cursor Blink Hz", ParameterType.FLOAT, 2.5f, minValue = 1.0f, maxValue = 6.0f),
            ParameterDefinition("show_dark_scrim", "Show Dark Scrim", ParameterType.BOOLEAN, true)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val text = validParams["terminal_text"].toString()
        val cps = validParams["typing_speed_cps"] as Float
        val blinkHz = validParams["cursor_blink_hz"] as Float
        val fontColor = (validParams["font_color"] as Long).toInt()
        val cursorChar = validParams["cursorChar"].toString()

        val masterAlpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 300_000L -> timestampUs / 300_000.0f
            timestampUs > 3_000_000L -> (defaultDurationUs - timestampUs) / 500_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val layers = mutableListOf<RenderLayerState>()
        if (masterAlpha > 0.0f) {
            val (slicedText, cursorOn) = TypewriterSlicer.slice(text, cps, timestampUs, blinkHz)
            val displayText = if (cursorOn) "$slicedText$cursorChar" else slicedText

            layers.add(
                RenderLayerState.TextLayer(
                    id = "terminal_text",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.5f,
                        opacity = masterAlpha
                    ),
                    text = displayText,
                    textColor = TemplateDrawUtils.applyAlpha(fontColor, masterAlpha)
                )
            )
        }

        return RenderState(
            timestampUs = timestampUs,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            layers = layers,
            masterOpacity = masterAlpha
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
        val masterAlpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 300_000L -> timestampUs / 300_000.0f
            timestampUs > 3_000_000L -> (defaultDurationUs - timestampUs) / 500_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (masterAlpha <= 0.0f) return

        val text = validParams["terminal_text"].toString()
        val cps = validParams["typing_speed_cps"] as Float
        val blinkHz = validParams["cursor_blink_hz"] as Float
        val fontColor = validParams["font_color"] as Long
        val cursorColor = validParams["cursor_color"] as Long
        val cursorChar = validParams["cursorChar"].toString()
        val showScrim = validParams["show_dark_scrim"] as Boolean

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f
        val textSize = viewportHeight * 0.045f

        val (slicedText, cursorOn) = TypewriterSlicer.slice(text, cps, timestampUs, blinkHz)

        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(fontColor, masterAlpha),
            textSize = textSize,
            isBold = false
        )

        // Draw scrim if enabled
        if (showScrim) {
            val fullBounds = TemplateDrawUtils.measureTextBounds(textPaint, "$text$cursorChar")
            val padX = textSize * 0.8f
            val padY = textSize * 0.5f
            val scrimRect = RectF(
                cx - fullBounds.width() * 0.5f - padX,
                cy - fullBounds.height() * 0.5f - padY,
                cx + fullBounds.width() * 0.5f + padX,
                cy + fullBounds.height() * 0.5f + padY
            )
            val scrimPaint = TemplateDrawUtils.createFillPaint(
                TemplateDrawUtils.applyAlpha(0xDD101510L, masterAlpha * 0.9f)
            )
            canvas.drawRoundRect(scrimRect, 8.0f, 8.0f, scrimPaint)
        }

        // Draw text and cursor
        val displayText = if (cursorOn) "$slicedText$cursorChar" else slicedText
        TemplateDrawUtils.drawCenteredText(canvas, displayText, cx, cy, textPaint)
    }
}
