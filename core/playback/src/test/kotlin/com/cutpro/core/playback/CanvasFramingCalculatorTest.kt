package com.cutpro.core.playback

import androidx.compose.ui.geometry.Offset
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ClipTransform
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CanvasFramingCalculatorTest {

    @Test
    fun computeViewportGeometry_pillarboxInWideContainer() {
        // Container 2000x1000 (2:1 ratio), Target 16:9 (1.777:1) -> Pillarbox
        val geo = CanvasFramingCalculator.computeViewportGeometry(
            containerWidth = 2000f,
            containerHeight = 1000f,
            aspectRatio = CanvasAspectRatio.RATIO_16_9
        )

        assertThat(geo.canvasBounds.height).isEqualTo(1000f)
        assertThat(geo.canvasBounds.width).isWithin(1f).of(1000f * (16f / 9f))
        assertThat(geo.letterboxOffset.x).isGreaterThan(0f)
        assertThat(geo.letterboxOffset.y).isEqualTo(0f)
    }

    @Test
    fun computeViewportGeometry_letterboxInTallContainer() {
        // Container 1000x2000 (1:2 ratio), Target 16:9 -> Letterbox
        val geo = CanvasFramingCalculator.computeViewportGeometry(
            containerWidth = 1000f,
            containerHeight = 2000f,
            aspectRatio = CanvasAspectRatio.RATIO_16_9
        )

        assertThat(geo.canvasBounds.width).isEqualTo(1000f)
        assertThat(geo.letterboxOffset.x).isEqualTo(0f)
        assertThat(geo.letterboxOffset.y).isGreaterThan(0f)
    }

    @Test
    fun safeZones_maintainCorrectMargins() {
        val geo = CanvasFramingCalculator.computeViewportGeometry(
            containerWidth = 1920f,
            containerHeight = 1080f,
            aspectRatio = CanvasAspectRatio.RATIO_16_9
        )

        // Action Safe 93% (3.5% margins on each side)
        val expectedActionWidth = 1920f * (1f - 2f * 0.035f)
        assertThat(geo.actionSafeRect.width).isWithin(1f).of(expectedActionWidth)

        // Title Safe 90% (5% margins on each side)
        val expectedTitleWidth = 1920f * (1f - 2f * 0.05f)
        assertThat(geo.titleSafeRect.width).isWithin(1f).of(expectedTitleWidth)
    }

    @Test
    fun snapRotation_snapsToCardinals() {
        // Near 0
        assertThat(CanvasFramingCalculator.snapRotation(1.5f)).isEqualTo(0f)
        assertThat(CanvasFramingCalculator.snapRotation(358.5f)).isEqualTo(0f)

        // Near 90
        assertThat(CanvasFramingCalculator.snapRotation(89.0f)).isEqualTo(90f)

        // Near 180
        assertThat(CanvasFramingCalculator.snapRotation(181.5f)).isEqualTo(180f)

        // Near 270
        assertThat(CanvasFramingCalculator.snapRotation(269.0f)).isEqualTo(270f)

        // Off angle (e.g. 45 degrees) remains unchanged
        assertThat(CanvasFramingCalculator.snapRotation(45f)).isEqualTo(45f)
    }

    @Test
    fun computeClipTransform_fitAndFillModes() {
        val (fitScaleX, fitScaleY, _) = CanvasFramingCalculator.computeClipTransform(
            canvasWidth = 1920f,
            canvasHeight = 1080f,
            sourceWidth = 1080f,
            sourceHeight = 1920f,
            fitMode = ClipFitMode.FIT,
            transform = ClipTransform()
        )
        // In FIT, fits entirely within canvas -> constrained by height: 1080 / 1920 = 0.5625
        assertThat(fitScaleX).isEqualTo(fitScaleY)
        assertThat(fitScaleX).isWithin(0.01f).of(0.5625f)

        val (fillScaleX, fillScaleY, _) = CanvasFramingCalculator.computeClipTransform(
            canvasWidth = 1920f,
            canvasHeight = 1080f,
            sourceWidth = 1080f,
            sourceHeight = 1920f,
            fitMode = ClipFitMode.FILL,
            transform = ClipTransform()
        )
        // In FILL, covers entire canvas -> constrained by width: 1920 / 1080 = 1.777f
        assertThat(fillScaleX).isEqualTo(fillScaleY)
        assertThat(fillScaleX).isWithin(0.01f).of(1.777f)
    }
}
