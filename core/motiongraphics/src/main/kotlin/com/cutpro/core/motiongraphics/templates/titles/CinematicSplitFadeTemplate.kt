package com.cutpro.core.motiongraphics.templates.titles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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

class CinematicSplitFadeTemplate : MotionGraphicsTemplate {
    override val id: String = "title_cinematic_split_fade"
    override val aliases: List<String> = listOf("title_cinematic_split", "cinematic_split")
    override val displayName: String = "Cinematic Split Fade"
    override val category: TemplateCategory = TemplateCategory.OPENING_TITLES
    override val defaultDurationUs: Long = 4_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("title_text", "Title Text", ParameterType.STRING, "ODYSSEY BEYOND", minLength = 1, maxLength = 32, disallowEmpty = true),
            ParameterDefinition("genre_tag", "Genre Tag", ParameterType.STRING, "A DIRECTORS CUT", minLength = 0, maxLength = 24),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFFEAEAEAL),
            ParameterDefinition("matte_line_color", "Razor Line Color", ParameterType.COLOR, 0xFFFF3366L),
            ParameterDefinition("split_distance_fraction", "Split Distance", ParameterType.FLOAT, 0.04f, minValue = 0.01f, maxValue = 0.1f),
            ParameterDefinition("splitWidth", "Split Width", ParameterType.FLOAT, 2.0f, minValue = 0.5f, maxValue = 10.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val title = validParams["title_text"].toString()
        val textColor = (validParams["text_color"] as Long).toInt()
        val splitDist = validParams["split_distance_fraction"] as Float

        val (alpha, splitProgress) = calculateSplitState(timestampUs)
        val layers = mutableListOf<RenderLayerState>()

        if (alpha > 0.0f) {
            val yOffset = splitProgress * splitDist * viewportHeight
            layers.add(
                RenderLayerState.TextLayer(
                    id = "title_upper",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.5f - yOffset,
                        opacity = alpha
                    ),
                    text = title,
                    textColor = TemplateDrawUtils.applyAlpha(textColor, alpha),
                    clipRect = RectF(0f, 0f, viewportWidth.toFloat(), viewportHeight * 0.5f)
                )
            )
            layers.add(
                RenderLayerState.TextLayer(
                    id = "title_lower",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.5f + yOffset,
                        opacity = alpha
                    ),
                    text = title,
                    textColor = TemplateDrawUtils.applyAlpha(textColor, alpha),
                    clipRect = RectF(0f, viewportHeight * 0.5f, viewportWidth.toFloat(), viewportHeight.toFloat())
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
        val (alpha, splitProgress) = calculateSplitState(timestampUs)
        if (alpha <= 0.0f) return

        val title = validParams["title_text"].toString()
        val genre = validParams["genre_tag"].toString()
        val textColor = validParams["text_color"] as Long
        val lineColor = validParams["matte_line_color"] as Long
        val splitDist = validParams["split_distance_fraction"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f
        val textSize = viewportHeight * 0.065f

        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(textColor, alpha),
            textSize = textSize,
            isBold = true,
            letterSpacing = 0.2f
        )

        val yOffset = splitProgress * splitDist * viewportHeight

        // Upper half clipped
        canvas.save()
        canvas.clipRect(0f, 0f, viewportWidth.toFloat(), cy)
        TemplateDrawUtils.drawCenteredText(canvas, title, cx, cy - yOffset, textPaint)
        canvas.restore()

        // Lower half clipped
        canvas.save()
        canvas.clipRect(0f, cy, viewportWidth.toFloat(), viewportHeight.toFloat())
        TemplateDrawUtils.drawCenteredText(canvas, title, cx, cy + yOffset, textPaint)
        canvas.restore()

        // Genre tag
        if (genre.isNotEmpty() && timestampUs in 500_000L..3_500_000L) {
            val genreAlpha = when {
                timestampUs < 1_000_000L -> (timestampUs - 500_000L) / 500_000.0f
                timestampUs > 3_000_000L -> (3_500_000L - timestampUs) / 500_000.0f
                else -> 1.0f
            }.coerceIn(0.0f, 1.0f) * alpha

            val genrePaint = TemplateDrawUtils.createTextPaint(
                color = TemplateDrawUtils.applyAlpha(textColor, genreAlpha * 0.7f),
                textSize = textSize * 0.3f,
                isBold = false,
                letterSpacing = 0.3f
            )
            TemplateDrawUtils.drawCenteredText(canvas, genre, cx, cy - yOffset - textSize * 0.8f, genrePaint)
        }

        // Razor matte line
        if (timestampUs in 0L..1_200_000L) {
            val lineScale = when {
                timestampUs < 400_000L -> EasingEvaluator.evaluate(EasingType.EASE_OUT, timestampUs / 400_000.0f)
                else -> 1.0f
            }
            val lineAlpha = when {
                timestampUs < 700_000L -> 1.0f
                else -> (1_200_000L - timestampUs) / 500_000.0f
            }.coerceIn(0.0f, 1.0f)

            if (lineAlpha > 0.0f) {
                val lineWidth = viewportWidth * 0.6f * lineScale
                val linePaint = TemplateDrawUtils.createStrokePaint(
                    color = TemplateDrawUtils.applyAlpha(lineColor, lineAlpha * alpha),
                    strokeWidth = 2.0f
                )
                canvas.drawLine(cx - lineWidth * 0.5f, cy, cx + lineWidth * 0.5f, cy, linePaint)
            }
        }
    }

    private fun calculateSplitState(t: Long): Pair<Float, Float> {
        if (t <= 0L || t >= defaultDurationUs) return Pair(0.0f, 0.0f)

        val alpha: Float
        val splitProgress: Float

        when {
            t < 300_000L -> {
                alpha = t / 300_000.0f
                splitProgress = 0.0f
            }
            t < 1_200_000L -> {
                alpha = 1.0f
                val frac = (t - 300_000L) / 900_000.0f
                splitProgress = CubicBezierSolver.solve(0.19f, 1.0f, 0.22f, 1.0f, frac)
            }
            t <= 3_200_000L -> {
                alpha = 1.0f
                splitProgress = 1.0f
            }
            else -> {
                val outFrac = (t - 3_200_000L) / 800_000.0f
                alpha = 1.0f - EasingEvaluator.evaluate(EasingType.EASE_IN, outFrac)
                splitProgress = 1.0f - EasingEvaluator.evaluate(EasingType.EASE_IN, outFrac)
            }
        }

        return Pair(alpha.coerceIn(0.0f, 1.0f), splitProgress.coerceIn(0.0f, 1.0f))
    }
}
