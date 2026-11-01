package com.cutpro.core.motiongraphics.templates.kinetic

import android.graphics.Canvas
import android.graphics.Paint
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
import kotlin.math.sin

class WordPopBounceTemplate : MotionGraphicsTemplate {
    override val id: String = "kinetic_word_pop"
    override val aliases: List<String> = listOf("kinetic_pop_bounce", "pop_bounce")
    override val displayName: String = "Pop Spring Bounce"
    override val category: TemplateCategory = TemplateCategory.KINETIC_TYPOGRAPHY
    override val defaultDurationUs: Long = 2_500_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("headline", "Headline Text", ParameterType.STRING, "LEVEL UP YOUR EDITS", minLength = 1, maxLength = 35, disallowEmpty = true),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFFFFE600L),
            ParameterDefinition("stroke_color", "Stroke Outline Color", ParameterType.COLOR, 0xFF000000L),
            ParameterDefinition("stroke_width", "Stroke Width Fraction", ParameterType.FLOAT, 0.008f, minValue = 0.0f, maxValue = 0.025f),
            ParameterDefinition("spring_stiffness", "Spring Stiffness", ParameterType.FLOAT, 280.0f, minValue = 100.0f, maxValue = 600.0f),
            ParameterDefinition("spring_damping", "Spring Damping Ratio", ParameterType.FLOAT, 0.55f, minValue = 0.2f, maxValue = 1.0f),
            ParameterDefinition("springDamping", "Spring Damping Alias", ParameterType.FLOAT, 0.55f, minValue = 0.2f, maxValue = 1.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val text = validParams["headline"].toString()
        val textColor = (validParams["text_color"] as Long).toInt()
        val strokeColor = (validParams["stroke_color"] as Long).toInt()
        val strokeFraction = validParams["stroke_width"] as Float
        val stiffness = (validParams["spring_stiffness"] as Float)
        val damping = (params["springDamping"] as? Number)?.toFloat()
            ?: (validParams["spring_damping"] as Float)

        val (scale, rot, alpha, yDrift) = evaluatePopState(timestampUs, stiffness, damping)
        val layers = mutableListOf<RenderLayerState>()

        if (alpha > 0.0f && scale > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "pop_headline",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.5f + yDrift * viewportHeight,
                        scaleX = scale,
                        scaleY = scale,
                        rotationDegrees = rot,
                        opacity = alpha
                    ),
                    text = text,
                    textColor = TemplateDrawUtils.applyAlpha(textColor, alpha),
                    strokeColor = TemplateDrawUtils.applyAlpha(strokeColor, alpha),
                    strokeWidth = strokeFraction * viewportHeight
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
        val stiffness = (validParams["spring_stiffness"] as Float)
        val damping = (params["springDamping"] as? Number)?.toFloat()
            ?: (validParams["spring_damping"] as Float)

        val (scale, rot, alpha, yDrift) = evaluatePopState(timestampUs, stiffness, damping)
        if (alpha <= 0.0f || scale <= 0.0f) return

        val text = validParams["headline"].toString()
        val textColor = validParams["text_color"] as Long
        val strokeColor = validParams["stroke_color"] as Long
        val strokeFraction = validParams["stroke_width"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f + yDrift * viewportHeight
        val textSize = viewportHeight * 0.07f * scale

        val fillPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(textColor, alpha),
            textSize = textSize,
            isBold = true
        )
        val strokePaint = TemplateDrawUtils.createStrokePaint(
            color = TemplateDrawUtils.applyAlpha(strokeColor, alpha),
            strokeWidth = strokeFraction * viewportHeight * scale,
            textSize = textSize,
            isBold = true
        )

        canvas.save()
        canvas.rotate(rot, cx, cy)
        TemplateDrawUtils.drawCenteredText(canvas, text, cx, cy, fillPaint, strokePaint)
        canvas.restore()
    }

    private fun evaluatePopState(t: Long, k: Float, zeta: Float): PopState {
        if (t <= 0L || t >= defaultDurationUs) return PopState(0.0f, 0.0f, 0.0f, 0.0f)

        val scale: Float
        val rotation: Float
        val alpha: Float
        var yDrift = 0.0f

        when {
            t < 800_000L -> {
                // Spring entry
                val springProgress = AnalyticalSpringSolver.solveAtTimestamp(k, zeta, t, 0L, 800_000L)
                scale = 0.1f + 0.9f * springProgress
                val rotSpring = AnalyticalSpringSolver.solveAtTimestamp(240f, 0.6f, t, 0L, 800_000L)
                rotation = -12.0f * (1.0f - rotSpring)
                alpha = (t / 150_000.0f).coerceIn(0.0f, 1.0f)
            }
            t <= 2_000_000L -> {
                scale = 1.0f
                rotation = 0.0f
                alpha = 1.0f
                val idleProgress = (t - 800_000L) / 1_200_000.0f
                yDrift = 0.004f * sin(2.0f * Math.PI.toFloat() * idleProgress)
            }
            else -> {
                val outFrac = (t - 2_000_000L) / 500_000.0f
                scale = 1.0f - EasingEvaluator.evaluate(EasingType.EASE_IN, outFrac)
                rotation = 0.0f
                val fadeFrac = (t - 2_200_000L) / 300_000.0f
                alpha = (1.0f - fadeFrac).coerceIn(0.0f, 1.0f)
            }
        }

        return PopState(scale, rotation, alpha, yDrift)
    }

    private data class PopState(val scale: Float, val rotation: Float, val alpha: Float, val yDrift: Float)
}
