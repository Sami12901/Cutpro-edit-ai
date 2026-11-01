package com.cutpro.core.motiongraphics.templates.lowerthirds

import android.graphics.Canvas
import android.graphics.Paint
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

class DualToneLineAccentPillTemplate : MotionGraphicsTemplate {
    override val id: String = "lower_third_dual_line"
    override val aliases: List<String> = listOf("lower_third_accent_pill", "lower_gradient_pill", "gradient_accent_pill")
    override val displayName: String = "Gradient Accent Pill"
    override val category: TemplateCategory = TemplateCategory.LOWER_THIRDS
    override val defaultDurationUs: Long = 4_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("title", "Title", ParameterType.STRING, "TECHNICAL DIRECTOR", minLength = 1, maxLength = 35, disallowEmpty = true),
            ParameterDefinition("subtitle", "Subtitle", ParameterType.STRING, "Core Architecture", minLength = 0, maxLength = 40),
            ParameterDefinition("accent_color", "Accent Neon Color", ParameterType.COLOR, 0xFF00E5FFL),
            ParameterDefinition("pill_bg_color", "Pill Background Color", ParameterType.COLOR, 0xCC121216L),
            ParameterDefinition("cornerRadiusPx", "Corner Radius Pixels", ParameterType.FLOAT, 16.0f, minValue = 4.0f, maxValue = 40.0f),
            ParameterDefinition("corner_radius_dp", "Corner Radius DP Alias", ParameterType.FLOAT, 16.0f, minValue = 4.0f, maxValue = 40.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val title = validParams["title"].toString()

        val masterAlpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 300_000L -> timestampUs / 300_000.0f
            timestampUs > 3_650_000L -> (defaultDurationUs - timestampUs) / 350_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val layers = mutableListOf<RenderLayerState>()
        if (masterAlpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "pill_title",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.25f,
                        positionY = viewportHeight * 0.85f,
                        opacity = masterAlpha
                    ),
                    text = title,
                    textColor = TemplateDrawUtils.applyAlpha(0xFFFFFFFF.toInt(), masterAlpha)
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
            timestampUs > 3_650_000L -> (defaultDurationUs - timestampUs) / 350_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (masterAlpha <= 0.0f) return

        val title = validParams["title"].toString()
        val subtitle = validParams["subtitle"].toString()
        val accentColor = validParams["accent_color"] as Long
        val bgColor = validParams["pill_bg_color"] as Long
        val cornerRadius = (params["cornerRadiusPx"] as? Number)?.toFloat()
            ?: (params["corner_radius_dp"] as? Number)?.toFloat()
            ?: (validParams["cornerRadiusPx"] as Float)

        val pillHeight = viewportHeight * 0.065f
        val pillWidth = viewportWidth * 0.36f
        val startX = viewportWidth * 0.06f
        val startY = viewportHeight * 0.84f

        val scaleX = when {
            timestampUs < 550_000L -> {
                0.1f + 0.9f * AnalyticalSpringSolver.solveAtTimestamp(260f, 0.65f, timestampUs, 0L, 550_000L)
            }
            timestampUs > 3_450_000L -> {
                1.0f - EasingEvaluator.evaluate(EasingType.EASE_IN, (timestampUs - 3_450_000L) / 450_000.0f)
            }
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (scaleX <= 0.0f) return

        // Pill background
        val bgPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(bgColor, masterAlpha))
        val currentWidth = pillWidth * scaleX
        val pillRect = RectF(startX, startY, startX + currentWidth, startY + pillHeight)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, bgPaint)

        // Neon indicator vertical bar
        val barHeightProgress = when {
            timestampUs < 150_000L -> 0.0f
            timestampUs in 150_000L..500_000L -> {
                EasingEvaluator.evaluate(EasingType.EASE_OUT, (timestampUs - 150_000L) / 350_000.0f)
            }
            else -> 1.0f
        }

        if (barHeightProgress > 0.0f && scaleX > 0.2f) {
            val barPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(accentColor, masterAlpha))
            val barWidth = viewportWidth * 0.006f
            val barActualHeight = pillHeight * 0.6f * barHeightProgress
            val barX = startX + pillHeight * 0.35f
            val barY = startY + (pillHeight - barActualHeight) * 0.5f
            canvas.drawRoundRect(RectF(barX, barY, barX + barWidth, barY + barActualHeight), 2f, 2f, barPaint)

            // Text
            if (timestampUs >= 300_000L && scaleX > 0.5f) {
                val textAlpha = when {
                    timestampUs in 300_000L..600_000L -> (timestampUs - 300_000L) / 300_000.0f
                    else -> 1.0f
                } * masterAlpha

                val textStartX = barX + barWidth + viewportWidth * 0.015f
                val titlePaint = TemplateDrawUtils.createTextPaint(
                    color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, textAlpha),
                    textSize = pillHeight * 0.35f,
                    isBold = true
                )
                canvas.drawText(title, textStartX, startY + pillHeight * 0.44f, titlePaint)

                if (subtitle.isNotEmpty()) {
                    val subPaint = TemplateDrawUtils.createTextPaint(
                        color = TemplateDrawUtils.applyAlpha(0xBBFFFFFFL, textAlpha),
                        textSize = pillHeight * 0.28f,
                        isBold = false
                    )
                    canvas.drawText(subtitle, textStartX, startY + pillHeight * 0.80f, subPaint)
                }
            }
        }
    }
}
