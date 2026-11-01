package com.cutpro.core.motiongraphics.templates.kinetic

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

class DynamicBounceWordSlideTemplate : MotionGraphicsTemplate {
    override val id: String = "kinetic_dynamic_bounce"
    override val aliases: List<String> = listOf("kinetic_word_by_word_slide", "kinetic_word_slide", "word_by_word_slide")
    override val displayName: String = "Word-by-Word Vertical Conveyor"
    override val category: TemplateCategory = TemplateCategory.KINETIC_TYPOGRAPHY
    override val defaultDurationUs: Long = 3_000_000L

    override val schema: ParameterSchema = ParameterSchema(
        listOf(
            ParameterDefinition("sentence", "Animated Sentence", ParameterType.STRING, "CREATE WITH ABSOLUTE FREEDOM", minLength = 2, maxLength = 80, disallowEmpty = true),
            ParameterDefinition("slideOffsetPx", "Slide Offset Pixels", ParameterType.INT, 50, minValue = 10, maxValue = 200),
            ParameterDefinition("active_word_color", "Active Word Color", ParameterType.COLOR, 0xFFFFFFFFL),
            ParameterDefinition("inactive_word_color", "Inactive Word Color", ParameterType.COLOR, 0x55FFFFFFL),
            ParameterDefinition("word_transition_us", "Word Transition Microseconds", ParameterType.INT, 500_000, minValue = 200_000, maxValue = 800_000)
        )
    )

    override fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState {
        val validParams = schema.validate(params)
        val sentence = validParams["sentence"].toString()
        val words = sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val activeColor = (validParams["active_word_color"] as Long).toInt()
        val inactiveColor = (validParams["inactive_word_color"] as Long).toInt()

        val count = words.size.coerceAtLeast(1)
        val slotDuration = defaultDurationUs / count
        val activeIndex = (timestampUs / slotDuration).toInt().coerceIn(0, count - 1)

        val layers = mutableListOf<RenderLayerState>()
        for (i in words.indices) {
            val isActive = i == activeIndex
            val scale = if (isActive) 1.15f else 0.90f
            val alpha = if (isActive) 1.0f else 0.45f
            val color = if (isActive) activeColor else inactiveColor

            layers.add(
                RenderLayerState.TextLayer(
                    id = "word_$i",
                    transform = TrackItemTransform(
                        positionX = viewportWidth * 0.5f,
                        positionY = viewportHeight * (0.45f + (i - activeIndex) * 0.08f),
                        scaleX = scale,
                        scaleY = scale,
                        opacity = alpha
                    ),
                    text = words[i],
                    textColor = TemplateDrawUtils.applyAlpha(color, alpha)
                )
            )
        }

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
        val sentence = validParams["sentence"].toString()
        val words = sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return

        val count = words.size
        val slotDuration = defaultDurationUs / count
        val activeIndex = (timestampUs / slotDuration).toInt().coerceIn(0, count - 1)
        val slotElapsed = timestampUs % slotDuration

        val activeColor = validParams["active_word_color"] as Long
        val inactiveColor = validParams["inactive_word_color"] as Long
        val slideOffset = (validParams["slideOffsetPx"] as? Number)?.toFloat() ?: 50.0f

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f
        val textSize = viewportHeight * 0.06f

        // Transition progress within the current word slot
        val transitionProgress = if (slotElapsed < 250_000L) {
            CubicBezierSolver.solve(0.2f, 0.9f, 0.3f, 1.0f, slotElapsed / 250_000.0f)
        } else {
            1.0f
        }

        for (i in words.indices) {
            val offsetFromActive = i - activeIndex
            val baseY = cy + offsetFromActive * textSize * 1.5f - (1.0f - transitionProgress) * slideOffset
            val isActive = (i == activeIndex)

            val wordScale = if (isActive) {
                0.85f + 0.30f * transitionProgress
            } else {
                0.90f
            }

            val wordAlpha = if (isActive) 1.0f else 0.4f
            val wordColor = if (isActive) activeColor else inactiveColor

            val paint = TemplateDrawUtils.createTextPaint(
                color = TemplateDrawUtils.applyAlpha(wordColor, wordAlpha),
                textSize = textSize * wordScale,
                isBold = isActive
            )

            TemplateDrawUtils.drawCenteredText(canvas, words[i], cx, baseY, paint)
        }
    }
}
