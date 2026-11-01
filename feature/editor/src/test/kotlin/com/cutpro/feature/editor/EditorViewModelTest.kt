package com.cutpro.feature.editor

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ColorAdjustments
import com.cutpro.core.model.LutFilterType
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.SpeedCurve
import com.cutpro.core.model.SpeedCurvePreset
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: EditorViewModel
    private lateinit var sampleProject: ProjectManifest
    private lateinit var sampleClip: TimelineClip

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = EditorViewModel(projectDao = null)

        val media = MediaSourceInfo(
            uriString = "content://media/1",
            fileName = "sample.mp4",
            mimeType = "video/mp4",
            durationUs = 10_000_000L
        )
        sampleClip = TimelineClip(
            id = "clip_1",
            trackId = "track_v1",
            mediaSource = media,
            timelineStartUs = 0L,
            timelineDurationUs = 6_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 6_000_000L
        )
        val track = TimelineTrack(
            id = "track_v1",
            name = "Video 1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(sampleClip)
        )
        sampleProject = ProjectManifest(
            id = "proj_test",
            title = "Sample Video",
            durationUs = 6_000_000L,
            tracks = listOf(track)
        )
        viewModel.setProject(sampleProject)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_undoAndRedoDisabled() {
        val state = viewModel.uiState.value
        assertThat(state.canUndo).isFalse()
        assertThat(state.canRedo).isFalse()
        assertThat(state.selectedClip).isNull()
        assertThat(state.isInspectorCollapsed).isTrue()
    }

    @Test
    fun playhead_clampingToDurationAndNegative() {
        viewModel.setPlayheadUs(7_000_000L, isScrubbing = false)
        assertThat(viewModel.uiState.value.playheadUs).isEqualTo(6_000_000L)

        viewModel.setPlayheadUs(-100_000L, isScrubbing = false)
        assertThat(viewModel.uiState.value.playheadUs).isEqualTo(0L)
    }

    @Test
    fun timecodeFormatting_matchesSpec() {
        viewModel.setPlayheadUs(3_750_000L, isScrubbing = false)
        assertThat(viewModel.uiState.value.formattedTimecode).isEqualTo("00:03:22")
    }

    @Test
    fun zoomLevel_clampedBetween10And500Percent() {
        viewModel.setZoomLevel(0.05f)
        assertThat(viewModel.uiState.value.zoomLevel).isEqualTo(0.1f)

        viewModel.setZoomLevel(6.0f)
        assertThat(viewModel.uiState.value.zoomLevel).isEqualTo(5.0f)

        viewModel.setZoomLevel(2.5f)
        assertThat(viewModel.uiState.value.zoomLevel).isEqualTo(2.5f)
    }

    @Test
    fun inspectorTabs_forVideoClip_has8Tabs() {
        viewModel.selectClip(sampleClip, "track_v1", TrackType.VIDEO)
        val state = viewModel.uiState.value
        assertThat(state.isInspectorCollapsed).isFalse()
        assertThat(state.getInspectorTabsForSelectedClip()).containsExactly(
            "Transform", "Color", "Filters", "Mask & Blend", "Speed Curve", "Audio", "Speed", "Cut"
        ).inOrder()
    }

    @Test
    fun inspectorTabs_forAudioClip_has3Tabs() {
        viewModel.selectClip(sampleClip, "track_a1", TrackType.AUDIO)
        val state = viewModel.uiState.value
        assertThat(state.getInspectorTabsForSelectedClip()).containsExactly("Volume", "Fades", "Speed").inOrder()
    }

    @Test
    fun inspectorTabs_forTextClip_has3Tabs() {
        viewModel.selectClip(sampleClip, "track_t1", TrackType.TEXT)
        val state = viewModel.uiState.value
        assertThat(state.getInspectorTabsForSelectedClip()).containsExactly("Typography", "Animation", "Style").inOrder()
    }

    @Test
    fun deselectClip_collapsesInspector() {
        viewModel.selectClip(sampleClip, "track_v1", TrackType.VIDEO)
        assertThat(viewModel.uiState.value.isInspectorCollapsed).isFalse()

        viewModel.deselectClip()
        assertThat(viewModel.uiState.value.isInspectorCollapsed).isTrue()
        assertThat(viewModel.uiState.value.selectedClip).isNull()
    }

    @Test
    fun splitSelectedClip_createsTwoClipsAndEnablesUndo() {
        viewModel.selectClip(sampleClip, "track_v1", TrackType.VIDEO)
        viewModel.setPlayheadUs(2_000_000L, isScrubbing = false)

        viewModel.splitSelectedClipAtPlayhead()

        val project = viewModel.uiState.value.project!!
        val clips = project.tracks.first().clips
        assertThat(clips).hasSize(2)
        assertThat(clips[0].timelineDurationUs).isEqualTo(2_000_000L)
        assertThat(clips[1].timelineDurationUs).isEqualTo(4_000_000L)
        assertThat(viewModel.uiState.value.canUndo).isTrue()

        // Undo split
        viewModel.undo()
        val revertedClips = viewModel.uiState.value.project!!.tracks.first().clips
        assertThat(revertedClips).hasSize(1)
        assertThat(revertedClips.first().id).isEqualTo(sampleClip.id)
    }

    @Test
    fun titleEditor_enforcesCharacterLimit() {
        val longTitle = "A".repeat(150)
        viewModel.updateProjectTitle(longTitle)
        assertThat(viewModel.uiState.value.project!!.title.length).isEqualTo(100)
    }

    @Test
    fun aspectRatioSwitcher_updatesProjectManifest() {
        viewModel.setAspectRatio(CanvasAspectRatio.RATIO_9_16)
        assertThat(viewModel.uiState.value.project!!.aspectRatio).isEqualTo(CanvasAspectRatio.RATIO_9_16)
        assertThat(viewModel.uiState.value.canUndo).isTrue()

        viewModel.undo()
        assertThat(viewModel.uiState.value.project!!.aspectRatio).isEqualTo(CanvasAspectRatio.RATIO_16_9)
    }

    @Test
    fun executeAiScript_autoEditsTimelineAndSupportsUndo() {
        val script = """
            SPLIT(track="track_v1", time="00:03.000")
            ADD_CAPTION(text="Bangla Caption", start="00:00.000", end="00:02.000", style="HIGHLIGHT_PILL", lang="bn")
        """.trimIndent()

        val result = viewModel.executeAiScript(script)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(2)

        val updatedProject = viewModel.uiState.value.project!!
        val videoTrack = updatedProject.tracks.first { it.id == "track_v1" }
        assertThat(videoTrack.clips).hasSize(2)
        assertThat(viewModel.uiState.value.canUndo).isTrue()

        // Verify user can tweak interactively: Undo rolls back
        viewModel.undo()
        val revertedProject = viewModel.uiState.value.project!!
        val revertedVideo = revertedProject.tracks.first { it.id == "track_v1" }
        assertThat(revertedVideo.clips).hasSize(1)
    }

    @Test
    fun updateClipColorAndFilter_mutatesStateCleanly() {
        viewModel.selectClip(sampleClip, "track_v1", TrackType.VIDEO)

        val adjustments = ColorAdjustments(exposure = 0.5f, contrast = 1.2f, saturation = 1.3f)
        viewModel.updateClipColor(adjustments)

        val updatedClip = viewModel.uiState.value.selectedClip!!
        assertThat(updatedClip.colorAdjustments.exposure).isEqualTo(0.5f)
        assertThat(updatedClip.colorAdjustments.contrast).isEqualTo(1.2f)

        viewModel.updateClipFilter(LutFilterType.CINEMATIC_TEAL_ORANGE)
        assertThat(viewModel.uiState.value.selectedClip!!.filterType).isEqualTo(LutFilterType.CINEMATIC_TEAL_ORANGE)
    }

    @Test
    fun sheetVisibility_togglesCorrectly() {
        assertThat(viewModel.uiState.value.isPromptSheetVisible).isFalse()
        viewModel.showPromptSheet()
        assertThat(viewModel.uiState.value.isPromptSheetVisible).isTrue()
        viewModel.hidePromptSheet()
        assertThat(viewModel.uiState.value.isPromptSheetVisible).isFalse()

        assertThat(viewModel.uiState.value.isCodeRunnerSheetVisible).isFalse()
        viewModel.showCodeRunnerSheet()
        assertThat(viewModel.uiState.value.isCodeRunnerSheetVisible).isTrue()
        viewModel.hideCodeRunnerSheet()
        assertThat(viewModel.uiState.value.isCodeRunnerSheetVisible).isFalse()
    }
}
