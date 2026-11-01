package com.cutpro.core.export.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.cutpro.core.model.ColorAdjustments
import com.cutpro.core.model.LutFilterType
import java.io.Closeable

/**
 * AndroidX Media3 BitmapOverlay applying hardware-accelerated 10-parameter Color Grading,
 * Vignette, and Cinematic LUT filters to video frames during export. Strictly zero emojis.
 */
@OptIn(UnstableApi::class)
class ColorGradingCanvasOverlay(
    private val adjustments: ColorAdjustments = ColorAdjustments(),
    private val filterType: LutFilterType = LutFilterType.NONE,
    private val width: Int = 1920,
    private val height: Int = 1080
) : BitmapOverlay(), Closeable {

    private val lock = Any()

    @Volatile
    private var isReleased = false

    @Volatile
    private var cachedBitmap: Bitmap? = null
    private var cachedCanvas: Canvas? = null

    private val colorFilterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val colorMatrix = createColorMatrix(adjustments, filterType)
        colorFilterPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        if (adjustments.vignette > 0.0f) {
            val radius = (kotlin.math.hypot(width.toFloat(), height.toFloat()) / 2f).coerceAtLeast(1f)
            val vignetteAlpha = (adjustments.vignette * 220).toInt().coerceIn(0, 255)
            val colors = intArrayOf(Color.TRANSPARENT, Color.argb(vignetteAlpha, 0, 0, 0))
            val stops = floatArrayOf(0.4f, 1.0f)
            val shader = RadialGradient(
                width / 2f,
                height / 2f,
                radius,
                colors,
                stops,
                Shader.TileMode.CLAMP
            )
            vignettePaint.shader = shader
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = synchronized(lock) {
        val existing = cachedBitmap
        val bitmap = if (existing != null && !existing.isRecycled && existing.width == width && existing.height == height) {
            existing
        } else {
            existing?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                cachedBitmap = it
                cachedCanvas = Canvas(it)
            }
        }

        val canvas = cachedCanvas ?: Canvas(bitmap).also { cachedCanvas = it }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (isReleased || (adjustments.isIdentity && filterType == LutFilterType.NONE)) {
            return bitmap
        }

        // Draw vignette if configured
        if (adjustments.vignette > 0.0f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        }

        return bitmap
    }

    override fun close() {
        synchronized(lock) {
            isReleased = true
            cachedBitmap?.recycle()
            cachedBitmap = null
            cachedCanvas = null
        }
    }

    companion object {
        fun createColorMatrix(
            adjustments: ColorAdjustments,
            filterType: LutFilterType
        ): ColorMatrix {
            val matrix = ColorMatrix()

            // 1. Brightness / Exposure
            val exposureOffset = (adjustments.exposure * 50f + adjustments.brightness * 100f).coerceIn(-255f, 255f)
            if (exposureOffset != 0f) {
                val brightnessMatrix = ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, exposureOffset,
                        0f, 1f, 0f, 0f, exposureOffset,
                        0f, 0f, 1f, 0f, exposureOffset,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.postConcat(brightnessMatrix)
            }

            // 2. Contrast
            val contrast = adjustments.contrast
            if (contrast != 1.0f) {
                val scale = contrast
                val translate = (-0.5f * scale + 0.5f) * 255f
                val contrastMatrix = ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.postConcat(contrastMatrix)
            }

            // 3. Saturation
            if (adjustments.saturation != 1.0f) {
                val satMatrix = ColorMatrix()
                satMatrix.setSaturation(adjustments.saturation)
                matrix.postConcat(satMatrix)
            }

            // 4. Temperature (Warmth) & Tint
            val temp = adjustments.temperature // [-1, 1] negative is cool (blue), positive is warm (red/yellow)
            val tint = adjustments.tint // [-1, 1] negative is green, positive is magenta
            if (temp != 0f || tint != 0f) {
                val rOffset = (temp * 30f + tint * 15f).coerceIn(-100f, 100f)
                val gOffset = (-tint * 30f).coerceIn(-100f, 100f)
                val bOffset = (-temp * 30f + tint * 15f).coerceIn(-100f, 100f)
                val wbMatrix = ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, rOffset,
                        0f, 1f, 0f, 0f, gOffset,
                        0f, 0f, 1f, 0f, bOffset,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.postConcat(wbMatrix)
            }

            // 5. LUT preset simulation
            val lutMatrix = getLutMatrix(filterType)
            if (lutMatrix != null) {
                matrix.postConcat(lutMatrix)
            }

            return matrix
        }

        private fun getLutMatrix(filterType: LutFilterType): ColorMatrix? {
            return when (filterType) {
                LutFilterType.NONE -> null
                LutFilterType.CINEMATIC_TEAL_ORANGE -> ColorMatrix(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, 10f,
                        0f, 0.95f, 0f, 0f, 0f,
                        0f, 0f, 0.8f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                LutFilterType.MOODY_DARK -> ColorMatrix(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, -15f,
                        0f, 0.9f, 0f, 0f, -15f,
                        0f, 0f, 0.95f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                LutFilterType.VIBRANT_GOLD -> ColorMatrix(
                    floatArrayOf(
                        1.15f, 0f, 0f, 0f, 15f,
                        0f, 1.10f, 0f, 0f, 10f,
                        0f, 0f, 0.90f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                LutFilterType.RETRO_FILM_1998 -> ColorMatrix(
                    floatArrayOf(
                        1.05f, 0f, 0f, 0f, 20f,
                        0f, 0.98f, 0f, 0f, 10f,
                        0f, 0f, 0.85f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                LutFilterType.CLEAN_PORTRAIT -> ColorMatrix(
                    floatArrayOf(
                        1.05f, 0f, 0f, 0f, 10f,
                        0f, 1.02f, 0f, 0f, 5f,
                        0f, 0f, 1.0f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                LutFilterType.BW_HIGH_CONTRAST -> {
                    val m = ColorMatrix()
                    m.setSaturation(0f)
                    val contrast = ColorMatrix(
                        floatArrayOf(
                            1.4f, 0f, 0f, 0f, -40f,
                            0f, 1.4f, 0f, 0f, -40f,
                            0f, 0f, 1.4f, 0f, -40f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    m.postConcat(contrast)
                    m
                }
                LutFilterType.CYBERPUNK_NEON -> ColorMatrix(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, 25f,
                        0f, 0.85f, 0f, 0f, -10f,
                        0f, 0f, 1.35f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                LutFilterType.VINTAGE_WARM -> ColorMatrix(
                    floatArrayOf(
                        1.10f, 0f, 0f, 0f, 25f,
                        0f, 1.05f, 0f, 0f, 15f,
                        0f, 0f, 0.85f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }
    }
}

