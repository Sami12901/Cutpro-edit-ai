package com.cutpro.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

/**
 * Adversarial stress and fuzzing test harness for TimelineClip and ClipTrimHelper.
 * Challenges:
 * - Negative and zero bounds for timestamps and durations
 * - Trim bounds inversions and media source duration overflows
 * - Speed multiplier limits, NaN, Infinity, and boundary conditions
 * - Speed-to-timeline-duration relationship invariants
 * - Long-timeline float-precision drift during split and trim operations
 * - Invariant maintenance under extensive generative fuzzing
 */
class TimelineClipAdversarialTest {

    private val validMedia = MediaSourceInfo(
        uriString = "content://media/adversarial/video",
        fileName = "stress_test.mp4",
        mimeType = "video/mp4",
        durationUs = 3600_000_000L, // 1 hour
        width = 1920,
        height = 1080
    )

    @Test
    fun challengeNegativeTimestamps_strictlyRejected() {
        // Negative timelineStartUs
        val negativeStarts = listOf(-1L, -100L, -1_000_000L, Long.MIN_VALUE)
        for (negStart in negativeStarts) {
            assertThrows("Expected failure for negative timelineStartUs=$negStart", IllegalArgumentException::class.java) {
                TimelineClip(
                    trackId = "track-1",
                    mediaSource = validMedia,
                    timelineStartUs = negStart,
                    timelineDurationUs = 1_000_000L,
                    sourceInUs = 0L,
                    sourceOutUs = 1_000_000L
                )
            }
        }

        // Negative sourceInUs
        val negativeSourceIns = listOf(-1L, -500L, -10_000_000L, Long.MIN_VALUE)
        for (negIn in negativeSourceIns) {
            assertThrows("Expected failure for negative sourceInUs=$negIn", IllegalArgumentException::class.java) {
                TimelineClip(
                    trackId = "track-1",
                    mediaSource = validMedia,
                    timelineStartUs = 0L,
                    timelineDurationUs = 1_000_000L,
                    sourceInUs = negIn,
                    sourceOutUs = 1_000_000L
                )
            }
        }
    }

    @Test
    fun challengeZeroOrNegativeDurations_strictlyRejected() {
        // Non-positive timelineDurationUs
        val invalidDurations = listOf(0L, -1L, -1_000_000L, Long.MIN_VALUE)
        for (invDur in invalidDurations) {
            assertThrows("Expected failure for timelineDurationUs=$invDur", IllegalArgumentException::class.java) {
                TimelineClip(
                    trackId = "track-1",
                    mediaSource = validMedia,
                    timelineStartUs = 0L,
                    timelineDurationUs = invDur,
                    sourceInUs = 0L,
                    sourceOutUs = 1_000_000L
                )
            }
        }
    }

    @Test
    fun challengeTrimBoundsInversions_strictlyRejected() {
        // sourceOutUs == sourceInUs (zero trim duration)
        assertThrows("Zero trim duration must fail", IllegalArgumentException::class.java) {
            TimelineClip(
                trackId = "track-1",
                mediaSource = validMedia,
                timelineStartUs = 0L,
                timelineDurationUs = 1_000_000L,
                sourceInUs = 5_000_000L,
                sourceOutUs = 5_000_000L
            )
        }

        // sourceOutUs < sourceInUs (inverted trim boundaries)
        val invertedBounds = listOf(
            Pair(5_000_000L, 4_999_999L),
            Pair(10_000_000L, 0L),
            Pair(1_000_000L, -5L)
        )
        for ((inUs, outUs) in invertedBounds) {
            assertThrows("Inverted trim boundaries [$inUs, $outUs] must fail", IllegalArgumentException::class.java) {
                TimelineClip(
                    trackId = "track-1",
                    mediaSource = validMedia,
                    timelineStartUs = 0L,
                    timelineDurationUs = 1_000_000L,
                    sourceInUs = inUs,
                    sourceOutUs = outUs
                )
            }
        }

        // sourceOutUs > mediaSource.durationUs (exceeds media file)
        assertThrows("sourceOutUs exceeding media duration must fail", IllegalArgumentException::class.java) {
            TimelineClip(
                trackId = "track-1",
                mediaSource = validMedia,
                timelineStartUs = 0L,
                timelineDurationUs = 1_000_000L,
                sourceInUs = 0L,
                sourceOutUs = validMedia.durationUs + 1L
            )
        }
    }

