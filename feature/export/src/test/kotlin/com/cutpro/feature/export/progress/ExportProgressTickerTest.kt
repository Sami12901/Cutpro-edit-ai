package com.cutpro.feature.export.progress

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExportProgressTickerTest {

    @Test
    fun calculateEta_accuracyAcrossPercentages() {
        // Less than 1 second elapsed -> null
        assertThat(ExportProgressTicker.calculateEta(10, 800L)).isNull()

        // 1% or less -> null
        assertThat(ExportProgressTicker.calculateEta(1, 5000L)).isNull()
        assertThat(ExportProgressTicker.calculateEta(0, 5000L)).isNull()

        // 50% at 10s -> ETA 10s
        assertThat(ExportProgressTicker.calculateEta(50, 10_000L)).isEqualTo(10_000L)

        // 20% at 4s -> Total 20s -> ETA 16s
        assertThat(ExportProgressTicker.calculateEta(20, 4_000L)).isEqualTo(16_000L)

        // 80% at 8s -> Total 10s -> ETA 2s
        assertThat(ExportProgressTicker.calculateEta(80, 8_000L)).isEqualTo(2_000L)

        // 100% -> 0
        assertThat(ExportProgressTicker.calculateEta(100, 15_000L)).isEqualTo(0L)
    }
}
