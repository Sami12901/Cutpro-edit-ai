package com.cutpro.core.motiongraphics.templates.counters

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import kotlin.math.roundToInt

class CurrencyCountSmoothPercentageTemplate : MotionGraphicsTemplate {
    override val id: String = "counter_currency_count"
    override val aliases: List<String> = listOf("counter_smooth_percentage", "smooth_percentage_gauge")
    override val displayName: String = "Smooth Percentage Gauge"
    override val category: TemplateCategory = TemplateCategory.ANIMATED_COUNTERS
    override val defaultDurationUs: Long = 3_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("target_percent", "Target Percentage", ParameterType.FLOAT, 100.0f, minValue = 0.0f, maxValue = 100.0f),
            ParameterDefinition("bar_width_fraction", "Bar Width Fraction", ParameterType.FLOAT, 0.6f, minValue = 0.3f, maxValue = 0.85f),
            ParameterDefinition("track_color", "Track Background Color", ParameterType.COLOR, 0x33FFFFFFL),
            ParameterDefinition("fill_color", "Fill Bar Color", ParameterType.COLOR, 0xFF00E676L),
            ParameterDefinition("percent_text_color", "Percentage Text Color", ParameterType.COLOR, 0xFFFFFFFFL)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val target = validParams["target_percent"] as Float
        val currentPercent = evaluatePercent(timestampUs, target)

        val alpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs > 2_600_000L -> (defaultDurationUs - timestampUs) / 400_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val layers = mutableListOf<RenderLayerState>()
        if (alpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "percentage_text",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.45f,
                        opacity = alpha
                    ),
                    text = "${currentPercent.roundToInt()}%",
                    textColor = TemplateDrawUtils.applyAlpha(0xFFFFFFFF.toInt(), alpha)
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
            timestampUs > 2_600_000L -> (defaultDurationUs - timestampUs) / 400_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (alpha <= 0.0f) return

        val target = validParams["target_percent"] as Float
        val widthFraction = validParams["bar_width_fraction"] as Float
        val trackColor = validParams["track_color"] as Long
        val fillColor = validParams["fill_color"] as Long
        val textColor = validParams["percent_text_color"] as Long

        val currentPercent = evaluatePercent(timestampUs, target)

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f
        val barTotalWidth = viewportWidth * widthFraction
        val barHeight = viewportHeight * 0.024f
        val cornerRadius = barHeight * 0.5f
        val barStartX = cx - barTotalWidth * 0.5f
        val barY = cy + viewportHeight * 0.04f

        // Text
        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(textColor, alpha),
            textSize = viewportHeight * 0.07f,
            isBold = true
        )
        TemplateDrawUtils.drawCenteredText(canvas, "${currentPercent.roundToInt()}%", cx, cy - viewportHeight * 0.02f, textPaint)

        // Track background
        val trackPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(trackColor, alpha))
        val trackRect = RectF(barStartX, barY, barStartX + barTotalWidth, barY + barHeight)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        // Filled track
        val filledWidth = barTotalWidth * (currentPercent / 100.0f)
        if (filledWidth > 0.0f) {
            val fillPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(fillColor, alpha))
            val fillRect = RectF(barStartX, barY, barStartX + filledWidth, barY + barHeight)
            canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, fillPaint)
        }
    }

    private fun evaluatePercent(t: Long, target: Float): Float {
        return when {
            t <= 0L -> 0.0f
            t >= 2_000_000L -> target
            else -> {
                val frac = t / 2_000_000.0f
                target * EasingEvaluator.evaluate(EasingType.EASE_OUT, frac)
            }
        }.coerceIn(0.0f, target)
    }
}
