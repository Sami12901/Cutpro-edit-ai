package com.cutpro.core.motiongraphics.templates.subtitles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.cutpro.core.motiongraphics.evaluator.AnalyticalSpringSolver
import com.cutpro.core.motiongraphics.evaluator.EasingEvaluator
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

class StrokeOutlinePopBubbleTemplate : MotionGraphicsTemplate {
    override val id: String = "subtitle_stroke_outline"
    override val aliases: List<String> = listOf("subtitle_pop_bubble", "sub_pop_bubble", "pop_bubble_caption")
    override val displayName: String = "Pop Bubble Short-Form Caption"
    override val category: TemplateCategory = TemplateCategory.ANIMATED_SUBTITLES
    override val defaultDurationUs: Long = 2_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("phrase_text", "Phrase Text", ParameterType.STRING, "WATCH THIS TRICK", minLength = 1, maxLength = 30, disallowEmpty = true),
            ParameterDefinition("bubble_color", "Bubble Color", ParameterType.COLOR, 0xFF00E676L),
            ParameterDefinition("bubbleBgColor", "Bubble Background Color Alias", ParameterType.COLOR, 0xCC111111L),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFF000000L),
            ParameterDefinition("bounce_intensity", "Bounce Intensity", ParameterType.FLOAT, 1.22f, minValue = 1.05f, maxValue = 1.45f),
            ParameterDefinition("y_position", "Y Position", ParameterType.FLOAT, 0.72f, minValue = 0.50f, maxValue = 0.88f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val text = validParams["phrase_text"].toString()
        val yPos = validParams["y_position"] as Float

        val (scale, alpha) = evaluatePopBubble(timestampUs)
        val layers = mutableListOf<RenderLayerState>()

        if (alpha > 0.0f && scale > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "bubble_phrase",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * yPos,
                        scaleX = scale,
                        scaleY = scale,
                        opacity = alpha
                    ),
                    text = text,
                    textColor = TemplateDrawUtils.applyAlpha(0xFF000000.toInt(), alpha)
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
        val (scale, alpha) = evaluatePopBubble(timestampUs)
        if (alpha <= 0.0f || scale <= 0.0f) return

        val text = validParams["phrase_text"].toString()
        val bubbleColor = if (params.containsKey("bubbleBgColor")) validParams["bubbleBgColor"] as Long else validParams["bubble_color"] as Long
        val textColor = validParams["text_color"] as Long
        val yPos = validParams["y_position"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * yPos
        val textSize = viewportHeight * 0.05f * scale

        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(textColor, alpha),
            textSize = textSize,
            isBold = true
        )

        val textBounds = TemplateDrawUtils.measureTextBounds(textPaint, text)
        val padH = textSize * 0.6f
        val padV = textSize * 0.35f
        val bubbleWidth = textBounds.width() + padH * 2.0f
        val bubbleHeight = textBounds.height() + padV * 2.0f
        val cornerRadius = bubbleHeight * 0.5f

        val bubbleRect = RectF(
            cx - bubbleWidth * 0.5f,
            cy - bubbleHeight * 0.5f,
            cx + bubbleWidth * 0.5f,
            cy + bubbleHeight * 0.5f
        )
        val bubblePaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(bubbleColor, alpha))

        canvas.drawRoundRect(bubbleRect, cornerRadius, cornerRadius, bubblePaint)
        TemplateDrawUtils.drawCenteredText(canvas, text, cx, cy, textPaint)
    }

    private fun evaluatePopBubble(t: Long): Pair<Float, Float> {
        if (t <= 0L || t >= defaultDurationUs) return Pair(0.0f, 0.0f)

        val scale: Float
        val alpha: Float

        when {
            t < 350_000L -> {
                val springProgress = AnalyticalSpringSolver.solveAtTimestamp(380f, 0.52f, t, 0L, 350_000L)
                scale = 0.2f + 0.8f * springProgress
                alpha = (t / 150_000.0f).coerceIn(0.0f, 1.0f)
            }
            t <= 1_750_000L -> {
                scale = 1.0f
                alpha = 1.0f
            }
            else -> {
                val outFrac = (t - 1_750_000L) / 250_000.0f
                scale = 1.0f - 0.9f * EasingEvaluator.evaluate(EasingType.EASE_IN, outFrac)
                alpha = (1.0f - outFrac).coerceIn(0.0f, 1.0f)
            }
        }

        return Pair(scale, alpha)
    }
}
