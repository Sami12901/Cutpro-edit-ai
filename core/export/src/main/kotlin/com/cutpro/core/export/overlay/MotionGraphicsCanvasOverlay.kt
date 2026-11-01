package com.cutpro.core.export.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.cutpro.core.motiongraphics.model.AnimationLoopMode
import com.cutpro.core.motiongraphics.model.TemplateInstance
import com.cutpro.core.motiongraphics.renderer.MotionGraphicsCanvasRenderer
import java.io.Closeable

/**
 * Timeline placement specification for a motion graphics template instance.
 */
data class MotionGraphicsTimelineItem(
    val templateInstance: TemplateInstance,
    val timelineStartUs: Long,
    val timelineDurationUs: Long,
    val loopMode: AnimationLoopMode = AnimationLoopMode.ONCE,
    val zIndex: Int = 0
) {
    val timelineEndUs: Long get() = timelineStartUs + timelineDurationUs

    fun isActiveAt(timestampUs: Long): Boolean =
        timestampUs in timelineStartUs until timelineEndUs

    fun calculateLocalTimeUs(timestampUs: Long): Long {
        val elapsed = timestampUs - timelineStartUs
        return when (loopMode) {
            AnimationLoopMode.ONCE -> elapsed.coerceIn(0L, templateInstance.durationUs)
            AnimationLoopMode.LOOP -> if (templateInstance.durationUs > 0) {
                elapsed % templateInstance.durationUs
            } else {
                elapsed
            }
            AnimationLoopMode.PING_PONG -> {
                val duration = templateInstance.durationUs
                if (duration > 0) {
                    val cycle = elapsed / duration
                    val rem = elapsed % duration
                    if (cycle % 2L == 0L) rem else duration - rem
                } else {
                    elapsed
                }
            }
        }
    }
}

/**
 * Media3 BitmapOverlay rendering deterministic motion graphics templates per export frame.
 * Single-bitmap recycling and canvas state isolation. Strictly zero emojis.
 */
@OptIn(UnstableApi::class)
class MotionGraphicsCanvasOverlay(
    private val items: List<MotionGraphicsTimelineItem>,
    private val width: Int = 1920,
    private val height: Int = 1080
) : BitmapOverlay(), Closeable {

    private val renderer = MotionGraphicsCanvasRenderer()
    private val lock = Any()

    @Volatile
    private var isReleased = false

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

        if (isReleased || items.isEmpty()) {
            return bitmap
        }

        val activeItems = items.filter { it.isActiveAt(presentationTimeUs) }.sortedBy { it.zIndex }
        if (activeItems.isEmpty()) {
            return bitmap
        }

        for (item in activeItems) {
            val localTimeUs = item.calculateLocalTimeUs(presentationTimeUs)
            val saveCount = canvas.save()
            try {
                renderer.renderAt(
                    canvas = canvas,
                    viewportWidth = width,
                    viewportHeight = height,
                    presentationTimestampUs = localTimeUs,
                    templateInstance = item.templateInstance
                )
            } finally {
                canvas.restoreToCount(saveCount)
            }
        }

        return bitmap
    }

    override fun release() = synchronized(lock) {
        isReleased = true
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
