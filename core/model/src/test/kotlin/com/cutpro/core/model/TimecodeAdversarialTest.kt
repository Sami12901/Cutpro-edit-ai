package com.cutpro.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Adversarial stress testing for TimecodeHelper and Timecode.
 * Challenges:
 * - Boundary frame transitions across hour rollovers (1h, 2h, 24h, 100h)
 * - Nominal and broadcast standard framerates: 24, 30, 60 fps and fractional NTSC rates (23.976, 29.97, 59.94)
 * - Microsecond edge cases immediately preceding and succeeding rollover ticks
 * - Srt and Vtt formatting/parsing roundtrips under extreme offsets
 * - Robust rejection of corrupt, malformed, or out-of-range timecode strings
 */
class TimecodeAdversarialTest {

    private val standardFramerates = listOf(24, 30, 60)
    private val ntscFractionalRates = listOf(23.976f, 29.97f, 59.94f)

    /**
     * EMPIRICAL DEFECT DEMONSTRATION:
     * At 24 fps, frame duration is 41666.666... us.
     * When Timecode(0, 0, 0, 1, 24).toUs() is evaluated, integer floor division yields 41666 us.
     * When passing 41666 us to TimecodeHelper.fromUs(41666L, 24), floor division yields:
     * (41666 * 24) / 1_000_000 = 999984 / 1_000_000 = 0!
     * Because of lack of midpoint rounding ((remainderUs * fps + 500_000L) / 1_000_000L),
     * frame 1 drops to frame 0, violating roundtrip fidelity and dropping frames across video playback.
     */
    @Test
    fun challengeTimecode_frameQuantizationDrop_at24Fps() {
        val originalTc = Timecode(hours = 0, minutes = 0, seconds = 0, frames = 1, fps = 24)
        val us = originalTc.toUs()
        val roundtripTc = TimecodeHelper.fromUs(us, 24)
        assertWithMessage("Frame 1 at 24 fps must not drop to Frame 0").that(roundtripTc.frames).isEqualTo(1)
    }

    @Test
    fun challengeHourRollover_boundaryMicrosecondTransitions() {
        for (fps in standardFramerates) {
            val frameDurationUs = (1_000_000.0 / fps).toLong()

            // One hour in microseconds: 3,600 seconds * 1,000,000 us = 3,600,000,000 us
            val oneHourUs = 3_600_000_000L

            // 1 microsecond before 1 hour: 00:59:59:(fps-1)
            val preRolloverUs = oneHourUs - 1L
            val preTc = TimecodeHelper.fromUs(preRolloverUs, fps)
            assertWithMessage("Pre-rollover hours at $fps fps").that(preTc.hours).isEqualTo(0)
            assertWithMessage("Pre-rollover minutes at $fps fps").that(preTc.minutes).isEqualTo(59)
            assertWithMessage("Pre-rollover seconds at $fps fps").that(preTc.seconds).isEqualTo(59)
            assertWithMessage("Pre-rollover frames at $fps fps").that(preTc.frames).isEqualTo(fps - 1)

            // Exactly at 1 hour: 01:00:00:00
            val exactTc = TimecodeHelper.fromUs(oneHourUs, fps)
            assertWithMessage("Exact rollover hours at $fps fps").that(exactTc.hours).isEqualTo(1)
            assertWithMessage("Exact rollover minutes at $fps fps").that(exactTc.minutes).isEqualTo(0)
            assertWithMessage("Exact rollover seconds at $fps fps").that(exactTc.seconds).isEqualTo(0)
            assertWithMessage("Exact rollover frames at $fps fps").that(exactTc.frames).isEqualTo(0)

            // Exactly 1 frame into the second hour
            val postFrameTc = TimecodeHelper.fromUs(oneHourUs + frameDurationUs, fps)
            assertWithMessage("Post-frame hours at $fps fps").that(postFrameTc.hours).isEqualTo(1)
            assertWithMessage("Post-frame minutes at $fps fps").that(postFrameTc.minutes).isEqualTo(0)
            assertWithMessage("Post-frame seconds at $fps fps").that(postFrameTc.seconds).isEqualTo(0)
            assertWithMessage("Post-frame frames at $fps fps").that(postFrameTc.frames).isEqualTo(1)
        }
    }

    @Test
    fun challengeMultiHourRollovers_24HoursAnd100Hours() {
        for (fps in standardFramerates) {
            // 24 Hour Rollover
            val dayUs = 24L * 3_600_000_000L
            val preDayTc = TimecodeHelper.fromUs(dayUs - 1L, fps)
            assertThat(preDayTc.hours).isEqualTo(23)
            assertThat(preDayTc.minutes).isEqualTo(59)
            assertThat(preDayTc.seconds).isEqualTo(59)
            assertThat(preDayTc.frames).isEqualTo(fps - 1)

            val exactDayTc = TimecodeHelper.fromUs(dayUs, fps)
            assertThat(exactDayTc.hours).isEqualTo(24)
            assertThat(exactDayTc.minutes).isEqualTo(0)
            assertThat(exactDayTc.seconds).isEqualTo(0)
            assertThat(exactDayTc.frames).isEqualTo(0)

            // 100 Hour Boundary
            val hundredHoursUs = 100L * 3_600_000_000L
            val exactHundredTc = TimecodeHelper.fromUs(hundredHoursUs, fps)
            assertThat(exactHundredTc.hours).isEqualTo(100)
            assertThat(exactHundredTc.minutes).isEqualTo(0)
            assertThat(exactHundredTc.seconds).isEqualTo(0)
            assertThat(exactHundredTc.frames).isEqualTo(0)
        }
    }

