package com.cutpro.core.playback

import androidx.compose.ui.geometry.Offset
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.TimelineClip
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.atMost
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackEngineAdversarialTest {

    private val sampleMedia = MediaSourceInfo(
        uriString = "content://media/adversarial.mp4",
        fileName = "adversarial.mp4",
        mimeType = "video/mp4",
        durationUs = 120_000_000L // 120 seconds
    )

    // =========================================================================
    // 1. TimelinePositionMapper Stress Tests
    // =========================================================================

    @Test
    fun timelinePositionMapper_speedMultipliers_roundTripAccuracy() {
        val speeds = floatArrayOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 5.0f, 8.0f, 10.0f)

        for (speed in speeds) {
            val mapper = TimelinePositionMapper()
            // Timeline duration = 10s (10_000_000 us)
            // Source duration needed = timelineDurationUs * speed
            val timelineDurationUs = 10_000_000L
            val sourceTrimDurationUs = (timelineDurationUs * speed).toLong()
            val sourceInUs = 1_000_000L
            val sourceOutUs = sourceInUs + sourceTrimDurationUs

            val clip = TimelineClip(
                id = "clip_speed_$speed",
                trackId = "v1",
                mediaSource = sampleMedia,
                timelineStartUs = 2_000_000L,
                timelineDurationUs = timelineDurationUs,
                sourceInUs = sourceInUs,
                sourceOutUs = sourceOutUs,
                speedMultiplier = speed
            )
            mapper.updateClips(listOf(clip))

            // Test sample points: 0%, 10%, 25%, 50%, 75%, 90%, 99% of clip duration
            val testFractions = doubleArrayOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99)
            for (fraction in testFractions) {
                val testTimelineUs = clip.timelineStartUs + (timelineDurationUs * fraction).toLong()
                val target = mapper.timelineToPlayer(testTimelineUs)

                assertThat(target.isGap).isFalse()
                assertThat(target.mediaItemIndex).isEqualTo(0)
                assertThat(target.clip).isEqualTo(clip)
                assertThat(target.speedMultiplier).isEqualTo(speed)

                // Round-trip map back to timeline
                val reconstructedTimelineUs = mapper.playerToTimeline(target.mediaItemIndex, target.positionMs)

                // Expected quantization margin: positionMs is integer milliseconds (1000us)
                // In timeline microseconds, 1ms source is 1000us / speed
                val maxAllowedErrorUs = (1000.0 / speed).toLong() + 1000L
                val errorUs = abs(reconstructedTimelineUs - testTimelineUs)

                assertThat(errorUs).isAtMost(maxAllowedErrorUs)
            }
        }
    }

    @Test
    fun timelinePositionMapper_interClipGaps_smoothTransitionAndNoCrash() {
        val mapper = TimelinePositionMapper()

        // Clip 1: [1.0s, 3.0s] (2s)
        val clip1 = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 1_000_000L,
            timelineDurationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L
        )
        // Gap 1: [3.0s, 5.0s] (2s empty space)

        // Clip 2: [5.0s, 8.0s] (3s, 2x speed)
        val clip2 = TimelineClip(
            id = "c2",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 5_000_000L,
            timelineDurationUs = 3_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 6_000_000L,
            speedMultiplier = 2.0f
        )
        // Gap 2: [8.0s, 11.0s] (3s empty space)

        // Clip 3: [11.0s, 15.0s] (4s)
        val clip3 = TimelineClip(
            id = "c3",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 11_000_000L,
            timelineDurationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L
        )

        mapper.updateClips(listOf(clip1, clip2, clip3))

        // 1. Initial gap before clip 1 [0s, 1s)
        val preTarget = mapper.timelineToPlayer(500_000L)
        assertThat(preTarget.isGap).isTrue()
        assertThat(preTarget.mediaItemIndex).isEqualTo(0)
        assertThat(preTarget.positionMs).isEqualTo(0L)
        assertThat(preTarget.clip).isNull()

        // 2. Inside Clip 1
        val c1Target = mapper.timelineToPlayer(2_000_000L)
        assertThat(c1Target.isGap).isFalse()
        assertThat(c1Target.mediaItemIndex).isEqualTo(0)
        assertThat(c1Target.positionMs).isEqualTo(1000L)
        assertThat(c1Target.clip).isEqualTo(clip1)

        // 3. Inside Gap 1 [3.0s, 5.0s)
        val gap1Target = mapper.timelineToPlayer(4_000_000L)
        assertThat(gap1Target.isGap).isTrue()
        assertThat(gap1Target.mediaItemIndex).isEqualTo(1) // Cues clip 2
        assertThat(gap1Target.positionMs).isEqualTo(0L)
        assertThat(gap1Target.clip).isNull()

        // 4. Boundary microsecond sweep across Gap 1 -> Clip 2 transition [4.999990s to 5.000010s]
        for (offset in -10L..10L) {
            val ts = 5_000_000L + offset
            val target = mapper.timelineToPlayer(ts)
            if (ts < 5_000_000L) {
                assertThat(target.isGap).isTrue()
                assertThat(target.mediaItemIndex).isEqualTo(1)
                assertThat(target.clip).isNull()
            } else {
                assertThat(target.isGap).isFalse()
                assertThat(target.mediaItemIndex).isEqualTo(1)
                assertThat(target.clip).isEqualTo(clip2)
                assertThat(target.speedMultiplier).isEqualTo(2.0f)
            }
        }

        // 5. Inside Gap 2 [8.0s, 11.0s)
        val gap2Target = mapper.timelineToPlayer(9_500_000L)
        assertThat(gap2Target.isGap).isTrue()
        assertThat(gap2Target.mediaItemIndex).isEqualTo(2) // Cues clip 3
        assertThat(gap2Target.positionMs).isEqualTo(0L)
        assertThat(gap2Target.clip).isNull()

        // 6. Inside Clip 3 [11.0s, 15.0s)
        val c3Target = mapper.timelineToPlayer(12_000_000L)
        assertThat(c3Target.isGap).isFalse()
        assertThat(c3Target.mediaItemIndex).isEqualTo(2)
        assertThat(c3Target.positionMs).isEqualTo(1000L)
        assertThat(c3Target.clip).isEqualTo(clip3)
    }

    @Test
    fun timelinePositionMapper_boundaryExtremeTimestamps() {
        val mapper = TimelinePositionMapper()
        val clip = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 2_000_000L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 1_000_000L,
            sourceOutUs = 6_000_000L,
            speedMultiplier = 1.0f
        )
        mapper.updateClips(listOf(clip))

        // Negative timestamps
        val negTarget1 = mapper.timelineToPlayer(-500_000L)
        assertThat(negTarget1.positionMs).isEqualTo(0L)
        assertThat(negTarget1.isGap).isTrue()

        val negTarget2 = mapper.timelineToPlayer(-100_000_000L)
        assertThat(negTarget2.positionMs).isEqualTo(0L)
        assertThat(negTarget2.isGap).isTrue()

        // Exact clip start boundary (2_000_000L)
        val startTarget = mapper.timelineToPlayer(2_000_000L)
        assertThat(startTarget.isGap).isFalse()
        assertThat(startTarget.mediaItemIndex).isEqualTo(0)
        assertThat(startTarget.positionMs).isEqualTo(0L)
        assertThat(startTarget.clip).isEqualTo(clip)

        // Exact clip end boundary (7_000_000L)
        val endTarget = mapper.timelineToPlayer(7_000_000L)
        assertThat(endTarget.isGap).isFalse()
        assertThat(endTarget.mediaItemIndex).isEqualTo(0)
        assertThat(endTarget.positionMs).isEqualTo(5000L) // sourceTrimDurationUs = 5s = 5000ms

        // Far beyond project duration (100 seconds)
        val beyondTarget = mapper.timelineToPlayer(100_000_000L)
        assertThat(beyondTarget.isGap).isFalse()
        assertThat(beyondTarget.mediaItemIndex).isEqualTo(0)
        assertThat(beyondTarget.positionMs).isEqualTo(5000L)

        // Extreme out-of-range playerToTimeline queries
        assertThat(mapper.playerToTimeline(-1, 1000L)).isEqualTo(1_000_000L)
        assertThat(mapper.playerToTimeline(999, 1000L)).isEqualTo(1_000_000L)
        assertThat(mapper.playerToTimeline(0, -500L)).isEqualTo(clip.timelineStartUs)
        assertThat(mapper.playerToTimeline(0, 999_999L)).isEqualTo(clip.timelineEndUs)
    }

    // =========================================================================
    // 2. AdaptiveSeekDispatcher Stress Tests
    // =========================================================================

    @Test
    fun adaptiveSeekDispatcher_highFrequencyBurst_conflatesToThrottledClosestSync() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        val mapper = TimelinePositionMapper()
        val clip = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 20_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 20_000_000L
        )
        mapper.updateClips(listOf(clip))

        val dispatcher = AdaptiveSeekDispatcher(player, this, mapper)
        runCurrent()

        // Violent user scrubbing burst: 1,000 rapid seek calls
        for (i in 1..1000) {
            dispatcher.dispatchSeek(targetTimelineUs = i * 10_000L, isScrubbing = true)
        }

        // Advance coroutines to process conflated flow
        runCurrent()

        // Verify that intermediate requests were conflated (throttled):
        // Player should receive a small conflated count (at most 5 seeks), NOT 1,000 seeks
        verify(player, atMost(5)).seekTo(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong())
        verify(player, atLeastOnce()).seekTo(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong())

        // Verify seek parameters were CLOSEST_SYNC
        verify(player, atLeastOnce()).setSeekParameters(SeekParameters.CLOSEST_SYNC)

        // The final seek processed should be the latest seek request: 1000 * 10_000 us = 10_000_000 us = 10,000 ms
        verify(player).seekTo(0, 10_000L)

        dispatcher.cancel()
    }

    @Test
    fun adaptiveSeekDispatcher_touchRelease_executesExactSeekImmediately() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        val mapper = TimelinePositionMapper()
        val clip = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L
        )
        mapper.updateClips(listOf(clip))

        val dispatcher = AdaptiveSeekDispatcher(player, this, mapper)

        // Touch release must execute immediately, even without yielding the coroutine
        dispatcher.dispatchSeek(targetTimelineUs = 4_500_000L, isScrubbing = false)

        verify(player).setSeekParameters(SeekParameters.EXACT)
        verify(player).seekTo(0, 4500L)

        dispatcher.cancel()
    }

    @Test
    fun adaptiveSeekDispatcher_burstScrubbingFollowedByRelease_maintainsExactSeek() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        val mapper = TimelinePositionMapper()
        val clip = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 20_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 20_000_000L
        )
        mapper.updateClips(listOf(clip))

        val dispatcher = AdaptiveSeekDispatcher(player, this, mapper)
        runCurrent()

        // User scrubs rapidly
        for (i in 1..100) {
            dispatcher.dispatchSeek(targetTimelineUs = i * 20_000L, isScrubbing = true)
        }
        // Let scrubbing flow process
        runCurrent()

        // User releases at 8,000,000 us (8000ms)
        dispatcher.dispatchSeek(targetTimelineUs = 8_000_000L, isScrubbing = false)
        advanceUntilIdle()

        // Verify final seek parameter was EXACT at 8000ms
        val seekParamCaptor = ArgumentCaptor.forClass(SeekParameters::class.java)
        verify(player, atLeastOnce()).setSeekParameters(seekParamCaptor.capture())
        assertThat(seekParamCaptor.allValues.last()).isEqualTo(SeekParameters.EXACT)

        val posCaptor = ArgumentCaptor.forClass(Long::class.java)
        verify(player, atLeastOnce()).seekTo(org.mockito.ArgumentMatchers.eq(0), posCaptor.capture())
        assertThat(posCaptor.allValues.last()).isEqualTo(8000L)

        dispatcher.cancel()
    }

    @Test
    fun adaptiveSeekDispatcher_pendingScrubbingCancelledOnRelease_noStaleSeekAfterRelease() = runTest {
        val player = mock(ExoPlayer::class.java)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        val mapper = TimelinePositionMapper()
        val clip = TimelineClip(
            id = "c1",
            trackId = "v1",
            mediaSource = sampleMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 20_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 20_000_000L
        )
        mapper.updateClips(listOf(clip))

        val dispatcher = AdaptiveSeekDispatcher(player, this, mapper)
        // User scrubs at 1,000,000 us (1000ms), followed immediately by release at 5,000,000 us (5000ms)
        // without coroutine dispatcher yielding between drag and touch release
        dispatcher.dispatchSeek(targetTimelineUs = 1_000_000L, isScrubbing = true)
        dispatcher.dispatchSeek(targetTimelineUs = 5_000_000L, isScrubbing = false)

        // Allow all background coroutines to process
        advanceUntilIdle()

        // The final seek MUST be EXACT at 5000ms. Pending scrubbing seek (1000ms) must have been cancelled.
        val seekParamCaptor = ArgumentCaptor.forClass(SeekParameters::class.java)
        verify(player, atLeastOnce()).setSeekParameters(seekParamCaptor.capture())
        assertThat(seekParamCaptor.allValues.last()).isEqualTo(SeekParameters.EXACT)

        val posCaptor = ArgumentCaptor.forClass(Long::class.java)
        verify(player, atLeastOnce()).seekTo(org.mockito.ArgumentMatchers.eq(0), posCaptor.capture())
        assertThat(posCaptor.allValues.last()).isEqualTo(5000L)

        dispatcher.cancel()
    }

    // =========================================================================
    // 3. CanvasFramingCalculator Stress Tests
    // =========================================================================

    @Test
    fun canvasFramingCalculator_extremeContainerDimensions_multiplesOf2And16() {
        val ratios = CanvasAspectRatio.entries.toTypedArray()
        val extremeDimensions = listOf(
            Pair(1000f, 1f),       // Extreme wide slit
            Pair(2f, 1000f),       // Very narrow container
            Pair(10_000f, 10_000f),// Massive 10k container
            Pair(10f, 10f),        // Small container
            Pair(2560f, 1080f),    // 21:9 container
            Pair(1080f, 1350f)     // 4:5 container
        )

        for (aspectRatio in ratios) {
            for ((w, h) in extremeDimensions) {
                val geo = CanvasFramingCalculator.computeViewportGeometry(
                    containerWidth = w,
                    containerHeight = h,
                    aspectRatio = aspectRatio
                )

                // Verify strictly positive dimensions
                assertThat(geo.previewWidth).isGreaterThan(0)
                assertThat(geo.previewHeight).isGreaterThan(0)
                assertThat(geo.encoderWidth).isGreaterThan(0)
                assertThat(geo.encoderHeight).isGreaterThan(0)

                // Verify multiples of 2 for preview (YUV420p)
                assertThat(geo.previewWidth % 2).isEqualTo(0)
                assertThat(geo.previewHeight % 2).isEqualTo(0)

                // Verify multiples of 16 for encoder (Macroblocks)
                assertThat(geo.encoderWidth % 16).isEqualTo(0)
                assertThat(geo.encoderHeight % 16).isEqualTo(0)

                // Verify canvas bounds are strictly positive
                assertThat(geo.canvasBounds.width).isGreaterThan(0f)
                assertThat(geo.canvasBounds.height).isGreaterThan(0f)

                // Safe zones must be inside canvas bounds
                assertThat(geo.actionSafeRect.width).isLessThan(geo.canvasBounds.width)
                assertThat(geo.actionSafeRect.height).isLessThan(geo.canvasBounds.height)
                assertThat(geo.titleSafeRect.width).isLessThan(geo.actionSafeRect.width)
                assertThat(geo.titleSafeRect.height).isLessThan(geo.actionSafeRect.height)
            }
        }
    }

    @Test
    fun canvasFramingCalculator_cardinalRotationSnapping_thresholdBoundaries() {
        val threshold = 3.0f

        // Quadrant 0 (0 deg / 360 deg)
        assertThat(CanvasFramingCalculator.snapRotation(0.0f, threshold)).isEqualTo(0.0f)
        assertThat(CanvasFramingCalculator.snapRotation(2.9f, threshold)).isEqualTo(0.0f)
        assertThat(CanvasFramingCalculator.snapRotation(3.0f, threshold)).isEqualTo(0.0f)
        assertThat(CanvasFramingCalculator.snapRotation(-2.9f, threshold)).isEqualTo(0.0f)
        assertThat(CanvasFramingCalculator.snapRotation(-3.0f, threshold)).isEqualTo(0.0f)
        assertThat(CanvasFramingCalculator.snapRotation(357.1f, threshold)).isEqualTo(0.0f)
        assertThat(CanvasFramingCalculator.snapRotation(359.9f, threshold)).isEqualTo(0.0f)
        assertThat(CanvasFramingCalculator.snapRotation(360.0f, threshold)).isEqualTo(0.0f)
        assertThat(CanvasFramingCalculator.snapRotation(720.0f, threshold)).isEqualTo(0.0f)

        // Outside 0 deg threshold
        assertThat(CanvasFramingCalculator.snapRotation(3.1f, threshold)).isWithin(0.001f).of(3.1f)
        assertThat(CanvasFramingCalculator.snapRotation(-3.1f, threshold)).isWithin(0.001f).of(356.9f)

        // Quadrant 1 (90 deg)
        assertThat(CanvasFramingCalculator.snapRotation(87.0f, threshold)).isEqualTo(90.0f)
        assertThat(CanvasFramingCalculator.snapRotation(89.5f, threshold)).isEqualTo(90.0f)
        assertThat(CanvasFramingCalculator.snapRotation(90.0f, threshold)).isEqualTo(90.0f)
        assertThat(CanvasFramingCalculator.snapRotation(93.0f, threshold)).isEqualTo(90.0f)
        assertThat(CanvasFramingCalculator.snapRotation(450.0f, threshold)).isEqualTo(90.0f)
        assertThat(CanvasFramingCalculator.snapRotation(-270.0f, threshold)).isEqualTo(90.0f)

        // Outside 90 deg threshold
        assertThat(CanvasFramingCalculator.snapRotation(86.9f, threshold)).isWithin(0.001f).of(86.9f)
        assertThat(CanvasFramingCalculator.snapRotation(93.1f, threshold)).isWithin(0.001f).of(93.1f)

        // Quadrant 2 (180 deg)
        assertThat(CanvasFramingCalculator.snapRotation(177.0f, threshold)).isEqualTo(180.0f)
        assertThat(CanvasFramingCalculator.snapRotation(180.0f, threshold)).isEqualTo(180.0f)
        assertThat(CanvasFramingCalculator.snapRotation(183.0f, threshold)).isEqualTo(180.0f)
        assertThat(CanvasFramingCalculator.snapRotation(-180.0f, threshold)).isEqualTo(180.0f)

        // Outside 180 deg threshold
        assertThat(CanvasFramingCalculator.snapRotation(176.9f, threshold)).isWithin(0.001f).of(176.9f)
        assertThat(CanvasFramingCalculator.snapRotation(183.1f, threshold)).isWithin(0.001f).of(183.1f)

        // Quadrant 3 (270 deg)
        assertThat(CanvasFramingCalculator.snapRotation(267.0f, threshold)).isEqualTo(270.0f)
        assertThat(CanvasFramingCalculator.snapRotation(270.0f, threshold)).isEqualTo(270.0f)
        assertThat(CanvasFramingCalculator.snapRotation(273.0f, threshold)).isEqualTo(270.0f)
        assertThat(CanvasFramingCalculator.snapRotation(-90.0f, threshold)).isEqualTo(270.0f)

        // Outside 270 deg threshold
        assertThat(CanvasFramingCalculator.snapRotation(266.9f, threshold)).isWithin(0.001f).of(266.9f)
        assertThat(CanvasFramingCalculator.snapRotation(273.1f, threshold)).isWithin(0.001f).of(273.1f)

        // Diagonal angles (no snap)
        assertThat(CanvasFramingCalculator.snapRotation(45.0f, threshold)).isEqualTo(45.0f)
        assertThat(CanvasFramingCalculator.snapRotation(135.0f, threshold)).isEqualTo(135.0f)
        assertThat(CanvasFramingCalculator.snapRotation(225.0f, threshold)).isEqualTo(225.0f)
        assertThat(CanvasFramingCalculator.snapRotation(315.0f, threshold)).isEqualTo(315.0f)
    }

    @Test
    fun canvasFramingCalculator_invalidContainerBounds_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            CanvasFramingCalculator.computeViewportGeometry(0f, 100f, CanvasAspectRatio.RATIO_16_9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasFramingCalculator.computeViewportGeometry(100f, 0f, CanvasAspectRatio.RATIO_16_9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasFramingCalculator.computeViewportGeometry(-10f, 100f, CanvasAspectRatio.RATIO_16_9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasFramingCalculator.computeViewportGeometry(100f, -10f, CanvasAspectRatio.RATIO_16_9)
        }
    }

    @Test
    fun canvasFramingCalculator_clipTransformsAndDelta_boundaryValues() {
        // FIT with equal aspect
        val (fitX, fitY, trans1) = CanvasFramingCalculator.computeClipTransform(
            canvasWidth = 1920f,
            canvasHeight = 1080f,
            sourceWidth = 1920f,
            sourceHeight = 1080f,
            fitMode = ClipFitMode.FIT,
            transform = ClipTransform(translationX = 0f, translationY = 0f, scaleX = 1f, scaleY = 1f)
        )
        assertThat(fitX).isEqualTo(1.0f)
        assertThat(fitY).isEqualTo(1.0f)
        assertThat(trans1).isEqualTo(Offset(0f, 0f))

        // STRETCH mode
        val (stretchX, stretchY, _) = CanvasFramingCalculator.computeClipTransform(
            canvasWidth = 1920f,
            canvasHeight = 1080f,
            sourceWidth = 1080f,
            sourceHeight = 1920f,
            fitMode = ClipFitMode.STRETCH,
            transform = ClipTransform()
        )
        assertThat(stretchX).isWithin(0.01f).of(1920f / 1080f)
        assertThat(stretchY).isWithin(0.01f).of(1080f / 1920f)

        // Translation translationX = 1.0 maps to canvasWidth / 2
        val (_, _, transEdge) = CanvasFramingCalculator.computeClipTransform(
            canvasWidth = 1000f,
            canvasHeight = 500f,
            sourceWidth = 1000f,
            sourceHeight = 500f,
            fitMode = ClipFitMode.FIT,
            transform = ClipTransform(translationX = 1.0f, translationY = -1.0f)
        )
        assertThat(transEdge.x).isEqualTo(500f)
        assertThat(transEdge.y).isEqualTo(-250f)

        // Delta to normalized translation round-trip
        val normDelta = CanvasFramingCalculator.screenDeltaToNormalizedTranslation(
            deltaX = 500f,
            deltaY = -250f,
            canvasWidth = 1000f,
            canvasHeight = 500f
        )
        assertThat(normDelta.x).isEqualTo(1.0f)
        assertThat(normDelta.y).isEqualTo(-1.0f)
    }

    // Adversarial edge test: Container 1x1000
    @Test
    fun canvasFramingCalculator_extreme1x1000Container() {
        // For 4:5 and 9:16 aspect ratios, canvasHeight >= 1.0f even in 1px width
        val geo45 = CanvasFramingCalculator.computeViewportGeometry(1f, 1000f, CanvasAspectRatio.RATIO_4_5)
        assertThat(geo45.previewWidth).isGreaterThan(0)
        assertThat(geo45.previewHeight).isGreaterThan(0)

        val geo916 = CanvasFramingCalculator.computeViewportGeometry(1f, 1000f, CanvasAspectRatio.RATIO_9_16)
        assertThat(geo916.previewWidth).isGreaterThan(0)
        assertThat(geo916.previewHeight).isGreaterThan(0)
    }

    @Test
    fun canvasFramingCalculator_extreme1x1000Container_allAspectRatios() {
        for (aspectRatio in CanvasAspectRatio.entries) {
            val geo = CanvasFramingCalculator.computeViewportGeometry(1f, 1000f, aspectRatio)
            assertThat(geo.previewWidth).isGreaterThan(0)
            assertThat(geo.previewHeight).isGreaterThan(0)
            assertThat(geo.previewWidth % 2).isEqualTo(0)
            assertThat(geo.previewHeight % 2).isEqualTo(0)
        }
    }
}
