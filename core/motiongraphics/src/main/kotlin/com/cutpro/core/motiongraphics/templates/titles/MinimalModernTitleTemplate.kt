package com.cutpro.core.motiongraphics.templates.titles

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
import kotlin.math.max

class MinimalModernTitleTemplate : MotionGraphicsTemplate {
    override val id: String = "title_minimal_modern"
    override val aliases: List<String> = listOf("minimal_modern")
    override val displayName: String = "Minimal Modern"
    override val category: TemplateCategory = TemplateCategory.OPENING_TITLES
    override val defaultDurationUs: Long = 3_500_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("primary_text", "Primary Title", ParameterType.STRING, "MINIMAL ARCHITECTURE", minLength = 1, maxLength = 40, disallowEmpty = true),
            ParameterDefinition("secondary_text", "Subtitle Text", ParameterType.STRING, "MONOGRAPH SERIES 2026", minLength = 0, maxLength = 50),
            ParameterDefinition("primary_color", "Primary Color", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("secondary_color", "Secondary Color", ParameterType.COLOR, 0xB0FFFFFFL),
            ParameterDefinition("accent_color", "Accent Color", ParameterType.COLOR, 0xFF00E5FFL),
            ParameterDefinition("font_size_scale", "Font Size Scale", ParameterType.FLOAT, 1.0f, minValue = 0.5f, maxValue = 2.0f),
            ParameterDefinition("letter_spacing", "Letter Spacing", ParameterType.FLOAT, 0.15f, minValue = 0.0f, maxValue = 0.5f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val duration = defaultDurationUs

        val (masterAlpha, scale) = evaluateTiming(timestampUs, duration)
        val layers = mutableListOf<RenderLayerState>()

        if (masterAlpha > 0.0f) {
            val primaryText = validParams["primary_text"].toString()
            val primaryColor = (validParams["primary_color"] as Long).toInt()
            val fontScale = (validParams["font_size_scale"] as Float) * scale
            val letterSpacing = validParams["letter_spacing"] as Float

            layers.add(
                RenderLayerState.TextLayer(
                    id = "primary_text",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.46f,
                        scaleX = scale,
                        scaleY = scale,
                        opacity = masterAlpha
                    ),
                    text = primaryText,
                    fontScale = fontScale,
                    textColor = TemplateDrawUtils.applyAlpha(primaryColor, masterAlpha),
                    letterSpacing = letterSpacing
                )
            )

            val secondaryText = validParams["secondary_text"].toString()
            if (secondaryText.isNotEmpty()) {
                val secColor = (validParams["secondary_color"] as Long).toInt()
                layers.add(
                    RenderLayerState.TextLayer(
                        id = "secondary_text",
                        transform = TrackItemTransform(
                            positionX = viewportWidth * 0.5f,
                            positionY = viewportHeight * 0.54f,
                            scaleX = scale,
                            scaleY = scale,
                            opacity = masterAlpha
                        ),
                        text = secondaryText,
                        fontScale = fontScale * 0.45f,
                        textColor = TemplateDrawUtils.applyAlpha(secColor, masterAlpha * 0.85f),
                        letterSpacing = letterSpacing * 0.8f
                    )
                )
            }
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
        val duration = defaultDurationUs
        val (masterAlpha, scale) = evaluateTiming(timestampUs, duration)
        if (masterAlpha <= 0.0f) return

        val primaryText = validParams["primary_text"].toString()
        val secondaryText = validParams["secondary_text"].toString()
        val primaryColor = validParams["primary_color"] as Long
        val secondaryColor = validParams["secondary_color"] as Long
        val accentColor = validParams["accent_color"] as Long
        val fontScale = validParams["font_size_scale"] as Float
        val letterSpacing = validParams["letter_spacing"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f

        val baseTextSize = viewportHeight * 0.055f * fontScale * scale
        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(primaryColor, masterAlpha),
            textSize = baseTextSize,
            isBold = true,
            letterSpacing = letterSpacing
        )

        // Draw primary text
        TemplateDrawUtils.drawCenteredText(canvas, primaryText, cx, cy - baseTextSize * 0.4f, textPaint)

        // Accent Line
        val lineProgress = when {
            timestampUs < 200_000L -> 0.0f
            timestampUs in 200_000L..900_000L -> {
                val frac = (timestampUs - 200_000L) / 700_000.0f
                EasingEvaluator.evaluate(EasingType.EASE_IN_OUT, frac)
            }
            timestampUs > 2_800_000L -> masterAlpha
            else -> 1.0f
        }

        if (lineProgress > 0.0f) {
            val lineWidth = viewportWidth * 0.35f * lineProgress * scale
            val linePaint = TemplateDrawUtils.createStrokePaint(
                color = TemplateDrawUtils.applyAlpha(accentColor, masterAlpha),
                strokeWidth = max(2.0f, viewportHeight * 0.003f)
            )
            val lineY = cy + baseTextSize * 0.2f
            canvas.drawLine(cx - lineWidth * 0.5f, lineY, cx + lineWidth * 0.5f, lineY, linePaint)
        }

        // Secondary text
        if (secondaryText.isNotEmpty() && timestampUs >= 400_000L) {
            val secAlpha = when {
                timestampUs in 400_000L..1_000_000L -> {
                    val frac = (timestampUs - 400_000L) / 600_000.0f
                    EasingEvaluator.evaluate(EasingType.EASE_OUT, frac) * masterAlpha
                }
                else -> masterAlpha
            }

            val secTextPaint = TemplateDrawUtils.createTextPaint(
                color = TemplateDrawUtils.applyAlpha(secondaryColor, secAlpha),
                textSize = baseTextSize * 0.4f,
                isBold = false,
                letterSpacing = letterSpacing * 1.2f
            )
            val secY = cy + baseTextSize * 0.75f
            TemplateDrawUtils.drawCenteredText(canvas, secondaryText, cx, secY, secTextPaint)
        }
    }

    private fun evaluateTiming(t: Long, duration: Long): Pair<Float, Float> {
        if (t <= 0L || t >= duration) return Pair(0.0f, 1.0f)

        val alpha: Float
        val scale: Float

        when {
            t < 1_000_000L -> {
                val inFrac = t / 1_000_000.0f
                alpha = CubicBezierSolver.solve(0.16f, 1.0f, 0.3f, 1.0f, inFrac.coerceIn(0.0f, 1.0f))
                scale = 0.95f + 0.05f * EasingEvaluator.evaluate(EasingType.EASE_OUT, inFrac)
            }
            t <= 2_800_000L -> {
                alpha = 1.0f
                val sustainFrac = (t - 1_000_000L) / 1_800_000.0f
                scale = 1.00f + 0.03f * sustainFrac
            }
            else -> {
                val outFrac = (t - 2_800_000L) / 700_000.0f
                alpha = 1.0f - EasingEvaluator.evaluate(EasingType.EASE_IN, outFrac.coerceIn(0.0f, 1.0f))
                scale = 1.03f + 0.03f * outFrac
            }
        }

        return Pair(alpha.coerceIn(0.0f, 1.0f), scale)
    }
}