    @Test
    fun challengeSpeedMultipliers_adversarialValues_strictlyValidated() {
        // Speeds below 0.1x
        val tooSlowSpeeds = listOf(-1.0f, -0.1f, 0.0f, 0.05f, 0.099f)
        for (spd in tooSlowSpeeds) {
            assertThrows("Speed $spd < 0.1 must fail", IllegalArgumentException::class.java) {
                val dur = (1_000_000L / spd.coerceAtLeast(0.001f)).toLong()
                TimelineClip(
                    trackId = "track-1",
                    mediaSource = validMedia,
                    timelineStartUs = 0L,
                    timelineDurationUs = dur,
                    sourceInUs = 0L,
                    sourceOutUs = 1_000_000L,
                    speedMultiplier = spd
                )
            }
        }

        // Speeds above 10.0x
        val tooFastSpeeds = listOf(10.001f, 10.1f, 15.0f, 100.0f)
        for (spd in tooFastSpeeds) {
            assertThrows("Speed $spd > 10.0 must fail", IllegalArgumentException::class.java) {
                val dur = (1_000_000L / spd).toLong()
                TimelineClip(
                    trackId = "track-1",
                    mediaSource = validMedia,
                    timelineStartUs = 0L,
                    timelineDurationUs = dur,
                    sourceInUs = 0L,
                    sourceOutUs = 1_000_000L,
                    speedMultiplier = spd
                )
            }
        }

        // Non-finite speed values: NaN, PosInf, NegInf
        val nonFiniteSpeeds = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (spd in nonFiniteSpeeds) {
            assertThrows("Non-finite speed $spd must fail", IllegalArgumentException::class.java) {
                TimelineClip(
                    trackId = "track-1",
                    mediaSource = validMedia,
                    timelineStartUs = 0L,
                    timelineDurationUs = 1_000_000L,
                    sourceInUs = 0L,
                    sourceOutUs = 1_000_000L,
                    speedMultiplier = spd
                )
            }
        }

        // Exact boundary speeds (0.1f and 10.0f) must succeed
        val clipMinSpeed = TimelineClip(
            trackId = "track-1",
            mediaSource = validMedia,
            timelineStartUs = 0L,
            timelineDurationUs = (1_000_000L / 0.1f).toLong(),
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
            speedMultiplier = 0.1f
        )
        assertThat(clipMinSpeed.speedMultiplier).isEqualTo(0.1f)

        val clipMaxSpeed = TimelineClip(
            trackId = "track-1",
            mediaSource = validMedia,
            timelineStartUs = 0L,
            timelineDurationUs = (1_000_000L / 10.0f).toLong(),
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
            speedMultiplier = 10.0f
        )
        assertThat(clipMaxSpeed.speedMultiplier).isEqualTo(10.0f)
    }

    @Test
    fun challengeSpeedToDurationDivergence_strictlyRejected() {
        // Discrepancy > 1000us between timelineDurationUs and trimDuration / speed
        assertThrows(IllegalArgumentException::class.java) {
            TimelineClip(
                trackId = "track-1",
                mediaSource = validMedia,
                timelineStartUs = 0L,
                timelineDurationUs = 5_000_000L, // Claims 5s
                sourceInUs = 0L,
                sourceOutUs = 5_000_000L, // Trim is 5s
                speedMultiplier = 2.0f // But 2x speed demands 2.5s! Divergence = 2.5s > 1ms margin
            )
        }
    }

