package com.cutpro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProVideoEffectsTest {

    @Test
    fun colorAdjustments_defaultsAreIdentity() {
        val adjustments = ColorAdjustments()
        assertTrue(adjustments.isIdentity)
        assertEquals(0.0f, adjustments.exposure, 0.001f)
        assertEquals(1.0f, adjustments.contrast, 0.001f)
        assertEquals(1.0f, adjustments.saturation, 0.001f)
    }

    @Test
    fun colorAdjustments_nonDefaultsAreNotIdentity() {
        val adjustments = ColorAdjustments(exposure = 0.5f, contrast = 1.2f)
        assertFalse(adjustments.isIdentity)
    }

    @Test(expected = IllegalArgumentException::class)
    fun colorAdjustments_exposureOutOfBoundsThrows() {
        ColorAdjustments(exposure = 3.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun colorAdjustments_contrastNegativeThrows() {
        ColorAdjustments(contrast = -0.5f)
    }

    @Test
    fun clipMask_defaultLinearMaskValid() {
        val mask = ClipMask()
        assertEquals(MaskType.LINEAR, mask.type)
        assertEquals(0.5f, mask.centerX, 0.001f)
        assertEquals(0.5f, mask.centerY, 0.001f)
        assertFalse(mask.isInverted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun clipMask_negativeScaleThrows() {
        ClipMask(scaleX = -1.0f)
    }

    @Test
    fun speedCurve_presetCreationSucceeds() {
        val heroCurve = SpeedCurve.createPreset(SpeedCurvePreset.HERO)
        assertEquals(SpeedCurvePreset.HERO, heroCurve.preset)
        assertEquals(4, heroCurve.controlPoints.size)
        assertEquals(0.0f, heroCurve.controlPoints.first().normalizedTime, 0.001f)
        assertEquals(1.0f, heroCurve.controlPoints.last().normalizedTime, 0.001f)

        val montageCurve = SpeedCurve.createPreset(SpeedCurvePreset.MONTAGE)
        assertEquals(SpeedCurvePreset.MONTAGE, montageCurve.preset)
        assertEquals(4, montageCurve.controlPoints.size)
    }

    @Test
    fun clipTransform_withBlendModeAndMask() {
        val mask = ClipMask(type = MaskType.RADIAL)
        val transform = ClipTransform(
            blendMode = BlendMode.SCREEN,
            mask = mask
        )
        assertFalse(transform.isIdentity)
        assertEquals(BlendMode.SCREEN, transform.blendMode)
        assertNotNull(transform.mask)
        assertEquals(MaskType.RADIAL, transform.mask?.type)
    }

    @Test
    fun kenBurnsEffect_validScales() {
        val kenBurns = KenBurnsEffect(startScale = 1.0f, endScale = 1.3f)
        assertEquals(1.0f, kenBurns.startScale, 0.001f)
        assertEquals(1.3f, kenBurns.endScale, 0.001f)
    }
}

