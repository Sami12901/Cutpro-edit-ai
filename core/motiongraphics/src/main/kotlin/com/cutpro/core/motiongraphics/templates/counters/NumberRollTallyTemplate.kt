package com.cutpro.core.motiongraphics.templates.counters

import android.graphics.Canvas
import android.graphics.Paint
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
import java.text.NumberFormat
import java.util.Locale

class NumberRollTallyTemplate : MotionGraphicsTemplate {
    override val id: String = "counter_number_roll"
    override val aliases: List<String> = listOf("counter_tally_metric", "numeric_tally_metric")
    override val displayName: String = "Numeric Tally Metric"
    override val category: TemplateCategory = TemplateCategory.ANIMATED_COUNTERS
    override val defaultDurationUs: Long = 3_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("start_value", "Start Value", ParameterType.FLOAT, 0.0f),
            ParameterDefinition("end_value", "End Value", ParameterType.FLOAT, 100000.0f),
            ParameterDefinition("prefix", "Prefix String", ParameterType.STRING, "$", minLength = 0, maxLength = 5),
            ParameterDefinition("suffix", "Suffix String", ParameterType.STRING, "+", minLength = 0, maxLength = 5),
            ParameterDefinition("decimals", "Decimals", ParameterType.INT, 0, minValue = 0, maxValue = 3),
            ParameterDefinition("metric_label", "Metric Label", ParameterType.STRING, "ACTIVE USERS WORLDWIDE", minLength = 0, maxLength = 40),
            ParameterDefinition("text_color", "Text Color", ParameterType.COLOR, 0xFF00E5FFL)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val formatted = formatNumber(validParams, timestampUs)
        val textColor = (validParams["text_color"] as Long).toInt()

        val alpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs > 2_700_000L -> (defaultDurationUs - timestampUs) / 300_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val layers = mutableListOf<RenderLayerState>()
        if (alpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "counter_number",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.48f,
                        opacity = alpha
                    ),
                    text = formatted,
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
        val alpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs > 2_700_000L -> (defaultDurationUs - timestampUs) / 300_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (alpha <= 0.0f) return

        val formatted = formatNumber(validParams, timestampUs)
        val label = validParams["metric_label"].toString()
        val textColor = validParams["text_color"] as Long

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.48f
        val numberSize = viewportHeight * 0.08f

        val numberPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(textColor, alpha),
            textSize = numberSize,
            isBold = true
        )
        TemplateDrawUtils.drawCenteredText(canvas, formatted, cx, cy, numberPaint)

        // Sub-label
        if (label.isNotEmpty() && timestampUs >= 400_000L) {
            val labelAlpha = when {
                timestampUs in 400_000L..1_000_000L -> (timestampUs - 400_000L) / 600_000.0f
                else -> 1.0f
            } * alpha

            val labelPaint = TemplateDrawUtils.createTextPaint(
                color = TemplateDrawUtils.applyAlpha(0xCCFFFFFFL, labelAlpha),
                textSize = numberSize * 0.32f,
                isBold = false,
                letterSpacing = 0.15f
            )
            TemplateDrawUtils.drawCenteredText(canvas, label, cx, cy + numberSize * 0.8f, labelPaint)
        }
    }

    private fun formatNumber(params: Map<String, Any>, t: Long): String {
        val startVal = (params["start_value"] as Float).toDouble()
        val endVal = (params["end_value"] as Float).toDouble()
        val prefix = params["prefix"].toString()
        val suffix = params["suffix"].toString()
        val decimals = params["decimals"] as Int

        val prog = when {
            t <= 0L -> 0.0f
            t >= 2_200_000L -> 1.0f
            else -> CubicBezierSolver.solve(0.12f, 0.8f, 0.25f, 1.0f, t / 2_200_000.0f)
        }

        val currentVal = startVal + (endVal - startVal) * prog
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
        val formattedNumber = nf.format(currentVal)
        return "$prefix$formattedNumber$suffix"
    }
}
