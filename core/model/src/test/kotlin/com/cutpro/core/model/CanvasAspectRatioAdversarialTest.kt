package com.cutpro.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

/**
 * Adversarial stress tests for CanvasAspectRatio dimension calculations.
 * Challenges YUV420p even dimension invariants and 16-pixel macroblock invariants
 * across extreme heights and generative fuzzing.
 */
class CanvasAspectRatioAdversarialTest {

    private val extremeHeights = listOf(1, 3, 1081, 2160, 4320)

    @Test
    fun challengeCalculateDimensions_withExtremeHeights_satisfiesYuv420pEvenInvariant() {
        for (ratio in CanvasAspectRatio.entries) {
            for (height in extremeHeights) {
                val (w, h) = ratio.calculateDimensions(height, alignMultipleOf = 2)

                // Invariant 1: Dimensions must be positive
                assertWithMessage("Width for $ratio at h=$height").that(w).isGreaterThan(0)
                assertWithMessage("Height for $ratio at h=$height").that(h).isGreaterThan(0)

                // Invariant 2: Even dimensions required for YUV420p chroma subsampling (no odd dimensions)
                assertWithMessage("YUV420p even width invariant for $ratio at h=$height").that(w % 2).isEqualTo(0)
                assertWithMessage("YUV420p even height invariant for $ratio at h=$height").that(h % 2).isEqualTo(0)

                // Invariant 3: Must be at least alignMultipleOf
                assertThat(w).isAtLeast(2)
                assertThat(h).isAtLeast(2)
            }
        }
    }

    @Test
    fun challengeCalculateMacroblockDimensions_withExtremeHeights_satisfiesMacroblock16Invariant() {
        for (ratio in CanvasAspectRatio.entries) {
            for (height in extremeHeights) {
                val (w, h) = ratio.calculateMacroblockDimensions(height, macroblockSize = 16)

                // Invariant 1: Dimensions must be positive
                assertWithMessage("Macroblock width for $ratio at h=$height").that(w).isGreaterThan(0)
                assertWithMessage("Macroblock height for $ratio at h=$height").that(h).isGreaterThan(0)

                // Invariant 2: Dimensions must be exact multiples of 16 for hardware video encoders
                assertWithMessage("16-pixel macroblock width invariant for $ratio at h=$height").that(w % 16).isEqualTo(0)
                assertWithMessage("16-pixel macroblock height invariant for $ratio at h=$height").that(h % 16).isEqualTo(0)

                // Invariant 3: Must be at least macroblockSize
                assertThat(w).isAtLeast(16)
                assertThat(h).isAtLeast(16)
            }
        }
    }

    @Test
    fun challengeCalculateDimensions_generativeFuzzing_10000RandomHeights() {
        val rng = Random(42)
        val sampleRatios = CanvasAspectRatio.entries

        for (i in 0 until 10_000) {
            val randomHeight = rng.nextInt(1, 100_000)
            val ratio = sampleRatios[rng.nextInt(sampleRatios.size)]

            val (w, h) = ratio.calculateDimensions(randomHeight, alignMultipleOf = 2)
            assertWithMessage("Fuzz YUV420p width at h=$randomHeight for $ratio").that(w % 2).isEqualTo(0)
            assertWithMessage("Fuzz YUV420p height at h=$randomHeight for $ratio").that(h % 2).isEqualTo(0)
            assertThat(w).isAtLeast(2)
            assertThat(h).isAtLeast(2)
        }
    }

    @Test
    fun challengeCalculateMacroblockDimensions_generativeFuzzing_10000RandomHeights() {
        val rng = Random(1337)
        val sampleRatios = CanvasAspectRatio.entries

        for (i in 0 until 10_000) {
            val randomHeight = rng.nextInt(1, 100_000)
            val ratio = sampleRatios[rng.nextInt(sampleRatios.size)]

            val (w, h) = ratio.calculateMacroblockDimensions(randomHeight, macroblockSize = 16)
            assertWithMessage("Fuzz macroblock width at h=$randomHeight for $ratio").that(w % 16).isEqualTo(0)
            assertWithMessage("Fuzz macroblock height at h=$randomHeight for $ratio").that(h % 16).isEqualTo(0)
            assertThat(w).isAtLeast(16)
            assertThat(h).isAtLeast(16)
        }
    }

    @Test
    fun challengeCalculateDimensions_boundaryInvalidParameters_strictlyRejected() {
        // Zero height
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(0)
        }

        // Negative height
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(-1080)
        }

        // Odd alignment (illegal for chroma subsampling)
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(1080, alignMultipleOf = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(1080, alignMultipleOf = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateDimensions(1080, alignMultipleOf = -2)
        }
    }

    @Test
    fun challengeCalculateMacroblockDimensions_invalidMacroblockSize_strictlyRejected() {
        // Macroblock size must be a multiple of 16
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateMacroblockDimensions(1080, macroblockSize = 8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateMacroblockDimensions(1080, macroblockSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanvasAspectRatio.RATIO_16_9.calculateMacroblockDimensions(1080, macroblockSize = -16)
        }
    }
}
