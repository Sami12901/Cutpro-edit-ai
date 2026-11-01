package com.cutpro.core.motiongraphics.templates.lowerthirds

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.cutpro.core.motiongraphics.evaluator.AnalyticalSpringSolver
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

class CleanRibbonSocialDuoTemplate : MotionGraphicsTemplate {
    override val id: String = "lower_third_clean_ribbon"
    override val aliases: List<String> = listOf("lower_third_social_duo", "lower_social_creator", "social_creator_duo")
    override val displayName: String = "Social Creator Duo-Line"
    override val category: TemplateCategory = TemplateCategory.LOWER_THIRDS
    override val defaultDurationUs: Long = 4_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("display_name", "Display Name", ParameterType.STRING, "Alex Rivera", minLength = 1, maxLength = 30, disallowEmpty = true),
            ParameterDefinition("handle", "Social Handle", ParameterType.STRING, "@alexcreates", minLength = 1, maxLength = 30, disallowEmpty = true),
            ParameterDefinition("handle_text", "Handle Alias", ParameterType.STRING, "@alexcreates", minLength = 1, maxLength = 30),
            ParameterDefinition("badge_letter", "Badge Initial Letter", ParameterType.STRING, "A", minLength = 1, maxLength = 1),
            ParameterDefinition("badge_bg_color", "Badge Background Color", ParameterType.COLOR, 0xFF6200EEL),
            ParameterDefinition("container_color", "Container Background Color", ParameterType.COLOR, 0xDD1E1E2EL),
            ParameterDefinition("y_position", "Y Position", ParameterType.FLOAT, 0.85f, minValue = 0.70f, maxValue = 0.92f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val name = validParams["display_name"].toString()
        val yPos = validParams["y_position"] as Float

        val (alpha, xOffset) = evaluateSlide(timestampUs, viewportWidth)
        val layers = mutableListOf<RenderLayerState>()

        if (alpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "creator_name",
                    transform = TrackItemTransform(
                        positionX = xOffset + viewportWidth * 0.15f,
                        positionY = viewportHeight * yPos,
                        opacity = alpha
                    ),
                    text = name,
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
        val (alpha, xOffset) = evaluateSlide(timestampUs, viewportWidth)
        if (alpha <= 0.0f) return

        val name = validParams["display_name"].toString()
        val handle = if (params.containsKey("handle")) validParams["handle"].toString() else validParams["handle_text"].toString()
        val badgeLetter = validParams["badge_letter"].toString()
        val badgeColor = validParams["badge_bg_color"] as Long
        val containerColor = validParams["container_color"] as Long
        val yPos = validParams["y_position"] as Float

        val cardHeight = viewportHeight * 0.075f
        val cardWidth = viewportWidth * 0.38f
        val startY = viewportHeight * yPos
        val startX = xOffset

        // Container rounded card
        val containerPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(containerColor, alpha))
        val cardRect = RectF(startX, startY, startX + cardWidth, startY + cardHeight)
        val cornerRadius = cardHeight * 0.5f
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, containerPaint)

        // Circular badge
        val badgeRadius = cardHeight * 0.42f
        val badgeCx = startX + cornerRadius
        val badgeCy = startY + cardHeight * 0.5f

        val badgePaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(badgeColor, alpha))
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgePaint)

        // Initial letter
        val letterPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, alpha),
            textSize = badgeRadius * 1.1f,
            isBold = true
        )
        TemplateDrawUtils.drawCenteredText(canvas, badgeLetter, badgeCx, badgeCy, letterPaint)

        // Text content: Name and Handle
        val textStartX = startX + cornerRadius + badgeRadius + viewportWidth * 0.02f
        val namePaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, alpha),
            textSize = cardHeight * 0.35f,
            isBold = true
        )
        canvas.drawText(name, textStartX, startY + cardHeight * 0.44f, namePaint)

        val handlePaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xBBFFFFFFL, alpha),
            textSize = cardHeight * 0.28f,
            isBold = false
        )
        canvas.drawText(handle, textStartX, startY + cardHeight * 0.80f, handlePaint)
    }

    private fun evaluateSlide(t: Long, width: Int): Pair<Float, Float> {
        if (t <= 0L || t >= defaultDurationUs) return Pair(0.0f, -0.4f * width)

        val alpha: Float
        val xOffset: Float

        when {
            t < 700_000L -> {
                alpha = (t / 350_000.0f).coerceIn(0.0f, 1.0f)
                val springFrac = AnalyticalSpringSolver.solveAtTimestamp(220f, 0.68f, t, 0L, 700_000L)
                val startX = -0.40f * width
                val targetX = 0.06f * width
                xOffset = startX + (targetX - startX) * springFrac
            }
            t <= 3_400_000L -> {
                alpha = 1.0f
                xOffset = 0.06f * width
            }
            else -> {
                val outFrac = ((t - 3_400_000L) / 600_000.0f).coerceIn(0.0f, 1.0f)
                alpha = 1.0f - outFrac
                val startX = 0.06f * width
                val targetX = -0.45f * width
                val bezier = CubicBezierSolver.solve(0.4f, 0.0f, 1.0f, 1.0f, outFrac)
                xOffset = startX + (targetX - startX) * bezier
            }
        }

        return Pair(alpha.coerceIn(0.0f, 1.0f), xOffset)
    }
}
