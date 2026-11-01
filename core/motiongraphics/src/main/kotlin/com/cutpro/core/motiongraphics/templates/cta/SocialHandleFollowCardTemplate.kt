package com.cutpro.core.motiongraphics.templates.cta

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.cutpro.core.motiongraphics.evaluator.CubicBezierSolver
import com.cutpro.core.motiongraphics.model.RenderLayerState
import com.cutpro.core.motiongraphics.model.RenderState
import com.cutpro.core.motiongraphics.model.TrackItemTransform
import com.cutpro.core.motiongraphics.schema.ParameterDefinition
import com.cutpro.core.motiongraphics.schema.ParameterSchema
import com.cutpro.core.motiongraphics.schema.ParameterType
import com.cutpro.core.motiongraphics.templates.MotionGraphicsTemplate
import com.cutpro.core.motiongraphics.templates.TemplateCategory
import com.cutpro.core.motiongraphics.templates.TemplateDrawUtils

class SocialHandleFollowCardTemplate : MotionGraphicsTemplate {
    override val id: String = "cta_social_handle"
    override val aliases: List<String> = listOf("cta_follow_handle", "clean_follow_handle")
    override val displayName: String = "Clean Follow Handle Card"
    override val category: TemplateCategory = TemplateCategory.CALL_TO_ACTIONS
    override val defaultDurationUs: Long = 3_500_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("platform_name", "Platform Name", ParameterType.STRING, "YOUTUBE", minLength = 1, maxLength = 20, disallowEmpty = true),
            ParameterDefinition("creator_handle", "Creator Handle", ParameterType.STRING, "@CutProApp", minLength = 1, maxLength = 25, disallowEmpty = true),
            ParameterDefinition("action", "Action Text", ParameterType.STRING, "Follow", minLength = 1, maxLength = 15, disallowEmpty = true),
            ParameterDefinition("platform_color", "Platform Color", ParameterType.COLOR, 0xFFFF0000L),
            ParameterDefinition("card_bg_color", "Card Background Color", ParameterType.COLOR, 0xE6181820L),
            ParameterDefinition("card_width_dp", "Card Width DP", ParameterType.FLOAT, 240.0f, minValue = 160.0f, maxValue = 360.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val handle = validParams["creator_handle"].toString()

        val (alpha, yOffset) = evaluateCardMotion(timestampUs, viewportHeight)
        val layers = mutableListOf<RenderLayerState>()

        if (alpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "handle_text",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * 0.5f + yOffset,
                        opacity = alpha
                    ),
                    text = handle,
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
        val (alpha, yOffset) = evaluateCardMotion(timestampUs, viewportHeight)
        if (alpha <= 0.0f) return

        val platform = validParams["platform_name"].toString()
        val handle = validParams["creator_handle"].toString()
        val action = validParams["action"].toString()
        val platformColor = validParams["platform_color"] as Long
        val bgColor = validParams["card_bg_color"] as Long
        val widthDp = validParams["card_width_dp"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f + yOffset

        val cardWidth = (widthDp * 2.0f).coerceAtMost(viewportWidth * 0.7f)
        val cardHeight = viewportHeight * 0.08f
        val cornerRadius = cardHeight * 0.35f

        val cardRect = RectF(
            cx - cardWidth * 0.5f,
            cy - cardHeight * 0.5f,
            cx + cardWidth * 0.5f,
            cy + cardHeight * 0.5f
        )
        val cardPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(bgColor, alpha))
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint)

        // Platform Tag Pill on left
        val tagWidth = cardWidth * 0.28f
        val tagRect = RectF(
            cardRect.left + cardHeight * 0.15f,
            cardRect.top + cardHeight * 0.15f,
            cardRect.left + tagWidth,
            cardRect.bottom - cardHeight * 0.15f
        )
        val tagPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(platformColor, alpha))
        canvas.drawRoundRect(tagRect, cornerRadius * 0.6f, cornerRadius * 0.6f, tagPaint)

        val tagTextPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, alpha),
            textSize = tagRect.height() * 0.45f,
            isBold = true
        )
        TemplateDrawUtils.drawCenteredText(canvas, platform, tagRect.centerX(), tagRect.centerY(), tagTextPaint)

        // Handle Text
        val handlePaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, alpha),
            textSize = cardHeight * 0.34f,
            isBold = true
        )
        val handleX = tagRect.right + cardWidth * 0.04f + handlePaint.measureText(handle) * 0.5f
        TemplateDrawUtils.drawCenteredText(canvas, handle, handleX, cy, handlePaint)
    }

    private fun evaluateCardMotion(t: Long, height: Int): Pair<Float, Float> {
        if (t <= 0L || t >= defaultDurationUs) return Pair(0.0f, 0.10f * height)

        val alpha: Float
        val yOffset: Float

        when {
            t < 600_000L -> {
                alpha = (t / 400_000.0f).coerceIn(0.0f, 1.0f)
                val inFrac = t / 600_000.0f
                val ease = CubicBezierSolver.solve(0.18f, 0.9f, 0.25f, 1.0f, inFrac)
                yOffset = (1.0f - ease) * 0.10f * height
            }
            t <= 3_000_000L -> {
                alpha = 1.0f
                yOffset = 0.0f
            }
            else -> {
                val outFrac = (t - 3_000_000L) / 500_000.0f
                alpha = (1.0f - (t - 3_100_000L) / 400_000.0f).coerceIn(0.0f, 1.0f)
                yOffset = outFrac * 0.08f * height
            }
        }

        return Pair(alpha.coerceIn(0.0f, 1.0f), yOffset)
    }
}
