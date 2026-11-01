package com.cutpro.core.designsystem

import androidx.compose.ui.graphics.toArgb
import com.cutpro.core.designsystem.theme.DarkBackground
import com.cutpro.core.designsystem.theme.DarkPrimary
import com.cutpro.core.designsystem.theme.DarkSurface
import com.cutpro.core.designsystem.theme.LightBackground
import com.cutpro.core.designsystem.theme.CutProTypography
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DesignSystemTokensTest {

    @Test
    fun darkThemeColorTokens_matchSpecification() {
        // Spec: Dark Slate palette (#121316 background, #1E2024 surface, #4A88DA primary)
        assertThat(DarkBackground.toArgb()).isEqualTo(0xFF121316.toInt())
        assertThat(DarkSurface.toArgb()).isEqualTo(0xFF1E2024.toInt())
        assertThat(DarkPrimary.toArgb()).isEqualTo(0xFF4A88DA.toInt())
    }

    @Test
    fun lightThemeTokens_matchSpecification() {
        assertThat(LightBackground.toArgb()).isEqualTo(0xFFF8F9FA.toInt())
    }


    @Test
    fun typographyTokens_titleLargerThanBody() {
        val titleSize = CutProTypography.titleLarge.fontSize.value
        val bodySize = CutProTypography.bodyMedium.fontSize.value
        assertThat(titleSize).isGreaterThan(bodySize)
    }
}
