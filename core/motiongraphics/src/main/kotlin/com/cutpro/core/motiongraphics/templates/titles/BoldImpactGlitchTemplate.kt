package com.cutpro.core.motiongraphics.templates.titles

import android.graphics.Canvas
import android.graphics.Paint
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

class BoldImpactGlitchTemplate : MotionGraphicsTemplate {
    override val id: String = "title_bold_impact"
    override val aliases: List<String> = listOf("title_bold_glitch", "bold_glitch")
    override val displayName: String = "Bold Cyber Glitch"
    override val category: TemplateCategory = TemplateCategory.OPENING_TITLES
    override val defaultDurationUs: Long = 3_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("headline", "Headline Text", ParameterType.STRING, "NEO TOKYO 2099", minLength = 1, maxLength = 30, disallowEmpty = true),
            ParameterDefinition("cyan_tint", "Cyan Tint Color", ParameterType.COLOR, 0xFF00FFFFL),
            ParameterDefinition("magenta_tint", "Magenta Tint Color", ParameterType.COLOR, 0xFFFF0055L),
            ParameterDefinition("glitch_intensity", "Glitch Intensity", ParameterType.FLOAT, 0.7f, minValue = 0.0f, maxValue = 1.0f),
            ParameterDefinition("glitchIntensity", "Glitch Intensity Alias", ParameterType.FLOAT, 0.7f, minValue = 0.0f, maxValue = 1.0f),
            ParameterDefinition("scanline_count", "Scanline Count", ParameterType.INT, 24, minValue = 8, maxValue = 64)
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
        val intensity = (params["glitchIntensity"] as? Number)?.toFloat()
            ?: (validParams["glitch_intensity"] as Float)

        val (alpha, cyanOffset, magOffset) = evaluateGlitch(timestampUs, intensity, viewportWidth)
        val layers = mutableListOf<RenderLayerState>()

        if (alpha > 0.0f) {
            val cx = viewportWidth * 0.5f
            val cy = viewportHeight * 0.5f
            val cyanColor = (validParams["cyan_tint"] as Long).toInt()
            val magColor = (validParams["magenta_tint"] as Long).toInt()

            layers.add(
                RenderLayerState.TextLayer(
                    id = "glitch_cyan",
                    transform = TrackItemTransform(positionX = cx + cyanOffset, positionY = cy, opacity = alpha * 0.8f),
                    text = text,
                    textColor = TemplateDrawUtils.applyAlpha(cyanColor, alpha * 0.8f)
                )
            )
            layers.add(
                RenderLayerState.TextLayer(
                    id = "glitch_magenta",
                    transform = TrackItemTransform(positionX = cx + magOffset, positionY = cy, opacity = alpha * 0.8f),
                    text = text,
                    textColor = TemplateDrawUtils.applyAlpha(magColor, alpha * 0.8f)
                )
            )
            layers.add(
                RenderLayerState.TextLayer(
                    id = "glitch_main",
                    transform = TrackItemTransform(positionX = cx, positionY = cy, opacity = alpha),
                    text = text,
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
        val intensity = (params["glitchIntensity"] as? Number)?.toFloat()
            ?: (validParams["glitch_intensity"] as Float)

        val (alpha, cyanOffset, magOffset) = evaluateGlitch(timestampUs, intensity, viewportWidth)
        if (alpha <= 0.0f) return

        val text = validParams["headline"].toString()
        val cyanColor = validParams["cyan_tint"] as Long
        val magColor = validParams["magenta_tint"] as Long

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f
        val textSize = viewportHeight * 0.07f

        val cyanPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(cyanColor, alpha * 0.75f),
            textSize = textSize,
            isBold = true,
            letterSpacing = 0.15f
        )
        val magPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(magColor, alpha * 0.75f),
            textSize = textSize,
            isBold = true,
            letterSpacing = 0.15f
        )
        val mainPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, alpha),
            textSize = textSize,
            isBold = true,
            letterSpacing = 0.15f
        )

        // Draw Chromatic split layers
        TemplateDrawUtils.drawCenteredText(canvas, text, cx + cyanOffset, cy, cyanPaint)
        TemplateDrawUtils.drawCenteredText(canvas, text, cx + magOffset, cy, magPaint)
        TemplateDrawUtils.drawCenteredText(canvas, text, cx, cy, mainPaint)
    }

    private fun evaluateGlitch(t: Long, intensity: Float, width: Int): Triple<Float, Float, Float> {
        if (t <= 0L || t >= defaultDurationUs) return Triple(0.0f, 0.0f, 0.0f)

        val alpha: Float
        val cyanOffset: Float
        val magOffset: Float

        when {
            t < 600_000L -> {
                // Entry burst with discrete deterministic oscillations
                val phase = (t / 100_000L).toInt()
                alpha = if (phase % 2 == 0) 1.0f else 0.4f + 0.6f * (phase / 6.0f)

                val step = (t / 50_000L).toInt()
                val offsets = floatArrayOf(-0.02f, 0.015f, -0.01f, 0.005f, 0.0f)
                val base = offsets.getOrElse(step % offsets.size) { 0.0f }
                cyanOffset = base * width * intensity
                magOffset = -base * width * intensity
            }
            t in 1_450_000L..1_550_000L -> {
                // Micro twitch
                alpha = 1.0f
                val frac = (t - 1_450_000L) / 100_000.0f
                val micro = sin(frac * Math.PI.toFloat()) * 0.008f * width * intensity
                cyanOffset = -micro
                magOffset = micro
            }
            t <= 2_400_000L -> {
                alpha = 1.0f
                cyanOffset = 0.0f
                magOffset = 0.0f
            }
            else -> {
                val outFrac = (t - 2_400_000L) / 600_000.0f
                alpha = 1.0f - EasingEvaluator.evaluate(EasingType.EASE_IN, outFrac)
                val outOffset = outFrac * 0.025f * width * intensity
                cyanOffset = -outOffset
                magOffset = outOffset
            }
        }

        return Triple(alpha.coerceIn(0.0f, 1.0f), cyanOffset, magOffset)
    }
}
