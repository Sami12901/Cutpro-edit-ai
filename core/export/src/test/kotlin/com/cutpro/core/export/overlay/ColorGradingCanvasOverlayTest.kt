package com.cutpro.core.export.overlay

import com.cutpro.core.model.ColorAdjustments
import com.cutpro.core.model.LutFilterType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColorGradingCanvasOverlayTest {

    @Test
    fun createColorMatrix_generatesValidColorMatrix() {
        val adjustments = ColorAdjustments(
            exposure = 0.5f,
            contrast = 1.2f,
            saturation = 1.3f,
            temperature = 0.2f
        )
        val matrix = ColorGradingCanvasOverlay.createColorMatrix(adjustments, LutFilterType.CINEMATIC_TEAL_ORANGE)
        assertNotNull(matrix)
        assertNotNull(matrix.array)
        assertTrue(matrix.array.isNotEmpty())
    }

    @Test
    fun getBitmap_recyclesAndReturnsBitmap() {
        val overlay = ColorGradingCanvasOverlay(
            adjustments = ColorAdjustments(vignette = 0.5f),
            width = 640,
            height = 360
        )

        val bitmap = overlay.getBitmap(0L)
        assertNotNull(bitmap)
        assertTrue(!bitmap.isRecycled)

        overlay.close()
    }
}

