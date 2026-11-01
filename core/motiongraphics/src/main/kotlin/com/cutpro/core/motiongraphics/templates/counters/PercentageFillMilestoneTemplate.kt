package com.cutpro.core.motiongraphics.templates.counters

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.cutpro.core.motiongraphics.evaluator.AnalyticalSpringSolver
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
import kotlin.math.roundToInt

class PercentageFillMilestoneTemplate : MotionGraphicsTemplate {
    override val id: String = "counter_percentage_fill"
    override val aliases: List<String> = listOf("counter_milestone_badge", "milestone_badge")
    override val displayName: String = "Milestone Achievement Badge"
    override val category: TemplateCategory = TemplateCategory.ANIMATED_COUNTERS
    override val defaultDurationUs: Long = 3_500_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("milestone_count", "Milestone Target Count", ParameterType.INT, 50, minValue = 1, maxValue = 99999),
            ParameterDefinition("targetNumber", "Target Number Alias", ParameterType.INT, 50, minValue = 1, maxValue = 99999),
            ParameterDefinition("badge_title", "Badge Title", ParameterType.STRING, "PROJECTS COMPLETED", minLength = 1, maxLength = 30, disallowEmpty = true),
            ParameterDefinition("arc_color", "Progress Arc Color", ParameterType.COLOR, 0xFFFF9100L),
            ParameterDefinition("badge_bg_color", "Badge Background Color", ParameterType.COLOR, 0xDD1A1A24L),
            ParameterDefinition("stroke_width_dp", "Stroke Width DP", ParameterType.FLOAT, 6.0f, minValue = 2.0f, maxValue = 16.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val target = (params["targetNumber"] as? Number)?.toInt()
            ?: (validParams["milestone_count"] as Int)

        val (scale, alpha) = evaluateScale(timestampUs)
        val count = evaluateCount(timestampUs, target)

        val layers = mutableListOf<RenderLayerState>()
        if (alpha > 0.0f && scale > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "milestone_count",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.48f,
                        scaleX = scale,
                        scaleY = scale,
                        opacity = alpha
                    ),
                    text = count.toString(),
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
        val (scale, alpha) = evaluateScale(timestampUs)
        if (alpha <= 0.0f || scale <= 0.0f) return

        val target = (params["targetNumber"] as? Number)?.toInt()
            ?: (validParams["milestone_count"] as Int)
        val title = validParams["badge_title"].toString()
        val arcColor = validParams["arc_color"] as Long
        val bgColor = validParams["badge_bg_color"] as Long
        val strokeDp = validParams["stroke_width_dp"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.48f
        val radius = viewportHeight * 0.14f * scale

        // Background Disc
        val discPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(bgColor, alpha))
        canvas.drawCircle(cx, cy, radius, discPaint)

        // Arc sweep
        val sweepAngle = evaluateSweep(timestampUs)
        if (sweepAngle > 0.0f) {
            val arcPaint = TemplateDrawUtils.createStrokePaint(
                color = TemplateDrawUtils.applyAlpha(arcColor, alpha),
                strokeWidth = strokeDp * 2.0f * scale
            )
            val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arcRect, -90.0f, sweepAngle, false, arcPaint)
        }

        // Stepping count
        val currentCount = evaluateCount(timestampUs, target)
        val countPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, alpha),
            textSize = radius * 0.7f,
            isBold = true
        )
        TemplateDrawUtils.drawCenteredText(canvas, currentCount.toString(), cx, cy, countPaint)

        // Title below badge
        val titlePaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xCCFFFFFFL, alpha),
            textSize = radius * 0.24f,
            isBold = true,
            letterSpacing = 0.1f
        )
        TemplateDrawUtils.drawCenteredText(canvas, title, cx, cy + radius * 1.35f, titlePaint)
    }

    private fun evaluateScale(t: Long): Pair<Float, Float> {
        if (t <= 0L || t >= defaultDurationUs) return Pair(0.0f, 0.0f)

        return when {
            t < 600_000L -> {
                val springProgress = AnalyticalSpringSolver.solveAtTimestamp(300f, 0.6f, t, 0L, 600_000L)
                Pair(springProgress, (t / 200_000.0f).coerceIn(0.0f, 1.0f))
            }
            t <= 3_000_000L -> Pair(1.0f, 1.0f)
            else -> {
                val outFrac = (t - 3_000_000L) / 500_000.0f
                val scale = 1.0f - EasingEvaluator.evaluate(EasingType.EASE_IN, outFrac)
                Pair(scale, 1.0f - outFrac)
            }
        }
    }

    private fun evaluateSweep(t: Long): Float {
        return when {
            t < 300_000L -> 0.0f
            t in 300_000L..2_200_000L -> {
                val frac = (t - 300_000L) / 1_900_000.0f
                360.0f * CubicBezierSolver.solve(0.2f, 0.8f, 0.2f, 1.0f, frac)
            }
            else -> 360.0f
        }.coerceIn(0.0f, 360.0f)
    }

    private fun evaluateCount(t: Long, target: Int): Int {
        return when {
            t < 300_000L -> 0
            t in 300_000L..2_200_000L -> {
                val frac = (t - 300_000L) / 1_900_000.0f
                val ease = CubicBezierSolver.solve(0.2f, 0.8f, 0.2f, 1.0f, frac)
                (target * ease).roundToInt()
            }
            else -> target
        }
    }
}