    @Test
    fun challengeTimestampMappingBoundaries_halfOpenAndClosedIntervals() {
        val clip = TimelineClip(
            trackId = "track-1",
            mediaSource = validMedia,
            timelineStartUs = 2_000_000L,
            timelineDurationUs = 4_000_000L, // [2s, 6s)
            sourceInUs = 1_000_000L,
            sourceOutUs = 9_000_000L, // [1s, 9s] -> trim duration 8s at 2.0x = 4s timeline
            speedMultiplier = 2.0f
        )

        // Half-open check: contains timelineStartUs, excludes timelineEndUs
        assertThat(clip.containsTimelineTimestamp(1_999_999L)).isFalse()
        assertThat(clip.containsTimelineTimestamp(2_000_000L)).isTrue()
        assertThat(clip.containsTimelineTimestamp(5_999_999L)).isTrue()
        assertThat(clip.containsTimelineTimestamp(6_000_000L)).isFalse()

        // Mapping timeline to source
        assertThat(clip.timelineToSourceTimestamp(2_000_000L)).isEqualTo(1_000_000L)
        assertThat(clip.timelineToSourceTimestamp(4_000_000L)).isEqualTo(1_000_000L + (2_000_000L * 2L))

        // Out of bounds timelineToSource must throw
        assertThrows(IllegalArgumentException::class.java) {
            clip.timelineToSourceTimestamp(1_999_999L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            clip.timelineToSourceTimestamp(6_000_000L)
        }

        // Mapping source to timeline
        assertThat(clip.sourceToTimelineTimestamp(1_000_000L)).isEqualTo(2_000_000L)
        assertThat(clip.sourceToTimelineTimestamp(9_000_000L)).isEqualTo(6_000_000L)

        // Out of bounds sourceToTimeline must throw
        assertThrows(IllegalArgumentException::class.java) {
            clip.sourceToTimelineTimestamp(999_999L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            clip.sourceToTimelineTimestamp(9_000_001L)
        }
    }

    /**
     * EMPIRICAL DEFECT DEMONSTRATION:
     * When trimming a clip's in-point to the left, if the requested in-point expands the clip
     * earlier than the start of the project timeline (t=0), ClipTrimHelper.trimIn computes a
     * negative timelineStartUs and invokes TimelineClip.init, crashing with IllegalArgumentException
     * instead of gracefully clamping to timelineStartUs >= 0L.
     */
    @Test
    fun challengeTrimIn_extendingPastTimelineZero_crashesWithNegativeTimelineStart() {
        val clip = TimelineClip(
            trackId = "track-1",
            mediaSource = validMedia,
            timelineStartUs = 2_000_000L, // Starts at 2s on timeline
            timelineDurationUs = 5_000_000L,
            sourceInUs = 10_000_000L,     // 10s into media
            sourceOutUs = 15_000_000L
        )

        // Trimming in-point to 0s expands source by 10s, pushing timelineStartUs back by 10s:
        // 2s - 10s = -8s!
        val trimmed = ClipTrimHelper.trimIn(clip, requestedNewInUs = 0L)
        assertThat(trimmed.timelineStartUs).isAtLeast(0L)
    }

    @Test
    fun challengeClipTrimHelper_generativeFuzzingAcrossRandomEdits() {
        val rng = Random(777)
        var currentClip = TimelineClip(
            trackId = "track-fuzz",
            mediaSource = validMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            speedMultiplier = 1.0f
        )

        for (i in 0 until 500) {
            val op = rng.nextInt(4)
            when (op) {
                0 -> { // Reposition
                    val newStart = rng.nextLong(0L, 50_000_000L)
                    currentClip = ClipTrimHelper.reposition(currentClip, newStart)
                    assertThat(currentClip.timelineStartUs).isEqualTo(newStart)
                }
                1 -> { // Trim In
                    val maxIn = currentClip.sourceOutUs - 200_000L
                    if (maxIn > 0L) {
                        val requestedIn = rng.nextLong(0L, maxIn)
                        currentClip = ClipTrimHelper.trimIn(currentClip, requestedIn)
                        assertThat(currentClip.sourceInUs).isAtLeast(0L)
                        assertThat(currentClip.sourceInUs).isLessThan(currentClip.sourceOutUs)
                    }
                }
                2 -> { // Trim Out
                    val minOut = currentClip.sourceInUs + 200_000L
                    if (minOut < validMedia.durationUs) {
                        val requestedOut = rng.nextLong(minOut, validMedia.durationUs)
                        currentClip = ClipTrimHelper.trimOut(currentClip, requestedOut)
                        assertThat(currentClip.sourceOutUs).isGreaterThan(currentClip.sourceInUs)
                        assertThat(currentClip.sourceOutUs).isAtMost(validMedia.durationUs)
                    }
                }
                3 -> { // Split
                    if (currentClip.timelineDurationUs > 300_000L) {
                        val minSplit = currentClip.timelineStartUs + 100_000L
                        val maxSplit = currentClip.timelineEndUs - 100_000L
                        val splitPts = rng.nextLong(minSplit, maxSplit)
                        val (left, right) = ClipTrimHelper.split(currentClip, splitPts)
                        assertThat(left.timelineEndUs).isEqualTo(splitPts)
                        assertThat(right.timelineStartUs).isEqualTo(splitPts)
                        // Continue fuzzing the larger half
                        currentClip = if (left.timelineDurationUs >= right.timelineDurationUs) left else right
                    }
                }
            }
        }
    }
}
