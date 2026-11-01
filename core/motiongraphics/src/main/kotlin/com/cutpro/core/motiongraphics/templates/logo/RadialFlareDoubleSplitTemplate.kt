package com.cutpro.core.motiongraphics.templates.logo

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

class RadialFlareDoubleSplitTemplate : MotionGraphicsTemplate {
    override val id: String = "logo_radial_flare"
    override val aliases: List<String> = listOf("logo_double_horizontal_split", "logo_double_split", "double_horizontal_split")
    override val displayName: String = "Double Horizontal Split Curtain"
    override val category: TemplateCategory = TemplateCategory.LOGO_REVEALS
    override val defaultDurationUs: Long = 3_500_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("logo_text", "Emblem Text", ParameterType.STRING, "ORIGIN ENGINE", minLength = 1, maxLength = 30, disallowEmpty = true),
            ParameterDefinition("curtain_color", "Curtain Color", ParameterType.COLOR, 0xFF121212L),
            ParameterDefinition("accent_edge_color", "Accent Seam Color", ParameterType.COLOR, 0xFFFF3D00L),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("slitAngleDeg", "Slit Angle Degrees", ParameterType.FLOAT, 0.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val text = validParams["logo_text"].toString()
        val textColor = (validParams["text_color"] as Long).toInt()

        val (curtainWidthFrac, textScale) = evaluateSplit(timestampUs)
        val layers = mutableListOf<RenderLayerState>()

        layers.add(
            RenderLayerState.TextLayer(
                id = "logo_emblem",
                transform = TrackItemTransform(
                    positionX = viewportWidth * 0.5f,
                    positionY = viewportHeight * 0.5f,
                    scaleX = textScale,
                    scaleY = textScale,
                    opacity = 1.0f
                ),
                text = text,
                textColor = textColor
            )
        )

        return RenderState(
            timestampUs = timestampUs,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            layers = layers,
            masterOpacity = 1.0f
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
        val text = validParams["logo_text"].toString()
        val curtainColor = validParams["curtain_color"] as Long
        val accentColor = validParams["accent_edge_color"] as Long
        val textColor = validParams["text_color"] as Long

        val (curtainWidthFrac, textScale) = evaluateSplit(timestampUs)

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f

        // Draw centered logo text (underneath curtains)
        val textSize = viewportHeight * 0.075f * textScale
        val textPaint = TemplateDrawUtils.createTextPaint(
            color = textColor.toInt(),
            textSize = textSize,
            isBold = true,
            letterSpacing = 0.2f
        )
        TemplateDrawUtils.drawCenteredText(canvas, text, cx, cy, textPaint)

        // Draw left and right curtains
        if (curtainWidthFrac > 0.0f) {
            val curtainPaint = TemplateDrawUtils.createFillPaint(curtainColor.toInt())
            val curtainWidth = viewportWidth * 0.5f * curtainWidthFrac

            // Left curtain
            val leftRect = RectF(0f, 0f, curtainWidth, viewportHeight.toFloat())
            canvas.drawRect(leftRect, curtainPaint)

            // Right curtain
            val rightRect = RectF(viewportWidth - curtainWidth, 0f, viewportWidth.toFloat(), viewportHeight.toFloat())
            canvas.drawRect(rightRect, curtainPaint)

            // Seam line
            val seamAlpha = (curtainWidthFrac * 2.0f).coerceIn(0.0f, 1.0f)
            if (seamAlpha > 0.0f) {
                val seamPaint = TemplateDrawUtils.createStrokePaint(
                    color = TemplateDrawUtils.applyAlpha(accentColor, seamAlpha),
                    strokeWidth = 2.0f
                )
                canvas.drawLine(curtainWidth, 0f, curtainWidth, viewportHeight.toFloat(), seamPaint)
                canvas.drawLine(viewportWidth - curtainWidth, 0f, viewportWidth - curtainWidth, viewportHeight.toFloat(), seamPaint)
            }
        }
    }

    private fun evaluateSplit(t: Long): Pair<Float, Float> {
        val curtainWidthFrac: Float
        val textScale: Float

        when {
            t <= 0L -> {
                curtainWidthFrac = 1.0f // Fully closed
                textScale = 0.9f
            }
            t < 1_000_000L -> {
                val frac = t / 1_000_000.0f
                val ease = CubicBezierSolver.solve(0.25f, 1.0f, 0.3f, 1.0f, frac)
                curtainWidthFrac = 1.0f - ease
                textScale = 0.9f + 0.1f * EasingEvaluator.evaluate(EasingType.EASE_OUT, frac)
            }
            t <= 2_800_000L -> {
                curtainWidthFrac = 0.0f // Fully open
                textScale = 1.0f
            }
            t < defaultDurationUs -> {
                val closeFrac = (t - 2_800_000L) / 700_000.0f
                val ease = EasingEvaluator.evaluate(EasingType.EASE_IN, closeFrac)
                curtainWidthFrac = ease
                textScale = 1.0f
            }
            else -> {
                curtainWidthFrac = 1.0f
                textScale = 1.0f
            }
        }

        return Pair(curtainWidthFrac.coerceIn(0.0f, 1.0f), textScale)
    }
}
