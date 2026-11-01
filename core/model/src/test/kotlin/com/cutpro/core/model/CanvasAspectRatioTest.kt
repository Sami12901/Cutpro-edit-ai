package com.cutpro.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CanvasAspectRatioTest {

    @Test
    fun calculateDimensions_returnsEvenDimensions_forStandardHeights() {
        val heights = listOf(480, 720, 1080, 2160, 721, 1079)
        for (ratio in CanvasAspectRatio.entries) {
            for (h in heights) {
                val (width, height) = ratio.calculateDimensions(h, alignMultipleOf = 2)
                assertThat(width % 2).isEqualTo(0)
                assertThat(height % 2).isEqualTo(0)
                assertThat(width).isGreaterThan(0)
                assertThat(height).isGreaterThan(0)
            }
        }
    }

    @Test
    fun calculateMacroblockDimensions_returnsMultiplesOf16() {
        val heights = listOf(720, 1080, 1088, 1920)
        for (ratio in CanvasAspectRatio.entries) {
            for (h in heights) {
                val (width, height) = ratio.calculateMacroblockDimensions(h, macroblockSize = 16)
                assertThat(width % 16).isEqualTo(0)
                assertThat(height % 16).isEqualTo(0)
                assertThat(width).isGreaterThan(0)
                assertThat(height).isGreaterThan(0)
            }
        }
    }

    @Test
    fun calculateDimensions_calculatesAccurate16By9Dimensions() {
        val (w, h) = CanvasAspectRatio.RATIO_16_9.calculateDimensions(1080)
        assertThat(w).isEqualTo(1920)
        assertThat(h).isEqualTo(1080)

        val (w720, h720) = CanvasAspectRatio.RATIO_16_9.calculateDimensions(720)
        assertThat(w720).isEqualTo(1280)
        assertThat(h720).isEqualTo(720)
    }

    @Test
    fun calculateDimensions_calculatesAccurate9By16Dimensions() {
        val (w, h) = CanvasAspectRatio.RATIO_9_16.calculateDimensions(1920)
        assertThat(w).isEqualTo(1080)
        assertThat(h).isEqualTo(1920)
    }

    @Test
    fun calculateDimensions_calculatesAccurateSquareDimensions() {
        val (w, h) = CanvasAspectRatio.RATIO_1_1.calculateDimensions(1080)
        assertThat(w).isEqualTo(1080)
        assertThat(h).isEqualTo(1080)
    }

    @Test
    fun calculateDimensions_rejectsZeroOrNegativeHeight() {
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(-100)
        }
    }

    @Test
    fun calculateDimensions_rejectsOddAlignment() {
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(1080, alignMultipleOf = 3)
        }
    }
}
