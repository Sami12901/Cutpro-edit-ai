package com.cutpro.core.ai.prompt

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ProjectManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptToPromptEngineTest {

    private val testProject = ProjectManifest(
        title = "Bangla Podcast Episode 1",
        aspectRatio = CanvasAspectRatio.RATIO_9_16,
        durationUs = 60_000_000L
    )

    private val sampleSegments = listOf(
        TranscriptSegment(
            startTimeUs = 0L,
            endTimeUs = 2_500_000L,
            text = "স্বাগতম সবাইকে আমাদের কাটপ্রো স্টুডিওতে!",
            speaker = "Host",
            language = "bn"
        ),
        TranscriptSegment(
            startTimeUs = 3_000_000L,
            endTimeUs = 7_000_000L,
            text = "Today we will explore AI video editing on Android.",
            speaker = "Guest",
            language = "en"
        )
    )

    @Test
    fun generatePrompt_viralShortReelContainsTranscriptAndDsl() {
        val result = TranscriptToPromptEngine.generatePrompt(
            project = testProject,
            segments = sampleSegments,
            preset = PromptObjectivePreset.VIRAL_SHORT_REEL
        )

        assertNotNull(result)
        assertEquals(PromptObjectivePreset.VIRAL_SHORT_REEL, result.preset)
        assertEquals(2, result.segmentCount)
        assertTrue(result.estimatedWordCount > 50)

        // Verify transcript is rendered
        assertTrue(result.fullPrompt.contains("স্বাগতম সবাইকে"))
        assertTrue(result.fullPrompt.contains("Today we will explore"))

        // Verify DSL syntax reference is included
        assertTrue(result.fullPrompt.contains("SPLIT"))
        assertTrue(result.fullPrompt.contains("DELETE_RANGE"))
        assertTrue(result.fullPrompt.contains("APPLY_ZOOM_PUNCH"))
        assertTrue(result.fullPrompt.contains("ADD_CAPTION"))
    }

    @Test
    fun generatePrompt_customInstructionsIncluded() {
        val custom = "Focus heavily on guest's emotional facial expressions and zoom in 1.3x."
        val result = TranscriptToPromptEngine.generatePrompt(
            project = testProject,
            segments = sampleSegments,
            preset = PromptObjectivePreset.CUSTOM,
            customInstructions = custom
        )

        assertTrue(result.fullPrompt.contains("User Custom Instructions"))
        assertTrue(result.fullPrompt.contains(custom))
    }
}

