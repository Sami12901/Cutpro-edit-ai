package com.cutpro.core.export.overlay

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import com.cutpro.core.subtitles.export.SubtitleCanvasOverlay
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionStyle
import com.google.common.collect.ImmutableList
import java.io.Closeable

/**
 * Bundles MotionGraphicsCanvasOverlay and SubtitleCanvasOverlay in strict FIFO order:
 * 1. MotionGraphicsCanvasOverlay is composited first onto the video frames.
 * 2. SubtitleCanvasOverlay is composited second (on top of motion graphics).
 * Strictly zero emojis.
 */
@OptIn(UnstableApi::class)
class CompositionCanvasOverlay(
    val motionGraphicsOverlay: MotionGraphicsCanvasOverlay? = null,
    val subtitleOverlay: SubtitleCanvasOverlay? = null
) : Closeable {

    fun getTextureOverlays(): List<TextureOverlay> {
        val overlays = mutableListOf<TextureOverlay>()
        motionGraphicsOverlay?.let { overlays.add(it) }
        subtitleOverlay?.let { overlays.add(it) }
        return overlays
    }

    fun toOverlayEffect(): OverlayEffect? {
        val overlays = getTextureOverlays()
        return if (overlays.isNotEmpty()) {
            OverlayEffect(ImmutableList.copyOf(overlays))
        } else {
            null
        }
    }

    override fun close() {
        motionGraphicsOverlay?.close()
        subtitleOverlay?.close()
    }

    companion object {
        fun create(
            width: Int,
            height: Int,
            motionItems: List<MotionGraphicsTimelineItem> = emptyList(),
            captionCues: List<CaptionCue> = emptyList(),
            captionStyle: CaptionStyle = CaptionStyle.DEFAULT
        ): CompositionCanvasOverlay {
            val mgOverlay = if (motionItems.isNotEmpty()) {
                MotionGraphicsCanvasOverlay(items = motionItems, width = width, height = height)
            } else {
                null
            }

            val subOverlay = if (captionCues.isNotEmpty()) {
                SubtitleCanvasOverlay(cues = captionCues, defaultStyle = captionStyle, width = width, height = height)
            } else {
                null
            }

            return CompositionCanvasOverlay(
                motionGraphicsOverlay = mgOverlay,
                subtitleOverlay = subOverlay
            )
        }
    }
}
