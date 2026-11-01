package com.cutpro.core.motiongraphics.templates.cta

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

class FollowBadgeSwipeIndicatorTemplate : MotionGraphicsTemplate {
    override val id: String = "cta_follow_badge"
    override val aliases: List<String> = listOf("cta_swipe_tap_indicator", "cta_swipe_indicator", "swipe_tap_indicator")
    override val displayName: String = "Swipe / Tap Mobile Indicator"
    override val category: TemplateCategory = TemplateCategory.CALL_TO_ACTIONS
    override val defaultDurationUs: Long = 3_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("instruction_text", "Instruction Text", ParameterType.STRING, "SWIPE UP FOR MORE", minLength = 1, maxLength = 30, disallowEmpty = true),
            ParameterDefinition("direction", "Direction", ParameterType.STRING, "UP", minLength = 2, maxLength = 10),
            ParameterDefinition("indicator_type", "Indicator Type", ParameterType.ENUM, "SWIPE_UP", allowedValues = listOf("SWIPE_UP", "TAP_RIPPLE")),
            ParameterDefinition("indicator_color", "Indicator Color", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("pulse_frequency_hz", "Pulse Frequency", ParameterType.FLOAT, 1.5f, minValue = 0.5f, maxValue = 3.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val text = validParams["instruction_text"].toString()

        val alpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 300_000L -> timestampUs / 300_000.0f
            timestampUs > 2_700_000L -> (defaultDurationUs - timestampUs) / 300_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val layers = mutableListOf<RenderLayerState>()
        if (alpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "swipe_instruction",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.88f,
                        opacity = alpha
                    ),
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
        val alpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs < 300_000L -> timestampUs / 300_000.0f
            timestampUs > 2_700_000L -> (defaultDurationUs - timestampUs) / 300_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (alpha <= 0.0f) return

        val text = validParams["instruction_text"].toString()
        val indicatorType = validParams["indicator_type"].toString()
        val color = validParams["indicator_color"] as Long
        val freq = validParams["pulse_frequency_hz"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.82f

        val periodUs = (1_000_000.0f / freq).toLong()
        val cycleProg = ((timestampUs % periodUs).toFloat() / periodUs).coerceIn(0.0f, 1.0f)

        if (indicatorType == "SWIPE_UP") {
            // Three stacked chevrons floating upwards
            val chevronPaint = TemplateDrawUtils.createStrokePaint(
                color = TemplateDrawUtils.applyAlpha(color, alpha * (1.0f - cycleProg)),
                strokeWidth = viewportHeight * 0.005f
            )

            val chevronW = viewportWidth * 0.06f
            val chevronH = viewportHeight * 0.015f

            for (k in 0..2) {
                val delayOffset = k * 0.25f
                val effectiveCycle = (cycleProg + delayOffset) % 1.0f
                val chevronY = cy - effectiveCycle * viewportHeight * 0.05f - k * chevronH * 1.5f

                val path = Path().apply {
                    moveTo(cx - chevronW * 0.5f, chevronY + chevronH)
                    lineTo(cx, chevronY)
                    lineTo(cx + chevronW * 0.5f, chevronY + chevronH)
                }
                val kAlpha = (1.0f - effectiveCycle) * alpha
                chevronPaint.color = TemplateDrawUtils.applyAlpha(color, kAlpha)
                canvas.drawPath(path, chevronPaint)
            }
        } else {
            // TAP_RIPPLE: concentric expanding circles
            val maxRadius = viewportHeight * 0.08f
            val rippleRadius = maxRadius * cycleProg
            val rippleAlpha = (1.0f - cycleProg) * alpha

            val ripplePaint = TemplateDrawUtils.createStrokePaint(
                color = TemplateDrawUtils.applyAlpha(color, rippleAlpha),
                strokeWidth = viewportHeight * 0.004f
            )
            canvas.drawCircle(cx, cy, rippleRadius, ripplePaint)
        }

        // Instruction Text with breathing scale
        val breath = 1.0f + 0.02f * sin(2.0f * Math.PI.toFloat() * cycleProg)
        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(color, alpha),
            textSize = viewportHeight * 0.028f * breath,
            isBold = true,
            letterSpacing = 0.1f
        )
        TemplateDrawUtils.drawCenteredText(canvas, text, cx, cy + viewportHeight * 0.06f, textPaint)
    }
}
