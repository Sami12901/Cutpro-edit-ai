package com.cutpro.core.subtitles.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cutpro.core.subtitles.export.SubtitleCanvasOverlay
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionStyle
import com.cutpro.core.subtitles.model.TimedWord
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SubtitleCanvasRendererTest {

    private val cues = listOf(
        CaptionCue(
            startTimeUs = 1_000_000L,
            endTimeUs = 4_000_000L,
            text = "Clean Native Subtitles",
            words = listOf(
                TimedWord("Clean", 1_000_000L, 2_000_000L),
                TimedWord("Native", 2_000_000L, 3_000_000L),
                TimedWord("Subtitles", 3_000_000L, 4_000_000L)
            )
        )
    )

    @Test
    fun subtitleCanvasRenderer_rendersAtTimestampWithoutCrashing() {
        val renderer = SubtitleCanvasRenderer()
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Render before cue
        renderer.renderAt(canvas, 1280, 720, 500_000L, cues)

        // Render during cue (word 1 active)
        renderer.renderAt(canvas, 1280, 720, 1_500_000L, cues, CaptionStyle.KARAOKE_POP)

        // Render during cue (word 2 active)
        renderer.renderAt(canvas, 1280, 720, 2_500_000L, cues, CaptionStyle.HIGHLIGHT_BOX)

        // Render after cue
        renderer.renderAt(canvas, 1280, 720, 5_000_000L, cues)
    }

    @Test
    fun subtitleCanvasOverlay_generatesBitmapAtTimestamp() {
        val overlay = SubtitleCanvasOverlay(
            cues = cues,
            defaultStyle = CaptionStyle.KARAOKE_POP,
            width = 1280,
            height = 720
        )

        val frameBitmap = overlay.getBitmap(1_500_000L)
        assertThat(frameBitmap).isNotNull()
        assertThat(frameBitmap.width).isEqualTo(1280)
        assertThat(frameBitmap.height).isEqualTo(720)
    }

    @Test
    fun captionCue_positionalConstructor_ergonomics() {
        // Test 1: 3-argument positional constructor
        val cue3 = CaptionCue(1_000_000L, 2_000_000L, "Positional Text")
        assertThat(cue3.startTimeUs).isEqualTo(1_000_000L)
        assertThat(cue3.endTimeUs).isEqualTo(2_000_000L)
        assertThat(cue3.text).isEqualTo("Positional Text")
        assertThat(cue3.id).isNotEmpty()

        // Test 2: 4-argument positional constructor with words
        val words = listOf(TimedWord("Positional", 1_000_000L, 1_500_000L))
        val cue4 = CaptionCue(1_000_000L, 2_000_000L, "Positional Text", words)
        assertThat(cue4.words).hasSize(1)
        assertThat(cue4.words[0].word).isEqualTo("Positional")
    }

    @Test
    fun subtitleCanvasRenderer_explicitNewlines_rendersMultiline() {
        val renderer = SubtitleCanvasRenderer()
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val multilineCue = CaptionCue(
            startTimeUs = 1_000_000L,
            endTimeUs = 3_000_000L,
            text = "Line One Subtitle\nLine Two Subtitle\nLine Three Subtitle"
        )

        renderer.renderAt(canvas, 1280, 720, 1_500_000L, listOf(multilineCue))
    }

    @Test
    fun subtitleCanvasRenderer_karaokeWords_wrapGreedilyAcrossLines() {
        val renderer = SubtitleCanvasRenderer()
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Generate 25 words that exceed 600px width
        val wordTokens = (1..25).map { "Word$it" }
        val timedWords = wordTokens.mapIndexed { idx, w ->
            TimedWord(w, (idx * 100_000L), ((idx + 1) * 100_000L))
        }
        val longCue = CaptionCue(
            startTimeUs = 0L,
            endTimeUs = 2_500_000L,
            text = wordTokens.joinToString(" "),
            words = timedWords,
            customStyle = CaptionStyle.KARAOKE_POP
        )

        // Render at timestamp where Word 15 is active (wrapped onto line 2 or 3)
        renderer.renderAt(canvas, 600, 400, 1_550_000L, listOf(longCue))
    }

    @Test
    fun subtitleCanvasOverlay_threadSafetyAndRecycle() {
        val cue = CaptionCue(1_000_000L, 2_000_000L, "Overlay Thread Test")
        val overlay = SubtitleCanvasOverlay(listOf(cue), width = 640, height = 360)

        // Multiple concurrent calls must succeed
        val threads = (1..4).map {
            Thread {
                val bmp = overlay.getBitmap(1_500_000L)
                assertThat(bmp.width).isEqualTo(640)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Test release / close
        overlay.release()
        overlay.close()

        // Subsequent call reallocates safely
        val freshBmp = overlay.getBitmap(1_500_000L)
        assertThat(freshBmp).isNotNull()
        assertThat(freshBmp.isRecycled).isFalse()
    }
}