    @Test
    fun challengeFractionalNtscRates_nominalFrameMappingAcrossRollovers() {
        // NTSC fractional rates (23.976, 29.97, 59.94) map to nominal integers (24, 30, 60) for SMPTE timecode
        for (fRate in ntscFractionalRates) {
            val nominalFps = fRate.roundToInt()
            val frameDurationUs = (1_000_000.0 / fRate).toLong()

            // Test 1-hour rollover with nominal fps
            val oneHourUs = 3_600_000_000L
            val rolloverTc = TimecodeHelper.fromUs(oneHourUs, nominalFps)
            assertThat(rolloverTc.hours).isEqualTo(1)
            assertThat(rolloverTc.minutes).isEqualTo(0)
            assertThat(rolloverTc.seconds).isEqualTo(0)
            assertThat(rolloverTc.frames).isEqualTo(0)

            // Test final frame before rollover
            val lastFrameUs = oneHourUs - frameDurationUs
            val lastFrameTc = TimecodeHelper.fromUs(lastFrameUs, nominalFps)
            assertThat(lastFrameTc.hours).isEqualTo(0)
            assertThat(lastFrameTc.minutes).isEqualTo(59)
            assertThat(lastFrameTc.seconds).isEqualTo(59)
        }
    }

    @Test
    fun challengeSmpteFormattingAndParsing_roundTripFidelity() {
        val testTimestamps = listOf(
            0L,
            999_999L,
            1_000_000L,
            3_599_999_999L,
            3_600_000_000L,
            86_400_000_000L,
            359_999_999_000L
        )

        for (fps in standardFramerates) {
            val maxAllowedDriftUs = 1_000_000L / fps
            for (ts in testTimestamps) {
                val formatted = TimecodeHelper.formatSmpte(ts, fps)
                val parsedUs = TimecodeHelper.parseSmpte(formatted, fps)

                // Quantization to frame interval: parsedUs must be within 1 frame interval of original
                val drift = kotlin.math.abs(parsedUs - ts)
                assertWithMessage("SMPTE roundtrip drift for ts=$ts at $fps fps").that(drift).isAtMost(maxAllowedDriftUs)
            }
        }
    }

    @Test
    fun challengeSrtAndVttFormattingAndParsing_microsecondPrecision() {
        val testOffsets = listOf(
            0L,
            1000L,       // 1 ms
            999_000L,    // 999 ms
            1_000_000L,  // 1 s
            3_600_123_456L,
            86_400_987_654L
        )

        for (ts in testOffsets) {
            // SRT format: HH:MM:SS,mmm
            val srt = TimecodeHelper.formatSrt(ts)
            val parsedSrtUs = TimecodeHelper.parseSrtTimecode(srt)
            // Millisecond granularity: drift must be less than 1 ms (1000 us)
            assertThat(kotlin.math.abs(parsedSrtUs - ts)).isLessThan(1000L)

            // VTT format: HH:MM:SS.mmm
            val vtt = TimecodeHelper.formatVtt(ts)
            val parsedVttUs = TimecodeHelper.parseVttTimecode(vtt)
            assertThat(kotlin.math.abs(parsedVttUs - ts)).isLessThan(1000L)
        }
    }

    @Test
    fun challengeTimecode_malformedInputs_strictlyRejected() {
        // Negative components in Timecode constructor
        assertThrows(IllegalArgumentException::class.java) {
            Timecode(hours = -1, minutes = 0, seconds = 0, frames = 0, fps = 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Timecode(hours = 0, minutes = 60, seconds = 0, frames = 0, fps = 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Timecode(hours = 0, minutes = 0, seconds = 60, frames = 0, fps = 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Timecode(hours = 0, minutes = 0, seconds = 0, frames = 30, fps = 30) // frames must be < fps
        }
        assertThrows(IllegalArgumentException::class.java) {
            Timecode(hours = 0, minutes = 0, seconds = 0, frames = 0, fps = 0)
        }

        // Malformed SMPTE strings
        assertThrows(IllegalArgumentException::class.java) {
            TimecodeHelper.parseSmpte("invalid", 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimecodeHelper.parseSmpte("00:00:00", 30) // Missing frames component
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimecodeHelper.parseSmpte("00:00:00:00:00", 30) // Too many components
        }

        // Malformed SRT strings
        assertThrows(IllegalArgumentException::class.java) {
            TimecodeHelper.parseSrtTimecode("00:00:00.000") // Uses dot instead of comma
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimecodeHelper.parseSrtTimecode("00:00:00")
        }

        // Malformed VTT strings
        assertThrows(IllegalArgumentException::class.java) {
            TimecodeHelper.parseVttTimecode("00:00:00,000") // Uses comma instead of dot
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimecodeHelper.parseVttTimecode("not-a-vtt-timestamp")
        }
    }

    @Test
    fun challengeTimecode_generativeRandomTimestampFuzzing() {
        val rng = Random(9999)
        for (i in 0 until 5000) {
            val randomUs = rng.nextLong(0L, 100L * 3_600_000_000L) // up to 100 hours
            val fps = standardFramerates[rng.nextInt(standardFramerates.size)]

            val tc = TimecodeHelper.fromUs(randomUs, fps)
            assertThat(tc.hours).isAtLeast(0)
            assertThat(tc.minutes).isIn(0..59)
            assertThat(tc.seconds).isIn(0..59)
            assertThat(tc.frames).isIn(0 until fps)

            val backToUs = tc.toUs()
            val maxFrameDrift = 1_000_000L / fps
            assertThat(kotlin.math.abs(backToUs - randomUs)).isAtMost(maxFrameDrift)
        }
    }
}
