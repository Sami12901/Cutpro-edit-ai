package com.cutpro.core.motiongraphics.templates.lowerthirds

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.cutpro.core.motiongraphics.evaluator.CubicBezierSolver
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

class ModernAccentNewsBarTemplate : MotionGraphicsTemplate {
    override val id: String = "lower_third_modern_accent"
    override val aliases: List<String> = listOf("lower_third_news_bar", "lower_news_broadcast", "news_broadcast_bar")
    override val displayName: String = "Broadcast News Strap"
    override val category: TemplateCategory = TemplateCategory.LOWER_THIRDS
    override val defaultDurationUs: Long = 5_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("topic_badge", "Topic Badge", ParameterType.STRING, "BREAKING", minLength = 1, maxLength = 15, disallowEmpty = true),
            ParameterDefinition("headline", "Headline", ParameterType.STRING, "CUTPRO 1.0 NATIVE ENGINE RELEASED", minLength = 1, maxLength = 50, disallowEmpty = true),
            ParameterDefinition("subtext", "Subtext", ParameterType.STRING, "Zero cloud dependency - Pure Media3 export", minLength = 0, maxLength = 60),
            ParameterDefinition("badge_color", "Badge Color", ParameterType.COLOR, 0xFFD50000L),
            ParameterDefinition("bar_color", "Bar Color", ParameterType.COLOR, 0xEE1A1A1AL),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("anchor", "Vertical Anchor", ParameterType.FLOAT, 0.85f, minValue = 0.70f, maxValue = 0.95f),
            ParameterDefinition("y_position", "Y Position Alias", ParameterType.FLOAT, 0.85f, minValue = 0.70f, maxValue = 0.95f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val anchor = (params["anchor"] as? Number)?.toFloat()
            ?: (params["y_position"] as? Number)?.toFloat()
            ?: (validParams["anchor"] as Float)

        val masterAlpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 300_000L -> timestampUs / 300_000.0f
            timestampUs > 4_700_000L -> (defaultDurationUs - timestampUs) / 300_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val layers = mutableListOf<RenderLayerState>()
        if (masterAlpha > 0.0f) {
            val headline = validParams["headline"].toString()
            val textColor = (validParams["text_color"] as Long).toInt()

            layers.add(
                RenderLayerState.TextLayer(
                    id = "news_headline",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.45f,
                        positionY = viewportHeight * anchor,
                        opacity = masterAlpha
                    ),
                    text = headline,
                    textColor = TemplateDrawUtils.applyAlpha(textColor, masterAlpha)
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
        val anchor = (params["anchor"] as? Number)?.toFloat()
            ?: (params["y_position"] as? Number)?.toFloat()
            ?: (validParams["anchor"] as Float)

        val masterAlpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 300_000L -> timestampUs / 300_000.0f
            timestampUs > 4_700_000L -> (defaultDurationUs - timestampUs) / 300_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (masterAlpha <= 0.0f) return

        val badge = validParams["topic_badge"].toString()
        val headline = validParams["headline"].toString()
        val subtext = validParams["subtext"].toString()
        val badgeColor = validParams["badge_color"] as Long
        val barColor = validParams["bar_color"] as Long
        val textColor = validParams["text_color"] as Long

        val barY = viewportHeight * anchor
        val barHeight = viewportHeight * 0.075f
        val startX = viewportWidth * 0.06f
        val maxBarWidth = viewportWidth * 0.88f

        // Expansion progress
        val expandFrac = when {
            timestampUs < 200_000L -> 0.0f
            timestampUs in 200_000L..800_000L -> {
                CubicBezierSolver.solve(0.16f, 1.0f, 0.3f, 1.0f, (timestampUs - 200_000L) / 600_000.0f)
            }
            timestampUs in 4_400_000L..5_000_000L -> {
                1.0f - EasingEvaluator.evaluate(EasingType.EASE_IN, (timestampUs - 4_400_000L) / 600_000.0f)
            }
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val currentBarWidth = maxBarWidth * expandFrac
        if (currentBarWidth <= 0.0f) return

        // Draw main dark bar
        val barPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(barColor, masterAlpha))
        val barRect = RectF(startX, barY, startX + currentBarWidth, barY + barHeight)
        canvas.drawRect(barRect, barPaint)

        // Draw red topic badge
        val badgeWidth = viewportWidth * 0.16f
        val badgeScale = when {
            timestampUs < 300_000L -> timestampUs / 300_000.0f
            timestampUs > 4_700_000L -> (defaultDurationUs - timestampUs) / 300_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (badgeScale > 0.0f) {
            val badgePaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(badgeColor, masterAlpha))
            val badgeRect = RectF(startX, barY, startX + badgeWidth * badgeScale, barY + barHeight)
            canvas.drawRect(badgeRect, badgePaint)

            if (badgeScale > 0.7f) {
                val badgeTextPaint = TemplateDrawUtils.createTextPaint(
                    color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, masterAlpha),
                    textSize = barHeight * 0.36f,
                    isBold = true
                )
                TemplateDrawUtils.drawCenteredText(
                    canvas,
                    badge,
                    startX + (badgeWidth * badgeScale) * 0.5f,
                    barY + barHeight * 0.5f,
                    badgeTextPaint
                )
            }
        }

        // Draw headline text with clip rect to avoid overflow during expand
        if (timestampUs >= 400_000L && expandFrac > 0.3f) {
            val textAlpha = when {
                timestampUs in 400_000L..750_000L -> (timestampUs - 400_000L) / 350_000.0f
                timestampUs > 4_500_000L -> masterAlpha
                else -> masterAlpha
            }.coerceIn(0.0f, 1.0f)

            canvas.save()
            canvas.clipRect(startX + badgeWidth, barY, startX + currentBarWidth, barY + barHeight)

            val headPaint = TemplateDrawUtils.createTextPaint(
                color = TemplateDrawUtils.applyAlpha(textColor, textAlpha),
                textSize = barHeight * 0.38f,
                isBold = true
            )
            val textX = startX + badgeWidth + viewportWidth * 0.02f
            canvas.drawText(headline, textX, barY + barHeight * 0.45f, headPaint)

            // Subtext
            if (subtext.isNotEmpty() && timestampUs >= 600_000L) {
                val subPaint = TemplateDrawUtils.createTextPaint(
                    color = TemplateDrawUtils.applyAlpha(textColor, textAlpha * 0.75f),
                    textSize = barHeight * 0.28f,
                    isBold = false
                )
                canvas.drawText(subtext, textX, barY + barHeight * 0.82f, subPaint)
            }

            canvas.restore()
        }
    }
}
