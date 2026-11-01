package com.cutpro.feature.editor

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipAudioProperties
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Random

/**
 * Adversarial stress testing for UI state mutations and EditorViewModel state machine.
 * Validates:
 * 1. Immutability of EditorUiState under mutations
 * 2. Rapid interleaved state transitions (Selection, Playhead, Snapping, AspectRatio, Title)
 * 3. Concurrent coroutine dispatches without state machine corruption
 * 4. High-frequency Undo/Redo cycles through ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorStateMutationAdversarialTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: EditorViewModel
    private lateinit var sampleProject: ProjectManifest
    private lateinit var clip1: TimelineClip
    private lateinit var clip2: TimelineClip

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = EditorViewModel(projectDao = null)

        val media = MediaSourceInfo(
            uriString = "content://media/adversarial.mp4",
            fileName = "adversarial.mp4",
            mimeType = "video/mp4",
            durationUs = 20_000_000L
        )
        clip1 = TimelineClip(
            id = "clip_adv_1",
            trackId = "track_v1",
            mediaSource = media,
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L
        )
        clip2 = TimelineClip(
            id = "clip_adv_2",
            trackId = "track_v1",
            mediaSource = media,
            timelineStartUs = 5_000_000L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L
        )
        val track = TimelineTrack(
            id = "track_v1",
            name = "Video 1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip1, clip2)
        )
        sampleProject = ProjectManifest(
            id = "proj_adv_editor",
            title = "Adversarial Editor Project",
            durationUs = 10_000_000L,
            tracks = listOf(track)
        )
        viewModel.setProject(sampleProject)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_immutability_previousSnapshotsRemainUnchanged() {
        val initialSnapshot = viewModel.uiState.value

        // Perform multiple state modifications
        viewModel.setPlayheadUs(3_000_000L, isScrubbing = true)
        viewModel.setZoomLevel(3.0f)
        viewModel.toggleSnapping()
        viewModel.selectClip(clip1, "track_v1", TrackType.VIDEO)

        val updatedSnapshot = viewModel.uiState.value

        // Verify initialSnapshot was not mutated in place
        assertThat(initialSnapshot.playheadUs).isEqualTo(0L)
        assertThat(initialSnapshot.isScrubbing).isFalse()
        assertThat(initialSnapshot.zoomLevel).isEqualTo(1.0f)
        assertThat(initialSnapshot.isSnappingEnabled).isTrue()
        assertThat(initialSnapshot.selectedClip).isNull()

        // Verify updatedSnapshot reflects the new state
        assertThat(updatedSnapshot.playheadUs).isEqualTo(3_000_000L)
        assertThat(updatedSnapshot.isScrubbing).isTrue()
        assertThat(updatedSnapshot.zoomLevel).isEqualTo(3.0f)
        assertThat(updatedSnapshot.isSnappingEnabled).isFalse()
        assertThat(updatedSnapshot.selectedClip).isEqualTo(clip1)
    }

    @Test
    fun highFrequencyMutations_undoRedoConsistency() = runTest {
        // Perform 30 sequential mutations
        for (i in 1..30) {
            viewModel.updateProjectTitle("Title $i")
            testDispatcher.scheduler.advanceUntilIdle()
        }

        assertThat(viewModel.uiState.value.project!!.title).isEqualTo("Title 30")
        assertThat(viewModel.uiState.value.canUndo).isTrue()

        // Undo all 30
        for (i in 30 downTo 1) {
            viewModel.undo()
            testDispatcher.scheduler.advanceUntilIdle()
        }

        assertThat(viewModel.uiState.value.project!!.title).isEqualTo(sampleProject.title)
        assertThat(viewModel.uiState.value.canUndo).isFalse()
        assertThat(viewModel.uiState.value.canRedo).isTrue()

        // Redo all 30
        for (i in 1..30) {
            viewModel.redo()
            testDispatcher.scheduler.advanceUntilIdle()
        }

        assertThat(viewModel.uiState.value.project!!.title).isEqualTo("Title 30")
        assertThat(viewModel.uiState.value.canUndo).isTrue()
        assertThat(viewModel.uiState.value.canRedo).isFalse()
    }

    @Test
    fun playheadBoundaryStress_extremeValuesClampedGracefully() {
        // Extreme negative
        viewModel.setPlayheadUs(-999_999_999L, isScrubbing = false)
        assertThat(viewModel.uiState.value.playheadUs).isEqualTo(0L)

        // Extreme positive
        viewModel.setPlayheadUs(Long.MAX_VALUE, isScrubbing = false)
        assertThat(viewModel.uiState.value.playheadUs).isEqualTo(10_000_000L)

        // Frame step backward at 0 -> remains 0
        viewModel.setPlayheadUs(0L, isScrubbing = false)
        viewModel.stepFrameBackward(30)
        assertThat(viewModel.uiState.value.playheadUs).isEqualTo(0L)

        // Frame step forward near end -> clamps to duration
        viewModel.setPlayheadUs(9_990_000L, isScrubbing = false)
        viewModel.stepFrameForward(30)
        assertThat(viewModel.uiState.value.playheadUs).isEqualTo(10_000_000L)
    }

    @Test
    fun concurrentDispatches_stateFlowRemainsCoherent() = runTest {
        // Launch 20 concurrent coroutines dispatching rapid UI events
        val jobs = List(20) { index ->
            launch {
                when (index % 5) {
                    0 -> viewModel.setPlayheadUs((index * 500_000L) % 10_000_000L, isScrubbing = index % 2 == 0)
                    1 -> viewModel.setZoomLevel(0.1f + (index * 0.2f))
                    2 -> viewModel.toggleSnapping()
                    3 -> viewModel.toggleGuideLines()
                    4 -> {
                        if (index % 2 == 0) {
                            viewModel.selectClip(clip1, "track_v1", TrackType.VIDEO)
                        } else {
                            viewModel.deselectClip()
                        }
                    }
                }
            }
        }
        jobs.joinAll()
        testDispatcher.scheduler.advanceUntilIdle()

        // Invariant checks
        val finalState = viewModel.uiState.value
        assertThat(finalState.playheadUs).isAtLeast(0L)
        assertThat(finalState.playheadUs).isAtMost(10_000_000L)
        assertThat(finalState.zoomLevel).isAtLeast(0.1f)
        assertThat(finalState.zoomLevel).isAtMost(5.0f)
        assertThat(finalState.durationUs).isEqualTo(10_000_000L)
    }
}
