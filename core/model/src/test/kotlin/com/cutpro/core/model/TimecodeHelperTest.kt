package com.cutpro.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TimecodeHelperTest {

    @Test
    fun formatSmpte_formatsZeroCorrectly() {
        val str = TimecodeHelper.formatSmpte(0L, fps = 30)
        assertThat(str).isEqualTo("00:00:00:00")
    }

    @Test
    fun formatSmpte_formatsSubSecondFramesCorrectly() {
        val halfSecondUs = 500_000L
        assertThat(TimecodeHelper.formatSmpte(halfSecondUs, fps = 30)).isEqualTo("00:00:00:15")
        assertThat(TimecodeHelper.formatSmpte(halfSecondUs, fps = 24)).isEqualTo("00:00:00:12")
        assertThat(TimecodeHelper.formatSmpte(halfSecondUs, fps = 60)).isEqualTo("00:00:00:30")
    }

    @Test
    fun formatSmpte_and_parseSmpte_roundTripFidelity() {
        val timeUs = 3661_500_000L // 1h 1m 1s 15 frames at 30 fps
        val formatted = TimecodeHelper.formatSmpte(timeUs, fps = 30)
        assertThat(formatted).isEqualTo("01:01:01:15")

        val parsedUs = TimecodeHelper.parseSmpte(formatted, fps = 30)
        val frameDurationUs = 1_000_000L / 30L
        assertThat(kotlin.math.abs(parsedUs - timeUs)).isLessThan(frameDurationUs)
    }

    @Test
    fun formatSrt_and_parseSrtTimecode_roundTripFidelity() {
        val timeUs = 3723_456_000L // 1h 2m 3s 456ms
        val srtStr = TimecodeHelper.formatSrt(timeUs)
        assertThat(srtStr).isEqualTo("01:02:03,456")

        val parsedUs = TimecodeHelper.parseSrtTimecode(srtStr)
        assertThat(parsedUs).isEqualTo(timeUs)
    }

    @Test
    fun formatVtt_and_parseVttTimecode_roundTripFidelity() {
        val timeUs = 3723_456_000L
        val vttStr = TimecodeHelper.formatVtt(timeUs)
        assertThat(vttStr).isEqualTo("01:02:03.456")

        val parsedUs = TimecodeHelper.parseVttTimecode(vttStr)
        assertThat(parsedUs).isEqualTo(timeUs)
    }

    @Test
    fun parseVttTimecode_parsesShortFormat() {
        val parsedUs = TimecodeHelper.parseVttTimecode("02:03.456")
        assertThat(parsedUs).isEqualTo((2 * 60 + 3) * 1_000_000L + 456_000L)
    }

    @Test
    fun parseSmpte_invalidFormat_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            TimecodeHelper.parseSmpte("00:00:00", fps = 30)
        }
    }
}
