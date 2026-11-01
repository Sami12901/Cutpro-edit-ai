package com.cutpro.core.motiongraphics.templates.logo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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
import kotlin.math.hypot

class SpinSettleScaleVignetteTemplate : MotionGraphicsTemplate {
    override val id: String = "logo_spin_settle"
    override val aliases: List<String> = listOf("logo_scale_vignette", "scale_and_vignette")
    override val displayName: String = "Cinematic Scale & Vignette Reveal"
    override val category: TemplateCategory = TemplateCategory.LOGO_REVEALS
    override val defaultDurationUs: Long = 4_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("brand_title", "Brand Title", ParameterType.STRING, "APEX STUDIOS", minLength = 1, maxLength = 30, disallowEmpty = true),
            ParameterDefinition("vignette_density", "Vignette Density", ParameterType.FLOAT, 0.75f, minValue = 0.2f, maxValue = 1.0f),
            ParameterDefinition("vignetteStrength", "Vignette Strength Alias", ParameterType.FLOAT, 0.75f, minValue = 0.0f, maxValue = 1.0f),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("letter_spacing", "Letter Spacing", ParameterType.FLOAT, 0.2f, minValue = 0.05f, maxValue = 0.5f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val title = validParams["brand_title"].toString()
        val textColor = (validParams["text_color"] as Long).toInt()

        val (scale, alpha) = evaluateScaleAndAlpha(timestampUs)
        val layers = mutableListOf<RenderLayerState>()

        if (alpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "vignette_brand",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.5f,
                        scaleX = scale,
                        scaleY = scale,
                        opacity = alpha
                    ),
                    text = title,
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
        val (scale, alpha) = evaluateScaleAndAlpha(timestampUs)
        if (alpha <= 0.0f) return

        val title = validParams["brand_title"].toString()
        val vignetteStrength = (params["vignetteStrength"] as? Number)?.toFloat()
            ?: (validParams["vignette_density"] as Float)
        val textColor = validParams["text_color"] as Long
        val letterSpacing = validParams["letter_spacing"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f

        // Draw radial vignette darkening edges
        val maxDist = hypot(cx, cy)
        val vignetteAlpha = when {
            timestampUs < 800_000L -> EasingEvaluator.evaluate(EasingType.EASE_OUT, timestampUs / 800_000.0f) * vignetteStrength
            timestampUs > 3_200_000L -> (1.0f - (timestampUs - 3_200_000L) / 800_000.0f).coerceIn(0.0f, 1.0f) * vignetteStrength
            else -> vignetteStrength
        } * alpha

        if (vignetteAlpha > 0.0f) {
            val edgeColor = TemplateDrawUtils.applyAlpha(0xFF000000L, vignetteAlpha)
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, maxDist,
                    intArrayOf(0x00000000, edgeColor),
                    floatArrayOf(0.4f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, viewportWidth.toFloat(), viewportHeight.toFloat(), vignettePaint)
        }

        // Draw title
        val baseTextSize = viewportHeight * 0.075f * scale
        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(textColor, alpha),
            textSize = baseTextSize,
            isBold = true,
            letterSpacing = letterSpacing
        )
        TemplateDrawUtils.drawCenteredText(canvas, title, cx, cy, textPaint)
    }

    private fun evaluateScaleAndAlpha(t: Long): Pair<Float, Float> {
        if (t <= 0L || t >= defaultDurationUs) return Pair(1.0f, 0.0f)

        val scale: Float
        val alpha: Float

        when {
            t < 1_500_000L -> {
                val frac = t / 1_500_000.0f
                val ease = CubicBezierSolver.solve(0.12f, 0.9f, 0.22f, 1.0f, frac)
                scale = 2.2f - 1.2f * ease
                alpha = (t / 600_000.0f).coerceIn(0.0f, 1.0f)
            }
            t <= 3_200_000L -> {
                val sustainFrac = (t - 1_500_000L) / 1_700_000.0f
                scale = 1.00f + 0.02f * sustainFrac
                alpha = 1.0f
            }
            else -> {
                val outFrac = (t - 3_200_000L) / 800_000.0f
                scale = 1.02f
                alpha = (1.0f - outFrac).coerceIn(0.0f, 1.0f)
            }
        }

        return Pair(scale, alpha)
    }
}
