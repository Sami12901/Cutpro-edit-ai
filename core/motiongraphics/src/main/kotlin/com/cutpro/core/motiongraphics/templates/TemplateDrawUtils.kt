package com.cutpro.core.motiongraphics.templates

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max

object TemplateDrawUtils {

    fun createTextPaint(
        color: Int,
        textSize: Float,
        isBold: Boolean = true,
        letterSpacing: Float = 0.0f
    ): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (letterSpacing > 0.0f) {
                this.letterSpacing = letterSpacing
            }
        }
    }

    fun createStrokePaint(
        color: Int,
        strokeWidth: Float,
        textSize: Float = 0.0f,
        isBold: Boolean = true
    ): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            if (textSize > 0.0f) {
                this.textSize = textSize
                this.typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
    }

    fun createFillPaint(color: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.FILL
        }
    }

    fun measureTextBounds(paint: Paint, text: String): Rect {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        return bounds
    }

    fun drawCenteredText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        paint: Paint,
        strokePaint: Paint? = null
    ) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val baseline = cy - bounds.exactCenterY()
        val startX = cx - bounds.exactCenterX()

        if (strokePaint != null) {
            canvas.drawText(text, startX, baseline, strokePaint)
        }
        canvas.drawText(text, startX, baseline, paint)
    }

    fun applyAlpha(color: Long, alphaMultiplier: Float): Int {
        val originalAlpha = ((color ushr 24) and 0xFF).toFloat()
        val newAlpha = (originalAlpha * alphaMultiplier.coerceIn(0.0f, 1.0f)).toInt()
        val rgb = (color and 0x00FFFFFFL).toInt()
        return (newAlpha shl 24) or rgb
    }

    fun applyAlpha(color: Int, alphaMultiplier: Float): Int {
        val originalAlpha = ((color ushr 24) and 0xFF).toFloat()
        val newAlpha = (originalAlpha * alphaMultiplier.coerceIn(0.0f, 1.0f)).toInt()
        val rgb = color and 0x00FFFFFF
        return (newAlpha shl 24) or rgb
    }
}
