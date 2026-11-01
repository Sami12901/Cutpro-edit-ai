package com.cutpro.core.subtitles.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.media3.effect.BitmapOverlay
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionStyle
import com.cutpro.core.subtitles.renderer.SubtitleCanvasRenderer
import java.io.Closeable

class SubtitleCanvasOverlay(
    private val cues: List<CaptionCue>,
    private val defaultStyle: CaptionStyle = CaptionStyle.DEFAULT,
    private val width: Int = 1920,
    private val height: Int = 1080
) : BitmapOverlay(), Closeable {

    private val renderer = SubtitleCanvasRenderer()
    private val lock = Any()

    @Volatile
    private var cachedBitmap: Bitmap? = null
    private var cachedCanvas: Canvas? = null

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

        renderer.renderAt(
            canvas = canvas,
            viewportWidth = width,
            viewportHeight = height,
            timestampUs = presentationTimeUs,
            cues = cues,
            defaultStyle = defaultStyle
        )

        return bitmap
    }

    /**
     * Releases native memory held by the cached Bitmap.
     */
    override fun release() = synchronized(lock) {
        cachedBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        cachedBitmap = null
        cachedCanvas = null
    }

    override fun close() {
        release()
    }
}
