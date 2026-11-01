package com.cutpro.core.motiongraphics.templates.logo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

class GeometricMaskCircularIrisTemplate : MotionGraphicsTemplate {
    override val id: String = "logo_geometric_mask"
    override val aliases: List<String> = listOf("logo_circular_mask_reveal", "logo_circular_iris", "circular_iris_mask")
    override val displayName: String = "Circular Mask Iris Reveal"
    override val category: TemplateCategory = TemplateCategory.LOGO_REVEALS
    override val defaultDurationUs: Long = 3_500_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("brand_text", "Brand Title", ParameterType.STRING, "CUTPRO", minLength = 1, maxLength = 25, disallowEmpty = true),
            ParameterDefinition("tagline", "Tagline", ParameterType.STRING, "PRECISION MOBILE EDITING", minLength = 0, maxLength = 40),
            ParameterDefinition("ring_color", "Ring Color", ParameterType.COLOR, 0xFF00E5FFL),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("max_radius_fraction", "Max Radius Fraction", ParameterType.FLOAT, 0.45f, minValue = 0.2f, maxValue = 0.8f),
            ParameterDefinition("maxRadiusPx", "Max Radius Pixels Alias", ParameterType.FLOAT, 400.0f, minValue = 50.0f, maxValue = 2000.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val brand = validParams["brand_text"].toString()
        val textColor = (validParams["text_color"] as Long).toInt()

        val (alpha, irisFrac) = evaluateIris(timestampUs)
        val layers = mutableListOf<RenderLayerState>()

        if (alpha > 0.0f && irisFrac > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "brand_text",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.48f,
                        opacity = alpha
                    ),
                    text = brand,
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
        val (alpha, irisFrac) = evaluateIris(timestampUs)
        if (alpha <= 0.0f || irisFrac <= 0.0f) return

        val brand = validParams["brand_text"].toString()
        val tagline = validParams["tagline"].toString()
        val ringColor = validParams["ring_color"] as Long
        val textColor = validParams["text_color"] as Long
        val radiusFrac = validParams["max_radius_fraction"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f
        val maxRadius = viewportHeight * radiusFrac
        val currentRadius = maxRadius * irisFrac

        // Clip circle iris mask
        canvas.save()
        val clipPath = Path().apply {
            addCircle(cx, cy, currentRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Text scale
        val textScale = when {
            timestampUs < 200_000L -> 0.85f
            timestampUs in 200_000L..1_200_000L -> {
                0.85f + 0.15f * EasingEvaluator.evaluate(EasingType.EASE_OUT, (timestampUs - 200_000L) / 1_000_000.0f)
            }
            else -> 1.0f
        }

        val brandSize = viewportHeight * 0.09f * textScale
        val brandPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(textColor, alpha),
            textSize = brandSize,
            isBold = true,
            letterSpacing = 0.2f
        )
        TemplateDrawUtils.drawCenteredText(canvas, brand, cx, cy - brandSize * 0.2f, brandPaint)

        if (tagline.isNotEmpty() && timestampUs >= 600_000L) {
            val tagAlpha = when {
                timestampUs in 600_000L..1_200_000L -> (timestampUs - 600_000L) / 600_000.0f
                else -> 1.0f
            } * alpha

            val tagPaint = TemplateDrawUtils.createTextPaint(
                color = TemplateDrawUtils.applyAlpha(textColor, tagAlpha * 0.8f),
                textSize = brandSize * 0.28f,
                isBold = false,
                letterSpacing = 0.25f
            )
            TemplateDrawUtils.drawCenteredText(canvas, tagline, cx, cy + brandSize * 0.6f, tagPaint)
        }

        canvas.restore()

        // Outer expanding glowing wave ring
        if (timestampUs in 0L..900_000L) {
            val ringFrac = (timestampUs / 900_000.0f).coerceIn(0.0f, 1.0f)
            val ringRadius = maxRadius * 1.1f * ringFrac
            val ringAlpha = (1.0f - ringFrac) * alpha

            val ringPaint = TemplateDrawUtils.createStrokePaint(
                color = TemplateDrawUtils.applyAlpha(ringColor, ringAlpha),
                strokeWidth = viewportHeight * 0.008f * (1.0f - ringFrac)
            )
            canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        }
    }

    private fun evaluateIris(t: Long): Pair<Float, Float> {
        if (t <= 0L || t >= defaultDurationUs) return Pair(0.0f, 0.0f)

        val alpha: Float
        val irisFrac: Float

        when {
            t < 1_200_000L -> {
                alpha = 1.0f
                val frac = t / 1_200_000.0f
                irisFrac = CubicBezierSolver.solve(0.16f, 1.0f, 0.3f, 1.0f, frac)
            }
            t <= 2_800_000L -> {
                alpha = 1.0f
                irisFrac = 1.0f
            }
            else -> {
                val outFrac = (t - 2_800_000L) / 700_000.0f
                val ease = EasingEvaluator.evaluate(EasingType.EASE_IN, outFrac)
                irisFrac = 1.0f - ease
                alpha = 1.0f - ease
            }
        }

        return Pair(alpha.coerceIn(0.0f, 1.0f), irisFrac.coerceIn(0.0f, 1.0f))
    }
}
