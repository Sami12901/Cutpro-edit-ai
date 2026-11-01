package com.cutpro.core.ai.assistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeterministicNlpParserTest {

    @Test
    fun parse_removeSilences_extractsCorrectIntentAndThreshold() {
        val result = DeterministicNlpParser.parse("Please remove all silences below -35db")
        assertThat(result.intent).isEqualTo(EditIntent.REMOVE_SILENCE)
        assertThat(result.options.silenceThresholdDb).isEqualTo(-35.0f)
        assertThat(result.confidence).isAtLeast(0.9f)
    }

    @Test
    fun parse_tightenCuts_extractsJumpCutIntent() {
        val result = DeterministicNlpParser.parse("tighten cuts and punch up pacing")
        assertThat(result.intent).isEqualTo(EditIntent.OPTIMIZE_JUMP_CUTS)
        assertThat(result.confidence).isAtLeast(0.85f)
    }

    @Test
    fun parse_pacing_extractsTalkingHeadIntent() {
        val result = DeterministicNlpParser.parse("break up long takes and check talking head pacing")
        assertThat(result.intent).isEqualTo(EditIntent.ANALYZE_PACING)
        assertThat(result.confidence).isAtLeast(0.85f)
    }

    @Test
    fun parse_speedUp1_5x_extractsNumericMultiplier() {
        val result = DeterministicNlpParser.parse("speed up 1.5x")
        assertThat(result.intent).isEqualTo(EditIntent.ADJUST_SPEED)
        assertThat(result.speedMultiplier).isEqualTo(1.5f)
    }

    @Test
    fun parse_doubleSpeed_extractsMultiplier() {
        val result = DeterministicNlpParser.parse("playback double speed")
        assertThat(result.intent).isEqualTo(EditIntent.ADJUST_SPEED)
        assertThat(result.speedMultiplier).isEqualTo(2.0f)
    }

    @Test
    fun parse_bengaliCaptions_extractsLanguageCode() {
        val result = DeterministicNlpParser.parse("transcribe audio and generate Bengali subtitles")
        assertThat(result.intent).isEqualTo(EditIntent.GENERATE_CAPTIONS)
        assertThat(result.options.languageCode).isEqualTo("bn")
    }

    @Test
    fun parse_applyTemplate_extractsTemplateId() {
        val result = DeterministicNlpParser.parse("apply minimal modern template")
        assertThat(result.intent).isEqualTo(EditIntent.APPLY_TEMPLATE)
        assertThat(result.templateId).isEqualTo("minimal_modern_title")
    }

    @Test
    fun parse_durationExtraction_secondsAndMillis() {
        val secResult = DeterministicNlpParser.parse("remove dead air longer than 2.5s")
        assertThat(secResult.options.minSilenceDurationUs).isEqualTo(2_500_000L)

        val msResult = DeterministicNlpParser.parse("strip pauses over 600ms")
        assertThat(msResult.options.minSilenceDurationUs).isEqualTo(600_000L)
    }

    @Test
    fun parse_unknownPrompt_cleanFallback() {
        val result = DeterministicNlpParser.parse("make a cup of coffee")
        assertThat(result.intent).isEqualTo(EditIntent.UNKNOWN)
        assertThat(result.confidence).isEqualTo(0.0f)
    }
}
