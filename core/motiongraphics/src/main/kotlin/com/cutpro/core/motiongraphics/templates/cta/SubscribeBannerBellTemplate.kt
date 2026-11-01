package com.cutpro.core.motiongraphics.templates.cta

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.cutpro.core.motiongraphics.evaluator.AnalyticalSpringSolver
import com.cutpro.core.motiongraphics.evaluator.EasingEvaluator
import com.cutpro.core.motiongraphics.evaluator.LinearRgbColorInterpolator
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
import kotlin.math.exp
import kotlin.math.sin

class SubscribeBannerBellTemplate : MotionGraphicsTemplate {
    override val id: String = "cta_subscribe_banner"
    override val aliases: List<String> = listOf("cta_subscribe_bell", "subscribe_bell")
    override val displayName: String = "Subscribe Pill with Notification Bell"
    override val category: TemplateCategory = TemplateCategory.CALL_TO_ACTIONS
    override val defaultDurationUs: Long = 4_500_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("cta_text_initial", "Initial Text", ParameterType.STRING, "SUBSCRIBE", minLength = 1, maxLength = 20, disallowEmpty = true),
            ParameterDefinition("buttonText", "Button Text Alias", ParameterType.STRING, "SUBSCRIBE", minLength = 1, maxLength = 20, disallowEmpty = true),
            ParameterDefinition("cta_text_clicked", "Clicked Text", ParameterType.STRING, "SUBSCRIBED", minLength = 1, maxLength = 20, disallowEmpty = true),
            ParameterDefinition("initial_btn_color", "Initial Button Color", ParameterType.COLOR, 0xFFFF0000L),
            ParameterDefinition("clicked_btn_color", "Clicked Button Color", ParameterType.COLOR, 0xFF333333L),
            ParameterDefinition("bell_color", "Bell Color", ParameterType.COLOR, 0xFFFFD600L),
            ParameterDefinition("y_position", "Y Position", ParameterType.FLOAT, 0.78f, minValue = 0.50f, maxValue = 0.90f),
            ParameterDefinition("cornerRadius", "Corner Radius", ParameterType.FLOAT, 24.0f, minValue = 12.0f, maxValue = 50.0f)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val initialText = if (params.containsKey("buttonText")) validParams["buttonText"].toString() else validParams["cta_text_initial"].toString()
        val clickedText = validParams["cta_text_clicked"].toString()
        val yPos = validParams["y_position"] as Float

        val isClicked = timestampUs >= 1_950_000L
        val text = if (isClicked) clickedText else initialText

        val alpha = when {
            timestampUs <= 0L || timestampUs >= defaultDurationUs -> 0.0f
            timestampUs > 4_000_000L -> (defaultDurationUs - timestampUs) / 500_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val layers = mutableListOf<RenderLayerState>()
        if (alpha > 0.0f) {
            layers.add(
                RenderLayerState.TextLayer(
                    id = "subscribe_text",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * yPos,
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
            timestampUs > 4_000_000L -> (defaultDurationUs - timestampUs) / 500_000.0f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        if (alpha <= 0.0f) return

        val initialText = if (params.containsKey("buttonText")) validParams["buttonText"].toString() else validParams["cta_text_initial"].toString()
        val clickedText = validParams["cta_text_clicked"].toString()
        val initialColor = validParams["initial_btn_color"] as Long
        val clickedColor = validParams["clicked_btn_color"] as Long
        val bellColor = validParams["bell_color"] as Long
        val yPos = validParams["y_position"] as Float
        val cornerRadius = validParams["cornerRadius"] as Float

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * yPos

        // Button entry scale & press scale
        val buttonScale = when {
            timestampUs < 600_000L -> AnalyticalSpringSolver.solveAtTimestamp(280f, 0.62f, timestampUs, 0L, 600_000L)
            timestampUs in 1_800_000L..2_050_000L -> {
                val frac = (timestampUs - 1_800_000L) / 250_000.0f
                1.0f - 0.08f * sin(frac * Math.PI.toFloat())
            }
            else -> 1.0f
        }.coerceIn(0.0f, 1.2f)

        if (buttonScale <= 0.0f) return

        // Color transition
        val currentColor = when {
            timestampUs < 1_900_000L -> initialColor
            timestampUs in 1_900_000L..2_100_000L -> {
                val frac = (timestampUs - 1_900_000L) / 200_000.0f
                LinearRgbColorInterpolator.interpolate(initialColor, clickedColor, frac)
            }
            else -> clickedColor
        }

        val btnWidth = viewportWidth * 0.32f * buttonScale
        val btnHeight = viewportHeight * 0.06f * buttonScale

        val btnRect = RectF(
            cx - btnWidth * 0.5f,
            cy - btnHeight * 0.5f,
            cx + btnWidth * 0.5f,
            cy + btnHeight * 0.5f
        )
        val btnPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(currentColor, alpha))
        canvas.drawRoundRect(btnRect, cornerRadius * buttonScale, cornerRadius * buttonScale, btnPaint)

        // Text
        val isClicked = timestampUs >= 1_950_000L
        val text = if (isClicked) clickedText else initialText
        val textSize = btnHeight * 0.42f
        val textPaint = TemplateDrawUtils.createTextPaint(
            color = TemplateDrawUtils.applyAlpha(0xFFFFFFFFL, alpha),
            textSize = textSize,
            isBold = true,
            letterSpacing = 0.05f
        )

        val bellSpacing = if (isClicked) btnHeight * 0.45f else 0.0f
        TemplateDrawUtils.drawCenteredText(canvas, text, cx - bellSpacing * 0.5f, cy, textPaint)

        // Bell animation after click
        if (isClicked && timestampUs in 2_100_000L..2_800_000L) {
            val bellTimeSec = (timestampUs - 2_100_000L) / 700_000.0f
            val bellAngle = 25.0f * exp(-5.0f * bellTimeSec) * sin(2.0f * Math.PI.toFloat() * 6.0f * bellTimeSec)

            val bellCx = cx + btnWidth * 0.38f
            val bellCy = cy

            canvas.save()
            canvas.rotate(bellAngle, bellCx, bellCy - btnHeight * 0.2f)

            val bellPaint = TemplateDrawUtils.createFillPaint(TemplateDrawUtils.applyAlpha(bellColor, alpha))
            val bellRadius = btnHeight * 0.18f
            canvas.drawCircle(bellCx, bellCy, bellRadius, bellPaint)

            canvas.restore()
        }
    }
}
