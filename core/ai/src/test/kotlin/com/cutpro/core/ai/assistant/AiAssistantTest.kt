package com.cutpro.core.ai.assistant

import com.cutpro.core.ai.model.AdjustSpeedAction
import com.cutpro.core.ai.model.AnalysisOptions
import com.cutpro.core.ai.model.ApplyTemplateAction
import com.cutpro.core.ai.model.DeleteSilenceAction
import com.cutpro.core.ai.model.MuteNoiseAction
import com.cutpro.core.ai.model.PushInZoomAction
import com.cutpro.core.ai.model.SuggestionType
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AiAssistantTest {

    private val dummySource = MediaSourceInfo(
        uriString = "content://media/external/video/sample",
        fileName = "sample.mp4",
        mimeType = "video/mp4",
        durationUs = 60_000_000L,
        width = 1920,
        height = 1080
    )

    private fun createProjectWithVideoAndAudio(): ProjectManifest {
        val videoClip = TimelineClip(
            id = "clip-v1",
            trackId = "track-video",
            mediaSource = dummySource,
            timelineStartUs = 0L,
            timelineDurationUs = 12_000_000L, // 12 seconds > 8 seconds
            sourceInUs = 0L,
            sourceOutUs = 12_000_000L
        )
        val videoTrack = TimelineTrack(
            id = "track-video",
            name = "Video Track",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(videoClip)
        )

        val audioClip = TimelineClip(
            id = "clip-a1",
            trackId = "track-audio",
            mediaSource = dummySource,
            timelineStartUs = 0L,
            timelineDurationUs = 12_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 12_000_000L
        )
        val audioTrack = TimelineTrack(
            id = "track-audio",
            name = "Audio Track",
            type = TrackType.AUDIO,
            zIndex = 1,
            clips = listOf(audioClip)
        )

        return ProjectManifest(
            title = "Assistant Test Project",
            tracks = listOf(videoTrack, audioTrack),
            durationUs = 12_000_000L
        )
    }

    @Test
    fun analyzeTimeline_detectsPacingAndSilenceSuggestions() = runTest {
        val project = createProjectWithVideoAndAudio()

        // Synthesize audio with speech and a 1s silence in the middle
        val sampleRate = 48000
        val totalSamples = sampleRate * 12
        val audioSamples = ShortArray(totalSamples) { i ->
            if (i in (sampleRate * 4)..(sampleRate * 5)) {
                0
            } else {
                (sin(2.0 * PI * 440.0 * i / sampleRate) * 16000.0).toInt().toShort()
            }
        }

        val assistant = StandardAiAssistant(
            audioSamplesProvider = { _ -> audioSamples }
        )

        val suggestionsResult = assistant.analyzeTimeline(project, AnalysisOptions.DEFAULT)
        assertThat(suggestionsResult.isSuccess).isTrue()

        val suggestions = suggestionsResult.getOrThrow()
        assertThat(suggestions).isNotEmpty()

        val pacingSuggestions = suggestions.filter { it.type == SuggestionType.PACING_ADJUSTMENT }
        assertThat(pacingSuggestions).isNotEmpty()

        val silenceSuggestions = suggestions.filter { it.type == SuggestionType.SILENCE_REMOVAL }
        assertThat(silenceSuggestions).isNotEmpty()
    }

    @Test
    fun generateEditPlan_silenceRemovalPrompt() = runTest {
        val project = createProjectWithVideoAndAudio()
        val assistant = StandardAiAssistant()

        val planResult = assistant.generateEditPlan("remove all silences", project)
        assertThat(planResult.isSuccess).isTrue()

        val plan = planResult.getOrThrow()
        assertThat(plan.title).contains("Silence Removal")
    }

    @Test
    fun generateEditPlan_speedUpPrompt() = runTest {
        val project = createProjectWithVideoAndAudio()
        val assistant = StandardAiAssistant()

        val planResult = assistant.generateEditPlan("speed up 1.5x", project)
        assertThat(planResult.isSuccess).isTrue()

        val plan = planResult.getOrThrow()
        assertThat(plan.title).contains("Speed Adjustment")
        assertThat(plan.actions).isNotEmpty()
        val speedAction = plan.actions.first() as AdjustSpeedAction
        assertThat(speedAction.targetSpeedMultiplier).isEqualTo(1.5f)
    }

    @Test
    fun generateEditPlan_applyTemplatePrompt() = runTest {
        val project = createProjectWithVideoAndAudio()
        val assistant = StandardAiAssistant()

        val planResult = assistant.generateEditPlan("apply minimal modern template", project)
        assertThat(planResult.isSuccess).isTrue()

        val plan = planResult.getOrThrow()
        assertThat(plan.title).contains("Template")
        assertThat(plan.actions).isNotEmpty()
        val templateAction = plan.actions.first() as ApplyTemplateAction
        assertThat(templateAction.templateId).isEqualTo("minimal_modern_title")
    }

    @Test
    fun generateEditPlan_muteNoisePrompt() = runTest {
        val project = createProjectWithVideoAndAudio()
        val assistant = StandardAiAssistant()

        val planResult = assistant.generateEditPlan("mute noise", project)
        assertThat(planResult.isSuccess).isTrue()

        val plan = planResult.getOrThrow()
        assertThat(plan.title).contains("Noise Suppression")
        assertThat(plan.actions).isNotEmpty()
        val muteAction = plan.actions.first() as MuteNoiseAction
        assertThat(muteAction.targetVolume).isEqualTo(0.0f)
    }
}
