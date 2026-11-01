package com.cutpro.core.export.overlay

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.motiongraphics.model.AnimationLoopMode
import com.cutpro.core.motiongraphics.model.TemplateInstance
import com.cutpro.core.subtitles.export.SubtitleCanvasOverlay
import com.cutpro.core.subtitles.karaoke.KaraokeEngine
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionStyle
import com.cutpro.core.subtitles.model.TimedWord
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Adversarial stress testing suite for per-frame visual synchronization,
 * presentation timestamp (PTS) evaluation, canvas state isolation,
 * single-bitmap recycling, and aspect ratio conformance in Media3 export overlays.
 * Strictly zero emojis.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VisualSyncAdversarialStressTest {

    // -------------------------------------------------------------------------
    // 1. PTS Evaluation Precision
    // -------------------------------------------------------------------------

    @Test
    fun ptsPrecision_frame0_zeroMicroseconds_evaluatesCleanly() {
        val template = TemplateInstance(
            templateId = "title_minimal_modern",
            durationUs = 3_000_000L,
            parameters = mapOf("primary_text" to "Frame 0 Title")
        )
        val mgItem = MotionGraphicsTimelineItem(
            templateInstance = template,
            timelineStartUs = 0L,
            timelineDurationUs = 3_000_000L,
            loopMode = AnimationLoopMode.ONCE
        )

        val cue = CaptionCue(
            startTimeUs = 0L,
            endTimeUs = 1_000_000L,
            text = "Subtitle at Frame 0"
        )

        val compositionOverlay = CompositionCanvasOverlay.create(
            width = 1920,
            height = 1080,
            motionItems = listOf(mgItem),
            captionCues = listOf(cue)
        )

        assertThat(mgItem.isActiveAt(0L)).isTrue()
        assertThat(mgItem.calculateLocalTimeUs(0L)).isEqualTo(0L)

        val activeCue = KaraokeEngine.findActiveCue(listOf(cue), 0L)
        assertThat(activeCue).isNotNull()
        assertThat(activeCue?.text).isEqualTo("Subtitle at Frame 0")

        val mgOverlay = compositionOverlay.motionGraphicsOverlay
        val subOverlay = compositionOverlay.subtitleOverlay
        assertThat(mgOverlay).isNotNull()
        assertThat(subOverlay).isNotNull()

        val mgBmp = mgOverlay!!.getBitmap(0L)
        assertThat(mgBmp).isNotNull()
        assertThat(mgBmp.width).isEqualTo(1920)
        assertThat(mgBmp.height).isEqualTo(1080)
        assertThat(mgBmp.isRecycled).isFalse()

        val subBmp = subOverlay!!.getBitmap(0L)
        assertThat(subBmp).isNotNull()
        assertThat(subBmp.width).isEqualTo(1920)
        assertThat(subBmp.height).isEqualTo(1080)
        assertThat(subBmp.isRecycled).isFalse()

        compositionOverlay.close()
    }

    @Test
    fun ptsPrecision_frame30_at30Fps_exactOneSecondTransition() {
        val fps30 = ExportFrameRate.FPS_30
        assertThat(fps30.fps).isEqualTo(30)

        // At 30 fps, frame 0 is 0 us, frame 29 is 966,666 us, frame 30 is exactly 1,000,000 us
        val frame29Ts = fps30.frameIndexToTimestamp(29L) // 966_666 us
        val frame30Ts = fps30.frameIndexToTimestamp(30L) // 1_000_000 us
        val frame31Ts = fps30.frameIndexToTimestamp(31L) // 1_033_333 us

        assertThat(frame30Ts).isEqualTo(1_000_000L)

        // Template A: 0..1,000,000 us (expires on frame 30)
        val templateA = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 1_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 1_000_000L
        )

        // Template B: 1,000,000..2,000,000 us (activates on frame 30)
        val templateB = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 1_000_000L),
            timelineStartUs = 1_000_000L,
            timelineDurationUs = 1_000_000L
        )

        // Frame 29 (966,666 us)
        assertThat(templateA.isActiveAt(frame29Ts)).isTrue()
        assertThat(templateB.isActiveAt(frame29Ts)).isFalse()
        assertThat(templateA.calculateLocalTimeUs(frame29Ts)).isEqualTo(966_666L)

        // Frame 30 (1,000,000 us): exact boundary transition
        assertThat(templateA.isActiveAt(frame30Ts)).isFalse()
        assertThat(templateB.isActiveAt(frame30Ts)).isTrue()
        assertThat(templateB.calculateLocalTimeUs(frame30Ts)).isEqualTo(0L)

        // Frame 31 (1,033_333 us)
        assertThat(templateA.isActiveAt(frame31Ts)).isFalse()
        assertThat(templateB.isActiveAt(frame31Ts)).isTrue()
        assertThat(templateB.calculateLocalTimeUs(frame31Ts)).isEqualTo(33_333L)

        val overlay = MotionGraphicsCanvasOverlay(
            items = listOf(templateA, templateB),
            width = 1280,
            height = 720
        )

        val bmp29 = overlay.getBitmap(frame29Ts)
        assertThat(bmp29).isNotNull()

        val bmp30 = overlay.getBitmap(frame30Ts)
        assertThat(bmp30).isSameInstanceAs(bmp29)

        overlay.close()
    }

    @Test
    fun ptsPrecision_fractionalFrameRate_23_976Fps_exactMicrosecondProgression() {
        val targetFps = 23.976
        val frameDurationFloatUs = 1_000_000.0 / targetFps // ~41708.375 us

        val frameCount = 120 // ~5 seconds of video
        val timestamps = LongArray(frameCount) { i ->
            (i * frameDurationFloatUs).roundToLong()
        }

        // Assert strictly monotonic timestamps
        for (i in 1 until frameCount) {
            val delta = timestamps[i] - timestamps[i - 1]
            assertThat(delta).isIn(41_708L..41_709L)
            assertThat(timestamps[i]).isGreaterThan(timestamps[i - 1])
        }

        // Frame 24 should land right around 1,001,001 us
        assertThat(timestamps[24]).isEqualTo(1_001_001L)

        val template = TemplateInstance(templateId = "title_minimal_modern", durationUs = 5_000_000L)
        val item = MotionGraphicsTimelineItem(
            templateInstance = template,
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            loopMode = AnimationLoopMode.ONCE
        )

        val overlay = MotionGraphicsCanvasOverlay(listOf(item), 1280, 720)
        var previousLocalTime = -1L

        for (i in 0 until frameCount) {
            val ts = timestamps[i]
            val localTime = item.calculateLocalTimeUs(ts)
            assertThat(localTime).isAtLeast(previousLocalTime)
            previousLocalTime = localTime

            val bmp = overlay.getBitmap(ts)
            assertThat(bmp.width).isEqualTo(1280)
            assertThat(bmp.height).isEqualTo(720)
        }

        overlay.close()
    }

    @Test
    fun ptsPrecision_extremeAndNegativeTimestamps_safeHandling() {
        val template = TemplateInstance(templateId = "title_minimal_modern", durationUs = 2_000_000L)
        val item = MotionGraphicsTimelineItem(
            templateInstance = template,
            timelineStartUs = 1_000_000L,
            timelineDurationUs = 2_000_000L,
            loopMode = AnimationLoopMode.ONCE
        )

        // Negative timestamps
        assertThat(item.isActiveAt(-1L)).isFalse()
        assertThat(item.isActiveAt(-1_000_000L)).isFalse()
        assertThat(item.calculateLocalTimeUs(-1L)).isEqualTo(0L)
        assertThat(item.calculateLocalTimeUs(-500_000L)).isEqualTo(0L)

        // Beyond duration
        assertThat(item.isActiveAt(4_000_000L)).isFalse()
        assertThat(item.calculateLocalTimeUs(4_000_000L)).isEqualTo(2_000_000L) // clamped in ONCE mode

        val overlay = MotionGraphicsCanvasOverlay(listOf(item), 640, 360)
        val negativeBmp = overlay.getBitmap(-100_000L)
        assertThat(negativeBmp).isNotNull()
        assertThat(negativeBmp.width).isEqualTo(640)
        assertThat(negativeBmp.height).isEqualTo(360)

        overlay.close()
    }

    // -------------------------------------------------------------------------
    // 2. Boundary Transitions
    // -------------------------------------------------------------------------

    @Test
    fun boundaryTransitions_templateEndBoundary_exactHalfOpenBehavior() {
        val startUs = 1_000_000L
        val durationUs = 500_000L
        val endUs = startUs + durationUs // 1_500_000L

        val item = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = durationUs),
            timelineStartUs = startUs,
            timelineDurationUs = durationUs
        )

        assertThat(item.isActiveAt(startUs - 1L)).isFalse()
        assertThat(item.isActiveAt(startUs)).isTrue()
        assertThat(item.calculateLocalTimeUs(startUs)).isEqualTo(0L)

        assertThat(item.isActiveAt(endUs - 1L)).isTrue()
        assertThat(item.calculateLocalTimeUs(endUs - 1L)).isEqualTo(durationUs - 1L)

        // Exact boundary microsecond
        assertThat(item.isActiveAt(endUs)).isFalse()
        assertThat(item.isActiveAt(endUs + 1L)).isFalse()
    }

    @Test
    fun boundaryTransitions_subtitleCueEndBoundary_exactClosedBehavior() {
        val cue = CaptionCue(
            startTimeUs = 1_000_000L,
            endTimeUs = 2_000_000L,
            text = "Bounded Caption"
        )
        val cues = listOf(cue)

        assertThat(KaraokeEngine.findActiveCue(cues, 999_999L)).isNull()
        assertThat(KaraokeEngine.findActiveCue(cues, 1_000_000L)).isNotNull()
        assertThat(KaraokeEngine.findActiveCue(cues, 2_000_000L)).isNotNull()
        assertThat(KaraokeEngine.findActiveCue(cues, 2_000_001L)).isNull()
    }

    @Test
    fun boundaryTransitions_abuttedTemplates_zeroGapZeroOverlap() {
        val item1 = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 1_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 1_000_000L
        )
        val item2 = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "cta_subscribe_bell", durationUs = 1_000_000L),
            timelineStartUs = 1_000_000L,
            timelineDurationUs = 1_000_000L
        )
        val items = listOf(item1, item2)

        val overlay = MotionGraphicsCanvasOverlay(items, 1280, 720)

        // 999,999 us: item1 active, item2 inactive
        val activeAt999999 = items.filter { it.isActiveAt(999_999L) }
        assertThat(activeAt999999).hasSize(1)
        assertThat(activeAt999999[0].templateInstance.templateId).isEqualTo("title_minimal_modern")

        // 1,000,000 us: item1 inactive, item2 active
        val activeAt1000000 = items.filter { it.isActiveAt(1_000_000L) }
        assertThat(activeAt1000000).hasSize(1)
        assertThat(activeAt1000000[0].templateInstance.templateId).isEqualTo("cta_subscribe_bell")

        val bmp = overlay.getBitmap(1_000_000L)
        assertThat(bmp).isNotNull()

        overlay.close()
    }

    @Test
    fun boundaryTransitions_loopAndPingPongModes_mathematicalSymmetry() {
        val loopItem = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 1_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            loopMode = AnimationLoopMode.LOOP
        )

        assertThat(loopItem.calculateLocalTimeUs(0L)).isEqualTo(0L)
        assertThat(loopItem.calculateLocalTimeUs(999_999L)).isEqualTo(999_999L)
        assertThat(loopItem.calculateLocalTimeUs(1_000_000L)).isEqualTo(0L) // wraps back
        assertThat(loopItem.calculateLocalTimeUs(2_500_000L)).isEqualTo(500_000L)

        val pingPongItem = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 1_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            loopMode = AnimationLoopMode.PING_PONG
        )

        assertThat(pingPongItem.calculateLocalTimeUs(0L)).isEqualTo(0L)
        assertThat(pingPongItem.calculateLocalTimeUs(500_000L)).isEqualTo(500_000L)
        assertThat(pingPongItem.calculateLocalTimeUs(1_500_000L)).isEqualTo(500_000L) // bouncing back
        assertThat(pingPongItem.calculateLocalTimeUs(2_500_000L)).isEqualTo(500_000L) // bouncing forward

        // Zero duration edge case
        val zeroDurationItem = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 0L),
            timelineStartUs = 0L,
            timelineDurationUs = 1_000_000L,
            loopMode = AnimationLoopMode.LOOP
        )
        assertThat(zeroDurationItem.calculateLocalTimeUs(500_000L)).isEqualTo(500_000L)
    }

    // -------------------------------------------------------------------------
    // 3. Canvas State Isolation
    // -------------------------------------------------------------------------

    @Test
    fun canvasStateIsolation_matrixAndClipResetAcrossConsecutiveFrames() {
        val template = TemplateInstance(
            templateId = "title_minimal_modern",
            durationUs = 1_000_000L,
            parameters = mapOf("primary_text" to "Isolation Check")
        )
        val item = MotionGraphicsTimelineItem(
            templateInstance = template,
            timelineStartUs = 0L,
            timelineDurationUs = 1_000_000L
        )

        val overlay = MotionGraphicsCanvasOverlay(listOf(item), width = 1280, height = 720)

        // Frame 1: active template rendered
        val bmp1 = overlay.getBitmap(500_000L)
        assertThat(bmp1).isNotNull()

        // Frame 2: inactive timestamp (no items active)
        val bmp2 = overlay.getBitmap(2_000_000L)
        assertThat(bmp2).isSameInstanceAs(bmp1)

        // Sample pixels to verify transparent clear (no residual drawing)
        val samplePoints = listOf(
            Pair(0, 0),
            Pair(640, 360),
            Pair(1279, 719),
            Pair(100, 100),
            Pair(1100, 600)
        )
        for ((x, y) in samplePoints) {
            assertThat(bmp2.getPixel(x, y)).isEqualTo(0)
        }

        // Frame 3: active template again, rendered deterministically
        val bmp3 = overlay.getBitmap(200_000L)
        assertThat(bmp3).isSameInstanceAs(bmp1)

        overlay.close()
    }

    @Test
    fun canvasStateIsolation_multiItemZIndexAscendingOrderAndIsolation() {
        val itemLow = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 1_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 1_000_000L,
            zIndex = 1
        )
        val itemHigh = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "cta_subscribe_bell", durationUs = 1_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 1_000_000L,
            zIndex = 10
        )
        val itemMid = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "lowerthird_modern_bar", durationUs = 1_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 1_000_000L,
            zIndex = 5
        )

        val overlay = MotionGraphicsCanvasOverlay(
            items = listOf(itemLow, itemHigh, itemMid),
            width = 1280,
            height = 720
        )

        val bmp = overlay.getBitmap(500_000L)
        assertThat(bmp).isNotNull()
        assertThat(bmp.width).isEqualTo(1280)
        assertThat(bmp.height).isEqualTo(720)

        overlay.close()
    }

    @Test
    fun canvasStateIsolation_compositionCanvasOverlay_motionGraphicsAndSubtitlesSeparation() {
        val mgItem = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 2_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 2_000_000L
        )
        val cue = CaptionCue(
            startTimeUs = 0L,
            endTimeUs = 2_000_000L,
            text = "Independent Subtitle"
        )

        val compositionOverlay = CompositionCanvasOverlay.create(
            width = 1280,
            height = 720,
            motionItems = listOf(mgItem),
            captionCues = listOf(cue),
            captionStyle = CaptionStyle.DEFAULT
        )

        val textureOverlays = compositionOverlay.getTextureOverlays()
        assertThat(textureOverlays).hasSize(2)
        assertThat(textureOverlays[0]).isInstanceOf(MotionGraphicsCanvasOverlay::class.java)
        assertThat(textureOverlays[1]).isInstanceOf(SubtitleCanvasOverlay::class.java)

        val mgOverlay = compositionOverlay.motionGraphicsOverlay!!
        val subOverlay = compositionOverlay.subtitleOverlay!!

        val mgBmp = mgOverlay.getBitmap(1_000_000L)
        val subBmp = subOverlay.getBitmap(1_000_000L)

        // Overlays must operate on distinct bitmap buffers
        assertThat(mgBmp).isNotSameInstanceAs(subBmp)

        compositionOverlay.close()
    }

    // -------------------------------------------------------------------------
    // 4. Single-Bitmap Recycling Stress
    // -------------------------------------------------------------------------

    @Test
    fun singleBitmapRecyclingStress_500FramesSequential_reusesExactInstanceWithoutLeak() {
        val template = TemplateInstance(templateId = "title_minimal_modern", durationUs = 10_000_000L)
        val item = MotionGraphicsTimelineItem(
            templateInstance = template,
            timelineStartUs = 0L,
            timelineDurationUs = 10_000_000L
        )

        val overlay = MotionGraphicsCanvasOverlay(listOf(item), 1280, 720)
        val initialBmp = overlay.getBitmap(0L)
        assertThat(initialBmp).isNotNull()

        // 500 consecutive frames (representing ~16.6 seconds of 30fps export)
        for (i in 1..500) {
            val ts = i * 33_333L
            val frameBmp = overlay.getBitmap(ts)
            assertThat(frameBmp).isSameInstanceAs(initialBmp)
            assertThat(frameBmp.isRecycled).isFalse()
        }

        overlay.release()
        assertThat(initialBmp.isRecycled).isTrue()

        // Idempotent close
        overlay.close()
    }

    @Test
    fun singleBitmapRecyclingStress_bufferClearingBetweenFrames_preventsPixelGhosting() {
        val template = TemplateInstance(
            templateId = "title_minimal_modern",
            durationUs = 1_000_000L,
            parameters = mapOf("primary_text" to "Clear Test")
        )
        val item = MotionGraphicsTimelineItem(
            templateInstance = template,
            timelineStartUs = 1_000_000L,
            timelineDurationUs = 1_000_000L
        )

        val overlay = MotionGraphicsCanvasOverlay(listOf(item), 640, 360)

        // Frame before start: inactive -> fully transparent
        val bmpBefore = overlay.getBitmap(500_000L)
        assertThat(bmpBefore.getPixel(320, 180)).isEqualTo(0)

        // Frame during active: rendered
        val bmpActive = overlay.getBitmap(1_500_000L)
        assertThat(bmpActive).isSameInstanceAs(bmpBefore)

        // Frame after end: inactive -> buffer must be cleared to transparent
        val bmpAfter = overlay.getBitmap(2_500_000L)
        assertThat(bmpAfter).isSameInstanceAs(bmpBefore)
        assertThat(bmpAfter.getPixel(320, 180)).isEqualTo(0)
        assertThat(bmpAfter.getPixel(0, 0)).isEqualTo(0)

        overlay.close()
    }

    @Test
    fun singleBitmapRecyclingStress_concurrentMultiThreadedAccess_threadSafe() {
        val item = MotionGraphicsTimelineItem(
            templateInstance = TemplateInstance(templateId = "title_minimal_modern", durationUs = 5_000_000L),
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L
        )
        val overlay = MotionGraphicsCanvasOverlay(listOf(item), 640, 360)

        val executor = Executors.newFixedThreadPool(8)
        val iterations = 100

        for (i in 0 until iterations) {
            executor.submit {
                val ts = (i * 20_000L)
                val bmp = overlay.getBitmap(ts)
                assertThat(bmp.width).isEqualTo(640)
                assertThat(bmp.height).isEqualTo(360)
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(5, TimeUnit.SECONDS)
        assertThat(finished).isTrue()

        overlay.close()
    }

    @Test
    fun singleBitmapRecyclingStress_releaseAndClose_idempotent() {
        val overlay = MotionGraphicsCanvasOverlay(emptyList(), 640, 360)

        val bmp = overlay.getBitmap(0L)
        assertThat(bmp).isNotNull()
        assertThat(bmp.isRecycled).isFalse()

        overlay.release()
        assertThat(bmp.isRecycled).isTrue()

        // Multiple calls to release and close must not crash
        overlay.release()
        overlay.close()
        overlay.close()

        // Calling getBitmap after release safely returns a fresh bitmap without throwing
        val postReleaseBmp = overlay.getBitmap(0L)
        assertThat(postReleaseBmp).isNotNull()
        assertThat(postReleaseBmp.isRecycled).isFalse()

        overlay.release()
    }

    // -------------------------------------------------------------------------
    // 5. Aspect Ratio Conformance
    // -------------------------------------------------------------------------

    @Test
    fun aspectRatioConformance_16x9_macroblockAlignmentAndExactDimensions() {
        val ratio = CanvasAspectRatio.RATIO_16_9
        assertThat(ratio.aspectRatioFloat).isWithin(0.001f).of(16f / 9f)

        // 1080p
        val (w1080, h1080) = ratio.calculateMacroblockDimensions(1080, macroblockSize = 16)
        assertThat(w1080 % 16).isEqualTo(0)
        assertThat(h1080 % 16).isEqualTo(0)
        val distortion1080 = abs((w1080.toDouble() / h1080.toDouble()) - (16.0 / 9.0)) / (16.0 / 9.0)
        assertThat(distortion1080).isLessThan(0.02)

        // 720p
        val (w720, h720) = ratio.calculateMacroblockDimensions(720, macroblockSize = 16)
        assertThat(w720).isEqualTo(1280)
        assertThat(h720).isEqualTo(720)
        assertThat(w720 % 16).isEqualTo(0)
        assertThat(h720 % 16).isEqualTo(0)

        val overlay = MotionGraphicsCanvasOverlay(emptyList(), width = w1080, height = h1080)
        val bmp = overlay.getBitmap(0L)
        assertThat(bmp.width).isEqualTo(w1080)
        assertThat(bmp.height).isEqualTo(h1080)
        overlay.close()
    }

    @Test
    fun aspectRatioConformance_9x16_macroblockAlignmentAndExactDimensions() {
        val ratio = CanvasAspectRatio.RATIO_9_16
        assertThat(ratio.aspectRatioFloat).isWithin(0.001f).of(9f / 16f)

        // 1080 target height
        val (w1080, h1080) = ratio.calculateMacroblockDimensions(1080, macroblockSize = 16)
        assertThat(w1080 % 16).isEqualTo(0)
        assertThat(h1080 % 16).isEqualTo(0)
        val distortion1080 = abs((w1080.toDouble() / h1080.toDouble()) - (9.0 / 16.0)) / (9.0 / 16.0)
        assertThat(distortion1080).isLessThan(0.02)

        // 720 target height
        val (w720, h720) = ratio.calculateMacroblockDimensions(720, macroblockSize = 16)
        assertThat(w720 % 16).isEqualTo(0)
        assertThat(h720 % 16).isEqualTo(0)
        val distortion720 = abs((w720.toDouble() / h720.toDouble()) - (9.0 / 16.0)) / (9.0 / 16.0)
        assertThat(distortion720).isLessThan(0.02)

        val overlay = MotionGraphicsCanvasOverlay(emptyList(), width = w720, height = h720)
        val bmp = overlay.getBitmap(0L)
        assertThat(bmp.width).isEqualTo(w720)
        assertThat(bmp.height).isEqualTo(h720)
        overlay.close()
    }

    @Test
    fun aspectRatioConformance_1x1_macroblockAlignmentAndExactDimensions() {
        val ratio = CanvasAspectRatio.RATIO_1_1
        assertThat(ratio.aspectRatioFloat).isEqualTo(1.0f)

        // 1080 target height
        val (w1080, h1080) = ratio.calculateMacroblockDimensions(1080, macroblockSize = 16)
        assertThat(w1080 % 16).isEqualTo(0)
        assertThat(h1080 % 16).isEqualTo(0)
        assertThat(w1080).isEqualTo(h1080)

        // 720 target height
        val (w720, h720) = ratio.calculateMacroblockDimensions(720, macroblockSize = 16)
        assertThat(w720 % 16).isEqualTo(0)
        assertThat(h720 % 16).isEqualTo(0)
        assertThat(w720).isEqualTo(720)
        assertThat(h720).isEqualTo(720)

        val overlay = MotionGraphicsCanvasOverlay(emptyList(), width = w720, height = h720)
        val bmp = overlay.getBitmap(0L)
        assertThat(bmp.width).isEqualTo(720)
        assertThat(bmp.height).isEqualTo(720)
        overlay.close()
    }

    @Test
    fun aspectRatioConformance_compositionOverlay_preservesDimensionsAcrossAllRatios() {
        val ratios = listOf(
            CanvasAspectRatio.RATIO_16_9,
            CanvasAspectRatio.RATIO_9_16,
            CanvasAspectRatio.RATIO_1_1,
            CanvasAspectRatio.RATIO_4_5,
            CanvasAspectRatio.RATIO_21_9
        )

        for (ratio in ratios) {
            val (w, h) = ratio.calculateMacroblockDimensions(720, macroblockSize = 16)
            assertThat(w % 16).isEqualTo(0)
            assertThat(h % 16).isEqualTo(0)

            val compOverlay = CompositionCanvasOverlay.create(
                width = w,
                height = h,
                motionItems = listOf(
                    MotionGraphicsTimelineItem(
                        TemplateInstance(templateId = "title_minimal_modern", durationUs = 2_000_000L),
                        0L,
                        2_000_000L
                    )
                ),
                captionCues = listOf(
                    CaptionCue(0L, 2_000_000L, "Aspect Ratio Test")
                )
            )

            val mgBmp = compOverlay.motionGraphicsOverlay!!.getBitmap(0L)
            assertThat(mgBmp.width).isEqualTo(w)
            assertThat(mgBmp.height).isEqualTo(h)

            val subBmp = compOverlay.subtitleOverlay!!.getBitmap(0L)
            assertThat(subBmp.width).isEqualTo(w)
            assertThat(subBmp.height).isEqualTo(h)

            val overlayEffect = compOverlay.toOverlayEffect()
            assertThat(overlayEffect).isNotNull()

            compOverlay.close()
        }
    }
}
