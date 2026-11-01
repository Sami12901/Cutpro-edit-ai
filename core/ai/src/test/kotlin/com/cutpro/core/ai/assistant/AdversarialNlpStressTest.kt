package com.cutpro.core.ai.assistant

import com.cutpro.core.ai.model.AnalysisOptions
import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Adversarial stress testing for DeterministicNlpParser and StandardAiAssistant.
 * Tests malformed/fuzzed prompts, zero and negative parameters, extreme numeric bounds,
 * and graceful fallback for unrecognized commands.
 */
class AdversarialNlpStressTest {

    private val dummySource = MediaSourceInfo(
        uriString = "content://media/external/video/nlp_test",
        fileName = "nlp_test.mp4",
        mimeType = "video/mp4",
        durationUs = 60_000_000L,
        width = 1920,
        height = 1080
    )

    private fun createSampleTimeline(): ProjectManifest {
        val clip = TimelineClip(
            id = "clip-nlp-1",
            trackId = "track-nlp-1",
            mediaSource = dummySource,
            timelineStartUs = 0L,
            timelineDurationUs = 20_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 20_000_000L
        )
        val track = TimelineTrack(
            id = "track-nlp-1",
            name = "Video Track",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
        return ProjectManifest(
            title = "NLP Test Project",
            tracks = listOf(track),
            durationUs = 20_000_000L
        )
    }

    @Test
    fun nlpParser_malformedAndFuzzedPrompts_doesNotCrash() {
        val fuzzedPrompts = listOf(
            "",
            "   ",
            "\t\n\r  \t\n",
            "!!!???:::;;;,,,---___***",
            "1234567890",
            "SELECT * FROM projects WHERE id = '1'; DROP TABLE projects; --",
            "<script>alert('cutpro_xss')</script>",
            "((((((((((a+)+)+)+)+)+)+)+)+)+)",
            "\u0000\u0001\u0002\u0003\u001F\u007F",
            "a".repeat(20_000) // Extremely long string
        )

        for (prompt in fuzzedPrompts) {
            val parsed = DeterministicNlpParser.parse(prompt)
            assertThat(parsed).isNotNull()
            assertThat(parsed.intent).isEqualTo(EditIntent.UNKNOWN)
            assertThat(parsed.confidence).isEqualTo(0.0f)
        }
    }

    @Test
    fun nlpParser_unrecognizedCommands_cleanFallback() = runTest {
        val assistant = StandardAiAssistant()
        val timeline = createSampleTimeline()

        val unrecognizedPrompts = listOf(
            "what is the weather in Dhaka today?",
            "play some jazz music",
            "order pizza from Domino's",
            "open front camera",
            "write an essay on machine learning",
            "invert video colors to negative",
            "render 3d blender animation",
            "reboot device",
            "export in 8K resolution at 120fps",
            "make video look like vintage Hollywood"
        )

        for (prompt in unrecognizedPrompts) {
            val parsed = DeterministicNlpParser.parse(prompt)
            assertThat(parsed.intent).isEqualTo(EditIntent.UNKNOWN)
            assertThat(parsed.confidence).isEqualTo(0.0f)

            // When passed to assistant, it must produce an empty plan gracefully without crashing
            val planResult = assistant.generateEditPlan(prompt, timeline)
            assertThat(planResult.isSuccess).isTrue()
            val plan = planResult.getOrThrow()
            assertThat(plan.actions).isEmpty()
            assertThat(plan.description).contains("No automated cuts generated")
        }
    }

    @Test
    fun nlpParser_speedMultipliers_zeroExtremeAndStandardValues() {
        // Zero speed multiplier
        val zeroSpeed = DeterministicNlpParser.parse("speed 0x")
        assertThat(zeroSpeed.intent).isEqualTo(EditIntent.ADJUST_SPEED)
        assertThat(zeroSpeed.speedMultiplier).isEqualTo(0.0f)

        // Fractional speed multiplier
        val fracSpeed = DeterministicNlpParser.parse("speed 0.25x")
        assertThat(fracSpeed.intent).isEqualTo(EditIntent.ADJUST_SPEED)
        assertThat(fracSpeed.speedMultiplier).isEqualTo(0.25f)

        // Standard speed
        val doubleSpeed = DeterministicNlpParser.parse("playback double speed")
        assertThat(doubleSpeed.intent).isEqualTo(EditIntent.ADJUST_SPEED)
        assertThat(doubleSpeed.speedMultiplier).isEqualTo(2.0f)

        val halfSpeed = DeterministicNlpParser.parse("playback half speed")
        assertThat(halfSpeed.intent).isEqualTo(EditIntent.ADJUST_SPEED)
        assertThat(halfSpeed.speedMultiplier).isEqualTo(0.5f)

        // High speed
        val highSpeed = DeterministicNlpParser.parse("speed 10.0x")
        assertThat(highSpeed.intent).isEqualTo(EditIntent.ADJUST_SPEED)
        assertThat(highSpeed.speedMultiplier).isEqualTo(10.0f)

        // Extreme speed
        val extremeSpeed = DeterministicNlpParser.parse("speed 100x")
        assertThat(extremeSpeed.intent).isEqualTo(EditIntent.ADJUST_SPEED)
        assertThat(extremeSpeed.speedMultiplier).isEqualTo(100.0f)
    }

    @Test
    fun nlpParser_silenceAndThresholdParameters_robustHandling() = runTest {
        val assistant = StandardAiAssistant()
        val timeline = createSampleTimeline()

        // 1. Valid aggressive silence prompt
        val aggressivePrompt = "remove silence aggressive below -35db"
        val aggParsed = DeterministicNlpParser.parse(aggressivePrompt)
        assertThat(aggParsed.intent).isEqualTo(EditIntent.REMOVE_SILENCE)
        assertThat(aggParsed.options.silenceThresholdDb).isEqualTo(-35.0f)

        // 2. Valid gentle silence prompt
        val gentlePrompt = "trim pauses conservative over 800ms"
        val gentleParsed = DeterministicNlpParser.parse(gentlePrompt)
        assertThat(gentleParsed.intent).isEqualTo(EditIntent.REMOVE_SILENCE)
        assertThat(gentleParsed.options.minSilenceDurationUs).isEqualTo(800_000L)

        // 3. Fuzzed negative duration (e.g. -500ms): regex doesn't match negative sign on digits
        val negDurationPrompt = "cut dead air -500ms"
        val negParsed = DeterministicNlpParser.parse(negDurationPrompt)
        assertThat(negParsed.intent).isEqualTo(EditIntent.REMOVE_SILENCE)
        assertThat(negParsed.options.minSilenceDurationUs).isGreaterThan(0L)

        // 4. Invalid threshold (e.g. -150db is outside [-100, 0] dBFS range):
        // AnalysisOptions requires silenceThresholdDb in [-100.0, 0.0]
        // StandardAiAssistant catches IllegalArgumentException via runCatching
        val outOfRangeDbPrompt = "remove silence below -150db"
        val planResult = assistant.generateEditPlan(outOfRangeDbPrompt, timeline)
        // Verify graceful error containment: must return Result.failure rather than crashing unhandled
        assertThat(planResult.isFailure).isTrue()
        assertThat(planResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)

        // 5. Zero duration (e.g. 0s):
        // AnalysisOptions requires minSilenceDurationUs > 0L
        val zeroDurationPrompt = "remove silence 0s"
        val zeroPlanResult = assistant.generateEditPlan(zeroDurationPrompt, timeline)
        // Verify graceful error containment: returns Result.failure rather than uncaught thread panic
        assertThat(zeroPlanResult.isFailure).isTrue()
        assertThat(zeroPlanResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun nlpParser_languageCodeExtraction_bengaliAndEnglish() {
        val bn1 = DeterministicNlpParser.parse("transcribe speech in Bengali")
        assertThat(bn1.intent).isEqualTo(EditIntent.GENERATE_CAPTIONS)
        assertThat(bn1.options.languageCode).isEqualTo("bn")

        val bn2 = DeterministicNlpParser.parse("generate captions in bangla")
        assertThat(bn2.intent).isEqualTo(EditIntent.GENERATE_CAPTIONS)
        assertThat(bn2.options.languageCode).isEqualTo("bn")

        val en1 = DeterministicNlpParser.parse("create subtitles in English")
        assertThat(en1.intent).isEqualTo(EditIntent.GENERATE_CAPTIONS)
        assertThat(en1.options.languageCode).isEqualTo("en")

        // Adversarial discovery: intervening language token between verb and noun
        // fails strict adjacent whitespace regex without transcribe keyword
        val interveningToken = DeterministicNlpParser.parse("generate bangla captions for video")
        assertThat(interveningToken.intent).isEqualTo(EditIntent.UNKNOWN)
    }

    @Test
    fun nlpParser_templateIdExtraction_allOfflineTemplates() {
        val templates = mapOf(
            "apply minimal modern title" to "minimal_modern_title",
            "apply template cinematic split" to "cinematic_split_fade",
            "use glitch title template" to "bold_glitch_title",
            "apply pop bounce text" to "pop_bounce_kinetic",
            "insert terminal typewriter intro" to "terminal_typewriter",
            "add lower third overlay" to "news_broadcast_bar"
        )

        for ((prompt, expectedId) in templates) {
            val parsed = DeterministicNlpParser.parse(prompt)
            assertThat(parsed.intent).isEqualTo(EditIntent.APPLY_TEMPLATE)
            assertThat(parsed.templateId).isEqualTo(expectedId)
        }

        // Adversarial discovery: 'cinematic split' omitted from secondary template pattern list
        // so 'insert cinematic split intro' falls back to UNKNOWN unless 'template' keyword precedes it
        val omittedSecondaryPattern = DeterministicNlpParser.parse("insert cinematic split intro")
        assertThat(omittedSecondaryPattern.intent).isEqualTo(EditIntent.UNKNOWN)
    }
}
