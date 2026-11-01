package com.cutpro.core.subtitles.karaoke

import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.TimedWord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KaraokeEngineTest {

    private val sampleCue = CaptionCue(
        startTimeUs = 1_000_000L,
        endTimeUs = 4_000_000L,
        text = "Hello world of CutPro",
        words = listOf(
            TimedWord("Hello", 1_000_000L, 1_500_000L),
            TimedWord("world", 1_500_000L, 2_200_000L),
            TimedWord("of", 2_200_000L, 2_600_000L),
            TimedWord("CutPro", 2_600_000L, 4_000_000L)
        )
    )

    @Test
    fun findActiveCue_atValidTimestamp() {
        val cues = listOf(sampleCue)
        val active = KaraokeEngine.findActiveCue(cues, 2_000_000L)
        assertThat(active).isNotNull()
        assertThat(active?.id).isEqualTo(sampleCue.id)

        val before = KaraokeEngine.findActiveCue(cues, 500_000L)
        assertThat(before).isNull()

        val after = KaraokeEngine.findActiveCue(cues, 4_500_000L)
        assertThat(after).isNull()
    }

    @Test
    fun findActiveWord_identifiesExactWordAtTimestamp() {
        // At 1.2s -> "Hello"
        assertThat(KaraokeEngine.findActiveWord(sampleCue, 1_200_000L)?.word).isEqualTo("Hello")
        // At 1.8s -> "world"
        assertThat(KaraokeEngine.findActiveWord(sampleCue, 1_800_000L)?.word).isEqualTo("world")
        // At 2.4s -> "of"
        assertThat(KaraokeEngine.findActiveWord(sampleCue, 2_400_000L)?.word).isEqualTo("of")
        // At 3.0s -> "CutPro"
        assertThat(KaraokeEngine.findActiveWord(sampleCue, 3_000_000L)?.word).isEqualTo("CutPro")
    }

    @Test
    fun calculateWordProgress_returnsNormalizedFraction() {
        val word = TimedWord("world", 1_000_000L, 2_000_000L)
        assertThat(KaraokeEngine.calculateWordProgress(word, 1_000_000L)).isEqualTo(0.0f)
        assertThat(KaraokeEngine.calculateWordProgress(word, 1_500_000L)).isEqualTo(0.5f)
        assertThat(KaraokeEngine.calculateWordProgress(word, 2_000_000L)).isEqualTo(1.0f)
    }
}
